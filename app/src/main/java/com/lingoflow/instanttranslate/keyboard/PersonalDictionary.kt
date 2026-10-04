package com.lingoflow.instanttranslate.keyboard

/**
 * Words and word pairs the user actually types, kept on the device only, so suggestions adapt the
 * way Gboard's do (names, slang, Hinglish spellings). Bounded: the least-used entries are dropped
 * once a cap is reached, so the file can't grow without limit. Not thread-safe; owned by the IME
 * main thread, and serialized snapshots are written elsewhere.
 */
class PersonalDictionary(private val maxWords: Int = 3000, private val maxPairs: Int = 6000) {
    private class Word(var text: String, var count: Int)

    private val words = HashMap<String, Word>()
    private val pairs = HashMap<String, Int>() // "previousKey nextKey" → count
    var changed = false
        private set

    fun count(key: String): Int = words[key]?.count ?: 0
    fun textOf(key: String): String? = words[key]?.text
    fun pairCount(previousKey: String, key: String): Int = pairs["$previousKey $key"] ?: 0

    /** Learned words whose key starts with [prefixKey], as display text → count. */
    fun startingWith(prefixKey: String): List<Pair<String, Int>> =
        words.entries.filter { it.key.startsWith(prefixKey) }.map { it.value.text to it.value.count }

    /** Learned followers of [previousKey], as key → count. */
    fun nextAfter(previousKey: String): List<Pair<String, Int>> {
        val head = "$previousKey "
        return pairs.entries.filter { it.key.startsWith(head) }.map { it.key.removePrefix(head) to it.value }
    }

    /** Records a finished word (and the word before it). Rejects anything that isn't a plain word. */
    fun learn(word: String, previous: String?): Boolean {
        if (!isLearnable(word)) return false
        val key = suggestionKey(word)
        // Keep deliberate inner capitals ("iPhone", "LingoBoard"); a sentence-initial capital is not a name signal.
        val text = if (word.drop(1).any { it.isUpperCase() }) word else word.lowercase()
        val entry = words.getOrPut(key) { Word(text, 0) }
        entry.count++; if (text != text.lowercase()) entry.text = text
        if (previous != null && isLearnable(previous)) {
            val pair = "${suggestionKey(previous)} $key"
            pairs[pair] = (pairs[pair] ?: 0) + 1
        }
        changed = true
        prune()
        return true
    }

    fun clear() { words.clear(); pairs.clear(); changed = true }

    /** Tab-separated lines: "w<TAB>text<TAB>count" and "p<TAB>previousKey<TAB>nextKey<TAB>count". */
    fun serialize(): String {
        changed = false
        return buildString {
            words.values.forEach { append("w\t").append(it.text).append('\t').append(it.count).append('\n') }
            pairs.forEach { (pair, count) -> append("p\t").append(pair.replace(' ', '\t')).append('\t').append(count).append('\n') }
        }
    }

    private fun prune() {
        // Drop the bottom ~10% at once so pruning doesn't run on every new word near the cap.
        if (words.size > maxWords) words.entries.sortedBy { it.value.count }.take(maxWords / 10 + 1).forEach { words.remove(it.key) }
        if (pairs.size > maxPairs) pairs.entries.sortedBy { it.value }.take(maxPairs / 10 + 1).forEach { pairs.remove(it.key) }
    }

    companion object {
        private val learnable = Regex("^\\p{L}[\\p{L}'’]{1,23}$")

        /** 2–24 letters (apostrophes allowed inside); no digits, symbols, URLs or emoji. */
        fun isLearnable(word: String) = learnable.matches(word)

        /** Restores a [serialize]d snapshot, skipping malformed lines instead of failing the keyboard. */
        fun parse(text: String, maxWords: Int = 3000, maxPairs: Int = 6000): PersonalDictionary {
            val dictionary = PersonalDictionary(maxWords, maxPairs)
            text.lineSequence().forEach { line ->
                val parts = line.split('\t')
                when {
                    parts.size == 3 && parts[0] == "w" && isLearnable(parts[1]) ->
                        parts[2].toIntOrNull()?.takeIf { it > 0 }?.let { dictionary.words[suggestionKey(parts[1])] = Word(parts[1], it) }
                    parts.size == 4 && parts[0] == "p" ->
                        parts[3].toIntOrNull()?.takeIf { it > 0 }?.let { dictionary.pairs["${parts[1]} ${parts[2]}"] = it }
                }
            }
            return dictionary
        }
    }
}
