package com.lingoflow.instanttranslate.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
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

/** Local typing plus explicit translation requests. No overlay permission or reading session needed. */
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

    // The open tab's draft/result/message live in these fields; the other tab's are parked in
    // [parkedTabs] so switching Write ↔ Read keeps both drafts. Reading text never moves into the
    // Write tab, so a received message can't be inserted into the user's reply.
    private var draft = ""
    private var result: String? = null
    private var message: String? = null
    private data class TabState(val draft: String = "", val result: String? = null, val message: String? = null)
    private val parkedTabs = mutableMapOf<TranslationPanel, TabState>()

    private var needsDisclosure = false
    /** A request is in flight for [requestTab]; it may finish while the other tab is open. */
    private var busy = false
    private var requestTab = TranslationPanel.NONE
    private var selected = false
    private var lastShiftTap = 0L
    private var session = 0L
    private var selectionRevision = 0L
    private var requestGeneration = 0L
    private var request: Job? = null
    private var selectionStart = -1
    private var selectionEnd = -1
    private var editorIdentity: Triple<String?, Int, Int>? = null

    // Suggestions. [recentText] mirrors the editor text just before the cursor while typing, so
    // each key needs no blocking editor query; it is reconciled with the real editor after a pause.
    private val handler = Handler(Looper.getMainLooper())
    private val recentText = StringBuilder()
    private var suggestionsAllowed = false
    private var learningAllowed = false
    private var capitalizeSentences = false
    /** False after the cursor jumps or the field empties, so tools return until the user types. */
    private var stripActive = false
    private val reconcile = Runnable { reconcileWithEditor() }

    override fun onCreate() {
        super.onCreate()
        SuggestionEngine.load(this) { refreshSuggestions() }
    }

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
            scheduleReconcile()
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
        configureSuggestions(type, attribute?.imeOptions ?: 0)
        shiftState = if ((attribute?.initialCapsMode ?: 0) != 0) ShiftState.ONCE else ShiftState.OFF
        keyPage = if (kind == InputType.TYPE_CLASS_NUMBER || kind == InputType.TYPE_CLASS_PHONE) KeyPage.SYMBOLS else KeyPage.LETTERS
        lastShiftTap = 0
        if (KeyboardPrivacy.passwordInputActive) KeyboardReadingInbox.clear()
        render(); renderKeys()
    }

    /**
     * Suggestions follow the editor's wishes: never in passwords, numbers, emails or URLs, nor when
     * the app opts out. Learning is additionally off in incognito fields (NO_PERSONALIZED_LEARNING).
     */
    private fun configureSuggestions(type: Int, imeOptions: Int) {
        val kind = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        suggestionsAllowed = !KeyboardPrivacy.passwordInputActive && kind == InputType.TYPE_CLASS_TEXT &&
            variation !in setOf(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_URI, InputType.TYPE_TEXT_VARIATION_FILTER) &&
            type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS == 0
        learningAllowed = suggestionsAllowed && imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0
        capitalizeSentences = type and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0
        recentText.clear()
        stripActive = false
        refreshSuggestions()
        scheduleReconcile()
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
        // A selection replaces the word context entirely; otherwise confirm our mirror after a pause.
        if (hasSelection && stripActive) { stripActive = false; refreshSuggestions() }
        scheduleReconcile()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        // Hiding the keyboard cancels requests; returning must never insert a stale response.
        clearPanel(); render()
        SuggestionEngine.save(this)
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        clearPanel()
        session++
        editorIdentity = null
        handler.removeCallbacks(reconcile)
        KeyboardPrivacy.passwordInputActive = false
        super.onFinishInput()
    }

    override fun onDestroy() {
        scope.cancel()
        handler.removeCallbacks(reconcile)
        SuggestionEngine.save(this)
        KeyboardPrivacy.passwordInputActive = false
        surface = null
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // InputMethodService recreates its views. Keep this request/draft only if its editor survives.
    }

    // ---- Write / Read tabs ---------------------------------------------------------------

    override fun translateIcon() {
        if (KeyboardPrivacy.passwordInputActive) return
        if (selected) {
            val text = selectedText()
            if (text == null) { toast(R.string.keyboard_selection_unavailable); return }
            openPanel(TranslationPanel.READ, text)
            translate()
        } else switchTab(TranslationPanel.WRITE)
    }

    override fun readIcon() {
        if (KeyboardPrivacy.passwordInputActive) return
        if (panel == TranslationPanel.READ) { closePanel(); return }
        val text = if (selected) selectedText() else null
        if (text != null) { switchTab(TranslationPanel.READ, freshDraft = text); translate() }
        else switchTab(TranslationPanel.READ)
    }

    /**
     * Tapping the open tab closes the panel; tapping the other tab swaps drafts in place without
     * cancelling a running request or rebuilding the composer. A first visit to Read pulls in the
     * copied message, as opening Read always has.
     */
    private fun switchTab(mode: TranslationPanel, freshDraft: String? = null) {
        if (panel == mode && freshDraft == null) { closePanel(); return }
        if (panel == TranslationPanel.NONE) {
            openPanel(mode, freshDraft.orEmpty())
            if (mode == TranslationPanel.READ && freshDraft == null) readCopy()
            return
        }
        if (panel != mode) parkedTabs[panel] = TabState(draft, result, message)
        needsDisclosure = false
        panel = mode
        val restored = freshDraft?.let { TabState(it) } ?: parkedTabs.remove(mode) ?: TabState()
        parkedTabs.remove(mode)
        draft = restored.draft; result = restored.result; message = restored.message
        if (mode == TranslationPanel.READ && freshDraft == null && draft.isEmpty() && result == null && !tabBusy()) {
            readCopy(); renderKeys(); return
        }
        render(); renderKeys()
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
        refreshSuggestions()
    }

    override fun closePanel() { clearPanel(); render(); renderKeys(); refreshSuggestions() }
    override fun swapDirection() {
        if (tabBusy()) return
        languages = languages.swapped()
        saveLanguages()
        result = null; message = null
        render(); renderKeys()
    }

    override fun languageChosen(source: Boolean, id: String) {
        if (tabBusy()) return
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
        if (tabBusy() || KeyboardPrivacy.passwordInputActive) return
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
        if (panel == TranslationPanel.NONE || KeyboardPrivacy.passwordInputActive || tabBusy()) return
        if (!DirectionDetector.isSupported(draft)) { message = getString(R.string.error_generic); render(); return }
        if (!disclosure.isAcknowledged()) { needsDisclosure = true; render(); return }
        // One request at a time: a new one replaces the other tab's, which keeps its draft.
        if (busy) cancelRequest()
        val text = draft
        val mode = panel
        val requestedLanguages = languages
        val target = KeyboardInsertionTarget(session, selectionRevision)
        val connection = currentInputConnection
        val generation = ++requestGeneration
        busy = true; requestTab = mode; result = null; message = null
        render(); renderKeys()
        request = scope.launch {
            val outcome = coordinator.translate(text, languages = requestedLanguages)
            if (generation != requestGeneration) return@launch
            busy = false
            deliver(mode, outcome, target, connection)
            render()
            renderKeys()
        }
    }

    /** Applies a finished request to [mode]'s tab, whether it is open or parked. */
    private fun deliver(mode: TranslationPanel, outcome: TranslationOutcome, target: KeyboardInsertionTarget,
        connection: android.view.inputmethod.InputConnection?) {
        val current = tabState(mode)
        when (outcome) {
            is TranslationOutcome.Translated -> {
                if (mode == TranslationPanel.WRITE && target.isCurrent(session, selectionRevision) && connection != null) {
                    val inserted = try { connection.commitText(outcome.translated, 1) } catch (_: RuntimeException) { false }
                    if (inserted) {
                        // commitText inserts/replaces the host selection; never perform its Send action.
                        // From a parked Write tab only that tab resets, so an open Read result stays put.
                        if (panel == TranslationPanel.WRITE) clearPanel() else parkedTabs.remove(TranslationPanel.WRITE)
                        toast(R.string.keyboard_inserted)
                        return
                    }
                }
                setTabState(mode, current.copy(result = outcome.translated,
                    message = if (mode == TranslationPanel.WRITE) getString(R.string.keyboard_target_changed) else null))
            }
            is TranslationOutcome.DisclosureRequired -> if (mode == panel) needsDisclosure = true
            is TranslationOutcome.Offline -> setTabState(mode, current.copy(message = getString(R.string.error_offline)))
            is TranslationOutcome.Failed -> setTabState(mode, current.copy(message = getString(when (outcome.reason) {
                FailureReason.UNSUPPORTED_INPUT -> R.string.error_generic
                FailureReason.RATE_LIMITED -> R.string.error_rate_limited
                FailureReason.TIMEOUT -> R.string.error_timeout
                FailureReason.PROVIDER_ERROR -> R.string.error_provider
                FailureReason.INVALID_RESPONSE -> R.string.error_invalid_response
            })))
        }
    }

    private fun tabState(mode: TranslationPanel) =
        if (mode == panel) TabState(draft, result, message) else parkedTabs[mode] ?: TabState()

    private fun setTabState(mode: TranslationPanel, state: TabState) {
        if (mode == panel) { draft = state.draft; result = state.result; message = state.message }
        else if (mode != TranslationPanel.NONE) parkedTabs[mode] = state
    }

    /** The open tab is waiting for its own request (the other tab's request doesn't block it). */
    private fun tabBusy() = busy && requestTab == panel

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

    // ---- Typing --------------------------------------------------------------------------

    override fun key(text: String) {
        if (needsDisclosure || (tabBusy() && panel != TranslationPanel.READ)) return
        val value = if (keyPage == KeyPage.LETTERS && shiftState != ShiftState.OFF && text.length == 1 && text[0].isLetter()) text.uppercase() else text
        if (!isEditingDraft()) {
            // Invalidate an insertion target immediately; the editor's selection callback is asynchronous.
            selectionRevision++
            if (currentInputConnection?.commitText(value, 1) == true && selected) {
                // A fast Delete can arrive before the editor reports that the replaced selection collapsed.
                selected = false
                surface?.renderToolbar(false, KeyboardPrivacy.passwordInputActive)
            }
            editorTyped(value)
        } else if (result == null) surface?.editDraft(value)
        if (shiftState == ShiftState.ONCE && text.length == 1 && text[0].isLetter()) {
            shiftState = ShiftState.OFF
            surface?.updateShift(shiftState)
        }
    }

    override fun backspace() {
        if (needsDisclosure || (tabBusy() && panel != TranslationPanel.READ)) return
        if (isEditingDraft()) { if (result == null) surface?.editDraft(delete = true); return }
        val connection = currentInputConnection ?: return
        selectionRevision++
        // Selection callbacks already carry this information; avoid a blocking editor query per delete.
        if (selected) {
            if (connection.commitText("", 1)) {
                selected = false
                surface?.renderToolbar(false, KeyboardPrivacy.passwordInputActive)
            }
            recentText.clear() // Unknown until reconciled with the editor.
        }
        else if (Build.VERSION.SDK_INT >= 24) connection.deleteSurroundingTextInCodePoints(1, 0)
        else {
            val before = connection.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            val count = if (before.length == 2 && Character.isSurrogatePair(before[0], before[1])) 2 else 1
            connection.deleteSurroundingText(count, 0)
        }
        if (!selected && recentText.isNotEmpty()) {
            recentText.setLength(recentText.offsetByCodePoints(recentText.length, -1))
        }
        refreshSuggestions()
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
        if (panel == TranslationPanel.NONE) return
        if (tabBusy()) cancelRequest()
        draft = ""; result = null; message = null; needsDisclosure = false
        if (panel == TranslationPanel.READ) readCopy() else render()
        renderKeys()
    }
    override fun switchKeyboard() { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }

    override fun enter() {
        if (isEditingDraft()) { key("\n"); return }
        val info = currentInputEditorInfo ?: return
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0 ||
            action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) key("\n")
        else {
            learnFinishedWord() // Send/Go finish the last word just like a space would.
            currentInputConnection?.performEditorAction(action)
        }
    }

    // ---- Suggestions ---------------------------------------------------------------------

    /** Keeps the local mirror in step with a commit and learns a word that a separator just finished. */
    private fun editorTyped(value: String) {
        if (!suggestionsAllowed) return
        if (value.none(TypingContext::isWordChar)) learnFinishedWord()
        recentText.append(value)
        if (recentText.length > RECENT_LIMIT * 2) recentText.delete(0, recentText.length - RECENT_LIMIT)
        stripActive = true
        // Gboard-style auto-capital after ". " when the field asks for sentence case.
        if (value == " " && capitalizeSentences && shiftState == ShiftState.OFF && recentText.isNotBlank() &&
            TypingContext.from(recentText.toString()).sentenceStart) {
            shiftState = ShiftState.ONCE
            surface?.updateShift(shiftState)
        }
        refreshSuggestions()
    }

    private fun learnFinishedWord() {
        if (!learningAllowed) return
        val context = TypingContext.from(recentText.toString())
        if (context.prefix.isNotEmpty()) SuggestionEngine.learn(context.prefix, context.previous.lastOrNull())
    }

    override fun suggestionPicked(text: String) {
        if (!suggestionsAllowed || isEditingDraft() || panel != TranslationPanel.NONE) return
        val connection = currentInputConnection ?: return
        // One editor query per tap (not per key) so the replaced word is exactly what's on screen.
        val before = try { connection.getTextBeforeCursor(RECENT_LIMIT, 0)?.toString() } catch (_: RuntimeException) { null }
            ?: recentText.toString()
        val after = try { connection.getTextAfterCursor(SUFFIX_LIMIT, 0)?.toString() } catch (_: RuntimeException) { null }.orEmpty()
        val context = TypingContext.from(before)
        val suffix = after.takeWhile(TypingContext::isWordChar)
        val word = if (context.prefix.isEmpty() && shiftState != ShiftState.OFF) text.replaceFirstChar { it.uppercaseChar() } else text
        // Gboard adds one space after a picked word, unless the text already continues with one.
        val spaceFollows = after.drop(suffix.length).startsWith(" ")
        connection.beginBatchEdit()
        if (context.prefix.isNotEmpty() || suffix.isNotEmpty()) connection.deleteSurroundingText(context.prefix.length, suffix.length)
        connection.commitText(if (spaceFollows) word else "$word ", 1)
        if (spaceFollows && selectionStart >= 0) {
            val cursor = selectionStart - context.prefix.length + word.length + 1
            connection.setSelection(cursor, cursor)
        }
        connection.endBatchEdit()
        selectionRevision++
        if (learningAllowed) SuggestionEngine.learn(word, context.previous.lastOrNull())
        recentText.setLength(0)
        recentText.append(before.dropLast(context.prefix.length)).append(word).append(' ')
        if (shiftState == ShiftState.ONCE) { shiftState = ShiftState.OFF; surface?.updateShift(shiftState) }
        stripActive = true
        refreshSuggestions()
    }

    private fun scheduleReconcile() {
        handler.removeCallbacks(reconcile)
        if (suggestionsAllowed) handler.postDelayed(reconcile, RECONCILE_DELAY_MS)
    }

    /**
     * Re-reads the text before the cursor once typing pauses. A mismatch means the cursor moved
     * or the app changed the text (e.g. cleared it after Send): hide the strip until the next key.
     */
    private fun reconcileWithEditor() {
        if (!suggestionsAllowed) return
        if (selectionStart != selectionEnd) return
        val actual = try { currentInputConnection?.getTextBeforeCursor(RECENT_LIMIT, 0)?.toString() } catch (_: RuntimeException) { null } ?: return
        val local = recentText.toString()
        val overlap = minOf(actual.length, local.length)
        val moved = local.isNotEmpty() && actual.takeLast(overlap) != local.takeLast(overlap)
        recentText.setLength(0); recentText.append(actual)
        if (moved || actual.isEmpty()) stripActive = false
        refreshSuggestions()
    }

    private fun refreshSuggestions() {
        val engine = SuggestionEngine.predictor
        val next = if (!suggestionsAllowed || !stripActive || engine == null || panel != TranslationPanel.NONE) emptyList()
        else {
            val context = TypingContext.from(recentText.toString())
            if (context.prefix.isNotEmpty()) engine.complete(context.prefix, context.previous)
            else engine.next(context.previous).map {
                // Next-word guesses follow Shift, so "Thanks" is offered when a sentence starts.
                if (shiftState != ShiftState.OFF) it.copy(text = it.text.replaceFirstChar { char -> char.uppercaseChar() }) else it
            }
        }
        surface?.renderSuggestions(next)
    }

    // ---- Shared state --------------------------------------------------------------------

    private fun cancelRequest() {
        requestGeneration++
        request?.cancel(); request = null
        busy = false
    }

    private fun clearPanel() {
        surface?.dismissLanguagePicker()
        cancelRequest()
        needsDisclosure = false
        panel = TranslationPanel.NONE
        draft = ""; result = null; message = null
        parkedTabs.clear()
    }

    private fun render() {
        surface?.renderPanel(KeyboardPanelState(panel, direction, draft, tabBusy(), result, message, needsDisclosure, selected, languages),
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
    private fun isEditingDraft() = panel != TranslationPanel.NONE && !(panel == TranslationPanel.READ && (tabBusy() || result != null))

    private companion object {
        /** Enough text before the cursor for the current word and two words of context. */
        const val RECENT_LIMIT = 64
        const val SUFFIX_LIMIT = 32
        /** Long enough that fast typing never waits on the editor, short enough to feel live. */
        const val RECONCILE_DELAY_MS = 120L
    }
}
