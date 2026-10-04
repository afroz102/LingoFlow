package com.lingoflow.instanttranslate.keyboard

/** What a key does when it fires. Only TEXT and SPACE type characters into the editor. */
enum class KeyAction { TEXT, SHIFT, DELETE, SPACE, ENTER, LETTERS, SYMBOLS, NEXT_SYMBOLS, EMOJI }

/** [width] is in key units; every row of a page adds up to [KeyboardLayout.ROW_UNITS]. */
data class KeySpec(val action: KeyAction, val label: String = "", val width: Float = 1f)

/** [height] is relative to the other non-bottom rows of the same page; [inset] pads both ends, in key units. */
data class KeyRow(val keys: List<KeySpec>, val height: Float = 1f, val inset: Float = 0f) {
    val units: Float get() = keys.sumOf { it.width.toDouble() }.toFloat() + inset * 2
}

/** Shared key data stays deterministic; normal typing never calls the translation provider. */
object KeyboardLayout {
    /** Matches Gboard's ten-key grid so muscle memory carries over when switching keyboards. */
    const val ROW_UNITS = 10f
    // Gboard renders its optional number row slightly shorter than the letter rows.
    const val NUMBER_ROW_HEIGHT = 0.82f
    const val EMOJI_COLUMNS = 10

    val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val letters = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

    // Every symbol page has four rows so the keyboard height never changes on a page switch;
    // a height change would make the host app re-layout and feel laggy.
    val symbols = listOf(
        digits,
        listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/"),
        listOf("=", "_", "<", ">", "[", "]", "{", "}", "\\", "|"),
        listOf("*", "\"", "'", ":", ";", "!", "?"),
    )
    val moreSymbols = listOf(
        listOf("~", "`", "^", "°", "•", "…", "√", "÷", "×", "✓"),
        listOf("₹", "€", "£", "¥", "₩", "₽", "¢", "₺", "₱", "₿"),
        listOf("©", "®", "™", "§", "¶", "π", "∆", "±", "≠", "≈"),
        listOf("←", "→", "↑", "↓", "∞", "≤", "≥"),
    )
    val extraSymbols = listOf(
        listOf("“", "”", "‘", "’", "«", "»", "‹", "›", "„", "‚"),
        listOf("–", "—", "¿", "¡", "·", "¬", "µ", "¹", "²", "³"),
        listOf("½", "¼", "¾", "⅓", "⅔", "‰", "′", "″", "‽", "№"),
        listOf("★", "☆", "♥", "♪", "♀", "♂", "※"),
    )
    val emoji = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "🙂",
        "🙃", "😉", "😍", "🥰", "😘", "😋", "😛", "😜", "🤪", "😎",
        "🤩", "🥳", "😏", "😌", "😔", "😢", "😭", "😤", "😡", "🤬",
        "😱", "😨", "😰", "😥", "😓", "🤗", "🤔", "🤫", "🤭", "🤐",
        "😴", "🥱", "🤤", "🤒", "🤕", "🤢", "🤮", "🤧", "😇", "🤠",
    )

    /** Rows for [page], top to bottom. The last row is always the shared bottom row. */
    fun rows(page: KeyPage, enterLabel: String): List<KeyRow> = when (page) {
        KeyPage.LETTERS -> listOf(
            textRow(digits, NUMBER_ROW_HEIGHT),
            textRow(letters[0].map(Char::toString)),
            // Gboard offsets the middle row by half a key instead of stretching its keys.
            textRow(letters[1].map(Char::toString), inset = 0.5f),
            modifierRow(KeySpec(KeyAction.SHIFT, "⇧", 1.5f), letters[2].map(Char::toString)),
            bottomRow(page, enterLabel),
        )
        KeyPage.EMOJI -> emoji.chunked(EMOJI_COLUMNS).map { textRow(it) } + bottomRow(page, enterLabel)
        else -> {
            // The cycling key names the current page, so users can tell which page they are on.
            val (symbolRows, position) = when (page) {
                KeyPage.SYMBOLS -> symbols to "1/3"
                KeyPage.MORE_SYMBOLS -> moreSymbols to "2/3"
                else -> extraSymbols to "3/3"
            }
            symbolRows.dropLast(1).map { textRow(it) } +
                modifierRow(KeySpec(KeyAction.NEXT_SYMBOLS, position, 1.5f), symbolRows.last()) +
                bottomRow(page, enterLabel)
        }
    }

    /** The symbol page that the page-cycling key opens from [page]. */
    fun nextSymbolPage(page: KeyPage): KeyPage = when (page) {
        KeyPage.SYMBOLS -> KeyPage.MORE_SYMBOLS
        KeyPage.MORE_SYMBOLS -> KeyPage.EXTRA_SYMBOLS
        else -> KeyPage.SYMBOLS
    }

    private fun textRow(labels: List<String>, height: Float = 1f, inset: Float = 0f) =
        KeyRow(labels.map { KeySpec(KeyAction.TEXT, it) }, height, inset)

    /** Third row: a 1.5-wide modifier, up to seven characters, then a 1.5-wide Delete. */
    private fun modifierRow(modifier: KeySpec, labels: List<String>): KeyRow {
        require(labels.size == 7) { "Modifier rows hold exactly 7 characters to fill 10 key units, got ${labels.size}" }
        return KeyRow(listOf(modifier) + labels.map { KeySpec(KeyAction.TEXT, it) } + KeySpec(KeyAction.DELETE, "⌫", 1.5f))
    }

    // Same order and proportions as Gboard: ?123 , emoji space . enter. Emoji swaps Enter for
    // Delete because the emoji grid has no third row to hold it.
    private fun bottomRow(page: KeyPage, enterLabel: String) = KeyRow(listOf(
        if (page == KeyPage.LETTERS) KeySpec(KeyAction.SYMBOLS, "?123", 1.5f) else KeySpec(KeyAction.LETTERS, "ABC", 1.5f),
        KeySpec(KeyAction.TEXT, ","),
        KeySpec(KeyAction.EMOJI, "☺"),
        KeySpec(KeyAction.SPACE, "Space", 4f),
        KeySpec(KeyAction.TEXT, "."),
        if (page == KeyPage.EMOJI) KeySpec(KeyAction.DELETE, "⌫", 1.5f) else KeySpec(KeyAction.ENTER, enterLabel, 1.5f),
    ))
}

enum class ShiftState { OFF, ONCE, LOCKED }
enum class KeyPage { LETTERS, SYMBOLS, MORE_SYMBOLS, EXTRA_SYMBOLS, EMOJI }
enum class TranslationPanel { NONE, WRITE, READ }

data class DraftEdit(val text: String, val cursor: Int)

/** Operates on a bounded, local translation draft, including emoji's surrogate pairs. */
object KeyboardDraft {
    fun insert(text: String, start: Int, end: Int, value: String): DraftEdit {
        val from = minOf(start, end).coerceIn(0, text.length)
        val to = maxOf(start, end).coerceIn(from, text.length)
        val updated = text.replaceRange(from, to, value)
        return if (updated.length <= 4000) DraftEdit(updated, from + value.length)
        else DraftEdit(text, to)
    }

    fun backspace(text: String, start: Int, end: Int): DraftEdit {
        val from = minOf(start, end).coerceIn(0, text.length)
        val to = maxOf(start, end).coerceIn(from, text.length)
        if (from != to) return DraftEdit(text.removeRange(from, to), from)
        if (from == 0) return DraftEdit(text, 0)
        val previous = text.offsetByCodePoints(from, -1)
        return DraftEdit(text.removeRange(previous, from), previous)
    }
}

/** A network response must not insert into a different editor or a moved selection. */
data class KeyboardInsertionTarget(val session: Long, val revision: Long) {
    fun isCurrent(session: Long, revision: Long): Boolean = this.session == session && this.revision == revision
}
