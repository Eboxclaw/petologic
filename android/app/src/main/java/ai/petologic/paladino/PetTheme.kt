package ai.petologic.paladino

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/** Shared palette for Compose screens and the native overlay window. */
internal object PetPalette {
 const val background=0xFF0B1422.toInt()
 const val panel=0xFF142238.toInt()
 const val raised=0xFF20324A.toInt()
 const val gold=0xFFE4BB65.toInt()
 const val text=0xFFF2F5FA.toInt()
 const val muted=0xFFADBDD1.toInt()
 const val outline=0xFF657B96.toInt()
}
internal val Ink=Color(PetPalette.background)
internal val Panel=Color(PetPalette.panel)
internal val Raised=Color(PetPalette.raised)
internal val Gold=Color(PetPalette.gold)
internal val Muted=Color(PetPalette.muted)
internal val Cream=Color(PetPalette.text)
internal val PetColors=darkColorScheme(
 primary=Gold,onPrimary=Ink,primaryContainer=Raised,onPrimaryContainer=Cream,
 secondary=Gold,onSecondary=Ink,secondaryContainer=Raised,onSecondaryContainer=Cream,
 tertiary=Color(0xFF9ACFFF),onTertiary=Ink,background=Ink,onBackground=Cream,
 surface=Panel,onSurface=Cream,surfaceVariant=Raised,onSurfaceVariant=Muted,
 surfaceContainerLowest=Ink,surfaceContainerLow=Panel,surfaceContainer=Panel,
 surfaceContainerHigh=Raised,surfaceContainerHighest=Raised,
 outline=Color(PetPalette.outline),outlineVariant=Raised,
 error=Color(0xFFFFB4AB),onError=Color(0xFF690005)
)
