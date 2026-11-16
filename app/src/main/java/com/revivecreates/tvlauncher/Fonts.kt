package com.revivecreates.tvlauncher

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * Outfit, the typeface from CleanYourTV (SIL Open Font License, see FONT-LICENSE-OFL.txt).
 * One variable font file, read at each weight the home screen uses.
 * Arabic titles fall back to the system Arabic font automatically.
 */
@OptIn(ExperimentalTextApi::class)
val Outfit: FontFamily = FontFamily(
    listOf(200 to FontWeight.ExtraLight, 300 to FontWeight.Light, 400 to FontWeight.Normal,
        500 to FontWeight.Medium, 600 to FontWeight.SemiBold, 700 to FontWeight.Bold)
        .map { (w, weight) ->
            Font(R.font.outfit, weight, variationSettings = FontVariation.Settings(FontVariation.weight(w)))
        }
)
