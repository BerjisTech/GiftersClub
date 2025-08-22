package club.gifters.giftersclub.gifts

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import club.gifters.giftersclub.R

class CreatePostActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_posts)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CreatePostFragment())
                .commit()
        }
    }
}

