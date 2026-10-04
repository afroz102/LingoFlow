package com.lingoflow.instanttranslate.direction

import com.lingoflow.instanttranslate.R

/** Wire values are shared with the backend; AUTO is resolved during the single model call. */
enum class Direction(val displayNameRes: Int) {
    AUTO(R.string.direction_auto),
    ENGLISH_TO_HINDI(R.string.direction_en_to_hi),
    HINDI_TO_ENGLISH(R.string.direction_hi_to_en),
    HINGLISH_TO_ENGLISH(R.string.direction_hinglish_to_en),
    ENGLISH_TO_HINGLISH(R.string.direction_en_to_hinglish),
    HINDI_TO_HINGLISH(R.string.direction_hi_to_hinglish),
    HINGLISH_TO_HINDI(R.string.direction_hinglish_to_hi),
    ;

    fun acceptsResolved(resolved: Direction): Boolean = if (this == AUTO) {
        resolved in setOf(ENGLISH_TO_HINDI, HINDI_TO_ENGLISH, HINGLISH_TO_ENGLISH)
    } else resolved == this
}
