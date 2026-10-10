package com.animacode.animadev.projects

import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color

/** Pulse changes the palette, never the user's choice of light/dark mode. */
object AppThemePolicy {
    fun dark(theme:String,systemDark:Boolean):Boolean = when(theme) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
}

fun pulseColorScheme(dark:Boolean):ColorScheme = if(dark)darkColorScheme(
    primary=AnimaPulse.green,onPrimary=AnimaPulse.dark,
    primaryContainer=Color(0xFF173C2C),onPrimaryContainer=AnimaPulse.green,
    secondary=Color(0xFF79C9FF),onSecondary=AnimaPulse.dark,
    secondaryContainer=Color(0xFF24484A),onSecondaryContainer=Color.White,
    tertiary=Color(0xFFFFCF80),
    background=Color(0xFF0B1113),onBackground=Color.White,
    surface=Color(0xFF111A1C),onSurface=Color.White,
    surfaceVariant=AnimaPulse.panel,onSurfaceVariant=AnimaPulse.muted,
    surfaceContainerLowest=Color(0xFF0B1113),
    surfaceContainerLow=Color(0xFF162123),
    surfaceContainer=AnimaPulse.panel,
    surfaceContainerHigh=Color(0xFF253335),
    surfaceContainerHighest=Color(0xFF2D3C3E),
    surfaceBright=Color(0xFF344547),surfaceDim=Color(0xFF0B1113),
    outline=AnimaPulse.border,outlineVariant=Color(0xFF30453E),
    error=Color(0xFFFF8279)) else lightColorScheme(
    primary=Color(0xFF006D3B),onPrimary=Color.White,
    primaryContainer=Color(0xFFBAF6CC),onPrimaryContainer=Color(0xFF002F17),
    secondary=Color(0xFF216379),onSecondary=Color.White,
    secondaryContainer=Color(0xFFCBEAF2),onSecondaryContainer=Color(0xFF003441),
    tertiary=Color(0xFF795600),onTertiary=Color.White,
    tertiaryContainer=Color(0xFFFFE2A2),onTertiaryContainer=Color(0xFF352500),
    background=Color(0xFFF5FAF6),onBackground=Color(0xFF17231B),
    surface=Color(0xFFF5FAF6),onSurface=Color(0xFF17231B),
    surfaceVariant=Color(0xFFDDEBE1),onSurfaceVariant=Color(0xFF43564A),
    surfaceContainerLowest=Color.White,surfaceContainerLow=Color(0xFFEEF5EF),
    surfaceContainer=Color(0xFFE6F0E8),surfaceContainerHigh=Color(0xFFDDEBE1),
    surfaceContainerHighest=Color(0xFFD4E4D8),surfaceBright=Color(0xFFF5FAF6),
    surfaceDim=Color(0xFFD3DFD5),outline=Color(0xFF647C6B),
    outlineVariant=Color(0xFFBCCFC1),inverseSurface=Color(0xFF26352C),
    inverseOnSurface=Color(0xFFEAF4EC),inversePrimary=AnimaPulse.green,
    error=Color(0xFFB3261E),onError=Color.White,
    errorContainer=Color(0xFFFFDAD6),onErrorContainer=Color(0xFF410002)
)
