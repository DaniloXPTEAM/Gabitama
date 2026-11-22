package com.novoagentehb.app.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.novoagentehb.app.app.navigation.Screen
import com.novoagentehb.app.gabi.GabiFaceAvatar
import com.novoagentehb.app.gabi.HBCharacterStage

data class BottomBarDestination(
    val screen: Screen,
    val icon: ImageVector?,
    val label: String,
    val isGabiCenterAvatar: Boolean = false
)

/**
 * Barra principal R1.4 em pílula flutuante inspirada na leveza do Tama V2:
 * preserva os 5 destinos originais (Início, Avisos, Gabi central sincronizada com a fase,
 * Quarto/Habitat e Ajustes) com indicador quente e respiro acima dos gestos do sistema.
 */
val primaryBottomNavItems = listOf(
    BottomBarDestination(Screen.Home, Icons.Default.Home, "Início"),
    BottomBarDestination(Screen.Feed, Icons.Default.Notifications, "Avisos"),
    BottomBarDestination(Screen.Chat, null, "Gabi", isGabiCenterAvatar = true),
    BottomBarDestination(Screen.Habitat, Icons.Default.MeetingRoom, "Quarto"),
    BottomBarDestination(Screen.Settings, Icons.Default.Settings, "Ajustes")
)

@Composable
fun GabiBottomBar(
    navController: NavController,
    activeStage: HBCharacterStage = LocalHBStage.current
) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 432.dp)
                .fillMaxWidth(),
            shape = CircleShape,
            color = GabiTokens.SurfaceDark.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, GabiTokens.GoldAccent.copy(alpha = 0.22f)),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                primaryBottomNavItems.forEach { item ->
                    val selected = currentRoute == item.screen.route
                    val tint by animateColorAsState(
                        targetValue = if (selected) GabiTokens.GoldAccent else GabiTokens.TextSecondary,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "bottom_nav_tint"
                    )
                    val indicatorBg by animateColorAsState(
                        targetValue = if (selected) GabiTokens.CopperAccent.copy(alpha = 0.24f) else Color.Transparent,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "bottom_nav_pill"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(CircleShape)
                            .background(indicatorBg)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!selected) {
                                    if (item.screen == Screen.Home) {
                                        // Returning home must not restore a saved sub-screen stack.
                                        // Deep-link entry may not have Home on the back stack.
                                        if (!navController.popBackStack(Screen.Home.route, inclusive = false)) {
                                            navController.navigate(Screen.Home.route) { launchSingleTop = true }
                                        }
                                    } else {
                                        navController.navigate(item.screen.route) {
                                            popUpTo(Screen.Home.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (item.isGabiCenterAvatar) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(GabiTokens.SurfaceMid)
                                        .border(
                                            width = if (selected) 2.dp else 1.5.dp,
                                            color = if (selected) GabiTokens.GoldAccent else GabiTokens.CopperAccent.copy(alpha = 0.72f),
                                            shape = CircleShape
                                        )
                                ) {
                                    GabiFaceAvatar(
                                        stage = activeStage,
                                        modifier = Modifier.fillMaxSize(),
                                        fallback = {
                                            Icon(
                                                imageVector = Icons.Default.SmartToy,
                                                contentDescription = item.label,
                                                tint = tint,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(6.dp)
                                            )
                                        }
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = item.icon!!,
                                    contentDescription = item.label,
                                    tint = tint,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = item.label,
                                color = tint,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}
