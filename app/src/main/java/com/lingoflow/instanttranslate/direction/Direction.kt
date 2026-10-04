package com.lingoflow.instanttranslate.direction

import com.lingoflow.instanttranslate.R

/** AUTO is the writing route; reading always targets English, including English input. */
enum class Direction(val displayNameRes: Int) {
    AUTO(R.string.direction_auto),
    HINGLISH_TO_ENGLISH(R.string.direction_hinglish_to_en),
    ENGLISH_TO_HINGLISH(R.string.direction_en_to_hinglish),
    READ_TO_ENGLISH(R.string.direction_read_to_en),
    ;

    fun acceptsResolved(resolved: Direction): Boolean = if (this == AUTO) {
        resolved == ENGLISH_TO_HINGLISH || resolved == HINGLISH_TO_ENGLISH
    } else resolved == this
}
