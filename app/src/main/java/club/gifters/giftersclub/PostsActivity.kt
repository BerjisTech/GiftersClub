package club.gifters.giftersclub

import android.os.Bundle
import android.content.Intent
import club.gifters.giftersclub.AuthActivity
import club.gifters.giftersclub.AuthUtils
import androidx.appcompat.app.AppCompatActivity
import club.gifters.giftersclub.gifts.PostsFragment

class PostsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AuthUtils.getCurrentUserId(this) == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_posts)

        val uri = intent.data
        // Validate the URI scheme and host to prevent deep link hijacking
        if (uri != null && uri.scheme == "giftersclub" && uri.host == "post") {
            val postId = uri.lastPathSegment
            if (postId != null) {
                val fragment = PostsFragment().apply {
                    arguments = Bundle().apply {
                        putString("post_id", postId)
                    }
                }
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit()
            }
        }
    }
}