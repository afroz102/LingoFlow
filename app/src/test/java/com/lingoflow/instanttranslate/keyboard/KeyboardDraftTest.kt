package com.lingoflow.instanttranslate.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyboardDraftTest {
    @Test fun insertionReplacesOnlyTheSelectionIncludingReversedSelection() {
        assertEquals(DraftEdit("before hi after", 9), KeyboardDraft.insert("before OLD after", 10, 7, "hi"))
    }
    @Test fun backspaceRemovesAWholeEmojiOrTheSelection() {
        assertEquals(DraftEdit("hello", 5), KeyboardDraft.backspace("hello😀", 7, 7))
        assertEquals(DraftEdit("after", 0), KeyboardDraft.backspace("helloafter", 0, 5))
        assertEquals(DraftEdit("hello", 0), KeyboardDraft.backspace("hello", 0, 0))
    }
    @Test fun draftLimitRejectsAnOverflowWithoutTruncatingACharacter() {
        val text = "a".repeat(3999)
        assertEquals(DraftEdit(text, 3999), KeyboardDraft.insert(text, 3999, 3999, "😀"))
        assertEquals(4000, KeyboardDraft.insert(text, 3999, 3999, "?").text.length)
    }
    @Test fun richKeyDataIncludes50DistinctSmileyAndThreeSymbolPages() {
        assertEquals(50, KeyboardLayout.emoji.size)
        assertEquals(50, KeyboardLayout.emoji.toSet().size)
        assertTrue(KeyboardLayout.symbols.flatten().containsAll(listOf(";", ":", "!", "?", "\"", "'", "[", "]", "{", "}", "\\", "<", ">", "_")))
        assertTrue(KeyboardLayout.moreSymbols.flatten().containsAll(listOf("€", "₹", "√", "©")))
    }
    @Test fun everydaySymbolsAreAvailableWithoutDuplicates() {
        val symbols = (KeyboardLayout.symbols + KeyboardLayout.moreSymbols + KeyboardLayout.extraSymbols).flatten()
        assertEquals(111, symbols.size)
        assertEquals(symbols.size, symbols.toSet().size)
        assertTrue(symbols.containsAll(listOf("%", "=", "$", "₹", "…", "—", "©", "™", "±", "←", "¿", "¡")))
    }
    @Test fun lettersHaveANumberRowAndNoLongPressAlternates() {
        val rows = KeyboardLayout.rows(KeyPage.LETTERS, "↵")
        assertEquals("Number row + three letter rows + bottom row", 5, rows.size)
        assertEquals(KeyboardLayout.digits, rows.first().keys.map { it.label })
        assertTrue(rows.first().height < rows[1].height)
        assertEquals("qwertyuiop", rows[1].keys.joinToString("") { it.label })
        assertEquals(listOf(KeyAction.SHIFT, KeyAction.DELETE), listOf(rows[3].keys.first().action, rows[3].keys.last().action))
    }
    @Test fun everyPageFillsTheSameTenUnitWidthAndRowCount() {
        // Equal row counts keep the IME height fixed across page switches (no host re-layout).
        for (page in KeyPage.values()) {
            val rows = KeyboardLayout.rows(page, "↵")
            assertEquals("$page row count", if (page == KeyPage.EMOJI) 6 else 5, rows.size)
            rows.forEach { assertEquals("$page row $it", KeyboardLayout.ROW_UNITS, it.units, 0.001f) }
        }
    }
    @Test fun bottomRowMatchesGboardOrderAndEmojiPageKeepsDelete() {
        assertEquals(listOf(KeyAction.SYMBOLS, KeyAction.TEXT, KeyAction.EMOJI, KeyAction.SPACE, KeyAction.TEXT, KeyAction.ENTER),
            KeyboardLayout.rows(KeyPage.LETTERS, "↵").last().keys.map { it.action })
        assertEquals(KeyAction.DELETE, KeyboardLayout.rows(KeyPage.EMOJI, "↵").last().keys.last().action)
        assertEquals(KeyPage.MORE_SYMBOLS, KeyboardLayout.nextSymbolPage(KeyPage.SYMBOLS))
        assertEquals(KeyPage.SYMBOLS, KeyboardLayout.nextSymbolPage(KeyPage.EXTRA_SYMBOLS))
    }
    @Test fun lateInsertionCannotTargetANewEditorOrMovedSelection() {
        val target = KeyboardInsertionTarget(3, 7)
        assertTrue(target.isCurrent(3, 7))
        assertFalse(target.isCurrent(4, 7))
        assertFalse(target.isCurrent(3, 8))
    }
}
