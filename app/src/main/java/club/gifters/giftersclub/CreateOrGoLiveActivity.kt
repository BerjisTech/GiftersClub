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
        val container = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.main)

        goLive.setOnClickListener {
            startActivity(Intent(this, LiveStreamActivity::class.java))
        }
        createPost.setOnClickListener {
            // hide the menu options and show the CreatePostFragment in this container
            goLive.visibility = View.GONE
            createPost.visibility = View.GONE
            findViewById<View>(R.id.midWay).visibility = View.GONE
            supportFragmentManager.beginTransaction()
                .replace(R.id.main, CreatePostFragment())
                .addToBackStack(null)
                .commit()
        }

        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Show/hide menu cards when fragment back stack changes
        supportFragmentManager.addOnBackStackChangedListener {
            val isInCreatePost = supportFragmentManager.findFragmentById(R.id.main) is CreatePostFragment
            val midWay = findViewById<View>(R.id.midWay)
            if (isInCreatePost) {
                // keep menu hidden under fragment
            } else {
                goLive.visibility = View.VISIBLE
                createPost.visibility = View.VISIBLE
                midWay.visibility = View.VISIBLE
            }
        }
    }
}