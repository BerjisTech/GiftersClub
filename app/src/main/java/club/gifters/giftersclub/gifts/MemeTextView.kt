package club.gifters.giftersclub.gifts

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * Simple TextView that renders white fill with a black stroke outline,
 * mimicking classic meme text style.
 */
class MemeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    var strokeWidthPx: Float = 6f
    var strokeColor: Int = Color.BLACK

    override fun onDraw(canvas: Canvas) {
        // Draw stroke
        val currentColor = currentTextColor
        val p: Paint = paint
        p.style = Paint.Style.STROKE
        p.strokeWidth = strokeWidthPx
        setTextColor(strokeColor)
        super.onDraw(canvas)
        // Draw fill
        p.style = Paint.Style.FILL
        setTextColor(currentColor)
        super.onDraw(canvas)
    }
}

