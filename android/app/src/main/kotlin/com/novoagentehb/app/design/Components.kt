package com.novoagentehb.app.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novoagentehb.app.agent.models.ProviderFailure
import com.novoagentehb.app.gabi.GabiAssetImage

/**
 * Fundo atmosférico unificado R1.4 usando `assets/theme/gabitama_home_backdrop.png`
 * com vinheta suave para preservar contraste alto em toda a hierarquia tipográfica.
 */
@Composable
fun GabiAtmosphericBackdrop(
    modifier: Modifier = Modifier,
    backdropAlpha: Float = 0.75f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GabiTokens.NightBg)
    ) {
        GabiAssetImage(
            fileName = "theme/gabitama_home_backdrop.png",
            contentScale = ContentScale.Crop,
            alpha = backdropAlpha,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to GabiTokens.NightBg.copy(alpha = 0.42f),
                        0.45f to Color.Transparent,
                        1.0f to GabiTokens.NightBg.copy(alpha = 0.85f)
                    )
                )
        )
        content()
    }
}

/**
 * Cabeçalho limpo e estruturado inspirado na leveza do Tama V2:
 * botão voltar em disco circular `40.dp`, título legível e ações à direita.
 */
@Composable
fun GabiTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (onBack != null) {
            GabiCircleActionButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Voltar",
                onClick = onBack
            )
        }
        if (leading != null) {
            leading()
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = GabiTokens.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = GabiTokens.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = actions
        )
    }
}

/**
 * Botão circular tátil (`40.dp` disco, área de toque confortável) inspirado na ergonomia Tama V2.
 */
@Composable
fun GabiCircleActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = GabiTokens.TextPrimary,
    borderColor: Color = GabiTokens.Hairline,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = GabiTokens.SurfaceDark.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 3.dp,
        modifier = modifier.size(size)
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Superfície de cartão com textura sutil `assets/theme/gabitama_card_surface.png`
 * e borda fina de acabamento arquitetônico.
 */
@Composable
fun GabiFeaturedSurface(
    modifier: Modifier = Modifier,
    borderColor: Color = GabiTokens.Hairline,
    cornerRadius: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Surface(
        modifier = modifier,
        shape = shape,
        color = GabiTokens.SurfaceDark.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box {
            GabiAssetImage(
                fileName = "theme/gabitama_card_surface.png",
                contentScale = ContentScale.Crop,
                alpha = 0.28f,
                modifier = Modifier.matchParentSize()
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                content = content
            )
        }
    }
}

/**
 * Estado vazio acolhedor com cena original `assets/theme/gabitama_empty_scene.png`.
 */
@Composable
fun GabiEmptyStateCard(
    title: String,
    subtitle: String,
    accent: Color = GabiTokens.GoldAccent,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    GabiFeaturedSurface(
        modifier = modifier.fillMaxWidth(),
        borderColor = accent.copy(alpha = 0.28f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GabiAssetImage(
                fileName = "theme/gabitama_empty_scene.png",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(124.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(18.dp))
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = GabiTokens.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp)
            )
            if (actionLabel != null && onAction != null) {
                OutlinedButton(
                    onClick = onAction,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(actionLabel, color = accent, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun GlassInfoCard(
    label: String,
    value: String,
    accent: Color,
    fillFraction: Float?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    emoji: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = GabiTokens.SurfaceDark.copy(alpha = 0.86f)),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .border(0.8.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (emoji != null) {
                        Text(emoji, fontSize = 15.sp)
                    } else if (icon != null) {
                        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label.uppercase(),
                        color = GabiTokens.TextSecondary,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = value,
                        color = GabiTokens.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(GabiTokens.Hairline.copy(alpha = 0.65f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((fillFraction ?: 1f).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent)
                )
            }
        }
    }
}

/**
 * Diálogo oficial da Bíblia da Personagem (`assets/bible.png`) e Spritesheet (`assets/gabi-spritesheet.png`).
 */
@Composable
fun CharacterBibleDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GabiTokens.SurfaceDark,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GabiAssetImage(
                    fileName = "theme/gabitama_brand_crest.png",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(30.dp)
                )
                Column {
                    Text("Bíblia & Referências da HB", color = GabiTokens.GoldAccent, fontWeight = FontWeight.Bold)
                    Text("Identidade visual oficial preservada em R1.4", color = GabiTokens.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GabiAssetImage(
                    fileName = "bible.png",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, GabiTokens.Hairline, RoundedCornerShape(12.dp))
                )
                Text(
                    text = "Paleta oficial da HB: Cabelo #C7541E · Pele #F6C7A7 · Sardas #E6A07A · Vestido #C62828 · Botas #1E1E1E",
                    color = GabiTokens.TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        GabiTokens.CopperHair to "Cabelo",
                        GabiTokens.WarmPeach to "Pele",
                        GabiTokens.FreckleCoral to "Sardas",
                        GabiTokens.CrimsonDress to "Vestido",
                        GabiTokens.BootsInk to "Botas"
                    ).forEach { (swatch, name) ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(1.dp, GabiTokens.TextSecondary.copy(alpha = 0.45f), CircleShape)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(name, color = GabiTokens.TextSecondary, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
                Text("Planilha oficial de 7 poses (gabi-spritesheet.png):", color = GabiTokens.GoldAccent, style = MaterialTheme.typography.labelMedium)
                GabiAssetImage(
                    fileName = "gabi-spritesheet.png",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GabiTokens.NightBg)
                        .border(1.dp, GabiTokens.Hairline, RoundedCornerShape(10.dp))
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar", color = GabiTokens.GoldAccent) }
        }
    )
}

@Composable
fun ConfirmActionBar(
    prompt: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GabiTokens.SurfaceWarm)
            .border(1.dp, GabiTokens.GoldAccent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = prompt,
            modifier = Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState()),
            color = GabiTokens.TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = GabiTokens.GoldAccent),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Sim, autorizar", color = GabiTokens.NightBg, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = onCancel,
                border = BorderStroke(1.dp, GabiTokens.Hairline),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancelar", color = GabiTokens.TextSecondary)
            }
        }
    }
}

@Composable
fun ActiveExecutionBanner(
    statusLabel: String,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GabiTokens.SurfaceMid)
            .border(1.dp, GabiTokens.CopperAccent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = statusLabel,
            color = GabiTokens.GoldAccent,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = GabiTokens.ErrorRed),
            shape = CircleShape
        ) {
            Icon(Icons.Default.Stop, contentDescription = "Parar", tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Parar", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProviderFailureCard(
    failure: ProviderFailure,
    onOpenDetails: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = GabiTokens.SurfaceMid),
        border = BorderStroke(1.dp, GabiTokens.ErrorRed.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = GabiTokens.ErrorRed, modifier = Modifier.size(18.dp))
                Text(
                    text = "${failure.providerId} · ${failure.category.label}",
                    color = GabiTokens.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = failure.userSummary,
                color = GabiTokens.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenDetails) { Text("Detalhes", color = GabiTokens.GoldAccent) }
                TextButton(onClick = onOpenSettings) { Text("Ajustes", color = GabiTokens.TealAccent) }
                TextButton(onClick = onRetry) { Text("Tentar novamente", color = GabiTokens.TextPrimary) }
            }
        }
    }
}

@Composable
fun ProviderFailureDetailDialog(
    failure: ProviderFailure,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GabiTokens.SurfaceDark,
        title = { Text("Detalhes da recusa", color = GabiTokens.GoldAccent) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Provedor: ${failure.providerId}", color = GabiTokens.TextPrimary, style = MaterialTheme.typography.bodySmall)
                Text("Modelo: ${failure.modelId.ifBlank { "indisponível" }}", color = GabiTokens.TextPrimary, style = MaterialTheme.typography.bodySmall)
                Text("HTTP: ${failure.httpStatus?.toString() ?: "indisponível"}", color = GabiTokens.TextPrimary, style = MaterialTheme.typography.bodySmall)
                Text("Categoria: ${failure.category.label}", color = GabiTokens.TextPrimary, style = MaterialTheme.typography.bodySmall)
                Text("Detalhe sanitizado: ${failure.sanitizedDetail}", color = GabiTokens.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar", color = GabiTokens.GoldAccent) }
        }
    )
}
