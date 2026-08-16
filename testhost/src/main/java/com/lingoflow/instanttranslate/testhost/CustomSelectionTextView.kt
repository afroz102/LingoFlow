package com.lingoflow.instanttranslate.testhost

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

/**
 * Negative/control fixture (docs/VALIDATION_PLAN.md §2.2): text drawn directly on a [Canvas] with a
 * hand-rolled long-press highlight, deliberately never calling [View.startActionMode] or
 * anything that could surface the framework's text-selection action menu. Any Process Text
 * entry appearing over this view would indicate it leaked in through some other mechanism, not
 * through this fixture's own (nonexistent) text-selection integration.
 */
class CustomSelectionTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var text: CharSequence = ""
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, context.resources.displayMetrics)
    }

    private val highlightPaint = Paint().apply {
        color = Color.YELLOW
        alpha = 120
    }

    private var isHighlighted = false

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                // Deliberately does nothing but a local visual highlight — no ActionMode,
                // no ClipboardManager, no framework text-selection API of any kind.
                isHighlighted = true
                invalidate()
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                isHighlighted = false
                invalidate()
                return true
            }
        },
    )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isHighlighted) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), highlightPaint)
        }
        canvas.drawText(text.toString(), 0f, paint.textSize, paint)
    }
}
