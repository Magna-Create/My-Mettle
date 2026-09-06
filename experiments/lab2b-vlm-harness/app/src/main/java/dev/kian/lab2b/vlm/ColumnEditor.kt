package dev.kian.lab2b.vlm

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/** Two draggable ends make a perspective-aware strip; OCR coordinates remain crop-relative. */
class ColumnEditor(context:Context,path:String,initial:ColumnSelection):View(context) {
    private val bitmap=requireNotNull(BitmapFactory.decodeFile(path))
    private val frame=RectF()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    var selection=initial; private set
    fun select(value:ColumnSelection) { selection=value;invalidate() }
    fun width(value:Double) { selection=selection.copy(halfWidth=value);invalidate() }
    override fun onDraw(canvas:Canvas) {
        val scale=min(width.toFloat()/bitmap.width,height.toFloat()/bitmap.height)
        val w=bitmap.width*scale;val h=bitmap.height*scale
        frame.set((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2)
        canvas.drawBitmap(bitmap,null,frame,null)
        fun x(v:Double)=frame.left+(v*w).toFloat()
        val s=selection
        val path=Path().apply { moveTo(x(s.topX-s.halfWidth),frame.top);lineTo(x(s.topX+s.halfWidth),frame.top)
            lineTo(x(s.bottomX+s.halfWidth),frame.bottom);lineTo(x(s.bottomX-s.halfWidth),frame.bottom);close() }
        paint.style=Paint.Style.FILL;paint.color=0x4400FFFF;canvas.drawPath(path,paint)
        paint.style=Paint.Style.STROKE;paint.color=Color.CYAN;paint.strokeWidth=3f;canvas.drawPath(path,paint)
        paint.style=Paint.Style.FILL
        canvas.drawCircle(x(s.topX),frame.top+12f,12f,paint);canvas.drawCircle(x(s.bottomX),frame.bottom-12f,12f,paint)
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(frame.width()<=0f) return false
        if(event.action==MotionEvent.ACTION_DOWN || event.action==MotionEvent.ACTION_MOVE || event.action==MotionEvent.ACTION_UP) {
            parent.requestDisallowInterceptTouchEvent(true)
            val x=((event.x-frame.left)/frame.width()).toDouble().coerceIn(0.0,1.0)
            selection=if(event.y<frame.centerY()) selection.copy(topX=x) else selection.copy(bottomX=x)
            invalidate()
            if(event.action==MotionEvent.ACTION_UP) { parent.requestDisallowInterceptTouchEvent(false);performClick() }
        }
        return true
    }
    override fun performClick():Boolean { super.performClick();return true }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow();bitmap.recycle() }
}
