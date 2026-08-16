package com.lingoflow.instanttranslate.direction

import com.lingoflow.instanttranslate.R

/** Translation direction. Stage 0A supports only the two unambiguous directions. */
enum class Direction(val displayNameRes: Int) {
    ENGLISH_TO_HINDI(R.string.direction_en_to_hi),
    HINDI_TO_ENGLISH(R.string.direction_hi_to_en),
}
