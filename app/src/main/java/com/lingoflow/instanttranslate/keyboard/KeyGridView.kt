package com.lingoflow.instanttranslate.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.lingoflow.instanttranslate.R

internal interface KeyGridListener {
    /** A completed key press. Delete also arrives here once per auto-repeat tick. */
    fun onKey(key: KeySpec)
    /** Shift and Space only. The release that ends a long press never types the key. */
    fun onLongPress(key: KeySpec)
}

/**
 * Draws every key on one canvas, the way Gboard and AOSP LatinIME do, instead of mounting ~40
 * Button views. Pressed colour and the key preview are drawn on ACTION_DOWN in the same frame,
 * so a tap never waits for a ripple animation or a deferred click runnable.
 *
 * Firing rules (mirroring Gboard so switching keyboards feels identical):
 * - characters, Space, Enter and page keys type on release; sliding a finger re-targets the key;
 * - Delete and Shift act on touch-down, because users expect them to respond instantly;
 * - a second finger landing commits the first finger's character immediately ("rollover"),
 *   so fast two-thumb typing keeps its order.
 */
@SuppressLint("ViewConstructor") // Created by LingoKeyboardView with its palette, never inflated.
internal class KeyGridView(
    context: Context,
    private val palette: KeyboardPalette,
    private val compact: Boolean,
    private val listener: KeyGridListener,
) : View(context) {
    // Gboard-like pitch: 56dp letter rows (46dp key + gaps) in portrait, shorter in landscape.
    private val rowPitch = dp(if (compact) 40f else 56f)
    private val numberRowPitch = dp(if (compact) 34f else 46f)
    private val gapX = dp(if (compact) 5f else 6f)
    private val gapY = dp(if (compact) 6f else 10f)
    private val radius = dp(if (compact) 6f else 8f)
    private val shadowOffset = dp(1.2f)
    private val iconSize = dp(if (compact) 20f else 23f).toInt()

    private var page = KeyPage.LETTERS
    private var shift = ShiftState.OFF
    private var enterLabel = "↵"
    private var rows: List<KeyRow> = KeyboardLayout.rows(page, enterLabel)
    private val keys = mutableListOf<KeySpec>()
    private val cells = mutableListOf<RectF>()
    private val rowBounds = mutableListOf<IntRange>()

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letterPaint = textPaint(if (compact) 19f else 22.5f, Typeface.NORMAL, "sans-serif")
    private val digitPaint = textPaint(if (compact) 16f else 19f, Typeface.NORMAL, "sans-serif")
    private val symbolPaint = textPaint(if (compact) 18f else 21f, Typeface.NORMAL, "sans-serif")
    private val emojiPaint = textPaint(if (compact) 21f else 25f, Typeface.NORMAL, "sans-serif")
    private val labelPaint = textPaint(if (compact) 13f else 14.5f, Typeface.NORMAL, "sans-serif-medium")
    private val drawRect = RectF()

    private val icons = mutableMapOf<Int, Drawable>()
    private val preview = KeyPreviewDrawable(palette, textPaint(if (compact) 26f else 30f, Typeface.NORMAL, "sans-serif"))
    /** The overlay host is the whole keyboard, so number-row previews can rise over the toolbar. */
    var previewHost: ViewGroup? = null
        set(value) {
            field?.overlay?.remove(preview)
            field = value
            value?.overlay?.add(preview)
        }

    private class Touch(val id: Int, var key: Int, var fired: Boolean)
    private val touches = mutableListOf<Touch>()
    private val handler = Handler(Looper.getMainLooper())
    private var longPressTouch: Touch? = null
    private var repeating = false
    private val longPress = Runnable { onLongPressTimeout() }
    private val repeatDelete = object : Runnable {
        override fun run() {
            if (!repeating) return
            listener.onKey(KeySpec(KeyAction.DELETE, "⌫"))
            handler.postDelayed(this, DELETE_REPEAT_INTERVAL_MS)
        }
    }

    private val accessibility = KeyGridAccessibility()

    init {
        isSoundEffectsEnabled = false
        isHapticFeedbackEnabled = true
        ViewCompat.setAccessibilityDelegate(this, accessibility)
        rebuildKeys()
    }

    fun setPage(next: KeyPage, nextShift: ShiftState, nextEnterLabel: String) {
        if (next == page && nextEnterLabel == enterLabel) { setShift(nextShift); return }
        // Indices of in-flight touches belong to the old layout; firing them later would type the wrong key.
        cancelTouches()
        page = next; shift = nextShift; enterLabel = nextEnterLabel
        rows = KeyboardLayout.rows(page, enterLabel)
        rebuildKeys()
        layoutCells()
        invalidate()
        accessibility.invalidateRoot()
    }

    fun setShift(next: ShiftState) {
        if (next == shift) return
        shift = next
        invalidate()
        accessibility.invalidateRoot()
    }

    /** Accessible/visible text for [key]; tests and TalkBack find keys by this label. */
    fun labelOf(key: KeySpec): String = when (key.action) {
        KeyAction.TEXT -> if (page == KeyPage.LETTERS && shift != ShiftState.OFF && key.label.length == 1 &&
            key.label[0].isLetter()) key.label.uppercase() else key.label
        KeyAction.SHIFT -> if (shift == ShiftState.LOCKED) "⇪" else "⇧"
        else -> key.label
    }

    /** Touch-target bounds of the first key labelled [label], in this view's coordinates. */
    fun keyBounds(label: String): RectF? = keys.indices.firstOrNull { labelOf(keys[it]) == label }?.let { RectF(cells[it]) }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = (numberRowPitch + rowPitch * 4).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { layoutCells() }

    private fun rebuildKeys() {
        keys.clear(); rowBounds.clear()
        rows.forEach { row ->
            val first = keys.size
            keys.addAll(row.keys)
            rowBounds.add(first until keys.size)
        }
        cells.clear(); repeat(keys.size) { cells.add(RectF()) }
    }

    /**
     * Cells tile the whole grid with no dead zones: the bottom row keeps one fixed pitch on every
     * page and the remaining height is shared by row weight, so page switches never resize the IME.
     */
    private fun layoutCells() {
        if (width == 0 || height == 0) return
        val contentWidth = (width - paddingLeft - paddingRight).toFloat()
        val unit = contentWidth / KeyboardLayout.ROW_UNITS
        val upperHeight = height - paddingTop - paddingBottom - rowPitch
        val upperWeight = rows.dropLast(1).sumOf { it.height.toDouble() }.toFloat()
        var top = paddingTop.toFloat()
        rows.forEachIndexed { rowIndex, row ->
            val rowHeight = if (rowIndex == rows.lastIndex) rowPitch else upperHeight * row.height / upperWeight
            var left = paddingLeft + row.inset * unit
            rowBounds[rowIndex].forEach { index ->
                val keyWidth = keys[index].width * unit
                cells[index].set(left, top, left + keyWidth, top + rowHeight)
                left += keyWidth
            }
            top += rowHeight
        }
    }

    // ---- Drawing ---------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(palette.background)
        val pressed = touches.mapTo(HashSet()) { it.key }
        for (index in keys.indices) drawKey(canvas, index, index in pressed)
    }

    private fun drawKey(canvas: Canvas, index: Int, pressed: Boolean) {
        val key = keys[index]
        val cell = cells[index]
        drawRect.set(cell.left + gapX / 2, cell.top + gapY / 2, cell.right - gapX / 2, cell.bottom - gapY / 2)
        val style = styleOf(index, key)
        // A hairline drop edge gives the flat keys a tactile, physical read without a heavy shadow.
        if (style != KeyStyle.ACCENT) {
            keyPaint.color = palette.keyShadow
            drawRect.offset(0f, shadowOffset)
            canvas.drawRoundRect(drawRect, radius, radius, keyPaint)
            drawRect.offset(0f, -shadowOffset)
        }
        keyPaint.color = when (style) {
            KeyStyle.CHARACTER -> if (pressed) palette.keyPressed else palette.key
            KeyStyle.FUNCTION -> if (pressed) palette.functionPressed else palette.function
            KeyStyle.ACTIVE -> if (pressed) palette.functionPressed else palette.softAccent
            KeyStyle.ACCENT -> if (pressed) palette.accentPressed else palette.accent
        }
        canvas.drawRoundRect(drawRect, radius, radius, keyPaint)

        val tint = if (style == KeyStyle.ACCENT) palette.onAccent else if (style == KeyStyle.ACTIVE) palette.accent else palette.ink
        val icon = iconFor(key)
        if (icon != 0) { drawIcon(canvas, icon, tint); return }
        if (key.action == KeyAction.SPACE) return // Gboard keeps a single-language space bar unlabelled.
        val paint = paintFor(index, key)
        paint.color = tint
        val label = labelOf(key)
        val baseline = drawRect.centerY() - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(label, drawRect.centerX(), baseline, paint)
    }

    private enum class KeyStyle { CHARACTER, FUNCTION, ACTIVE, ACCENT }

    private fun styleOf(index: Int, key: KeySpec): KeyStyle = when (key.action) {
        KeyAction.ENTER -> KeyStyle.ACCENT
        KeyAction.SHIFT -> if (shift == ShiftState.LOCKED) KeyStyle.ACTIVE else KeyStyle.FUNCTION
        KeyAction.EMOJI -> if (page == KeyPage.EMOJI) KeyStyle.ACTIVE else KeyStyle.FUNCTION
        KeyAction.SPACE -> KeyStyle.CHARACTER
        // Comma and period sit with the function keys, like Gboard, so Space reads as the bottom row's target.
        KeyAction.TEXT -> if (index in rowBounds.last()) KeyStyle.FUNCTION else KeyStyle.CHARACTER
        else -> KeyStyle.FUNCTION
    }

    private fun paintFor(index: Int, key: KeySpec): Paint = when {
        key.action != KeyAction.TEXT -> labelPaint
        page == KeyPage.EMOJI && index !in rowBounds.last() -> emojiPaint
        page == KeyPage.LETTERS && index in rowBounds.first() -> digitPaint
        key.label.length == 1 && key.label[0].isLetter() -> letterPaint
        else -> symbolPaint
    }

    private fun iconFor(key: KeySpec): Int = when (key.action) {
        KeyAction.SHIFT -> when (shift) {
            ShiftState.OFF -> R.drawable.ic_key_shift
            ShiftState.ONCE -> R.drawable.ic_key_shift_on
            ShiftState.LOCKED -> R.drawable.ic_key_caps_lock
        }
        KeyAction.DELETE -> R.drawable.ic_key_backspace
        KeyAction.EMOJI -> R.drawable.ic_key_emoji
        KeyAction.ENTER -> when (key.label) {
            "↵" -> R.drawable.ic_key_return
            "⌕" -> R.drawable.ic_key_search
            "→" -> R.drawable.ic_key_next
            "✓" -> R.drawable.ic_key_done
            context.getString(R.string.keyboard_send) -> R.drawable.ic_key_send
            else -> 0 // e.g. "Go" stays a word, as in Gboard.
        }
        else -> 0
    }

    private fun drawIcon(canvas: Canvas, res: Int, tint: Int) {
        val icon = icons.getOrPut(res) {
            requireNotNull(AppCompatResources.getDrawable(context, res)) { "Keyboard icon $res is missing" }.mutate()
        }
        val size = iconSize
        val left = (drawRect.centerX() - size / 2f).toInt()
        val top = (drawRect.centerY() - size / 2f).toInt()
        icon.setBounds(left, top, left + size, top + size)
        icon.setTint(tint)
        icon.draw(canvas)
    }

    // ---- Touch -----------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility") // Keys are virtual; KeyGridAccessibility performs their clicks.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val pointer = event.actionIndex
                pointerDown(event.getPointerId(pointer), event.getX(pointer), event.getY(pointer))
            }
            MotionEvent.ACTION_MOVE -> for (pointer in 0 until event.pointerCount) {
                pointerMove(event.getPointerId(pointer), event.getX(pointer), event.getY(pointer))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> pointerUp(event.getPointerId(event.actionIndex))
            MotionEvent.ACTION_CANCEL -> cancelTouches()
        }
        return true
    }

    private fun pointerDown(id: Int, x: Float, y: Float) {
        val index = keyAt(x, y) ?: return
        // Rollover: commit characters still held by earlier fingers first, preserving typing order.
        touches.filter { !it.fired && keys[it.key].action in ROLLOVER_ACTIONS }.forEach { it.fired = true; fire(it.key) }
        cancelKeyHold()
        val touch = Touch(id, index, fired = false)
        touches.add(touch)
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        val action = keys[index].action
        if (action in DOWN_ACTIONS) { touch.fired = true; fire(index) }
        scheduleLongPress(touch)
        updatePreview()
        invalidate()
    }

    private fun pointerMove(id: Int, x: Float, y: Float) {
        val touch = touches.firstOrNull { it.id == id } ?: return
        if (touch.fired || keys[touch.key].action in DOWN_ACTIONS) return
        // Keep the current key until the finger fully leaves its cell, avoiding flicker on borders.
        if (cells[touch.key].contains(x, y)) return
        val index = keyAt(x, y) ?: return
        if (index == touch.key || keys[index].action in DOWN_ACTIONS) return
        touch.key = index
        if (longPressTouch === touch) cancelKeyHold()
        scheduleLongPress(touch)
        updatePreview()
        invalidate()
    }

    private fun pointerUp(id: Int) {
        val touch = touches.firstOrNull { it.id == id } ?: return
        touches.remove(touch)
        if (longPressTouch === touch) cancelKeyHold()
        if (keys[touch.key].action == KeyAction.DELETE) stopRepeat()
        if (!touch.fired) fire(touch.key)
        updatePreview()
        invalidate()
    }

    private fun cancelTouches() {
        touches.clear()
        cancelKeyHold()
        stopRepeat()
        updatePreview()
        invalidate()
    }

    private fun fire(index: Int) {
        val key = keys.getOrNull(index) ?: return
        listener.onKey(key)
    }

    private fun scheduleLongPress(touch: Touch) {
        val delay = when (keys[touch.key].action) {
            KeyAction.DELETE -> DELETE_REPEAT_DELAY_MS
            KeyAction.SHIFT, KeyAction.SPACE -> ViewConfiguration.getLongPressTimeout().toLong()
            else -> return // Letters have no long-press alternates: holding a key just types it once.
        }
        longPressTouch = touch
        handler.postDelayed(longPress, delay)
    }

    private fun cancelKeyHold() {
        longPressTouch = null
        handler.removeCallbacks(longPress)
    }

    private fun onLongPressTimeout() {
        val touch = longPressTouch ?: return
        longPressTouch = null
        if (touch !in touches) return
        val key = keys[touch.key]
        if (key.action == KeyAction.DELETE) { repeating = true; handler.post(repeatDelete); return }
        touch.fired = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        listener.onLongPress(key)
        invalidate()
    }

    private fun stopRepeat() { repeating = false; handler.removeCallbacks(repeatDelete) }

    /** Nearest key to the point; edge and gap touches resolve to a key instead of being lost. */
    private fun keyAt(x: Float, y: Float): Int? {
        if (keys.isEmpty() || cells.first().isEmpty) return null
        val row = rowBounds.indices.firstOrNull { y < cells[rowBounds[it].first].bottom } ?: rowBounds.lastIndex
        return rowBounds[row].minByOrNull { index ->
            val cell = cells[index]
            if (x < cell.left) cell.left - x else if (x > cell.right) x - cell.right else 0f
        }
    }

    // ---- Key preview -----------------------------------------------------------------------

    private fun updatePreview() {
        val host = previewHost
        val touch = touches.lastOrNull { !it.fired && keys[it.key].action == KeyAction.TEXT }
        if (host == null || touch == null || page == KeyPage.EMOJI) { preview.hide(); return }
        val cell = cells[touch.key]
        val keyWidth = cell.width() - gapX
        val keyHeight = cell.height() - gapY
        val width = maxOf(keyWidth + dp(14f), dp(48f)).toInt()
        val height = (keyHeight * 1.18f).toInt().coerceAtLeast(dp(52f).toInt())
        val anchor = Rect(cell.left.toInt(), (cell.top + gapY / 2).toInt(), cell.right.toInt(), cell.bottom.toInt())
        host.offsetDescendantRectToMyCoords(this, anchor)
        val left = (anchor.centerX() - width / 2).coerceIn(0, maxOf(0, host.width - width))
        // Float the balloon just above the key; clamp at the window top for the number row.
        val top = (anchor.top - height - dp(4f).toInt()).coerceAtLeast(0)
        preview.show(labelOf(keys[touch.key]), Rect(left, top, left + width, top + height))
    }

    override fun onDetachedFromWindow() { cancelTouches(); super.onDetachedFromWindow() }

    // ---- Accessibility: each key is a virtual Button with the same label tests and TalkBack use.

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        accessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        accessibility.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        accessibility.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    private inner class KeyGridAccessibility : ExploreByTouchHelper(this@KeyGridView) {
        override fun getVirtualViewAt(x: Float, y: Float): Int = keyAt(x, y) ?: INVALID_ID

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) { virtualViewIds.addAll(keys.indices) }

        @Suppress("DEPRECATION") // ExploreByTouchHelper requires parent-relative bounds.
        override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
            val key = keys.getOrNull(virtualViewId)
            if (key == null) { node.text = ""; node.setBoundsInParent(Rect(0, 0, 1, 1)); return }
            node.className = "android.widget.Button"
            node.text = labelOf(key)
            node.contentDescription = descriptionOf(key)
            val cell = cells[virtualViewId]
            node.setBoundsInParent(Rect(cell.left.toInt(), cell.top.toInt(), cell.right.toInt(), cell.bottom.toInt())
                .takeUnless { it.isEmpty } ?: Rect(0, 0, 1, 1))
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            if (key.action == KeyAction.SHIFT || key.action == KeyAction.SPACE) node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)
        }

        override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            val key = keys.getOrNull(virtualViewId) ?: return false
            return when (action) {
                AccessibilityNodeInfoCompat.ACTION_CLICK -> { listener.onKey(key); true }
                AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> {
                    if (key.action == KeyAction.SHIFT || key.action == KeyAction.SPACE) { listener.onLongPress(key); true } else false
                }
                else -> false
            }
        }

        private fun descriptionOf(key: KeySpec): String? = when (key.action) {
            KeyAction.SHIFT -> context.getString(when (shift) {
                ShiftState.OFF -> R.string.keyboard_shift
                ShiftState.ONCE -> R.string.keyboard_shift_once
                ShiftState.LOCKED -> R.string.keyboard_caps_lock
            })
            KeyAction.DELETE -> context.getString(R.string.keyboard_delete)
            KeyAction.ENTER -> context.getString(R.string.keyboard_enter)
            KeyAction.EMOJI -> context.getString(R.string.keyboard_emoji)
            KeyAction.NEXT_SYMBOLS -> context.getString(R.string.keyboard_more_symbols)
            else -> null
        }
    }

    private fun textPaint(sp: Float, style: Int, family: String) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(family, style)
        textSize = sp * resources.displayMetrics.scaledDensity
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private companion object {
        val ROLLOVER_ACTIONS = setOf(KeyAction.TEXT, KeyAction.SPACE)
        val DOWN_ACTIONS = setOf(KeyAction.DELETE, KeyAction.SHIFT)
        // LatinIME defaults: repeat starts after 400ms, then ~20 deletions per second.
        const val DELETE_REPEAT_DELAY_MS = 400L
        const val DELETE_REPEAT_INTERVAL_MS = 50L
    }
}

/** Gboard-style balloon drawn in the keyboard's overlay: no window, layout pass or animation per tap. */
private class KeyPreviewDrawable(private val palette: KeyboardPalette, private val textPaint: Paint) : Drawable() {
    private var label: String? = null
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    fun show(text: String, at: Rect) {
        label = text
        if (bounds != at) { invalidateSelf(); bounds = at }
        invalidateSelf()
    }

    fun hide() {
        if (label == null) return
        label = null
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val text = label ?: return
        val radius = bounds.height() * 0.2f
        val lift = bounds.height() * 0.06f
        rect.set(bounds)
        // Two soft layers stand in for elevation; Paint shadows on shapes need API 28 with HW rendering.
        body.color = if (palette.dark) 0x66000000 else 0x30000000
        rect.offset(0f, lift); canvas.drawRoundRect(rect, radius, radius, body)
        rect.offset(0f, -lift)
        body.color = if (palette.dark) palette.keyPressed else palette.key
        canvas.drawRoundRect(rect, radius, radius, body)
        // A hairline edge separates the balloon from same-coloured keys it floats over.
        outline.color = palette.keyShadow
        outline.strokeWidth = maxOf(1f, bounds.height() * 0.02f)
        canvas.drawRoundRect(rect, radius, radius, outline)
        textPaint.color = palette.ink
        canvas.drawText(text, rect.centerX(), rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
