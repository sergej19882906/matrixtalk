package com.matrix.messenger.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import matrixtalk.shared.generated.resources.Res
import matrixtalk.shared.generated.resources.Inter_Bold
import matrixtalk.shared.generated.resources.Inter_Medium
import matrixtalk.shared.generated.resources.Inter_Regular
import matrixtalk.shared.generated.resources.Inter_SemiBold
import org.jetbrains.compose.resources.Font

/**
 * Element X typeface: Inter for the whole app.
 * org.jetbrains.compose.resources.Font is @Composable, so the family
 * is exposed as a function instead of a top-level val.
 */
@Composable
fun interFontFamily(): FontFamily = FontFamily(
    Font(Res.font.Inter_Regular),
    Font(Res.font.Inter_Medium, FontWeight.Medium),
    Font(Res.font.Inter_SemiBold, FontWeight.SemiBold),
    Font(Res.font.Inter_Bold, FontWeight.Bold)
)
