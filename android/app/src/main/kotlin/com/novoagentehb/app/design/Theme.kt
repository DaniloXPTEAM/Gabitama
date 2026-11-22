package com.novoagentehb.app.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.novoagentehb.app.gabi.HBCharacterStage

/**
 * Estágio ativo da HB propagado para barra inferior, cabeçalhos e atalhos
 * para garantir continuidade visual entre Infância, Pré-adolescente e Adolescente.
 */
val LocalHBStage = staticCompositionLocalOf { HBCharacterStage.YOUNG_CHILD }

/**
 * Sistema de Tokens GabiTama R1.4.1 (`DESIGN_SYSTEM_GABITAMA_R1_4.md`),
 * harmonizando a Bíblia Oficial da Personagem (`bible.png`: cabelo #C7541E,
 * pele #F6C7A7, sardas #E6A07A, vestido #C62828, botas #1E1E1E) com atmosfera
 * noturna quente de cobre, carmesim e dourado (reduzindo o peso azul frio de R1.4).
 */
object GabiTokens {
    // Âncoras oficiais de bible.png
    val CopperHair = Color(0xFFC7541E)
    val WarmPeach = Color(0xFFF6C7A7)
    val FreckleCoral = Color(0xFFE6A07A)
    val CrimsonDress = Color(0xFFC62828)
    val BootsInk = Color(0xFF1E1E1E)

    // Superfícies & Atmosfera (obsidiana quente cobre/carmesim)
    val NightBg = Color(0xFF120F11)
    val SurfaceDark = Color(0xFF1B1618)
    val SurfaceMid = Color(0xFF251E20)
    val SurfaceWarm = Color(0xFF2D201E)
    val Hairline = Color(0xFF3E302E)

    // Acentos semânticos e identitários
    val GoldAccent = Color(0xFFFFCF8C)
    val CopperAccent = Color(0xFFD96830)
    val AmberAccent = Color(0xFFE5A952)
    val TealAccent = Color(0xFF5FB8AC)
    val TextPrimary = Color(0xFFF5F1ED)
    val TextSecondary = Color(0xFFB5A9A5)
    val ErrorRed = Color(0xFFF2635A)
    val SuccessGreen = Color(0xFF3FB950)
    val RoomAccent = Color(0xFFD96830)
    val VioletAccent = Color(0xFFB292F6)
    val SpotifyGreen = Color(0xFF1DB954)

    val SkyGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF241718),
            Color(0xFF181214),
            NightBg
        )
    )

    val WarmStageGlow = Brush.radialGradient(
        colors = listOf(
            CopperHair.copy(alpha = 0.24f),
            GoldAccent.copy(alpha = 0.12f),
            Color.Transparent
        )
    )
}

private val NovoAgenteColorScheme = darkColorScheme(
    primary = GabiTokens.GoldAccent,
    secondary = GabiTokens.TealAccent,
    tertiary = GabiTokens.CopperAccent,
    background = GabiTokens.NightBg,
    surface = GabiTokens.SurfaceDark,
    surfaceVariant = GabiTokens.SurfaceMid,
    outline = GabiTokens.Hairline,
    onPrimary = GabiTokens.NightBg,
    onSecondary = GabiTokens.TextPrimary,
    onBackground = GabiTokens.TextPrimary,
    onSurface = GabiTokens.TextPrimary,
    onSurfaceVariant = GabiTokens.TextSecondary,
    error = GabiTokens.ErrorRed
)

private val NovoAgenteTypography = Typography(
    headlineLarge = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.5.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun NovoAgenteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NovoAgenteColorScheme,
        typography = NovoAgenteTypography,
        content = content
    )
}
