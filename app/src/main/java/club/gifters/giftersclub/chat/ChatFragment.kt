package club.gifters.giftersclub.chat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.ChatApi
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.AwsConfig
import club.gifters.giftersclub.network.PresignRequest
import okhttp3.Request
import retrofit2.HttpException
import club.gifters.giftersclub.chat.ConversationAdapter
import club.gifters.giftersclub.chat.ConversationUi
import androidx.recyclerview.widget.ConcatAdapter
import club.gifters.giftersclub.chat.ChatHeaderAdapter
import club.gifters.giftersclub.chat.ChatHeaderAdapter.HeaderType
import club.gifters.giftersclub.social.FriendsFragment
import club.gifters.giftersclub.chat.NotificationListFragment
import club.gifters.giftersclub.chat.SystemNotificationsFragment
import club.gifters.giftersclub.chat.MessageAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import club.gifters.giftersclub.model.ConversationDetails
import club.gifters.giftersclub.model.ConversationOverview
import club.gifters.giftersclub.model.Message
import club.gifters.giftersclub.model.Profile

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

    private val gson = Gson()
    private val prefsName = "chat_prefs"
    private val prefsKeyConversations = "chatConversations"

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
        Log.w("ChatFragment", "Current userId = $userId")
        Log.w("ChatFragment", "Current userId = $userId")
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
            chatPane.findViewById<ImageButton>(R.id.btnAttach).setOnClickListener { pickAttachment() }
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
        // Otherwise show conversation list as usual
        val rvConvs = view.findViewById<RecyclerView>(R.id.rvConversations)
        rvConvs.layoutManager = LinearLayoutManager(requireContext())
        // Header items: New followers, Activity, System notifications
        val headerAdapter = ChatHeaderAdapter { type ->
            when (type) {
                ChatHeaderAdapter.HeaderType.NEW_FOLLOWERS ->
                    parentFragmentManager.beginTransaction()
                        .replace(
                            R.id.mainContentContainer,
                            FriendsFragment.newInstance(0)
                        )
                        .addToBackStack(null)
                        .commit()
                ChatHeaderAdapter.HeaderType.ACTIVITY ->
                    parentFragmentManager.beginTransaction()
                        .replace(
                            R.id.mainContentContainer,
                            NotificationListFragment()
                        )
                        .addToBackStack(null)
                        .commit()
                ChatHeaderAdapter.HeaderType.SYSTEM_NOTIFICATIONS ->
                    parentFragmentManager.beginTransaction()
                        .replace(
                            R.id.mainContentContainer,
                            SystemNotificationsFragment()
                        )
                        .addToBackStack(null)
                        .commit()
            }
        }
        val convAdapter = ConversationAdapter(userId) { conv ->
            // user tapped a conversation: show its chat pane
            view.findViewById<RecyclerView>(R.id.rvConversations).visibility = View.GONE
            val chatPane = view.findViewById<ConstraintLayout>(R.id.chatPane)
            chatPane.visibility = View.VISIBLE

            val rvMsgs = chatPane.findViewById<RecyclerView>(R.id.rvMessages)
            rvMsgs.layoutManager = LinearLayoutManager(requireContext())
            val innerMsgAdapter = MessageAdapter(userId)
            rvMsgs.adapter = innerMsgAdapter

            val etMsg = chatPane.findViewById<EditText>(R.id.etMessage)
            chatPane.findViewById<ImageButton>(R.id.btnAttach).setOnClickListener { pickAttachment() }
            chatPane.findViewById<ImageButton>(R.id.btnSend).setOnClickListener {
                sendMessage(innerMsgAdapter, rvMsgs, etMsg, conv.partner.userId)
            }

            selectConversation(
                conv.partner.userId,
                conv.partner.name ?: conv.partner.username,
                innerMsgAdapter,
                rvMsgs
            )
        }
        rvConvs.adapter = ConcatAdapter(headerAdapter, convAdapter)
        loadConversations(convAdapter)

        convsPollingJob?.cancel()
        convsPollingJob = lifecycleScope.launch {
            while (isActive) {
                delay(1000)
                try {
                    val convs = chatApi.getConversationDetails()
                    val uiModels = convs.mapNotNull { detail ->
                        val profile = Profile(
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
                        )
                        val lastMsg = detail.lastMessageContent?.let { content ->
                            Message(
                                id = detail.lastMessageId ?: "",
                                senderId = userId,
                                receiverId = detail.partnerId,
                                content = content,
                                createdAt = detail.lastMessageAt,
                                attachments = detail.lastMessageAttachments.orEmpty()
                            )
                        }
                        val overview = ConversationOverview(detail.userA, detail.userB, detail.lastMessageAt)
                        ConversationUi(overview, profile, lastMsg, detail.unreadCount)
                    }
                    val valid = uiModels.filter { it.lastMessage != null }
                    val sorted = valid.sortedByDescending { it.overview.lastMessageAt }
                    val deduped = sorted.distinctBy { it.partner.userId }
                    convAdapter.submitList(deduped)
                } catch (_: Exception) {
                }
            }
        }

        // attachment preview controls
        val attachmentPreviewContainer = view.findViewById<FrameLayout>(R.id.attachmentPreviewContainer)
        val ivAttachmentPreview = view.findViewById<ImageView>(R.id.ivAttachmentPreview)
        val pbAttachmentUpload = view.findViewById<ProgressBar>(R.id.pbAttachmentUpload)
        val btnCancelAttachment = view.findViewById<ImageButton>(R.id.btnCancelAttachment)
        btnCancelAttachment.setOnClickListener {
            uploadJob?.cancel()
            attachmentPreviewContainer.isVisible = false
            selectedAttachment = null
        }
    }


    /**
     * Load the list of conversation overviews and bind to adapter.
     */
    private fun loadConversations(adapter: ConversationAdapter) {
        val prefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val cacheKey = "chatConversations-$userId"
        prefs.getString(cacheKey, null)?.let { cachedJson ->
            try {
                val type = object : TypeToken<List<ConversationUi>>() {}.type
                val cachedList: List<ConversationUi> = gson.fromJson(cachedJson, type)
                val validCache = cachedList.filter { it.lastMessage != null }
                val sortedCache = validCache.sortedByDescending { it.overview.lastMessageAt }
                val dedupedCache = sortedCache.distinctBy { it.partner.userId }
                adapter.submitList(dedupedCache)
            } catch (_: Exception) { }
        }
        lifecycleScope.launch {
            try {
                val convs = chatApi.getConversationDetails()
                Log.w("ChatFragment", "Fetched conversations: ${convs.size}")
                val uiModels = convs.mapNotNull { detail ->
                    val profile = Profile(
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
                    )
                    val lastMsg = detail.lastMessageContent?.let { content ->
                        Message(
                            id = detail.lastMessageId ?: "",
                            senderId = userId,
                            receiverId = detail.partnerId,
                            content = content,
                            createdAt = detail.lastMessageAt,
                            attachments = detail.lastMessageAttachments.orEmpty()
                        )
                    }
                    val overview = ConversationOverview(detail.userA, detail.userB, detail.lastMessageAt)
                    ConversationUi(overview, profile, lastMsg, detail.unreadCount)
                }
                val valid = uiModels.filter { it.lastMessage != null }
                val sorted = valid.sortedByDescending { it.overview.lastMessageAt }
                val deduped = sorted.distinctBy { it.partner.userId }
                adapter.submitList(deduped)
                
                try {
                    prefs.edit().putString(cacheKey, gson.toJson(deduped)).apply()
                } catch (_: Exception) { }
            } catch (e: Exception) {
                Log.w("ChatFragment", "Error loading conversations", e)
            }
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
        requireActivity().title = partnerName
        val chatPane = requireView().findViewById<ConstraintLayout>(R.id.chatPane)
        chatPane.findViewById<TextView>(R.id.tvPartnerName).text = partnerName
        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch {
            var lastTimestamp: String? = null
            try {
                val initialMsgs = chatApi.getMessages(
                    select = "*",
                    orFilter = "(and(sender_id.eq.$userId,receiver_id.eq.$partnerId)," +
                               "and(sender_id.eq.$partnerId,receiver_id.eq.$userId))",
                    order = "created_at.asc"
                )
                msgAdapter.submitList(initialMsgs)
                rvMessages.scrollToPosition(initialMsgs.size - 1)
                lastTimestamp = initialMsgs.lastOrNull()?.createdAt
            } catch (_: Exception) {
            }

            while (isActive) {
                delay(1000)
                try {
                    val newMsgs = chatApi.getMessages(
                        select = "*",
                        orFilter = "(and(sender_id.eq.$userId,receiver_id.eq.$partnerId)," +
                                   "and(sender_id.eq.$partnerId,receiver_id.eq.$userId))",
                        order = "created_at.asc",
                        createdAtFilter = lastTimestamp?.let { "gt.$it" }
                    )
                    if (newMsgs.isNotEmpty()) {
                        newMsgs.forEach { msgAdapter.addMessage(it) }
                        rvMessages.scrollToPosition(msgAdapter.itemCount - 1)
                        lastTimestamp = newMsgs.last().createdAt
                    }
                } catch (_: Exception) {
                }
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
                        val type = requireContext().contentResolver.getType(uri) ?: "application/octet-stream"
                        val ext = type.substringAfterLast('/', "bin")
                        val filename = "${System.currentTimeMillis()}-${UUID.randomUUID()}.$ext"
                        val bytes = withContext(Dispatchers.IO) {
                            requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        } ?: throw Exception("Failed to read attachment data")
                        val body = bytes.toRequestBody(type.toMediaTypeOrNull())
                        val presignResp = withContext(Dispatchers.IO) {
                            RetrofitClient.functionsApi.uploadMedia(
                                PresignRequest(fileName = filename, fileType = type, bucket = "post")
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
                requireView().findViewById<FrameLayout>(R.id.attachmentPreviewContainer).isVisible = false
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
                Log.w("ChatFragment", "Error loading conversations", e)
            }
        }
    }
}