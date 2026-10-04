package com.lingoflow.instanttranslate.keyboard

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WordPredictorTest {
    private val assets = File("src/main/assets/dictionary")
    private fun predictor(personal: PersonalDictionary = PersonalDictionary()) = WordPredictor(
        WordPredictor.parseWords(File(assets, "en.txt").readText()),
        WordPredictor.parseWords(File(assets, "hi-Latn.txt").readText()),
        WordPredictor.parseNextWords(File(assets, "next-words.txt").readText()),
        personal,
    )
    private fun texts(list: List<Suggestion>) = list.map { it.text }

    @Test fun bundledListsAreLargeEnoughAndParse() {
        assertTrue(WordPredictor.parseWords(File(assets, "en.txt").readText()).toSet().size > 2000)
        assertTrue(WordPredictor.parseWords(File(assets, "hi-Latn.txt").readText()).toSet().size > 300)
        assertEquals(listOf("you"), WordPredictor.parseNextWords("# c\nthank: you").getValue("thank"))
    }

    @Test fun completesCommonWordsAndKeepsTypedCase() {
        val engine = predictor()
        assertTrue(texts(engine.complete("tom")).contains("tomorrow"))
        assertEquals("Exact known word ranks first", "the", engine.complete("the").first().text)
        assertTrue(texts(engine.complete("Tom")).contains("Tomorrow"))
        assertTrue(texts(engine.complete("TOM")).contains("TOMORROW"))
        assertTrue("Apostrophes are optional", texts(engine.complete("dont")).contains("don't"))
        assertEquals("I", engine.complete("i").first().text)
    }

    @Test fun suggestsRomanHindiForHinglishTypists() {
        val engine = predictor()
        assertTrue(texts(engine.complete("nah")).contains("nahi"))
        assertTrue(texts(engine.complete("kai")).contains("kaise"))
    }

    @Test fun correctsOneTypoOnlyWhenPrefixMatchesRunOut() {
        val engine = predictor()
        assertTrue(texts(engine.complete("tommorow")).contains("tomorrow"))
        assertTrue(texts(engine.complete("recieve")).contains("receive"))
        assertTrue(texts(engine.complete("thnks")).contains("thanks"))
        assertEquals(1, WordPredictor.prefixEditDistance("teh", "the", 2))
        assertEquals(0, WordPredictor.prefixEditDistance("hel", "hello", 1))
        assertEquals(2, WordPredictor.prefixEditDistance("abc", "xyz", 1))
        assertFalse("Partial words don't waste a slot on the literal text", engine.complete("th").any { it.literal })
        assertEquals(3, engine.complete("h").size)
    }

    @Test fun unknownWordIsOfferedLiterallyAndLearnedWordsRankUp() {
        val personal = PersonalDictionary()
        val engine = predictor(personal)
        val unknown = engine.complete("Rahu")
        assertTrue(unknown.first().literal)
        assertEquals("Rahu", unknown.first().text)
        repeat(3) { personal.learn("Rahul", null) }
        assertEquals("rahul", engine.complete("rah").first { !it.literal }.text)
        assertFalse(engine.complete("rahul").first().literal)
    }

    @Test fun predictsNextWordsFromBuiltInsThenLearnedPairs() {
        val personal = PersonalDictionary()
        val engine = predictor(personal)
        assertEquals("you", engine.next(listOf("thank")).first().text)
        assertEquals("hai", engine.next(listOf("kya")).first().text)
        repeat(4) { personal.learn("bro", "thank") }
        assertEquals("bro", engine.next(listOf("thank")).first().text)
        assertTrue(engine.next(emptyList()).isEmpty())
    }

    @Test fun contextComesFromTheCurrentSentenceOnly() {
        assertEquals(TypingContext("wor", listOf("hello"), false), TypingContext.from("hello wor"))
        assertEquals(TypingContext("", listOf("good", "morning"), false), TypingContext.from("say good morning "))
        assertEquals(TypingContext("h", emptyList(), true), TypingContext.from("Hi. h"))
        assertEquals("Comma breaks the phrase", listOf("how"), TypingContext.from("ok, how ar").previous)
        assertTrue("Cursor touching punctuation has no context", TypingContext.from("ok,").let { it.prefix.isEmpty() && it.previous.isEmpty() })
        assertEquals("don't", TypingContext.from("I don't").prefix)
        assertTrue(TypingContext.from("").sentenceStart)
    }

    @Test fun personalDictionaryRejectsNonWordsRoundTripsAndStaysBounded() {
        val personal = PersonalDictionary(maxWords = 20, maxPairs = 20)
        assertFalse(personal.learn("abc123", null))
        assertFalse(personal.learn("https://x.y", null))
        assertFalse(personal.learn("a", null))
        assertTrue(personal.learn("iPhone", "my"))
        assertEquals("iPhone", personal.textOf("iphone"))
        val restored = PersonalDictionary.parse(personal.serialize() + "garbage line\nw\t\t-1\n")
        assertEquals(1, restored.count("iphone"))
        assertEquals(1, restored.pairCount("my", "iphone"))
        repeat(40) { personal.learn("word" + ('a' + it % 26) + ('a' + it / 26), null) }
        assertTrue(personal.startingWith("").size <= 20)
        personal.clear()
        assertEquals(0, personal.count("iphone"))
    }
}
