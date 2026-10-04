package com.lingoflow.instanttranslate.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/**
 * One colour source for the toolbar, panels and key grid so they never drift apart.
 * Neutral graphite surfaces with a single indigo accent: the accent marks only the primary
 * action (Enter, Translate) and active modes, which keeps the keyboard calm while typing.
 */
internal data class KeyboardPalette(
    val dark: Boolean,
    val background: Int,
    val key: Int,
    val keyPressed: Int,
    val function: Int,
    val functionPressed: Int,
    val keyShadow: Int,
    val ink: Int,
    val muted: Int,
    val accent: Int,
    val accentPressed: Int,
    val onAccent: Int,
    val softAccent: Int,
    val surface: Int,
    val ripple: Int,
) {
    companion object {
        fun of(context: Context): KeyboardPalette {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
            return if (night) dark else light
        }

        private val light = KeyboardPalette(
            dark = false,
            background = Color.parseColor("#EEF0F5"),
            key = Color.parseColor("#FFFFFF"),
            keyPressed = Color.parseColor("#DCE0E8"),
            function = Color.parseColor("#D8DCE5"),
            functionPressed = Color.parseColor("#C3C8D4"),
            keyShadow = Color.parseColor("#C4C9D3"),
            ink = Color.parseColor("#1A1D24"),
            muted = Color.parseColor("#5F6676"),
            accent = Color.parseColor("#4357DB"),
            accentPressed = Color.parseColor("#3446B8"),
            onAccent = Color.parseColor("#FFFFFF"),
            softAccent = Color.parseColor("#E2E6FB"),
            surface = Color.parseColor("#FFFFFF"),
            ripple = Color.parseColor("#1F1A1D24"),
        )

        private val dark = KeyboardPalette(
            dark = true,
            background = Color.parseColor("#111318"),
            key = Color.parseColor("#2C2F38"),
            keyPressed = Color.parseColor("#3D414C"),
            function = Color.parseColor("#1F2229"),
            functionPressed = Color.parseColor("#353944"),
            keyShadow = Color.parseColor("#07080A"),
            ink = Color.parseColor("#ECEEF4"),
            muted = Color.parseColor("#9AA1B2"),
            accent = Color.parseColor("#8A99FF"),
            accentPressed = Color.parseColor("#7283F2"),
            onAccent = Color.parseColor("#10153D"),
            softAccent = Color.parseColor("#2A3260"),
            surface = Color.parseColor("#252830"),
            ripple = Color.parseColor("#29ECEEF4"),
        )
    }
}
