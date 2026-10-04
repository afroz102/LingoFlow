package com.lingoflow.instanttranslate.keyboard

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import com.lingoflow.instanttranslate.R
import com.lingoflow.instanttranslate.reading.KeyboardPrivacy

/** Optional, local-only Roman keyboard. No keystrokes, surrounding messages or passwords leave it. */
class LingoKeyboardService : InputMethodService() {
    private var uppercase = false
    private var symbols = false

    override fun onCreateInputView(): View = keyboard()

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        val type = attribute?.inputType ?: 0
        val variation = type and InputType.TYPE_MASK_VARIATION
        val kind = type and InputType.TYPE_MASK_CLASS
        KeyboardPrivacy.passwordInputActive =
            (kind == InputType.TYPE_CLASS_TEXT && variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (kind == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        uppercase = false
        symbols = kind == InputType.TYPE_CLASS_NUMBER || kind == InputType.TYPE_CLASS_PHONE
        setInputView(keyboard())
    }

    override fun onFinishInput() {
        KeyboardPrivacy.passwordInputActive = false
        super.onFinishInput()
    }

    override fun onDestroy() {
        KeyboardPrivacy.passwordInputActive = false
        super.onDestroy()
    }

    private fun keyboard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val rows = if (symbols) listOf("1234567890", "@#₹_&-+()/", "*\"':;!?.,")
            else listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        rows.forEach { letters ->
            addView(LinearLayout(this@LingoKeyboardService).apply {
                letters.forEach { char ->
                    val label = if (uppercase && !symbols) char.uppercase() else char.toString()
                    addKey(label) { currentInputConnection?.commitText(label, 1) }
                }
            })
        }
        addView(LinearLayout(this@LingoKeyboardService).apply {
            addKey(if (symbols) "ABC" else "123") { symbols = !symbols; setInputView(keyboard()) }
            addKey("⇧") { uppercase = !uppercase; setInputView(keyboard()) }
            addKey("⌫") {
                val connection = currentInputConnection ?: return@addKey
                if (!connection.getSelectedText(0).isNullOrEmpty()) connection.commitText("", 1)
                else if (Build.VERSION.SDK_INT >= 24) connection.deleteSurroundingTextInCodePoints(1, 0)
                else {
                    val before = connection.getTextBeforeCursor(2, 0)?.toString().orEmpty()
                    val count = if (before.length == 2 && Character.isSurrogatePair(before[0], before[1])) 2 else 1
                    connection.deleteSurroundingText(count, 0)
                }
            }
        })
        addView(LinearLayout(this@LingoKeyboardService).apply {
            addKey(getString(R.string.keyboard_switch)) {
                getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            }
            addKey(getString(R.string.keyboard_space), 2f) { currentInputConnection?.commitText(" ", 1) }
            addKey("↵") {
                val options = currentInputEditorInfo?.imeOptions ?: 0
                val action = options and EditorInfo.IME_MASK_ACTION
                if (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0 ||
                    action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
                    currentInputConnection?.commitText("\n", 1)
                } else currentInputConnection?.performEditorAction(action)
            }
        })
    }

    private fun LinearLayout.addKey(label: String, weight: Float = 1f, action: () -> Unit) {
        addView(Button(this@LingoKeyboardService).apply {
            text = label; textSize = 16f; minWidth = 0; minimumWidth = 0
            setPadding(0, 0, 0, 0); isAllCaps = false
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(0, (48 * resources.displayMetrics.density).toInt(), weight))
    }
}
