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
        val creator = intent.getStringExtra("creator_username")
        val price = intent.getStringExtra("price")

        // Show access overlay if not free
        val locked = access == "subscription" || access == "paid"
        lockOverlay.visibility = if (locked) android.view.View.VISIBLE else android.view.View.GONE
        if (locked) {
            val tvLockAction = lockOverlay.findViewById<TextView>(R.id.tvLockAction)
            tvLockAction?.text = when (access) {
                "paid" -> if (!price.isNullOrEmpty()) getString(R.string.purchase_access) else getString(R.string.purchase_access)
                else -> getString(R.string.subscribe_to_creator)
            }
            lockOverlay.findViewById<TextView>(R.id.tvCreatorName)?.text = creator?.let { "@" + it } ?: ""
            // Show blurred media behind overlay on Android 12+
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                try {
                    val blur = android.graphics.RenderEffect.createBlurEffect(24f, 24f, android.graphics.Shader.TileMode.CLAMP)
                    mediaPager.setRenderEffect(blur)
                    findViewById<android.view.View>(R.id.postDetails)?.setRenderEffect(blur)
                } catch (_: Exception) {}
            }
            // Disable CTA in preview (no purchase flow here)
            lockOverlay.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnLockCta)?.apply {
                isEnabled = false
                text = if (access == "subscription") getString(R.string.subscribe) else getString(R.string.unlock)
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
