package dev.kian.lab2b.vlm

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/** Drag a rectangle on the full-frame preview; coordinates are independent of display size. */
class CropEditor(context: Context, path: String, initial: CropRegion? = null) : View(context) {
    private val bitmap = requireNotNull(BitmapFactory.decodeFile(path))
    private val frame = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var startX = 0f
    private var startY = 0f
    private var selection = initial ?: CropRegion("manual region", 0.0, 0.0, 1.0, 1.0)
    fun region(label: String) = selection.copy(label = label.ifBlank { "manual region" })
    override fun onDraw(canvas: Canvas) {
        val scale = min(width.toFloat()/bitmap.width, height.toFloat()/bitmap.height)
        val w = bitmap.width*scale; val h = bitmap.height*scale
        frame.set((width-w)/2, (height-h)/2, (width+w)/2, (height+h)/2)
        canvas.drawBitmap(bitmap, null, frame, null)
        paint.color = Color.CYAN; paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f
        canvas.drawRect(frame.left + selection.left.toFloat()*w, frame.top+selection.top.toFloat()*h,
            frame.left+selection.right.toFloat()*w, frame.top+selection.bottom.toFloat()*h, paint)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (frame.width() <= 0 || frame.height() <= 0) return false
        val x = ((event.x-frame.left)/frame.width()).coerceIn(0f,1f)
        val y = ((event.y-frame.top)/frame.height()).coerceIn(0f,1f)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { startX=x; startY=y; parent.requestDisallowInterceptTouchEvent(true) }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                if (kotlin.math.abs(x-startX) > 0.005f && kotlin.math.abs(y-startY) > 0.005f) {
                    selection = CropRegion(selection.label, minOf(x,startX).toDouble(), minOf(y,startY).toDouble(),
                        maxOf(x,startX).toDouble(), maxOf(y,startY).toDouble()); invalidate()
                }
                if (event.action == MotionEvent.ACTION_UP) { parent.requestDisallowInterceptTouchEvent(false); performClick() }
            }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); bitmap.recycle() }
}
