package club.gifters.giftersclub.gifts

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import club.gifters.giftersclub.R

class FullscreenPostPreviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.item_post)

        val lockOverlay = findViewById<android.view.View>(R.id.lockOverlay)
        val mediaPager = findViewById<ViewPager2>(R.id.mediaPager)
        val contentText = findViewById<TextView>(R.id.contentText)

        val imagePath = intent.getStringExtra("image_path")
        val content = intent.getStringExtra("content_text") ?: ""
        val access = intent.getStringExtra("access_type") ?: "free"
        val price = intent.getStringExtra("price")

        // Show access overlay if not free
        val locked = access == "subscription" || access == "paid"
        lockOverlay.visibility = if (locked) android.view.View.VISIBLE else android.view.View.GONE
        if (locked) {
            val tvLockAction = lockOverlay.findViewById<TextView>(R.id.tvLockAction)
            tvLockAction?.text = when (access) {
                "paid" -> if (!price.isNullOrEmpty()) "Paid • $price tokens" else "Paid"
                else -> getString(R.string.subscribe_to_view)
            }
        }

        // Minimal pager adapter: single image page for the preview
        mediaPager.adapter = object : RecyclerView.Adapter<ImageVH>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ImageVH {
                val iv = ImageView(parent.context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
                return ImageVH(iv)
            }
            override fun getItemCount(): Int = 1
            override fun onBindViewHolder(holder: ImageVH, position: Int) {
                val path = imagePath
                if (!path.isNullOrEmpty()) {
                    try { holder.image.setImageBitmap(BitmapFactory.decodeFile(path)) } catch (_: Exception) {}
                } else {
                    holder.image.setImageDrawable(null)
                }
            }
        }

        contentText.text = content
    }

    private class ImageVH(val image: ImageView) : RecyclerView.ViewHolder(image)
}
