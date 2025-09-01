@file:Suppress("DEPRECATION")

package club.gifters.giftersclub.chat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.ConversationOverview
import club.gifters.giftersclub.model.Message
import club.gifters.giftersclub.model.Profile
import club.gifters.giftersclub.network.ChatApi
import club.gifters.giftersclub.network.PresignRequest
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.network.NotificationApi
import club.gifters.giftersclub.chat.ChatListAdapter
import club.gifters.giftersclub.chat.ChatListItem
import club.gifters.giftersclub.chat.ChatListItem.HeaderType
import club.gifters.giftersclub.social.FriendsFragment
import club.gifters.giftersclub.chat.NotificationListFragment
import club.gifters.giftersclub.chat.SystemNotificationsFragment
import club.gifters.giftersclub.gifts.GifterFragment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.HttpException
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Fragment for displaying chat conversations and messages.
 * TODO: implement conversation list and message UI with media support.
 */
class ChatFragment : Fragment(R.layout.fragment_chat) {
    private val chatApi: ChatApi = RetrofitClient.chatApi

    private var userId: String = ""
    private var selectedAttachment: Uri? = null
    private var uploadJob: Job? = null
    private val REQUEST_ATTACHMENT = 3001
    private var pollingJob: Job? = null
    private var convsPollingJob: Job? = null
    private lateinit var notificationApi: NotificationApi
    private lateinit var chatListAdapter: ChatListAdapter


    companion object {
        private const val ARG_PARTNER_ID = "partner_id"
        private const val ARG_PARTNER_NAME = "partner_name"

        fun newInstance(partnerId: String, partnerName: String) = ChatFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_PARTNER_ID, partnerId)
                putString(ARG_PARTNER_NAME, partnerName)
            }
        }
    }

    private fun decodeCurrentUserId(): String {
        val token = requireContext().getSharedPreferences("supabase", Context.MODE_PRIVATE)
            .getString("access_token", "")
            ?.takeIf { it.isNotBlank() } ?: return ""
        return token.split('.').getOrNull(1)
            ?.let { String(Base64.decode(it, Base64.URL_SAFE)) }
            ?.let { JSONObject(it).optString("sub") } ?: ""
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userId = decodeCurrentUserId()
        // Intercept back press to toggle between conversation list and chat pane
        val backCallback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val convoList = view.findViewById<RecyclerView>(R.id.rvConversations)
                val chatPane = view.findViewById<ConstraintLayout>(R.id.chatPane)
                if (chatPane.isVisible) {
                    chatPane.isVisible = false
                    convoList.isVisible = true
                } else {
                    isEnabled = false
                    requireActivity().onBackPressed()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        // Log.w("ChatFragment", "Current userId = $userId")
        // Log.w("ChatFragment", "Current userId = $userId")
        // Update toolbar title
        requireActivity().title = getString(R.string.conversations)

        // If opened with a partnerId arg, go straight to that chat (skip conv list)
        val partnerIdArg = arguments?.getString(ARG_PARTNER_ID)
        val partnerNameArg = arguments?.getString(ARG_PARTNER_NAME)
        if (!partnerIdArg.isNullOrBlank() && !partnerNameArg.isNullOrBlank()) {
            view.findViewById<RecyclerView>(R.id.rvConversations).visibility = View.GONE
            val chatPane = view.findViewById<ConstraintLayout>(R.id.chatPane)
            chatPane.visibility = View.VISIBLE

            val rvMessages = chatPane.findViewById<RecyclerView>(R.id.rvMessages)
            rvMessages.layoutManager = LinearLayoutManager(requireContext())
            val msgAdapter = MessageAdapter(userId)
            rvMessages.adapter = msgAdapter

            val etMessage = chatPane.findViewById<EditText>(R.id.etMessage)
            chatPane.findViewById<ImageButton>(R.id.btnAttach)
                .setOnClickListener { pickAttachment() }
            chatPane.findViewById<ImageButton>(R.id.btnSend).setOnClickListener {
                sendMessage(msgAdapter, rvMessages, etMessage, partnerIdArg)
            }

            selectConversation(
                partnerIdArg!!,
                partnerNameArg!!,
                msgAdapter,
                rvMessages
            )
            return
        }
        // Initialize chat list (notifications now in accordion)
        val rvConvs = view.findViewById<RecyclerView>(R.id.rvConversations)
        rvConvs.layoutManager = LinearLayoutManager(requireContext())
        // Prepare APIs for notifications
        notificationApi = RetrofitClient.notificationApi
        // Adapter merging header entries and conversations
        chatListAdapter = ChatListAdapter(
            onHeaderClick = { _ -> /* headers handled by accordion */ },
            onConversationClick = { ui ->
                // user tapped a conversation: show its chat pane
                rvConvs.visibility = View.GONE
                val chatPane = view.findViewById<ConstraintLayout>(R.id.chatPane)
                chatPane.visibility = View.VISIBLE

                val rvMsgs = chatPane.findViewById<RecyclerView>(R.id.rvMessages)
                rvMsgs.layoutManager = LinearLayoutManager(requireContext())
                val innerMsgAdapter = MessageAdapter(userId)
                rvMsgs.adapter = innerMsgAdapter

                val etMsg = chatPane.findViewById<EditText>(R.id.etMessage)
                chatPane.findViewById<ImageButton>(R.id.btnAttach)
                    .setOnClickListener { pickAttachment() }
                chatPane.findViewById<ImageButton>(R.id.btnSend).setOnClickListener {
                    sendMessage(innerMsgAdapter, rvMsgs, etMsg, ui.partner.userId)
                }

                selectConversation(
                    ui.partner.userId,
                    ui.partner.username,
                    innerMsgAdapter,
                    rvMsgs
                )
            }
        )
        rvConvs.adapter = chatListAdapter
        // initial placeholder
        chatListAdapter.submitList(listOf(ChatListItem.Empty))
        loadChatList()

        // Poll every few seconds to refresh chats and notifications
        convsPollingJob?.cancel()
        convsPollingJob = lifecycleScope.launch {
            while (isActive) {
                delay(5000)
                loadChatList()
            }
        }

        // attachment preview controls
        val attachmentPreviewContainer =
            view.findViewById<FrameLayout>(R.id.attachmentPreviewContainer)
        view.findViewById<ImageView>(R.id.ivAttachmentPreview)
        view.findViewById<ProgressBar>(R.id.pbAttachmentUpload)
        val btnCancelAttachment = view.findViewById<ImageButton>(R.id.btnCancelAttachment)
        btnCancelAttachment.setOnClickListener {
            uploadJob?.cancel()
            attachmentPreviewContainer.isVisible = false
            selectedAttachment = null
        }

        // Swipe-to-refresh
        val swipe = view.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)
        swipe.setOnRefreshListener { loadChatList { swipe.isRefreshing = false } }

        // Accordion setup
        val accordionHeader = view.findViewById<View>(R.id.accordionHeader)
        val accordionCaret = view.findViewById<TextView>(R.id.tvAccordionCaret)
        val accordionContent = view.findViewById<View>(R.id.accordionContent)
        val rowFollowers = view.findViewById<View>(R.id.rowNewFollowers)
        val rowActivity = view.findViewById<View>(R.id.rowActivity)
        val rowSystem = view.findViewById<View>(R.id.rowSystem)
        fun toggleAccordion() {
            val showing = accordionContent.isVisible
            accordionContent.isVisible = !showing
            accordionCaret.text = if (showing) ">" else "v"
        }
        accordionHeader.setOnClickListener { toggleAccordion() }
        rowFollowers.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainContentContainer, FriendsFragment.newInstance(0))
                .addToBackStack(null).commit()
        }
        rowActivity.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainContentContainer, NotificationListFragment())
                .addToBackStack(null).commit()
        }
        rowSystem.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainContentContainer, SystemNotificationsFragment())
                .addToBackStack(null).commit()
        }
    }


    /**
     * Load and merge static notification headers and chat conversations,
     * then display in a single sorted list.
     */
    /**
     * Safe ISO Instant parser supporting offsets/fractions.
     */
    private fun parseInstant(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return try {
            Instant.parse(raw)
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(raw).toInstant()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun loadChatList(done: (() -> Unit)? = null) {
        lifecycleScope.launch {
            // compute header timestamps, but never fail entire load
            val notes = try {
                notificationApi.getNotifications("*", "eq.$userId")
            } catch (_: Exception) {
                emptyList()
            }
            val followNotes = notes.filter { it.type == "follow" || it.type == "friend_request" }
            val cutoff = Instant.now().minusSeconds(24 * 3600)
            val followCount =
                followNotes.count { parseInstant(it.createdAt)?.isAfter(cutoff) == true }
            val activityNotes = notes.filter {
                it.type != "transaction" && it.type != "withdrawal" && it.type != "follow" && it.type != "friend_request"
            }
            val activityCount =
                activityNotes.count { parseInstant(it.createdAt)?.isAfter(cutoff) == true }
            val systemNotes = notes.filter { it.type == "transaction" || it.type == "withdrawal" }
            val lastFollow =
                followNotes.maxOfOrNull { parseInstant(it.createdAt)?.toEpochMilli() ?: 0L } ?: 0L
            val lastActivity =
                activityNotes.maxOfOrNull { parseInstant(it.createdAt)?.toEpochMilli() ?: 0L } ?: 0L
            val lastSystem =
                systemNotes.maxOfOrNull { parseInstant(it.createdAt)?.toEpochMilli() ?: 0L } ?: 0L

            // fetch conversation overviews, but continue on error
            val sortedConvs = try {
                chatApi.getConversationDetails().mapNotNull { detail ->
                    detail.lastMessageContent?.let { content ->
                        ConversationUi(
                            ConversationOverview(detail.userA, detail.userB, detail.lastMessageAt),
                            Profile(
                                id = detail.partnerId,
                                userId = detail.partnerId,
                                email = null,
                                username = detail.partnerName ?: "",
                                name = detail.partnerName,
                                bio = null,
                                image = detail.partnerImage ?: "",
                                tokenBalance = null,
                                tokensReceived = null,
                                tokensSent = null,
                                followersCount = null,
                                followingCount = null,
                                isFollowing = null,
                                gifterLevel = null,
                                gifterLevelName = null,
                                giftsSent = null,
                                giftsReceived = null
                            ),
                            Message(
                                id = detail.lastMessageId.orEmpty(),
                                senderId = userId,
                                receiverId = detail.partnerId,
                                content = content,
                                createdAt = detail.lastMessageAt,
                                attachments = detail.lastMessageAttachments.orEmpty()
                            ),
                            detail.unreadCount
                        )
                    }
                }.sortedByDescending {
                    parseInstant(it.overview.lastMessageAt)?.toEpochMilli() ?: 0L
                }
                    .distinctBy { it.partner.userId }
            } catch (_: Exception) {
                emptyList()
            }

            // Update accordion timestamps
            fun rel(ts: Long): String = if (ts > 0L) {
                android.text.format.DateUtils.getRelativeTimeSpanString(
                    ts, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS
                ).toString()
            } else ""
            view?.findViewById<TextView>(R.id.tvNewFollowersTime)?.text = rel(lastFollow)
            view?.findViewById<TextView>(R.id.tvActivityTime)?.text = rel(lastActivity)
            view?.findViewById<TextView>(R.id.tvSystemTime)?.text = rel(lastSystem)

            // Only conversations in the list now
            val items = if (sortedConvs.isEmpty()) listOf(ChatListItem.Empty)
            else sortedConvs.map { ChatListItem.Conversation(it) }
            chatListAdapter.submitList(items)
            done?.invoke()
        }
    }

    /**
     * Select a conversation to display messages and set up chat UI.
     */
    private fun selectConversation(
        partnerId: String,
        partnerName: String,
        msgAdapter: MessageAdapter,
        rvMessages: RecyclerView
    ) {
        lifecycleScope.launch {
            val partnerProfile =
                RetrofitClient.profileApi.getProfileByUserId("*", "eq.$partnerId").firstOrNull()
                    ?: throw IllegalArgumentException("No profile found for userId: $partnerId")
            val partnerUserName = partnerProfile.username
            requireActivity().title = partnerName
            val chatPane = requireView().findViewById<ConstraintLayout>(R.id.chatPane)
            val tvPartnerUserName = chatPane.findViewById<TextView>(R.id.tvPartnerName)
            tvPartnerUserName.text = partnerUserName
            tvPartnerUserName.setOnClickListener {
                // Open partner profile when tapped
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.mainContentContainer, GifterFragment.newInstance(partnerUserName))
                    .addToBackStack(null).commit()
            }
            // The rest of your logic that depends on partnerProfile should go here
        }
        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch {
            // mark unread messages as read on first load
            try {
                val resp = chatApi.markMessagesAsRead(
                    senderFilter = "sender_id.eq.$partnerId",
                    receiverFilter = "receiver_id.eq.$userId",
                    readFilter = "is.null",
                    updates = mapOf("read_at" to Instant.now().toString())
                )
                if (resp.isSuccessful) {
                    loadChatList()
                }
            } catch (e: Exception) {
                // Log.w("ChatFragment", "Error marking messages as read", e)
            }

            while (isActive) {
                try {
                    val msgs = chatApi.getMessages(
                        select = "*",
                        orFilter = "(and(sender_id.eq.$userId,receiver_id.eq.$partnerId)," +
                                "and(sender_id.eq.$partnerId,receiver_id.eq.$userId))",
                        order = "created_at.asc"
                    )
                    msgAdapter.submitList(msgs)
                    if (msgs.isNotEmpty()) {
                        // rvMessages.scrollToPosition(msgs.size - 1)
                    }
                } catch (e: Exception) {
                    // Log.w("ChatFragment", "Error polling messages", e)
                }
                delay(1000)
            }
        }
    }

    private fun pickAttachment() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        }
        startActivityForResult(intent, REQUEST_ATTACHMENT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ATTACHMENT && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                selectedAttachment = uri
                view?.findViewById<FrameLayout>(R.id.attachmentPreviewContainer)?.isVisible = true
                view?.findViewById<ImageView>(R.id.ivAttachmentPreview)?.setImageURI(uri)
            }
        }
    }

    private fun sendMessage(
        msgAdapter: MessageAdapter,
        rvMessages: RecyclerView,
        etMessage: EditText,
        partnerId: String
    ) {
        val content = etMessage.text.toString().trim()
        lifecycleScope.launch {
            val attachmentsPayload = mutableListOf<Map<String, Any>>()
            selectedAttachment?.let { uri ->
                requireView().findViewById<ProgressBar>(R.id.pbAttachmentUpload).isVisible = true
                val job = lifecycleScope.launch {
                    try {
                        val type = requireContext().contentResolver.getType(uri)
                            ?: "application/octet-stream"
                        val ext = type.substringAfterLast('/', "bin")
                        val filename = "${System.currentTimeMillis()}-${UUID.randomUUID()}.$ext"
                        val bytes = withContext(Dispatchers.IO) {
                            requireContext().contentResolver.openInputStream(uri)
                                ?.use { it.readBytes() }
                        } ?: throw Exception("Failed to read attachment data")
                        val body = bytes.toRequestBody(type.toMediaTypeOrNull())
                        val presignResp = withContext(Dispatchers.IO) {
                            RetrofitClient.functionsApi.uploadMedia(
                                PresignRequest(
                                    fileName = filename,
                                    fileType = type,
                                    bucket = "post"
                                )
                            )
                        }
                        if (!presignResp.isSuccessful) throw HttpException(presignResp)
                        val presignData = presignResp.body()!!
                        val putReq = Request.Builder()
                            .url(presignData.uploadUrl)
                            .put(body)
                            .build()
                        val putResp = withContext(Dispatchers.IO) {
                            RetrofitClient.awsClient.newCall(putReq).execute()
                        }
                        if (!putResp.isSuccessful) throw Exception("Upload failed: ${putResp.code}")
                        val publicUrl = presignData.publicUrl
                        val mediaType = if (type.startsWith("image/")) "image" else "video"
                        attachmentsPayload.add(mapOf("url" to publicUrl, "type" to mediaType))
                    } catch (_: CancellationException) {
                    } catch (_: Exception) {
                    }
                }
                uploadJob = job
                job.join()
                requireView().findViewById<ProgressBar>(R.id.pbAttachmentUpload).isVisible = false
                requireView().findViewById<FrameLayout>(R.id.attachmentPreviewContainer).isVisible =
                    false
                selectedAttachment = null
            }
            try {
                val payload = mutableMapOf<String, Any>(
                    "sender_id" to userId,
                    "receiver_id" to partnerId,
                    "content" to content
                )
                if (attachmentsPayload.isNotEmpty()) payload["attachments"] = attachmentsPayload
                val resp = chatApi.sendMessage(payload)
                if (resp.isSuccessful) {
                    resp.body()?.firstOrNull()?.let { newMsg ->
                        msgAdapter.addMessage(newMsg)
                        rvMessages.scrollToPosition(msgAdapter.itemCount - 1)
                    }
                    etMessage.text.clear()
                }
            } catch (e: Exception) {
                // Log.w("ChatFragment", "Error loading conversations", e)
            }
        }
    }
}
