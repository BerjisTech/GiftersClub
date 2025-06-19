package club.gifters.giftersclub.chat

import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.chat.ConversationAdapter
import club.gifters.giftersclub.chat.ConversationUi
import club.gifters.giftersclub.chat.ChatFragment
import club.gifters.giftersclub.network.ChatApi
import club.gifters.giftersclub.network.ProfileApi
import club.gifters.giftersclub.network.RetrofitClient
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Displays the list of conversations for the current user.
 */
class ConversationListFragment : Fragment(R.layout.fragment_conversation_list) {
    private val chatApi: ChatApi = RetrofitClient.chatApi
    private val profileApi: ProfileApi = RetrofitClient.profileApi
    private var userId: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Decode current user ID from stored JWT
        requireContext().getSharedPreferences("supabase", Context.MODE_PRIVATE)
            .getString("access_token", "")
            ?.takeIf { it.isNotBlank() }
            ?.let { token ->
                token.split('.').getOrNull(1)?.let { payload ->
                    val json = String(Base64.decode(payload, Base64.URL_SAFE))
                    userId = JSONObject(json).optString("sub")
                }
            }

        val rv = view.findViewById<RecyclerView>(R.id.rvConversations)
        rv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        val adapter = ConversationAdapter { conv ->
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainContentContainer,
                    ChatFragment.newInstance(
                        conv.partner.userId,
                        conv.partner.name ?: conv.partner.username
                    )
                )
                .addToBackStack(null)
                .commit()
        }
        rv.adapter = adapter
        loadConversations(adapter)
    }

    private fun loadConversations(adapter: ConversationAdapter) {
        lifecycleScope.launch {
            try {
                val overviews = chatApi.getConversations(
                    select = "user_a,user_b,last_message_at",
                    userIdFilter = "or(user_a.eq.$userId,user_b.eq.$userId)"
                )
                val list = mutableListOf<ConversationUi>()
                for (ov in overviews) {
                    val partnerId = if (ov.userA == userId) ov.userB else ov.userA
                    val profile = profileApi.getProfileByUserId("*", "eq.$partnerId")[0]
                    val lastMsg = chatApi.getLastMessage(
                        select = "*",
                        orFilter = "and(sender_id.eq.$userId,receiver_id.eq.$partnerId)," +
                                   "and(sender_id.eq.$partnerId,receiver_id.eq.$userId)",
                        order = "created_at.desc"
                    ).firstOrNull()
                    val unreadResp = chatApi.getUnreadCount(
                        orFilter = "and(sender_id.eq.$partnerId,receiver_id.eq.$userId)"
                    )
                    val header = unreadResp.headers()["Content-Range"]
                    val unreadCount = header?.substringAfterLast('/')?.toIntOrNull() ?: 0
                    list.add(ConversationUi(ov, profile, lastMsg, unreadCount))
                }
                adapter.submitList(list)
            } catch (_: Exception) {
            }
        }
    }
}