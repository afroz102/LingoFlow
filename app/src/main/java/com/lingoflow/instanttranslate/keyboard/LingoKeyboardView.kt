package com.lingoflow.instanttranslate.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.annotation.SuppressLint
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.lingoflow.instanttranslate.R
import com.lingoflow.instanttranslate.direction.Direction

internal data class KeyboardPanelState(
    val panel: TranslationPanel,
    val direction: Direction,
    val draft: String,
    val busy: Boolean,
    val result: String?,
    val message: String?,
    val disclosure: Boolean,
    val selected: Boolean,
)

interface KeyboardActions {
    fun translateIcon()
    fun readIcon()
    fun closePanel()
    fun swapDirection()
    fun translate()
    fun continueDisclosure()
    fun readCopy()
    fun copyResult()
    fun insertResult()
    fun switchKeyboard()
    fun key(text: String)
    fun shift()
    fun lockShift()
    fun newTranslation()
    fun page(page: KeyPage)
    fun backspace()
    fun enter()
    fun draftChanged(text: String)
}

/** The toolbar and key grid stay mounted while typing; only shift labels change per letter. */
@SuppressLint("ViewConstructor") // Constructed by the IME with its callbacks, never inflated from XML.
internal class LingoKeyboardView(context: Context, private val actions: KeyboardActions) : LinearLayout(context) {
    private val ink = Color.rgb(31, 45, 43)
    private val muted = Color.rgb(91, 111, 105)
    private val accent = Color.rgb(210, 237, 222)
    private val base = Color.rgb(237, 243, 239)
    private val handler = Handler(Looper.getMainLooper())
    private val compact = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private val keyHeight = if (compact) 35 else 49
    private val toolbar = LinearLayout(context)
    private val panel = LinearLayout(context)
    private val keys = LinearLayout(context)
    private val letterButtons = mutableListOf<Pair<Button, String>>()
    private var shiftButton: Button? = null
    private var translateButton: Button? = null
    private var draftField: EditText? = null
    private var state: KeyboardPanelState? = null
    private var enterLabel = "↵"
    private var repeating = false
    private val repeatDelete = object : Runnable {
        override fun run() {
            if (!repeating) return
            actions.backspace()
            handler.postDelayed(this, 65)
        }
    }

    init {
        orientation = VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(6))
        setBackgroundColor(base)
        if (Build.VERSION.SDK_INT >= 26) importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        isSaveEnabled = false
        toolbar.gravity = Gravity.CENTER_VERTICAL
        toolbar.setPadding(dp(4), 0, dp(4), dp(4))
        addView(toolbar, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        panel.orientation = VERTICAL
        panel.setPadding(dp(8), dp(4), dp(8), dp(6))
        addView(panel)
        keys.orientation = VERTICAL
        addView(keys)
        renderToolbar(false, false)
    }

    fun renderToolbar(selected: Boolean, password: Boolean) {
        toolbar.removeAllViews()
        translateButton = button(context.getString(if (selected) R.string.keyboard_translate_selection else R.string.action_translate), true) {
            actions.translateIcon()
        }.apply {
            contentDescription = context.getString(R.string.keyboard_translate_description)
            val icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.ic_keyboard_translate)
            icon?.setBounds(0, 0, dp(18), dp(18))
            setCompoundDrawables(icon, null, null, null)
            compoundDrawablePadding = dp(4)
            isEnabled = !password
        }
        toolbar.addView(translateButton, LayoutParams(0, dp(38), 1.5f))
        toolbar.addView(button(context.getString(R.string.keyboard_read), false) { actions.readIcon() }.apply {
            isEnabled = !password
            contentDescription = context.getString(R.string.keyboard_read_description)
        }, LayoutParams(0, dp(38), 0.9f))
        toolbar.addView(button("🌐", false) { actions.switchKeyboard() }.apply {
            contentDescription = context.getString(R.string.keyboard_switch)
        }, LayoutParams(dp(48), dp(38)))
    }

    fun renderPanel(next: KeyboardPanelState) {
        state = next
        panel.removeAllViews()
        draftField = null
        panel.visibility = if (next.panel == TranslationPanel.NONE) GONE else VISIBLE
        if (next.panel == TranslationPanel.NONE) return
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false }
        val title = if (next.panel == TranslationPanel.READ) context.getString(R.string.keyboard_read_title)
            else context.getString(if (next.direction == Direction.HINGLISH_TO_ENGLISH)
                R.string.keyboard_hindi_to_en else R.string.keyboard_en_to_hindi)
        header.addView(TextView(context).apply {
            text = title; textSize = 14f; setTextColor(ink); setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER_VERTICAL
        }, LayoutParams(0, dp(32), 1f))
        if (next.panel == TranslationPanel.WRITE) header.addView(button("⇄", false) { actions.swapDirection() }.apply {
            contentDescription = context.getString(R.string.keyboard_swap)
            textSize = 20f
            isEnabled = !next.busy
        }, LayoutParams(dp(48), dp(32)))
        header.addView(button("×", false) { actions.closePanel() }.apply {
            contentDescription = context.getString(R.string.action_close)
            textSize = 24f
        }, LayoutParams(dp(48), dp(32)))
        panel.addView(header)
        if (next.disclosure) {
            addScrollableText(context.getString(R.string.disclosure_message), if (compact) 70 else 110, false)
            panel.addView(button(context.getString(R.string.action_continue), true) { actions.continueDisclosure() },
                LayoutParams(LayoutParams.MATCH_PARENT, dp(40)))
            return
        }
        if (next.result != null) {
            addScrollableText(next.result, if (compact) 65 else 108, true)
            next.message?.let { addStatus(it) }
            val resultActions = LinearLayout(context)
            resultActions.addView(button(context.getString(R.string.action_copy), false) { actions.copyResult() }, LayoutParams(0, dp(40), 1f))
            if (next.panel == TranslationPanel.WRITE) {
                resultActions.addView(button(context.getString(R.string.keyboard_insert), true) { actions.insertResult() }, LayoutParams(0, dp(40), 1f))
            }
            resultActions.addView(button(context.getString(R.string.keyboard_new), false) {
                actions.newTranslation()
            }, LayoutParams(0, dp(40), 1f))
            panel.addView(resultActions)
            return
        }
        draftField = EditText(context).apply {
            setText(next.draft)
            setSelection(text.length)
            setTextColor(ink); setHintTextColor(muted); textSize = 16f
            hint = context.getString(if (next.panel == TranslationPanel.WRITE) R.string.keyboard_draft_hint else R.string.keyboard_read_hint)
            contentDescription = context.getString(R.string.keyboard_draft_description)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(4000))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = rounded(Color.WHITE, 14f)
            gravity = Gravity.TOP or Gravity.START
            showSoftInputOnFocus = false
            isSaveEnabled = false
            isEnabled = !next.busy
            // Focus/cursor editing are local to this draft; the host editor remains the IME target.
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    actions.draftChanged(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        panel.addView(draftField, LayoutParams(LayoutParams.MATCH_PARENT, dp(if (compact) 52 else 76)))
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(button(context.getString(R.string.keyboard_paste), false) { actions.readCopy() }.apply {
            isEnabled = !next.busy
        }, LayoutParams(0, dp(40), 1f))
        row.addView(button(context.getString(if (next.busy) R.string.keyboard_translating
            else if (next.panel == TranslationPanel.WRITE) R.string.keyboard_translate_insert else R.string.keyboard_translate_read), true) {
            actions.translate()
        }.apply { isEnabled = !next.busy }, LayoutParams(0, dp(40), 2f))
        panel.addView(row)
        next.message?.let { addStatus(it) }
    }

    fun editDraft(value: String? = null, delete: Boolean = false): Boolean {
        val field = draftField ?: return false
        if (!field.isEnabled) return true
        val text = field.text.toString()
        val edited = if (delete) KeyboardDraft.backspace(text, field.selectionStart, field.selectionEnd)
            else KeyboardDraft.insert(text, field.selectionStart, field.selectionEnd, value.orEmpty())
        field.setText(edited.text)
        field.setSelection(edited.cursor)
        return true
    }

    fun renderKeys(page: KeyPage, shift: ShiftState, actionLabel: String) {
        stopRepeat()
        enterLabel = actionLabel
        keys.removeAllViews()
        letterButtons.clear()
        shiftButton = null
        if (page == KeyPage.EMOJI) {
            val grid = LinearLayout(context).apply { orientation = VERTICAL }
            KeyboardLayout.emoji.chunked(8).forEach { emojiRow ->
                val row = LinearLayout(context)
                emojiRow.forEach { emoji -> row.addView(keyButton(emoji), LayoutParams(0, dp(44), 1f)) }
                // Keep the last two emoji the same size as the other rows.
                repeat(8 - emojiRow.size) { row.addView(View(context), LayoutParams(0, dp(44), 1f)) }
                grid.addView(row)
            }
            keys.addView(ScrollView(context).apply { addView(grid); isFillViewport = false },
                LayoutParams(LayoutParams.MATCH_PARENT, dp(keyHeight * 3)))
        } else {
            val rows = when (page) {
                KeyPage.LETTERS -> KeyboardLayout.letters.map { it.map(Char::toString) }
                KeyPage.SYMBOLS -> KeyboardLayout.symbols
                else -> KeyboardLayout.moreSymbols
            }
            rows.forEachIndexed { index, labels ->
                val row = LinearLayout(context).apply { gravity = Gravity.CENTER; isBaselineAligned = false }
                if (index == 1 && page == KeyPage.LETTERS) row.setPadding(dp(15), 0, dp(15), 0)
                if (index == 2) {
                    val label = if (page == KeyPage.LETTERS) "⇧" else if (page == KeyPage.SYMBOLS) "=\\<" else "?123"
                    shiftButton = button(label, false) {
                        if (page == KeyPage.LETTERS) actions.shift()
                        else actions.page(if (page == KeyPage.SYMBOLS) KeyPage.MORE_SYMBOLS else KeyPage.SYMBOLS)
                    }.apply {
                        contentDescription = context.getString(if (page == KeyPage.LETTERS) R.string.keyboard_shift else R.string.keyboard_more_symbols)
                        textSize = if (page == KeyPage.LETTERS) 22f else 13f
                        if (page == KeyPage.LETTERS) setOnLongClickListener { actions.lockShift(); true }
                    }
                    row.addView(shiftButton, keyParams(1.4f))
                }
                labels.forEach { label ->
                    val key = keyButton(label)
                    if (page == KeyPage.LETTERS) letterButtons.add(key to label)
                    row.addView(key, keyParams(1f))
                }
                if (index == 2) row.addView(deleteButton(), keyParams(1.4f))
                keys.addView(row)
            }
        }
        val bottom = LinearLayout(context).apply { isBaselineAligned = false }
        bottom.addView(button(if (page == KeyPage.LETTERS) "?123" else "ABC", false) {
            actions.page(if (page == KeyPage.LETTERS) KeyPage.SYMBOLS else KeyPage.LETTERS)
        }, keyParams(1.5f))
        bottom.addView(button(if (page == KeyPage.EMOJI) "ABC" else "☺", false) {
            actions.page(if (page == KeyPage.EMOJI) KeyPage.LETTERS else KeyPage.EMOJI)
        }.apply { contentDescription = context.getString(R.string.keyboard_emoji) }, keyParams(1f))
        bottom.addView(keyButton(","), keyParams(0.8f))
        bottom.addView(button(context.getString(R.string.keyboard_space), false) { actions.key(" ") }.apply {
            setOnLongClickListener { actions.switchKeyboard(); true }
        }, keyParams(3.5f))
        bottom.addView(keyButton("."), keyParams(0.8f))
        bottom.addView(if (page == KeyPage.EMOJI) deleteButton() else button(enterLabel, true) { actions.enter() }.apply {
            contentDescription = context.getString(R.string.keyboard_enter)
        }, keyParams(1.4f))
        keys.addView(bottom)
        updateShift(shift)
    }

    fun updateShift(shift: ShiftState) {
        letterButtons.forEach { (button, label) -> button.text = if (shift == ShiftState.OFF) label else label.uppercase() }
        if (letterButtons.isNotEmpty()) shiftButton?.apply {
            text = if (shift == ShiftState.LOCKED) "⇪" else "⇧"
            background = ripple(if (shift == ShiftState.OFF) base else accent)
            contentDescription = context.getString(when (shift) {
                ShiftState.OFF -> R.string.keyboard_shift
                ShiftState.ONCE -> R.string.keyboard_shift_once
                ShiftState.LOCKED -> R.string.keyboard_caps_lock
            })
        }
    }

    @SuppressLint("ClickableViewAccessibility") // Return false: Button handles click/TalkBack; listener only ends hold-repeat.
    private fun deleteButton(): Button = button("⌫", false) { actions.backspace() }.apply {
        textSize = 22f
        contentDescription = context.getString(R.string.keyboard_delete)
        setOnLongClickListener { repeating = true; handler.post(repeatDelete); true }
        setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) stopRepeat()
            false
        }
    }

    private fun keyButton(text: String) = button(text, false) { actions.key(text) }.apply {
        textSize = if (text.length > 1) 24f else 21f
        background = ripple(Color.WHITE)
        if (text.length == 1 && text[0].isLetter()) setOnLongClickListener {
            val digit = KeyboardLayout.letters[0].indexOf(text)
            if (digit >= 0) { actions.key(((digit + 1) % 10).toString()); true } else false
        }
    }

    private fun button(label: String, primary: Boolean, action: () -> Unit): Button = Button(context).apply {
        text = label; textSize = 13f; setTextColor(ink); isAllCaps = false
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(5), 0, dp(5), 0)
        gravity = Gravity.CENTER
        background = ripple(if (primary) accent else base)
        stateListAnimator = null
        isSaveEnabled = false
        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); action() }
    }

    private fun addScrollableText(text: String, height: Int, result: Boolean) {
        panel.addView(ScrollView(context).apply {
            background = rounded(if (result) accent else Color.WHITE, 14f)
            addView(TextView(context).apply {
                this.text = text; textSize = if (result) 17f else 13f; setTextColor(ink)
                setPadding(dp(12), dp(8), dp(12), dp(8)); isSaveEnabled = false
                if (result) contentDescription = context.getString(R.string.keyboard_result_description)
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            })
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(height)))
    }

    private fun addStatus(text: String) {
        panel.addView(TextView(context).apply {
            this.text = text; setTextColor(muted); textSize = 12f; maxLines = 2
            setPadding(dp(4), dp(3), dp(4), dp(2))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        })
    }
    private fun keyParams(weight: Float) = LayoutParams(0, dp(keyHeight), weight).apply {
        setMargins(dp(2), dp(3), dp(2), dp(3))
    }
    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun ripple(color: Int) = RippleDrawable(android.content.res.ColorStateList.valueOf(Color.rgb(187, 214, 199)), rounded(color, 10f), null)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()
    private fun stopRepeat() { repeating = false; handler.removeCallbacks(repeatDelete) }
    override fun onDetachedFromWindow() { stopRepeat(); super.onDetachedFromWindow() }
}
