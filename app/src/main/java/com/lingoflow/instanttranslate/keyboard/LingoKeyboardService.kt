package com.lingoflow.instanttranslate.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.lingoflow.instanttranslate.R
import com.lingoflow.instanttranslate.cloud.AndroidConnectivityChecker
import com.lingoflow.instanttranslate.coordinator.TranslateCoordinator
import com.lingoflow.instanttranslate.coordinator.TranslationOutcome
import com.lingoflow.instanttranslate.direction.Direction
import com.lingoflow.instanttranslate.direction.TranslationLanguagePair
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
    private val languagePreferences by lazy { getSharedPreferences("translation_languages", MODE_PRIVATE) }
    private var languages = TranslationLanguagePair()
    private val direction = Direction.MULTILINGUAL
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
    private var editorIdentity: Triple<String?, Int, Int>? = null

    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View = LingoKeyboardView(this, this).also {
        surface = it
        render()
        renderKeys()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        val identity = Triple(attribute?.packageName, attribute?.fieldId ?: 0, attribute?.inputType ?: 0)
        val savedLanguages = TranslationLanguagePair(languagePreferences.getString("source", "auto") ?: "auto",
            languagePreferences.getString("target", "en") ?: "en").takeIf { it.isValid() } ?: TranslationLanguagePair()
        // Chat apps can restart the same editor while updating its state. Keep the local panel,
        // but invalidate any write-insertion target so a restarted cursor never gets stale text.
        if (restarting && identity == editorIdentity && languages == savedLanguages) {
            selectionRevision++
            selectionStart = attribute?.initialSelStart ?: -1
            selectionEnd = attribute?.initialSelEnd ?: -1
            selected = selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd
            render()
            return
        }
        editorIdentity = identity
        languages = savedLanguages
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
        editorIdentity = null
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
        } else if (panel == TranslationPanel.READ) translate()
        else openPanel(TranslationPanel.WRITE, "")
    }

    override fun readIcon() {
        if (KeyboardPrivacy.passwordInputActive) return
        if (panel == TranslationPanel.READ) { closePanel(); return }
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
        languages = languages.swapped()
        saveLanguages()
        result = null; message = null
        render(); renderKeys()
    }

    override fun languageChosen(source: Boolean, id: String) {
        if (busy) return
        val updated = if (source) languages.copy(source = id) else languages.copy(target = id)
        if (!updated.isValid()) return
        languages = updated
        saveLanguages()
        result = null; message = null
        render(); renderKeys()
    }

    private fun saveLanguages() {
        languagePreferences.edit().putString("source", languages.source).putString("target", languages.target).apply()
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
        val requestedLanguages = languages
        val target = KeyboardInsertionTarget(session, selectionRevision)
        val connection = currentInputConnection
        val generation = ++requestGeneration
        busy = true; result = null; message = null
        render(); renderKeys()
        request = scope.launch {
            val outcome = coordinator.translate(text, languages = requestedLanguages)
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
        if (needsDisclosure || (busy && panel != TranslationPanel.READ)) return
        val value = if (keyPage == KeyPage.LETTERS && shiftState != ShiftState.OFF && text.length == 1 && text[0].isLetter()) text.uppercase() else text
        if (!isEditingDraft()) {
            // Invalidate an insertion target immediately; the editor's selection callback is asynchronous.
            selectionRevision++
            if (currentInputConnection?.commitText(value, 1) == true && selected) {
                // A fast Delete can arrive before the editor reports that the replaced selection collapsed.
                selected = false
                surface?.renderToolbar(false, KeyboardPrivacy.passwordInputActive)
            }
        } else if (result == null) surface?.editDraft(value)
        if (shiftState == ShiftState.ONCE && text.length == 1 && text[0].isLetter()) {
            shiftState = ShiftState.OFF
            surface?.updateShift(shiftState)
        }
    }

    override fun backspace() {
        if (needsDisclosure || (busy && panel != TranslationPanel.READ)) return
        if (isEditingDraft()) { if (result == null) surface?.editDraft(delete = true); return }
        val connection = currentInputConnection ?: return
        selectionRevision++
        // Selection callbacks already carry this information; avoid a blocking editor query per delete.
        if (selected) {
            if (connection.commitText("", 1)) {
                selected = false
                surface?.renderToolbar(false, KeyboardPrivacy.passwordInputActive)
            }
        }
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
        surface?.dismissLanguagePicker()
        requestGeneration++
        request?.cancel(); request = null
        busy = false; needsDisclosure = false
        panel = TranslationPanel.NONE
        draft = ""; result = null; message = null
    }

    private fun render() {
        surface?.renderPanel(KeyboardPanelState(panel, direction, draft, busy, result, message, needsDisclosure, selected, languages),
            KeyboardPrivacy.passwordInputActive)
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
    private fun isEditingDraft() = panel != TranslationPanel.NONE && !(panel == TranslationPanel.READ && (busy || result != null))
}
