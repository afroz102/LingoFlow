package com.lingoflow.instanttranslate.keyboard

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.animation.DecelerateInterpolator
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import com.lingoflow.instanttranslate.R
import com.lingoflow.instanttranslate.direction.Direction
import com.lingoflow.instanttranslate.direction.TranslationLanguagePair
import com.lingoflow.instanttranslate.direction.TranslationLanguages

internal data class KeyboardPanelState(
    val panel: TranslationPanel,
    val direction: Direction,
    val draft: String,
    val busy: Boolean,
    val result: String?,
    val message: String?,
    val disclosure: Boolean,
    val selected: Boolean,
    val languages: TranslationLanguagePair = TranslationLanguagePair(),
)

interface KeyboardActions {
    fun translateIcon()
    fun readIcon()
    fun closePanel()
    fun languageChosen(source: Boolean, id: String) = Unit
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
    /** A suggestion-strip word was tapped; it replaces the word at the editor's cursor. */
    fun suggestionPicked(text: String) = Unit
}

/**
 * Toolbar + optional translation panel + the drawn key grid. The grid stays mounted for the
 * whole input session; panel and language updates never touch it, so typing never stutters.
 */
@SuppressLint("ViewConstructor") // Constructed by the IME with its callbacks, never inflated from XML.
internal class LingoKeyboardView(context: Context, private val actions: KeyboardActions) :
    LinearLayout(context), KeyGridListener {
    private val palette = KeyboardPalette.of(context)
    private val compact = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private val toolbarHeight = if (compact) 38 else 44
    private val toolbar = LinearLayout(context)
    private val tabGroup = FrameLayout(context)
    private val tabIndicator = View(context)
    private val languageBar = LinearLayout(context)
    private val suggestionStrip = LinearLayout(context)
    private val suggestionSlots = mutableListOf<TextView>()
    private val suggestionDividers = mutableListOf<View>()
    private var suggestions: List<Suggestion> = emptyList()
    /** Set while the view itself replaces draft text, so that isn't echoed back as user typing. */
    private var applyingDraft = false
    private val panel = LinearLayout(context)
    private val keyGrid = KeyGridView(context, palette, compact, this)
    private var languagePopup: PopupWindow? = null

    private var translateButton: ImageButton? = null
    private var readButton: ImageButton? = null
    private var sourceButton: Button? = null
    private var targetButton: Button? = null
    private var swapButton: ImageButton? = null
    private var trailingButton: ImageButton? = null
    private var toolbarPanel = TranslationPanel.NONE
    private var toolbarLanguages = TranslationLanguagePair()
    private var toolbarBusy = false
    private var toolbarPassword = false

    private var renderedKeyPage = KeyPage.LETTERS
    private var renderedPanel: KeyboardPanelState? = null
    private var draftField: EditText? = null
    private var pasteButton: ImageButton? = null
    private var submitButton: ImageButton? = null
    private var submitProgress: ProgressBar? = null
    private var statusContainer: LinearLayout? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(palette.background)
        if (Build.VERSION.SDK_INT >= 26) importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        isSaveEnabled = false
        toolbar.gravity = Gravity.CENTER_VERTICAL
        // 2dp + the 4dp ripple inset lines the first icon's highlight up with the key edges (6dp).
        toolbar.setPadding(dp(2), 0, dp(2), 0)
        addView(toolbar, LayoutParams(LayoutParams.MATCH_PARENT, dp(toolbarHeight)))
        panel.orientation = VERTICAL
        panel.setPadding(dp(6), 0, dp(6), dp(4))
        addView(panel)
        keyGrid.setPadding(dp(3), dp(2), dp(3), dp(4))
        keyGrid.previewHost = this
        addView(keyGrid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        createToolbar()
        renderToolbar(false, false)
    }

    // ---- Toolbar ---------------------------------------------------------------------------

    private fun createToolbar() {
        toolbar.isBaselineAligned = false
        translateButton = toolbarIcon(R.drawable.ic_keyboard_translate, context.getString(R.string.action_translate)) {
            actions.translateIcon()
        }
        readButton = toolbarIcon(R.drawable.ic_keyboard_read, context.getString(R.string.keyboard_read)) { actions.readIcon() }
        // Write and Read act as tabs: one highlight slides between them instead of each icon
        // repainting its own background, so a switch reads as a single smooth motion.
        tabIndicator.background = rounded(palette.softAccent, 14f)
        tabIndicator.alpha = 0f
        tabIndicator.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        tabGroup.addView(tabIndicator, FrameLayout.LayoutParams(dp(TAB_WIDTH - 8), dp(toolbarHeight - 8), Gravity.CENTER_VERTICAL)
            .apply { leftMargin = dp(4) })
        val tabs = LinearLayout(context)
        tabs.addView(translateButton, LayoutParams(dp(TAB_WIDTH), dp(toolbarHeight)))
        tabs.addView(readButton, LayoutParams(dp(TAB_WIDTH), dp(toolbarHeight)))
        tabGroup.addView(tabs, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        toolbar.addView(tabGroup, LayoutParams(LayoutParams.WRAP_CONTENT, dp(toolbarHeight)))
        // Language choice only matters once a translation panel is open, so the default header
        // stays as quiet as Gboard's; the bar keeps its space to avoid shifting the other icons.
        languageBar.gravity = Gravity.CENTER_VERTICAL
        languageBar.isBaselineAligned = false
        languageBar.setPadding(dp(4), 0, dp(2), 0)
        sourceButton = languageChip(true)
        targetButton = languageChip(false)
        swapButton = toolbarIcon(R.drawable.ic_keyboard_swap, context.getString(R.string.keyboard_swap)) { actions.swapDirection() }
            .apply { setPadding(dp(7), dp(7), dp(7), dp(7)) }
        languageBar.addView(sourceButton, chipParams())
        languageBar.addView(swapButton, LayoutParams(dp(34), dp(34)))
        languageBar.addView(targetButton, chipParams())
        toolbar.addView(languageBar, LayoutParams(0, dp(toolbarHeight), 1f))
        createSuggestionStrip()
        toolbar.addView(suggestionStrip, LayoutParams(0, dp(toolbarHeight), 1f))
        trailingButton = toolbarIcon(R.drawable.ic_keyboard_globe, context.getString(R.string.keyboard_switch)) {
            if (toolbarPanel == TranslationPanel.NONE) actions.switchKeyboard() else actions.closePanel()
        }
        toolbar.addView(trailingButton, LayoutParams(dp(44), dp(toolbarHeight)))
    }

    private fun chipParams() = LayoutParams(0, dp(if (compact) 30 else 34), 1f)

    private fun languageChip(source: Boolean): Button = Button(context).apply {
        textSize = 13f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; isAllCaps = false
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(palette.ink)
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(10), 0, dp(10), 0)
        includeFontPadding = false
        stateListAnimator = null; isSaveEnabled = false; isSoundEffectsEnabled = false
        background = ripple(palette.surface, 17f)
        contentDescription = if (source) "Choose source language" else "Choose target language"
        setOnClickListener {
            showLanguages(this, source, if (source) toolbarLanguages.source else toolbarLanguages.target)
        }
    }

    fun renderToolbar(selected: Boolean, password: Boolean, mode: TranslationPanel = toolbarPanel,
        languages: TranslationLanguagePair = toolbarLanguages, busy: Boolean = toolbarBusy) {
        if (toolbarLanguages != languages) {
            // IME accessibility can retain a stale target label after simultaneous text changes.
            // New chip nodes refresh the spoken pair; keys and the draft editor remain mounted.
            languageBar.removeView(sourceButton); languageBar.removeView(targetButton)
            sourceButton = languageChip(true); targetButton = languageChip(false)
            languageBar.addView(sourceButton, 0, chipParams())
            languageBar.addView(targetButton, 2, chipParams())
        }
        val panelOpened = toolbarPanel == TranslationPanel.NONE && mode != TranslationPanel.NONE
        val tabChanged = toolbarPanel != mode
        toolbarPanel = mode; toolbarLanguages = languages; toolbarBusy = busy; toolbarPassword = password
        // Tabs stay enabled while a request runs so users can switch away; it finishes in its own tab.
        translateButton?.apply {
            contentDescription = context.getString(if (selected) R.string.keyboard_translate_selection else R.string.action_translate)
            isEnabled = !password
            alpha = if (isEnabled) 1f else DISABLED_ALPHA
            styleModeIcon(this, mode == TranslationPanel.WRITE)
        }
        readButton?.apply {
            isEnabled = !password
            alpha = if (isEnabled) 1f else DISABLED_ALPHA
            styleModeIcon(this, mode == TranslationPanel.READ)
        }
        if (tabChanged) moveTabIndicator(mode)
        updateToolbarMode()
        if (panelOpened) fadeIn(languageBar)
        sourceButton?.apply {
            val label = TranslationLanguages.find(languages.source)?.label ?: languages.source
            setChipText(this, if (languages.source == "auto") "Auto" else label)
            contentDescription = "Choose source language: $label"
            isEnabled = !password && !busy
        }
        targetButton?.apply {
            val label = TranslationLanguages.find(languages.target)?.label ?: languages.target
            setChipText(this, label)
            contentDescription = "Choose target language: $label"
            isEnabled = !password && !busy
        }
        swapButton?.isEnabled = !password && !busy
        trailingButton?.apply {
            val close = mode != TranslationPanel.NONE
            setImageResource(if (close) R.drawable.ic_keyboard_close else R.drawable.ic_keyboard_globe)
            imageTintList = ColorStateList.valueOf(palette.muted)
            contentDescription = context.getString(if (close) R.string.action_close else R.string.keyboard_switch)
        }
    }

    /** The caret is part of the label (tests and TalkBack read "English ▾") but drawn muted. */
    private fun setChipText(chip: Button, name: String) {
        val value = "$name ▾"
        if (chip.text.toString() == value) return
        chip.text = SpannableString(value).apply {
            setSpan(ForegroundColorSpan(palette.muted), value.length - 1, value.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** Icon colour cross-fades in step with the sliding highlight. */
    private fun styleModeIcon(button: ImageButton, active: Boolean) {
        if (button.isSelected == active && button.tag == active) return
        val firstStyle = button.tag == null
        button.isSelected = active; button.isActivated = active; button.tag = active
        val target = if (active) palette.accent else palette.muted
        val start = button.imageTintList?.defaultColor ?: palette.muted
        if (firstStyle || !button.isAttachedToWindow) { button.imageTintList = ColorStateList.valueOf(target); return }
        ValueAnimator.ofObject(ArgbEvaluator(), start, target).apply {
            duration = TAB_ANIMATION_MS
            addUpdateListener { button.imageTintList = ColorStateList.valueOf(it.animatedValue as Int) }
        }.start()
    }

    private fun moveTabIndicator(mode: TranslationPanel) {
        val targetX = if (mode == TranslationPanel.READ) dp(TAB_WIDTH).toFloat() else 0f
        val targetAlpha = if (mode == TranslationPanel.NONE) 0f else 1f
        tabIndicator.animate().cancel()
        if (!tabIndicator.isAttachedToWindow) { tabIndicator.translationX = targetX; tabIndicator.alpha = targetAlpha; return }
        // Appearing: fade in under the chosen tab rather than sliding in from the other one.
        if (tabIndicator.alpha == 0f) tabIndicator.translationX = targetX
        tabIndicator.animate().translationX(targetX).alpha(targetAlpha).setDuration(TAB_ANIMATION_MS)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    /**
     * Gboard-style header: while words are being suggested the strip takes the space of Read,
     * the language bar and the switcher; Translate stays as the one-tap entry to translation.
     */
    private fun updateToolbarMode() {
        val suggesting = toolbarPanel == TranslationPanel.NONE && suggestions.isNotEmpty()
        readButton?.visibility = if (suggesting) GONE else VISIBLE
        languageBar.visibility = when {
            toolbarPanel != TranslationPanel.NONE -> VISIBLE
            suggesting -> GONE
            else -> INVISIBLE
        }
        trailingButton?.visibility = if (suggesting) GONE else VISIBLE
        val stripWasVisible = suggestionStrip.visibility == VISIBLE
        suggestionStrip.visibility = if (suggesting) VISIBLE else GONE
        if (suggesting && !stripWasVisible) fadeIn(suggestionStrip)
    }

    // ---- Suggestion strip ------------------------------------------------------------------

    private fun createSuggestionStrip() {
        suggestionStrip.gravity = Gravity.CENTER_VERTICAL
        suggestionStrip.visibility = GONE
        repeat(3) { index ->
            if (index > 0) {
                val divider = View(context).apply { setBackgroundColor((palette.muted and 0x00FFFFFF) or 0x47000000) }
                suggestionDividers.add(divider)
                suggestionStrip.addView(divider, LayoutParams(dp(1), dp(18)))
            }
            val slot = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = if (compact) 15f else 16.5f
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setTextColor(palette.ink)
                // The centre slot holds the best guess, emphasised like Gboard's.
                typeface = Typeface.create(if (index == 1) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
                background = ripple(Color.TRANSPARENT, 12f, inset = 3)
                setPadding(dp(6), 0, dp(6), 0)
                isSoundEffectsEnabled = false
                setOnClickListener {
                    val suggestion = tag as? Suggestion ?: return@setOnClickListener
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    actions.suggestionPicked(suggestion.text)
                }
            }
            suggestionSlots.add(slot)
            suggestionStrip.addView(slot, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
    }

    /** [next] is best-first; the best goes in the centre, and a kept-as-typed word on the left. */
    fun renderSuggestions(next: List<Suggestion>) {
        if (next == suggestions) return
        suggestions = next
        val literal = next.firstOrNull()?.literal == true
        val ordered = if (literal) listOf(next.getOrNull(0), next.getOrNull(1), next.getOrNull(2))
        else listOf(next.getOrNull(1), next.getOrNull(0), next.getOrNull(2))
        suggestionSlots.forEachIndexed { index, slot ->
            val suggestion = ordered[index]
            slot.tag = suggestion
            slot.text = when {
                suggestion == null -> ""
                suggestion.literal -> "“${suggestion.text}”"
                else -> suggestion.text
            }
            slot.contentDescription = suggestion?.let { if (it.literal) "Keep ${it.text}" else it.text }
            slot.isClickable = suggestion != null
            slot.importantForAccessibility = if (suggestion == null) IMPORTANT_FOR_ACCESSIBILITY_NO else IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        suggestionDividers.forEachIndexed { index, divider ->
            divider.visibility = if (ordered[index] != null && ordered[index + 1] != null) VISIBLE else INVISIBLE
        }
        updateToolbarMode()
    }

    private fun toolbarIcon(iconRes: Int, label: String, action: () -> Unit): ImageButton = ImageButton(context).apply {
        contentDescription = label
        setImageDrawable(AppCompatResources.getDrawable(context, iconRes)?.mutate())
        imageTintList = ColorStateList.valueOf(palette.muted)
        scaleType = ImageView.ScaleType.FIT_CENTER
        background = ripple(Color.TRANSPARENT, 14f, inset = 4)
        val vertical = (dp(toolbarHeight) - dp(22)) / 2
        setPadding(dp(11), vertical, dp(11), vertical)
        stateListAnimator = null; isSaveEnabled = false; isSoundEffectsEnabled = false
        setOnClickListener { action() }
    }

    fun dismissLanguagePicker() { languagePopup?.dismiss(); languagePopup = null }

    private fun showLanguages(anchor: View, source: Boolean, selectedId: String) {
        languagePopup?.dismiss()
        val preferred = listOf(selectedId, "en", "hi-Latn", "hi").mapNotNull(TranslationLanguages::find)
        val choices = ((if (source) listOf(TranslationLanguages.auto) else emptyList()) + preferred +
            TranslationLanguages.available.sortedBy { it.label }).filter { source || it.id != "auto" }.distinctBy { it.id }
        val list = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(6), dp(6), dp(6), dp(6)) }
        val popup = PopupWindow(ScrollView(context).apply { addView(list); background = rounded(palette.surface, 20f) },
            minOf(resources.displayMetrics.widthPixels - dp(24), dp(320)), dp(if (compact) 180 else 280), false)
        choices.forEach { language ->
            val chosen = language.id == selectedId
            list.addView(TextView(context).apply {
                text = language.label + if (chosen) "  ✓" else ""
                textSize = 15f
                setTextColor(if (chosen) palette.accent else palette.ink)
                typeface = Typeface.create(if (chosen) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setPadding(dp(16), 0, dp(16), 0)
                background = ripple(if (chosen) palette.softAccent else palette.surface, 12f)
                contentDescription = (if (source) "Source: " else "Target: ") + language.label
                isClickable = true; isSoundEffectsEnabled = false
                setOnClickListener { popup.dismiss(); actions.languageChosen(source, language.id) }
            }, LayoutParams(LayoutParams.MATCH_PARENT, dp(46)))
        }
        popup.setBackgroundDrawable(rounded(palette.surface, 20f))
        popup.isOutsideTouchable = true
        popup.elevation = dp(16).toFloat()
        languagePopup = popup
        val location = IntArray(2); anchor.getLocationInWindow(location)
        popup.showAtLocation(this, Gravity.TOP or Gravity.START, dp(12), maxOf(0, location[1] - popup.height))
    }

    // ---- Translation panel -----------------------------------------------------------------

    fun renderPanel(next: KeyboardPanelState, password: Boolean = toolbarPassword) {
        renderToolbar(next.selected, password, next.panel, next.languages, next.busy)
        val previous = renderedPanel
        renderedPanel = next
        val tabChanged = previous != null && previous.panel != TranslationPanel.NONE &&
            next.panel != TranslationPanel.NONE && previous.panel != next.panel
        // Keep the draft editor, cursor and key grid alive across language/status/request updates,
        // and across Write/Read tab switches, so switching never rebuilds or flashes the panel.
        if (previous != null && previous.panel != TranslationPanel.NONE && next.panel != TranslationPanel.NONE &&
            previous.result == null && next.result == null && !previous.disclosure && !next.disclosure && draftField != null) {
            val field = draftField!!
            if (tabChanged) field.hint = context.getString(draftHint(next.panel))
            if (field.text.toString() != next.draft) {
                applyingDraft = true
                field.text.replace(0, field.text.length, next.draft)
                applyingDraft = false
                field.setSelection(field.text.length)
            }
            field.isEnabled = !next.busy
            pasteButton?.isEnabled = !next.busy
            renderSubmit(next)
            updateStatus(next.message)
            return
        }
        val wasHidden = panel.visibility != VISIBLE || panel.childCount == 0
        panel.removeAllViews()
        draftField = null; pasteButton = null; submitButton = null; submitProgress = null; statusContainer = null
        panel.visibility = if (next.panel == TranslationPanel.NONE) GONE else VISIBLE
        if (next.panel == TranslationPanel.NONE) return
        when {
            next.disclosure -> renderDisclosure()
            next.result != null -> renderResult(next, next.result)
            else -> renderComposer(next)
        }
        // Opening fades in fully; a tab switch that needs new content only dips briefly, so the
        // swap feels continuous instead of blinking.
        if (wasHidden) fadeIn(panel) else if (tabChanged) fadeIn(panel, from = 0.35f)
    }

    private fun draftHint(mode: TranslationPanel) =
        if (mode == TranslationPanel.WRITE) R.string.keyboard_draft_hint else R.string.keyboard_read_hint

    private fun renderDisclosure() {
        panel.addView(scrollingText(context.getString(R.string.disclosure_message), result = false),
            LayoutParams(LayoutParams.MATCH_PARENT, dp(if (compact) 64 else 104)))
        panel.addView(pillButton(context.getString(R.string.action_continue), primary = true) { actions.continueDisclosure() },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(40)).apply { topMargin = dp(6) })
    }

    /** Result text and its actions share one card so the actions don't cost a full extra row. */
    private fun renderResult(next: KeyboardPanelState, result: String) {
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            background = rounded(palette.softAccent, 16f)
            setPadding(dp(4), dp(4), dp(8), dp(8))
        }
        card.addView(scrollingText(result, result = true), LayoutParams(LayoutParams.MATCH_PARENT, dp(if (compact) 40 else 64)))
        val resultActions = LinearLayout(context).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        val chipHeight = dp(if (compact) 30 else 32)
        resultActions.addView(pillButton(context.getString(R.string.action_copy), primary = false) { actions.copyResult() },
            LayoutParams(LayoutParams.WRAP_CONTENT, chipHeight))
        resultActions.addView(pillButton(context.getString(R.string.keyboard_new), primary = false) { actions.newTranslation() },
            LayoutParams(LayoutParams.WRAP_CONTENT, chipHeight).apply { marginStart = dp(6) })
        // Reading results never offer insertion: received text must not end up in the user's reply.
        if (next.panel == TranslationPanel.WRITE) {
            resultActions.addView(pillButton(context.getString(R.string.keyboard_insert), primary = true) { actions.insertResult() },
                LayoutParams(LayoutParams.WRAP_CONTENT, chipHeight).apply { marginStart = dp(6) })
        }
        card.addView(resultActions, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) })
        panel.addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        next.message?.let { panel.addView(statusText(it)) }
    }

    /**
     * Chat-style composer: paste lives inside the field and Translate is a round send button
     * beside it, so writing and reading cost one row instead of a field plus a button row.
     */
    private fun renderComposer(next: KeyboardPanelState) {
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false }
        val composer = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(palette.surface, 18f)
        }
        draftField = EditText(context).apply {
            setText(next.draft)
            setSelection(text.length)
            setTextColor(palette.ink); setHintTextColor(palette.muted); textSize = 15.5f
            hint = context.getString(draftHint(next.panel))
            contentDescription = context.getString(R.string.keyboard_draft_description)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(4000))
            setPadding(dp(14), dp(4), dp(4), dp(4))
            background = null
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            showSoftInputOnFocus = false
            isSaveEnabled = false
            isEnabled = !next.busy
            highlightColor = palette.softAccent
            // Focus/cursor editing are local to this draft; the host editor remains the IME target.
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!applyingDraft) actions.draftChanged(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        composer.addView(draftField, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        pasteButton = toolbarIcon(R.drawable.ic_keyboard_paste, context.getString(R.string.keyboard_paste)) { actions.readCopy() }.apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
            isEnabled = !next.busy
        }
        composer.addView(pasteButton, LayoutParams(dp(42), dp(42)))
        val fieldHeight = dp(if (compact) 42 else 52)
        row.addView(composer, LayoutParams(0, fieldHeight, 1f))

        val submitSize = dp(if (compact) 40 else 46)
        val submitFrame = FrameLayout(context)
        submitButton = ImageButton(context).apply {
            setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_keyboard_submit)?.mutate())
            imageTintList = ColorStateList.valueOf(palette.onAccent)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val pad = submitSize / 4
            setPadding(pad, pad, pad, pad)
            background = ripple(palette.accent, 0f, oval = true)
            stateListAnimator = null; isSaveEnabled = false; isSoundEffectsEnabled = false
            setOnClickListener { actions.translate() }
        }
        submitFrame.addView(submitButton, FrameLayout.LayoutParams(submitSize, submitSize))
        submitProgress = ProgressBar(context).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(palette.onAccent)
            visibility = GONE
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val spinner = submitSize / 2
        submitFrame.addView(submitProgress, FrameLayout.LayoutParams(spinner, spinner, Gravity.CENTER))
        row.addView(submitFrame, LayoutParams(submitSize, submitSize).apply { marginStart = dp(8) })
        panel.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        renderSubmit(next)
        statusContainer = LinearLayout(context).apply { orientation = VERTICAL }
        panel.addView(statusContainer)
        updateStatus(next.message)
    }

    /** Busy swaps the arrow for a spinner in place, so the panel never changes size mid-request. */
    private fun renderSubmit(state: KeyboardPanelState) {
        val button = submitButton ?: return
        button.isEnabled = !state.busy
        button.imageAlpha = if (state.busy) 0 else 255
        button.contentDescription = context.getString(when {
            state.busy -> R.string.keyboard_translating
            state.panel == TranslationPanel.WRITE -> R.string.keyboard_translate_insert
            else -> R.string.keyboard_translate_read
        })
        submitProgress?.visibility = if (state.busy) VISIBLE else GONE
    }

    private fun updateStatus(message: String?) {
        val container = statusContainer ?: return
        val current = (container.getChildAt(0) as? TextView)?.text?.toString()
        if (current == message) return
        container.removeAllViews()
        if (message != null) container.addView(statusText(message))
    }

    fun editDraft(value: String? = null, delete: Boolean = false): Boolean {
        val field = draftField ?: return false
        if (!field.isEnabled) return true
        val editable = field.text
        val start = minOf(field.selectionStart, field.selectionEnd).coerceIn(0, editable.length)
        val end = maxOf(field.selectionStart, field.selectionEnd).coerceIn(start, editable.length)
        if (delete) {
            val from = if (start == end && start > 0) Character.offsetByCodePoints(editable, start, -1) else start
            editable.delete(from, end)
            field.setSelection(from)
        } else {
            val replacement = value.orEmpty()
            // Reject the entire key when the draft is full; never split a surrogate-pair emoji.
            if (editable.length - (end - start) + replacement.length > 4000) return true
            editable.replace(start, end, replacement)
            field.setSelection(start + replacement.length)
        }
        return true
    }

    // ---- Keys ------------------------------------------------------------------------------

    fun renderKeys(page: KeyPage, shift: ShiftState, actionLabel: String) {
        renderedKeyPage = page
        keyGrid.setPage(page, shift, actionLabel)
    }

    fun updateShift(shift: ShiftState) { keyGrid.setShift(shift) }

    override fun onKey(key: KeySpec) {
        when (key.action) {
            KeyAction.TEXT -> actions.key(key.label)
            KeyAction.SPACE -> actions.key(" ")
            KeyAction.ENTER -> actions.enter()
            KeyAction.DELETE -> actions.backspace()
            KeyAction.SHIFT -> actions.shift()
            KeyAction.SYMBOLS -> actions.page(KeyPage.SYMBOLS)
            KeyAction.LETTERS -> actions.page(KeyPage.LETTERS)
            KeyAction.NEXT_SYMBOLS -> actions.page(KeyboardLayout.nextSymbolPage(renderedKeyPage))
            KeyAction.EMOJI -> actions.page(if (renderedKeyPage == KeyPage.EMOJI) KeyPage.LETTERS else KeyPage.EMOJI)
        }
    }

    override fun onLongPress(key: KeySpec) {
        when (key.action) {
            KeyAction.SHIFT -> actions.lockShift()
            KeyAction.SPACE -> actions.switchKeyboard()
            else -> Unit
        }
    }

    // ---- Building blocks -------------------------------------------------------------------

    private fun pillButton(label: String, primary: Boolean, action: () -> Unit): Button = Button(context).apply {
        text = label; textSize = 13.5f; isAllCaps = false
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (primary) palette.onAccent else palette.ink)
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(16), 0, dp(16), 0)
        gravity = Gravity.CENTER
        includeFontPadding = false
        background = ripple(if (primary) palette.accent else palette.surface, 20f)
        stateListAnimator = null; isSaveEnabled = false; isSoundEffectsEnabled = false
        setOnClickListener { action() }
    }

    private fun scrollingText(text: String, result: Boolean) = ScrollView(context).apply {
        if (!result) background = rounded(palette.surface, 16f)
        addView(TextView(context).apply {
            this.text = text; textSize = if (result) 16.5f else 13f; setTextColor(palette.ink)
            setLineSpacing(0f, 1.12f)
            setPadding(dp(12), dp(8), dp(12), dp(4)); isSaveEnabled = false
            if (result) contentDescription = context.getString(R.string.keyboard_result_description)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        })
    }

    private fun statusText(value: String) = TextView(context).apply {
        text = value; setTextColor(palette.muted); textSize = 12f; maxLines = 2
        setPadding(dp(6), dp(4), dp(6), 0)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    private fun fadeIn(view: View, from: Float = 0f) {
        view.animate().cancel()
        // Detached views (previews, first render) never get an animation frame; show them directly.
        if (!view.isAttachedToWindow) { view.alpha = 1f; return }
        view.alpha = from
        view.animate().alpha(1f).setDuration(140).start()
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    /**
     * Ripples are kept to toolbar and panel controls; keys use instant pressed colours instead.
     * An explicit opaque mask is required: without one a transparent background masks the ripple away.
     */
    private fun ripple(color: Int, radius: Float, inset: Int = 0, oval: Boolean = false): Drawable {
        fun shape(fill: Int) = GradientDrawable().apply {
            setColor(fill)
            if (oval) shape = GradientDrawable.OVAL else cornerRadius = dp(radius).toFloat()
        }
        val drawable = RippleDrawable(ColorStateList.valueOf(palette.ripple), shape(color), shape(Color.WHITE))
        return if (inset > 0) InsetDrawable(drawable, dp(inset)) else drawable
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()
    override fun onDetachedFromWindow() { languagePopup?.dismiss(); super.onDetachedFromWindow() }

    private companion object {
        const val TAB_WIDTH = 44
        const val TAB_ANIMATION_MS = 180L
        const val DISABLED_ALPHA = 0.38f
    }
}
