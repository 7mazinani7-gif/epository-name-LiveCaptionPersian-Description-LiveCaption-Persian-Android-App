package com.livecaption.persian

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.widget.TextView

/** TextView that paints a real outline beneath its normal glyph fill. */
class OutlinedCaptionTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : TextView(context, attrs) {
    var outlineColor: Int = android.graphics.Color.BLACK
    var outlineWidthPx: Float = 2f

    override fun onDraw(canvas: Canvas) {
        val textLayout = layout
        if (textLayout != null && outlineWidthPx > 0f) {
            val paint = paint
            val oldColor = paint.color
            val oldStyle = paint.style
            val oldWidth = paint.strokeWidth
            val oldJoin = paint.strokeJoin
            canvas.save()
            canvas.translate(totalPaddingLeft.toFloat(), (extendedPaddingTop - scrollY).toFloat())
            paint.color = outlineColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = outlineWidthPx
            paint.strokeJoin = Paint.Join.ROUND
            textLayout.draw(canvas)
            canvas.restore()
            paint.color = oldColor
            paint.style = oldStyle
            paint.strokeWidth = oldWidth
            paint.strokeJoin = oldJoin
        }
        super.onDraw(canvas)
    }
}
