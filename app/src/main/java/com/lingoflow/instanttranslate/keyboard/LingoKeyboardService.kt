package com.lingoflow.instanttranslate.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.lingoflow.instanttranslate.R
import com.lingoflow.instanttranslate.cloud.AndroidConnectivityChecker
import com.lingoflow.instanttranslate.coordinator.TranslateCoordinator
import com.lingoflow.instanttranslate.coordinator.TranslationOutcome
import com.lingoflow.instanttranslate.direction.Direction
import com.lingoflow.instanttranslate.direction.DirectionDetector
import com.lingoflow.instanttranslate.prefs.DisclosurePreferences
import com.lingoflow.instanttranslate.provider.FailureReason
import com.lingoflow.instanttranslate.provider.backend.BackendTranslationProvider
import com.lingoflow.instanttranslate.reading.ClipboardAccess
import com.lingoflow.instanttranslate.reading.KeyboardPrivacy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Local typing plus explicit translation requests. No overlay permission or reading service needed. */
class LingoKeyboardService : InputMethodService(), KeyboardActions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val disclosure by lazy { DisclosurePreferences(this) }
    private val coordinator by lazy {
        TranslateCoordinator(BackendTranslationProvider(), disclosure, AndroidConnectivityChecker(this))
    }
    private var surface: LingoKeyboardView? = null
    private var shiftState = ShiftState.OFF
    private var keyPage = KeyPage.LETTERS
    private var panel = TranslationPanel.NONE
    private var direction = Direction.HINGLISH_TO_ENGLISH
    private var draft = ""
    private var result: String? = null
    private var message: String? = null
    private var needsDisclosure = false
    private var busy = false
    private var selected = false
    private var lastShiftTap = 0L
    private var session = 0L
    private var selectionRevision = 0L
    private var requestGeneration = 0L
    private var request: Job? = null
    private var selectionStart = -1
    private var selectionEnd = -1

    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View = LingoKeyboardView(this, this).also {
        surface = it
        render()
        renderKeys()
        // Text is held only in this IME instance, never saved into a view state or screenshots.
        window?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        clearPanel()
        session++
        selectionRevision = 0
        selectionStart = attribute?.initialSelStart ?: -1
        selectionEnd = attribute?.initialSelEnd ?: -1
        selected = selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd
        val type = attribute?.inputType ?: 0
        val variation = type and InputType.TYPE_MASK_VARIATION
        val kind = type and InputType.TYPE_MASK_CLASS
        KeyboardPrivacy.passwordInputActive =
            (kind == InputType.TYPE_CLASS_TEXT && variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (kind == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        shiftState = if ((attribute?.initialCapsMode ?: 0) != 0) ShiftState.ONCE else ShiftState.OFF
        keyPage = if (kind == InputType.TYPE_CLASS_NUMBER || kind == InputType.TYPE_CLASS_PHONE) KeyPage.SYMBOLS else KeyPage.LETTERS
        lastShiftTap = 0
        if (KeyboardPrivacy.passwordInputActive) KeyboardReadingInbox.clear()
        render(); renderKeys()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!KeyboardPrivacy.passwordInputActive) KeyboardReadingInbox.take()?.let {
            openPanel(TranslationPanel.READ, it)
            // The selection-menu action already confirmed this exact message.
            translate()
        }
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (selectionStart != newSelStart || selectionEnd != newSelEnd) selectionRevision++
        selectionStart = newSelStart; selectionEnd = newSelEnd
        val hasSelection = newSelStart >= 0 && newSelEnd >= 0 && newSelStart != newSelEnd
        if (selected != hasSelection) {
            selected = hasSelection
            surface?.renderToolbar(selected, KeyboardPrivacy.passwordInputActive)
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        // Hiding the keyboard cancels requests; returning must never insert a stale response.
        clearPanel(); render()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        clearPanel()
        session++
        KeyboardPrivacy.passwordInputActive = false
        super.onFinishInput()
    }

    override fun onDestroy() {
        scope.cancel()
        KeyboardPrivacy.passwordInputActive = false
        surface = null
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // InputMethodService recreates its views. Keep this request/draft only if its editor survives.
    }

    override fun translateIcon() {
        if (KeyboardPrivacy.passwordInputActive) return
        if (selected) {
            val text = selectedText()
            if (text == null) { toast(R.string.keyboard_selection_unavailable); return }
            openPanel(TranslationPanel.READ, text)
            translate()
        } else openPanel(TranslationPanel.WRITE, "")
    }

    override fun readIcon() {
        if (KeyboardPrivacy.passwordInputActive) return
        val text = if (selected) selectedText() else null
        openPanel(TranslationPanel.READ, text.orEmpty())
        if (text != null) translate()
        else readCopy()
    }

    private fun selectedText(): String? = try {
        currentInputConnection?.getSelectedText(0)?.toString()?.takeIf(DirectionDetector::isSupported)
    } catch (_: RuntimeException) { null }

    private fun openPanel(mode: TranslationPanel, text: String) {
        clearPanel()
        panel = mode
        draft = text
        render()
        renderKeys()
    }

    override fun closePanel() { clearPanel(); render(); renderKeys() }
    override fun swapDirection() {
        if (busy) return
        direction = if (direction == Direction.HINGLISH_TO_ENGLISH) Direction.ENGLISH_TO_HINGLISH else Direction.HINGLISH_TO_ENGLISH
        result = null; message = null
        render()
    }

    override fun draftChanged(text: String) { draft = text; message = null }

    override fun readCopy() {
        if (busy || KeyboardPrivacy.passwordInputActive) return
        val text = ClipboardAccess.readText(this)
        if (text == null) message = getString(R.string.clipboard_unavailable)
        else { draft = text; result = null; message = null }
        render()
    }

    override fun continueDisclosure() {
        disclosure.acknowledge()
        needsDisclosure = false
        translate()
    }

    override fun translate() {
        if (busy || panel == TranslationPanel.NONE || KeyboardPrivacy.passwordInputActive) return
        if (!DirectionDetector.isSupported(draft)) { message = getString(R.string.error_generic); render(); return }
        if (!disclosure.isAcknowledged()) { needsDisclosure = true; render(); return }
        val text = draft
        val mode = panel
        val target = KeyboardInsertionTarget(session, selectionRevision)
        val connection = currentInputConnection
        val generation = ++requestGeneration
        busy = true; result = null; message = null
        render()
        request = scope.launch {
            val outcome = coordinator.translate(text, if (mode == TranslationPanel.READ) Direction.READ_TO_ENGLISH else direction)
            if (generation != requestGeneration) return@launch
            busy = false
            when (outcome) {
                is TranslationOutcome.Translated -> {
                    if (mode == TranslationPanel.WRITE && target.isCurrent(session, selectionRevision) && connection != null) {
                        val inserted = try { connection.commitText(outcome.translated, 1) } catch (_: RuntimeException) { false }
                        if (inserted) {
                            // commitText inserts/replaces the host selection; never perform its Send action.
                            clearPanel(); render(); renderKeys()
                            toast(R.string.keyboard_inserted)
                            return@launch
                        }
                    }
                    result = outcome.translated
                    if (mode == TranslationPanel.WRITE) message = getString(R.string.keyboard_target_changed)
                }
                is TranslationOutcome.DisclosureRequired -> needsDisclosure = true
                is TranslationOutcome.Offline -> message = getString(R.string.error_offline)
                is TranslationOutcome.Failed -> message = getString(when (outcome.reason) {
                    FailureReason.UNSUPPORTED_INPUT -> R.string.error_generic
                    FailureReason.RATE_LIMITED -> R.string.error_rate_limited
                    FailureReason.TIMEOUT -> R.string.error_timeout
                    FailureReason.PROVIDER_ERROR -> R.string.error_provider
                    FailureReason.INVALID_RESPONSE -> R.string.error_invalid_response
                })
            }
            render()
            renderKeys()
        }
    }

    override fun insertResult() {
        if (panel != TranslationPanel.WRITE || KeyboardPrivacy.passwordInputActive) return
        val text = result ?: return
        val inserted = try { currentInputConnection?.commitText(text, 1) == true } catch (_: RuntimeException) { false }
        if (inserted) { closePanel(); toast(R.string.keyboard_inserted) }
        else { message = getString(R.string.keyboard_target_changed); render() }
    }

    override fun copyResult() {
        val text = result ?: return
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(ClipboardAccess.OWN_CLIP_LABEL, text))
        toast(R.string.copied_confirmation)
    }

    override fun key(text: String) {
        if (busy || needsDisclosure) return
        val value = if (keyPage == KeyPage.LETTERS && shiftState != ShiftState.OFF && text.length == 1 && text[0].isLetter()) text.uppercase() else text
        if (!isEditingDraft()) {
            // Invalidate an insertion target immediately; the editor's selection callback is asynchronous.
            selectionRevision++
            currentInputConnection?.commitText(value, 1)
        } else if (result == null) surface?.editDraft(value)
        if (shiftState == ShiftState.ONCE && text.length == 1 && text[0].isLetter()) {
            shiftState = ShiftState.OFF
            surface?.updateShift(shiftState)
        }
    }

    override fun backspace() {
        if (busy || needsDisclosure) return
        if (isEditingDraft()) { if (result == null) surface?.editDraft(delete = true); return }
        val connection = currentInputConnection ?: return
        selectionRevision++
        if (!connection.getSelectedText(0).isNullOrEmpty()) connection.commitText("", 1)
        else if (Build.VERSION.SDK_INT >= 24) connection.deleteSurroundingTextInCodePoints(1, 0)
        else {
            val before = connection.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            val count = if (before.length == 2 && Character.isSurrogatePair(before[0], before[1])) 2 else 1
            connection.deleteSurroundingText(count, 0)
        }
    }

    override fun shift() {
        val now = SystemClock.uptimeMillis()
        shiftState = when {
            shiftState == ShiftState.LOCKED -> ShiftState.OFF
            shiftState == ShiftState.ONCE && now - lastShiftTap < 350 -> ShiftState.LOCKED
            shiftState == ShiftState.ONCE -> ShiftState.OFF
            else -> ShiftState.ONCE
        }
        lastShiftTap = now
        surface?.updateShift(shiftState)
    }

    override fun page(page: KeyPage) { keyPage = page; renderKeys() }
    override fun lockShift() { shiftState = ShiftState.LOCKED; surface?.updateShift(shiftState) }
    override fun newTranslation() {
        val previous = panel
        openPanel(previous, "")
        if (previous == TranslationPanel.READ) readCopy()
    }
    override fun switchKeyboard() { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }

    override fun enter() {
        if (isEditingDraft()) { key("\n"); return }
        val info = currentInputEditorInfo ?: return
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0 ||
            action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) key("\n")
        else currentInputConnection?.performEditorAction(action)
    }

    private fun clearPanel() {
        requestGeneration++
        request?.cancel(); request = null
        busy = false; needsDisclosure = false
        panel = TranslationPanel.NONE
        draft = ""; result = null; message = null
    }

    private fun render() {
        surface?.renderToolbar(selected, KeyboardPrivacy.passwordInputActive)
        surface?.renderPanel(KeyboardPanelState(panel, direction, draft, busy, result, message, needsDisclosure, selected))
    }

    private fun renderKeys() {
        val label = if (isEditingDraft()) "↵" else when (currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)) {
            EditorInfo.IME_ACTION_GO -> getString(R.string.keyboard_go)
            EditorInfo.IME_ACTION_SEARCH -> "⌕"
            EditorInfo.IME_ACTION_SEND -> getString(R.string.keyboard_send)
            EditorInfo.IME_ACTION_NEXT -> "→"
            EditorInfo.IME_ACTION_DONE -> "✓"
            else -> "↵"
        }
        surface?.renderKeys(keyPage, shiftState, label)
    }

    private fun toast(res: Int) { Toast.makeText(this, res, Toast.LENGTH_SHORT).show() }
    private fun isEditingDraft() = panel != TranslationPanel.NONE && !(panel == TranslationPanel.READ && result != null)
}
