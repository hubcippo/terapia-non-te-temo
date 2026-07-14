package com.carletto.terapianontetemo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val SchemaChiaro = lightColorScheme(
    primary = VerdePrimario,
    onPrimary = VerdeSuPrimario,
    primaryContainer = VerdeContenitore,
    onPrimaryContainer = VerdeSuContenitore,
    secondary = BluSecondario,
    onSecondary = BluSuSecondario,
    secondaryContainer = BluContenitore,
    onSecondaryContainer = BluSuContenitore,
    tertiary = AmbraTerziario,
    onTertiary = AmbraSuTerziario,
    tertiaryContainer = AmbraContenitore,
    onTertiaryContainer = AmbraSuContenitore,
    error = RossoErrore,
    onError = RossoSuErrore,
    errorContainer = RossoErroreContenitore,
    onErrorContainer = RossoSuErroreContenitore,
    background = SfondoChiaro,
    onBackground = SuSfondoChiaro,
    surface = SuperficieChiara,
    onSurface = SuSuperficieChiara,
)

private val SchemaScuro = darkColorScheme(
    primary = VerdePrimarioScuro,
    onPrimary = VerdeSuPrimarioScuro,
    primaryContainer = VerdeContenitoreScuro,
    onPrimaryContainer = VerdeSuContenitoreScuro,
    secondary = BluSecondarioScuro,
    onSecondary = BluSuSecondarioScuro,
    secondaryContainer = BluContenitoreScuro,
    onSecondaryContainer = BluSuContenitoreScuro,
    tertiary = AmbraTerziarioScuro,
    onTertiary = AmbraSuTerziarioScuro,
    tertiaryContainer = AmbraContenitoreScuro,
    onTertiaryContainer = AmbraSuContenitoreScuro,
    error = RossoErroreScuro,
    onError = RossoSuErroreScuro,
    errorContainer = RossoErroreContenitoreScuro,
    onErrorContainer = RossoSuErroreContenitoreScuro,
    background = SfondoScuro,
    onBackground = SuSfondoScuro,
    surface = SuperficieScura,
    onSurface = SuSuperficieScura,
)

/**
 * Tema dell'app: dynamic color automatico su Android 12+ (API 31),
 * variante chiara/scura dal sistema, tipografia maggiorata per leggibilita'.
 */
@Composable
fun TerapiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> SchemaScuro
        else -> SchemaChiaro
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
