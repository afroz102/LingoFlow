package com.lingoflow.instanttranslate.keyboard

/** Shared key data stays deterministic; normal typing never calls the translation provider. */
object KeyboardLayout {
    val letters = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    val symbols = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/"),
        listOf("*", "\"", "'", ":", ";", "!", "?"),
    )
    val moreSymbols = listOf(
        listOf("=", "<", ">", "_", "[", "]", "{", "}", "\\", "|"),
        listOf("₹", "€", "£", "¥", "₩", "₽", "¢", "°", "•", "…"),
        listOf("~", "`", "^", "√", "÷", "×", "✓"),
    )
    val extraSymbols = listOf(
        listOf("©", "®", "™", "§", "¶", "π", "∆", "±", "≠", "≈"),
        listOf("←", "→", "↑", "↓", "«", "»", "“", "”", "‘", "’"),
        listOf("–", "—", "¿", "¡", "∞", "≤", "≥"),
    )
    // Secondary labels expose frequent symbols without a page switch.
    val longPress = letters.flattenCharacters().zip(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
            "@", "#", "$", "%", "&", "-", "+", "(", ")", "*", "\"", "'", ":", ";", "!", "?")
    ).toMap()
    private fun List<String>.flattenCharacters() = flatMap { row -> row.map(Char::toString) }
    val emoji = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "🙂",
        "🙃", "😉", "😍", "🥰", "😘", "😋", "😛", "😜", "🤪", "😎",
        "🤩", "🥳", "😏", "😌", "😔", "😢", "😭", "😤", "😡", "🤬",
        "😱", "😨", "😰", "😥", "😓", "🤗", "🤔", "🤫", "🤭", "🤐",
        "😴", "🥱", "🤤", "🤒", "🤕", "🤢", "🤮", "🤧", "😇", "🤠",
    )
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
