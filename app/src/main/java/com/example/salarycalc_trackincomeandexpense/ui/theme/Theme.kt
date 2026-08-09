package com.example.salarycalc_trackincomeandexpense.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = GradientStart,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = GradientStart,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,
    background = AppBgLight,
    surface = AppSurfaceLight,
    onBackground = AppTextLight,
    onSurface = AppTextLight,
    onSurfaceVariant = AppText2Light,
    outline = AppBorderLight,
    outlineVariant = AppBorderLight,
    surfaceVariant = AppSurfaceLight
)

private val DarkColorScheme = darkColorScheme(
    primary = GradientStart,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = GradientStart,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,
    background = AppBgDark,
    surface = AppSurfaceDark,
    onBackground = AppTextDark,
    onSurface = AppTextDark,
    onSurfaceVariant = AppText2Dark,
    outline = AppBorderDark,
    outlineVariant = AppBorderDark,
    surfaceVariant = AppSurfaceDark
)

@Composable
fun SalaryCalcTrackIncomeAndExpenseTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
