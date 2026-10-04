package com.lingoflow.instanttranslate.keyboard

import kotlin.math.ln

/** One strip entry. [literal] marks the user's own typed word, offered so they can keep it as is. */
data class Suggestion(val text: String, val literal: Boolean = false)

/** The word being typed and up to two words before it, derived from text before the cursor. */
data class TypingContext(val prefix: String, val previous: List<String>, val sentenceStart: Boolean) {
    companion object {
        private val sentenceEnd = charArrayOf('.', '!', '?', '\n')
        private val whitespace = Regex("\\s+")

        fun isWordChar(char: Char) = char.isLetter() || char == '\'' || char == '’'

        /**
         * [before] is any tail of the editor text before the cursor. Previous words only count
         * when separated by plain spaces inside the current sentence: "Hi. how" must not predict
         * from "Hi", and "ok,|" (cursor after punctuation) has no prefix or next-word context.
         */
        fun from(before: String): TypingContext {
            val prefix = before.takeLastWhile(::isWordChar).trimStart('\'', '’')
            val rest = before.dropLast(prefix.length)
            val sentence = rest.substring(rest.lastIndexOfAny(sentenceEnd) + 1)
            val sentenceStart = sentence.isBlank()
            val previous = if (rest.isEmpty() || !rest.last().isWhitespace()) emptyList()
            else sentence.trim().split(whitespace).filter(String::isNotEmpty)
                // "ok, how" predicts from "how" only; a comma breaks the phrase.
                .takeLastWhile { token -> token.all(::isWordChar) }.takeLast(2)
            return TypingContext(prefix, previous, sentenceStart)
        }
    }
}

/** Lowercase lookup key without apostrophes, so typing "dont" finds "don't". */
internal fun suggestionKey(word: String) = word.lowercase().filter { it != '\'' && it != '’' }

/**
 * Offline Gboard-style suggestions: prefix completions, one-typo corrections and next-word
 * predictions, ranked by built-in frequency, the user's learned words and the previous word.
 * Everything runs locally; typing never reaches the translation backend.
 */
class WordPredictor(
    english: List<String>,
    romanHindi: List<String>,
    private val nextWords: Map<String, List<String>>,
    private val personal: PersonalDictionary,
) {
    private class Entry(val text: String, val key: String, val base: Double)

    /** Sorted by key so a prefix is one binary-searched range. */
    private val entries: List<Entry>
    private val byKey: Map<String, Entry>

    init {
        // Roman Hindi ranks are stretched onto the English scale so "hai" competes with
        // "have" without crowding out everyday English words for English-only typists.
        val ranked = HashMap<String, Pair<String, Int>>()
        english.forEachIndexed { rank, word -> val key = suggestionKey(word); if (key !in ranked) ranked[key] = word to rank }
        romanHindi.forEachIndexed { rank, word ->
            val key = suggestionKey(word); val stretched = rank * 3 + 40
            val existing = ranked[key]
            if (existing == null || existing.second > stretched) ranked[key] = (existing?.first ?: word) to stretched
        }
        val maxRank = (ranked.values.maxOfOrNull { it.second } ?: 0) + 2.0
        entries = ranked.map { (key, value) -> Entry(value.first, key, 1 - ln(value.second + 2.0) / ln(maxRank + 1)) }
            .filter { it.key.isNotEmpty() }.sortedBy { it.key }
        byKey = entries.associateBy { it.key }
    }

    fun isKnown(word: String): Boolean = suggestionKey(word).let { it in byKey || personal.count(it) > 0 }

    /** Up to three suggestions for [prefix], best first; a literal entry is added for unknown words. */
    fun complete(prefix: String, previous: List<String> = emptyList()): List<Suggestion> {
        val key = suggestionKey(prefix)
        if (key.isEmpty() || prefix.any { it.isDigit() }) return emptyList()
        val scores = HashMap<String, Pair<String, Double>>()
        fun offer(text: String, score: Double) {
            val candidateKey = suggestionKey(text)
            if ((scores[candidateKey]?.second ?: Double.NEGATIVE_INFINITY) < score) scores[candidateKey] = text to score
        }
        val context = previous.lastOrNull()?.let(::suggestionKey)
        prefixRange(key).forEach { entry ->
            offer(entry.text, entry.base + learnedBonus(entry.key, context) + contextBonus(previous, entry.key) +
                if (entry.key == key) EXACT_BONUS else 0.0)
        }
        personal.startingWith(key).forEach { (text, _) ->
            val candidateKey = suggestionKey(text)
            offer(byKey[candidateKey]?.text ?: text, (byKey[candidateKey]?.base ?: 0.0) + learnedBonus(candidateKey, context) +
                contextBonus(previous, candidateKey) + if (candidateKey == key) EXACT_BONUS else 0.0)
        }
        val completions = scores.size
        // Typo tolerance only fills empty slots for unknown input, so correct typing is never second-guessed.
        if (completions < 3 && key.length >= 3 && !isKnown(prefix)) {
            val allowed = if (key.length >= 6) 2 else 1
            entries.forEach { entry ->
                if (entry.key[0] == key[0] && entry.key !in scores) {
                    val edits = prefixEditDistance(key, entry.key, allowed)
                    if (edits <= allowed) offer(entry.text, entry.base * 0.6 + learnedBonus(entry.key, context) - TYPO_PENALTY * edits)
                }
            }
        }
        val best = scores.values.sortedByDescending { it.second }.take(3).map { Suggestion(matchCase(it.first, prefix)) }
        // Like Gboard's quoted slot: when nothing completes the typed word (only corrections are
        // left), keep the word as typed one tap away; it is learned when kept.
        return if (completions == 0 && best.isNotEmpty()) listOf(Suggestion(prefix, literal = true)) + best.take(2) else best
    }

    /** Predictions after a finished word: learned pairs first, then the built-in table. */
    fun next(previous: List<String>): List<Suggestion> {
        val last = previous.lastOrNull()?.let(::suggestionKey) ?: return emptyList()
        val scores = LinkedHashMap<String, Pair<String, Double>>()
        fun offer(text: String, score: Double) {
            val key = suggestionKey(text)
            if ((scores[key]?.second ?: Double.NEGATIVE_INFINITY) < score) scores[key] = text to score
        }
        personal.nextAfter(last).forEach { (key, count) -> offer(byKey[key]?.text ?: personal.textOf(key) ?: key, 1.0 + 0.4 * ln(1.0 + count)) }
        builtInNext(previous).forEachIndexed { position, text ->
            offer(text, 0.9 - position * 0.05 + 0.3 * ln(1.0 + personal.count(suggestionKey(text))))
        }
        return scores.values.sortedByDescending { it.second }.take(3).map { Suggestion(it.first) }
    }

    private fun builtInNext(previous: List<String>): List<String> {
        val pair = previous.takeLast(2).joinToString(" ") { suggestionKey(it) }
        val single = suggestionKey(previous.last())
        return ((if (previous.size == 2) nextWords[pair].orEmpty() else emptyList()) + nextWords[single].orEmpty()).distinct()
    }

    private fun learnedBonus(key: String, context: String?): Double {
        val count = personal.count(key)
        val pair = context?.let { personal.pairCount(it, key) } ?: 0
        return (if (count > 0) 0.35 * ln(1.0 + count) else 0.0) + (if (pair > 0) 0.4 * ln(1.0 + pair) else 0.0)
    }

    private fun contextBonus(previous: List<String>, key: String): Double {
        if (previous.isEmpty()) return 0.0
        val position = builtInNext(previous).indexOfFirst { suggestionKey(it) == key }
        return if (position < 0) 0.0 else 0.6 - position * 0.05
    }

    private fun prefixRange(key: String): List<Entry> {
        var low = 0; var high = entries.size
        while (low < high) { val mid = (low + high) ushr 1; if (entries[mid].key < key) low = mid + 1 else high = mid }
        val matches = mutableListOf<Entry>()
        var index = low
        while (index < entries.size && entries[index].key.startsWith(key)) matches.add(entries[index++])
        return matches
    }

    companion object {
        private const val EXACT_BONUS = 0.5
        private const val TYPO_PENALTY = 0.3

        /** Parses the bundled "word: next next" table; '#' lines are comments. */
        fun parseNextWords(text: String): Map<String, List<String>> = text.lineSequence()
            .map { it.substringBefore('#').trim() }.filter { ':' in it }
            .associate { line -> line.substringBefore(':').trim().lowercase() to line.substringAfter(':').trim().split(Regex("\\s+")).filter(String::isNotEmpty) }

        /** Parses a ranked whitespace-separated word list; '#' starts a comment. */
        fun parseWords(text: String): List<String> = text.lineSequence()
            .flatMap { it.substringBefore('#').trim().split(Regex("\\s+")).asSequence() }.filter(String::isNotEmpty).toList()

        /** Follows the typed word's case: "Hel" → "Hello", "HEL" → "HELLO"; "I" keeps its capital. */
        fun matchCase(word: String, typed: String): String = when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
            typed.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.uppercaseChar() }
            else -> word
        }

        /**
         * Fewest edits (substitution, insertion, deletion or adjacent swap) turning [typed] into any
         * prefix of [word], so "thnks" is 1 from "thanks" and "tommorow" 2 from "tomorrow". Returns
         * [limit] + 1 as soon as the distance must exceed [limit], keeping per-key cost tiny.
         */
        internal fun prefixEditDistance(typed: String, word: String, limit: Int): Int {
            if (word.length < typed.length - limit) return limit + 1
            val columns = minOf(word.length, typed.length + limit)
            var beforePrevious = IntArray(columns + 1)
            var previous = IntArray(columns + 1) { it }
            for (i in 1..typed.length) {
                val current = IntArray(columns + 1)
                current[0] = i
                var rowBest = current[0]
                for (j in 1..columns) {
                    val cost = if (typed[i - 1] == word[j - 1]) 0 else 1
                    var value = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                    if (i > 1 && j > 1 && typed[i - 1] == word[j - 2] && typed[i - 2] == word[j - 1]) value = minOf(value, beforePrevious[j - 2] + 1)
                    current[j] = value
                    rowBest = minOf(rowBest, value)
                }
                if (rowBest > limit) return limit + 1
                beforePrevious = previous; previous = current
            }
            return previous.minOrNull()!!.coerceAtMost(limit + 1)
        }
    }
}
