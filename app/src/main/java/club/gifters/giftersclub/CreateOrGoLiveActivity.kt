package club.gifters.giftersclub

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.View
import club.gifters.giftersclub.gifts.CreatePostFragment
import club.gifters.giftersclub.live.LiveStreamActivity

class CreateOrGoLiveActivity : AppCompatActivity() {
    private lateinit var goLive: CardView
    private lateinit var createPost: CardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_create_or_go_live)
        goLive = findViewById(R.id.goLive)
        createPost = findViewById(R.id.createPost)
        val container = findViewById<View>(R.id.main)

        goLive.setOnClickListener {
            startActivity(Intent(this, LiveStreamActivity::class.java))
        }
        createPost.setOnClickListener {
            // Open full-screen CreatePostActivity instead of embedding fragment
            startActivity(Intent(this, club.gifters.giftersclub.gifts.CreatePostActivity::class.java))
        }

        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Show/hide menu cards when fragment back stack changes
        supportFragmentManager.addOnBackStackChangedListener {
            val isInCreatePost = supportFragmentManager.findFragmentById(R.id.main) is CreatePostFragment
            if (isInCreatePost) {
                // keep menu hidden under fragment
            } else {
                goLive.visibility = View.VISIBLE
                createPost.visibility = View.VISIBLE
            }
        }
    }
}
