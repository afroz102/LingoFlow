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
        assertTrue(KeyboardLayout.symbols.flatten().containsAll(listOf(";", ":", "!", "?", "\"", "'")))
        assertTrue(KeyboardLayout.moreSymbols.flatten().containsAll(listOf("[", "]", "{", "}", "€", "\\", "<", ">", "_")))
    }
    @Test fun everydaySymbolsAndSecondaryLetterKeysAreAvailable() {
        val symbols = (KeyboardLayout.symbols + KeyboardLayout.moreSymbols + KeyboardLayout.extraSymbols).flatten()
        assertEquals(81, symbols.toSet().size)
        assertTrue(symbols.containsAll(listOf("%", "=", "$", "₹", "…", "—", "©", "™", "±", "←", "¿", "¡")))
        assertEquals(26, KeyboardLayout.longPress.size)
        assertEquals("1", KeyboardLayout.longPress["q"])
        assertEquals("@", KeyboardLayout.longPress["a"])
        assertEquals("?", KeyboardLayout.longPress["m"])
    }
    @Test fun lateInsertionCannotTargetANewEditorOrMovedSelection() {
        val target = KeyboardInsertionTarget(3, 7)
        assertTrue(target.isCurrent(3, 7))
        assertFalse(target.isCurrent(4, 7))
        assertFalse(target.isCurrent(3, 8))
    }
}
