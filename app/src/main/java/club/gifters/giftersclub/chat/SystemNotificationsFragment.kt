package club.gifters.giftersclub.chat

import android.content.Context
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Base64
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.NotificationApi
import club.gifters.giftersclub.network.RetrofitClient
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Fragment listing only system notifications (e.g., transactions).
 */
class SystemNotificationsFragment : Fragment(R.layout.fragment_notifications) {
    private val notificationApi: NotificationApi = RetrofitClient.notificationApi
    private var userId: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userId = decodeCurrentUserId()
        // Update toolbar title
        requireActivity().title = getString(R.string.system_notifications)

        val rv = view.findViewById<RecyclerView>(R.id.rvNotifications)
        rv.layoutManager = LinearLayoutManager(requireContext())
        val adapter = NotificationAdapter()
        rv.adapter = adapter

        lifecycleScope.launch {
            try {
                val list = notificationApi.getNotifications("*", "eq.$userId")
                // Filter for system notification types (e.g., transactions)
                val sys = list.filter { it.type == "transaction" || it.type == "withdrawal" }
                adapter.submitList(sys)
                for (note in sys) {
                    if (!note.isRead) {
                        notificationApi.markAsRead(
                            "eq.${note.id}", mapOf("is_read" to true)
                        )
                        note.isRead = true
                    }
                }
            } catch (_: Exception) {
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
}