package com.novoagentehb.app.gabi

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.LayoutInflater
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.novoagentehb.app.R
import com.novoagentehb.app.design.GabiTokens
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Lê preferência do sistema Android para redução de movimento (Acessibilidade > Remover animações)
 * e atualiza automaticamente quando a tela volta ao primeiro plano (`ON_RESUME`).
 */
@Composable
fun rememberReduceMotionEnabled(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var reduceMotion by remember(context) {
        mutableStateOf(isSystemReduceMotionEnabled(context))
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reduceMotion = isSystemReduceMotionEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return reduceMotion
}

fun isSystemReduceMotionEnabled(context: Context): Boolean {
    return runCatching {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        scale == 0f
    }.getOrDefault(false)
}

fun buildAssetLookup(context: Context): AssetLookup {
    val rootList = runCatching { context.assets.list("").orEmpty().toSet() }.getOrDefault(emptySet())
    val animDirs = runCatching { context.assets.list("anim").orEmpty().toSet() }.getOrDefault(emptySet())
    val avatarDirs = runCatching { context.assets.list("avatar").orEmpty().toSet() }.getOrDefault(emptySet())
    return AssetLookup(
        hasAnimFolder = { folder ->
            folder in animDirs && runCatching { context.assets.list("anim/$folder").orEmpty().isNotEmpty() }.getOrDefault(false)
        },
        hasAssetFile = { path ->
            if (path.contains('/')) {
                val parent = path.substringBeforeLast('/')
                val file = path.substringAfterLast('/')
                runCatching { context.assets.list(parent).orEmpty().contains(file) }.getOrDefault(false)
            } else {
                path in rootList
            }
        },
        hasAvatarFolder = { folder ->
            folder in avatarDirs && runCatching { context.assets.list("avatar/$folder").orEmpty().isNotEmpty() }.getOrDefault(false)
        }
    )
}

/**
 * Renderiza imagem da pasta assets/ com segurança em thread de I/O.
 */
@Composable
fun GabiAssetImage(
    fileName: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f
) {
    val context = LocalContext.current
    var bitmap by remember(fileName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(fileName) {
        bitmap = withContext(Dispatchers.IO) {
            decodeAssetBitmap(context, fileName)
        }
    }

    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier.alpha(alpha)
        )
    } else {
        Box(modifier = modifier)
    }
}

@Composable
fun GabiUriImage(
    uriString: String,
    fallbackAsset: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    var bitmap by remember(uriString) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(uriString) {
        bitmap = if (uriString.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(uriString)
                    val stream = when (uri.scheme) {
                        "file", null -> File(uri.path ?: uriString).inputStream()
                        else -> context.contentResolver.openInputStream(uri)
                    }
                    stream?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                }.getOrNull()
            }
        }
    }

    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded,
            contentDescription = "Fundo personalizado do Quarto",
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        GabiAssetImage(
            fileName = fallbackAsset,
            contentDescription = "Quarto da Gabi",
            contentScale = contentScale,
            modifier = modifier
        )
    }
}

/**
 * Reproduz sequências WebP transparentes em assets/avatar/{idle,listening,thinking,talking}/ (~24 fps).
 * Pausa automaticamente em segundo plano e respeita `reduceMotion` exibindo apenas o 1º quadro.
 */
/**
 * Reproduz [files] em loop com decodificação antecipada limitada a [AvatarFramePlanner.PREFETCH_FRAMES]
 * quadros (memória constante, sem carregar a sequência inteira). Retorna se algum quadro foi exibido.
 * Um quadro que não decodifica é pulado; se nenhum decodificar em uma volta completa, retorna false.
 */
private suspend fun playLoop(
    context: Context,
    folder: String,
    files: List<String>,
    fps: Int,
    show: (ImageBitmap) -> Unit
): Boolean = coroutineScope {
    val ready = Channel<ImageBitmap>(capacity = AvatarFramePlanner.PREFETCH_FRAMES)
    val producer = launch(Dispatchers.IO) {
        var idx = 0
        var consecutiveFailures = 0
        while (isActive) {
            val bmp = decodeAssetBitmap(context, "avatar/$folder/${files[idx]}")
            if (bmp != null) {
                consecutiveFailures = 0
                ready.send(bmp)
            } else {
                consecutiveFailures++
                if (consecutiveFailures >= AvatarFramePlanner.maxConsecutiveFailures(files.size)) break
            }
            idx = AvatarFramePlanner.nextIndex(idx, files.size)
        }
        ready.close()
    }
    val frameDelayMs = AvatarFramePlanner.frameDelayMs(fps)
    var shownAny = false
    var lastShownAt = SystemClock.uptimeMillis()
    try {
        for (bmp in ready) {
            show(bmp)
            shownAny = true
            val elapsed = SystemClock.uptimeMillis() - lastShownAt
            delay((frameDelayMs - elapsed).coerceAtLeast(0L))
            lastShownAt = SystemClock.uptimeMillis()
        }
    } finally {
        producer.cancel()
    }
    shownAny
}

@Composable
fun AvatarAnimation(
    state: AvatarState,
    fallbackPng: String,
    modifier: Modifier = Modifier,
    folderOverride: String? = null,
    fps: Int = 24,
    reduceMotion: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val folder = folderOverride ?: state.assetFolder
    var currentFrame by remember(folder) { mutableStateOf<ImageBitmap?>(null) }
    var useFallback by remember(folder) { mutableStateOf(false) }

    LaunchedEffect(folder, fps, reduceMotion, lifecycleOwner) {
        useFallback = false
        val names = withContext(Dispatchers.IO) {
            runCatching { context.assets.list("avatar/$folder")?.toList().orEmpty() }.getOrDefault(emptyList())
        }
        when (val plan = AvatarFramePlanner.plan(names, reduceMotion)) {
            is AvatarFramePlanner.Plan.Fallback -> useFallback = true
            is AvatarFramePlanner.Plan.Still -> {
                val first = withContext(Dispatchers.IO) { decodeAssetBitmap(context, "avatar/$folder/${plan.file}") }
                if (first != null) currentFrame = first else useFallback = true
            }
            is AvatarFramePlanner.Plan.Loop -> {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    playLoop(context, folder, plan.files, fps) { bmp -> currentFrame = bmp }
                        .let { shownAny -> if (!shownAny && currentFrame == null) useFallback = true }
                }
            }
        }
    }

    val frame = currentFrame
    if (useFallback || frame == null) {
        GabiAssetImage(
            fileName = fallbackPng,
            contentDescription = "Personagem HB",
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    } else {
        Image(
            bitmap = frame,
            contentDescription = "Personagem HB animada",
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    }
}

/**
 * Retorna quantidade de quadros disponíveis em assets/anim/<folder>/ ou null se ausente.
 */
@Composable
fun rememberAnimFrameCount(folder: String): Int? {
    val context = LocalContext.current
    return remember(context, folder) {
        runCatching {
            val count = context.assets.list("anim/$folder")
                ?.count { it.endsWith(".png") || it.endsWith(".webp") }
                ?: 0
            if (count > 0) count else null
        }.getOrNull()
    }
}

/**
 * Avatar de rosto para barra inferior e cabeçalho do Chat sincronizado com a fase ativa.
 */
@Composable
fun GabiFaceAvatar(
    modifier: Modifier = Modifier,
    stage: HBCharacterStage = HBCharacterStage.YOUNG_CHILD,
    fallback: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    var bitmap by remember(stage) { mutableStateOf<ImageBitmap?>(null) }
    var checked by remember(stage) { mutableStateOf(false) }

    LaunchedEffect(stage) {
        bitmap = withContext(Dispatchers.IO) {
            decodeAssetBitmap(context, stage.avatarCirclePngPath)
                ?: decodeAssetBitmap(context, "gabi-face.png")
                ?: decodeAssetBitmap(context, "gabi-idle.png")
        }
        checked = true
    }

    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded,
            contentDescription = "Rosto da Gabi (${stage.ageGroupLabel})",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else if (checked) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            fallback()
        }
    } else {
        Box(modifier = modifier)
    }
}

/**
 * Orbe de fallback quando nenhum bitmap estiver disponível (respeita `reduceMotion`).
 */
@Composable
fun GabiParticleOrb(
    sizeDp: Int,
    state: AvatarState,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotionEnabled()
) {
    val pulse = if (reduceMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "orb_pulse")
        val animated by transition.animateFloat(
            initialValue = 0.88f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orb_scale"
        )
        animated
    }

    val primaryColor = when (state) {
        AvatarState.TALKING -> GabiTokens.SuccessGreen
        AvatarState.LISTENING -> GabiTokens.ErrorRed
        AvatarState.THINKING -> GabiTokens.GoldAccent
        AvatarState.IDLE -> GabiTokens.TealAccent
    }

    Canvas(modifier = modifier.size(sizeDp.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = (size.minDimension / 2f) * pulse
        drawCircle(color = primaryColor.copy(alpha = 0.25f), radius = radius, center = center)
        drawCircle(color = primaryColor.copy(alpha = 0.72f), radius = radius * 0.56f, center = center)
    }
}

/**
 * Reproduz sequências PNG transparentes em assets/anim/<folder>/.
 * Pausa automaticamente em segundo plano e respeita `reduceMotion`.
 */
@Composable
fun FrameSequenceAnimation(
    folder: String,
    modifier: Modifier = Modifier,
    fallbackPng: String = "gabi-idle.png",
    frameCount: Int? = null,
    fps: Int = 18,
    reduceMotion: Boolean = rememberReduceMotionEnabled()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var currentFrame by remember(folder) { mutableStateOf<ImageBitmap?>(null) }
    var useFallback by remember(folder) { mutableStateOf(false) }

    LaunchedEffect(folder, frameCount, fps, reduceMotion, lifecycleOwner) {
        useFallback = false
        val discovered = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.list("anim/$folder")
                    ?.filter { it.endsWith(".png") || it.endsWith(".webp") }
                    ?.sorted()
                    .orEmpty()
            }.getOrDefault(emptyList())
        }
        val files = if (frameCount != null && frameCount > 0) discovered.take(frameCount) else discovered
        if (files.isEmpty()) {
            useFallback = true
            return@LaunchedEffect
        }
        if (reduceMotion) {
            val first = withContext(Dispatchers.IO) { decodeAssetBitmap(context, "anim/$folder/${files.first()}") }
            if (first != null) currentFrame = first else useFallback = true
            return@LaunchedEffect
        }

        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val frameDelayMs = (1000L / fps.coerceAtLeast(1)).coerceAtLeast(16L)
            var idx = 0
            while (isActive) {
                val t0 = SystemClock.uptimeMillis()
                val bmp = withContext(Dispatchers.IO) {
                    decodeAssetBitmap(context, "anim/$folder/${files[idx]}")
                }
                if (bmp != null) {
                    currentFrame = bmp
                } else if (idx == 0 && currentFrame == null) {
                    useFallback = true
                    break
                }
                idx = (idx + 1) % files.size
                val elapsed = SystemClock.uptimeMillis() - t0
                delay((frameDelayMs - elapsed).coerceAtLeast(16L))
            }
        }
    }

    val frame = currentFrame
    if (useFallback || frame == null) {
        BreathingCharacter(
            fileName = fallbackPng,
            reduceMotion = reduceMotion,
            modifier = modifier
        )
    } else {
        Image(
            bitmap = frame,
            contentDescription = "Personagem HB em cena",
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    }
}

/**
 * Respiração sutil sobre poses estáticas transparentes (comer, ler, dormir) enquanto cenas dedicadas
 * aguardam geração externa no Google Flow.
 */
@Composable
fun BreathingCharacter(
    fileName: String,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false
) {
    val scale = if (reduceMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "hb_breath")
        val animated by transition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.022f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2300, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breath_scale"
        )
        animated
    }

    GabiAssetImage(
        fileName = fileName,
        contentDescription = "Personagem HB",
        contentScale = ContentScale.Fit,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    )
}

/**
 * Componente unificado de renderização da HB em Home, Chat e Quarto da Gabi.
 * Sincronizado com o catálogo central `HBAnimationCatalog`, preserva sombra de contato natural
 * sem retângulos artificiais e respeita acessibilidade `reduceMotion`.
 */
@Composable
fun HBStageCharacter(
    visualSource: HBVisualSource,
    activity: HBActivity,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotionEnabled(),
    preferCircularStageMedallion: Boolean = false
) {
    val isTapOrOutcome = activity is HBActivity.TapReaction ||
        activity is HBActivity.Success ||
        activity is HBActivity.Eating ||
        activity is HBActivity.OutfitChange

    val pulseScale = if (!reduceMotion && isTapOrOutcome) {
        val t = rememberInfiniteTransition(label = "hb_react")
        val v by t.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.035f,
            animationSpec = infiniteRepeatable(
                animation = tween(360, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "react_scale"
        )
        v
    } else {
        1f
    }

    val stageRingColor = when (activity) {
        is HBActivity.Speaking -> GabiTokens.SuccessGreen
        is HBActivity.Listening -> GabiTokens.ErrorRed
        is HBActivity.Thinking, is HBActivity.Working -> GabiTokens.GoldAccent
        is HBActivity.Error -> GabiTokens.ErrorRed
        is HBActivity.MusicReaction -> GabiTokens.SpotifyGreen
        else -> GabiTokens.CopperAccent
    }

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = pulseScale
            scaleY = pulseScale
        },
        contentAlignment = Alignment.BottomCenter
    ) {
        if (visualSource !is HBVisualSource.FullSceneStageVideo) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(width = 112.dp, height = 20.dp)
            ) {
                drawOval(
                    color = Color.Black.copy(alpha = 0.28f),
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height)
                )
            }
        }

        when (visualSource) {
            is HBVisualSource.AvatarWebp -> AvatarAnimation(
                state = AvatarState.entries.firstOrNull { it.assetFolder == visualSource.folder } ?: AvatarState.IDLE,
                folderOverride = visualSource.folder,
                fallbackPng = visualSource.fallbackPng,
                reduceMotion = reduceMotion,
                modifier = Modifier.fillMaxSize()
            )
            is HBVisualSource.FramePngSequence -> FrameSequenceAnimation(
                folder = visualSource.folder,
                fallbackPng = visualSource.fallbackPng,
                fps = visualSource.fps,
                reduceMotion = reduceMotion,
                modifier = Modifier.fillMaxSize()
            )
            is HBVisualSource.StaticPose -> BreathingCharacter(
                fileName = visualSource.assetFile,
                reduceMotion = reduceMotion,
                modifier = Modifier.fillMaxSize()
            )
            is HBVisualSource.FullSceneStageVideo -> {
                if (preferCircularStageMedallion) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(190.dp)
                            .clip(CircleShape)
                            .background(GabiTokens.SurfaceDark)
                            .border(2.5.dp, stageRingColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        StageLoopVideo(
                            assetPath = visualSource.assetVideoPath,
                            fallbackPng = visualSource.stage.avatarCirclePngPath,
                            reduceMotion = reduceMotion,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    StageLoopVideo(
                        assetPath = visualSource.assetVideoPath,
                        fallbackPng = visualSource.fallbackPng,
                        reduceMotion = reduceMotion,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Vídeo de palco em tela cheia para estágios etários da HB (`idade1` / `idade2`) em `assets/hb-stages/`.
 * Utiliza `TextureView` (`view_texture_player.xml`) com `resize_mode="zoom"` e volume 0 para compor
 * sem retângulos de fundo e sem conflitar com o áudio de voz/TTS ou Spotify.
 */
@Composable
fun StageLoopVideo(
    assetPath: String,
    fallbackPng: String,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotionEnabled()
) {
    if (reduceMotion) {
        GabiAssetImage(
            fileName = fallbackPng,
            contentDescription = "Fase da personagem HB (movimento reduzido)",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var fallbackToImage by remember(assetPath) { mutableStateOf(false) }

    if (fallbackToImage) {
        GabiAssetImage(
            fileName = fallbackPng,
            contentDescription = "Fase da personagem HB",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
        return
    }

    val player = remember(context, assetPath) {
        runCatching {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse("asset:///$assetPath")))
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        fallbackToImage = true
                    }
                })
                prepare()
            }
        }.getOrNull()
    }

    if (player == null) {
        GabiAssetImage(
            fileName = fallbackPng,
            contentDescription = "Fase da personagem HB",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
        return
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    player.playWhenReady = true
                    player.play()
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    player.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            val inflated = LayoutInflater.from(ctx).inflate(R.layout.view_texture_player, null, false)
            (inflated as PlayerView).apply {
                this.player = player
            }
        },
        update = { view ->
            if (view.player !== player) {
                view.player = player
            }
        },
        modifier = modifier
    )
}

/**
 * Vídeo de fundo opcional do Quarto (res/raw/room_bg_loop.mp4).
 * Usa PlayerView com TextureView (`view_texture_player.xml`) para preservar corretamente
 * o Z-order sobre a camada transparente da personagem HB e controles do Compose.
 */
@Composable
fun RoomLoopBackgroundVideo(
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotionEnabled()
) {
    if (reduceMotion) {
        GabiAssetImage(
            fileName = "avatar/room.webp",
            contentDescription = "Quarto da Gabi (movimento reduzido)",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var fallbackToImage by remember { mutableStateOf(false) }

    if (fallbackToImage) {
        GabiAssetImage(
            fileName = "avatar/room.webp",
            contentDescription = "Quarto da Gabi",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
        return
    }

    val player = remember(context) {
        runCatching {
            val rawUri = Uri.parse("android.resource://${context.packageName}/${R.raw.room_bg_loop}")
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(rawUri))
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        fallbackToImage = true
                    }
                })
                prepare()
            }
        }.getOrNull()
    }

    if (player == null) {
        GabiAssetImage(
            fileName = "avatar/room.webp",
            contentDescription = "Quarto da Gabi",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
        return
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    player.playWhenReady = true
                    player.play()
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    player.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            val inflated = LayoutInflater.from(ctx).inflate(R.layout.view_texture_player, null, false)
            (inflated as PlayerView).apply {
                this.player = player
            }
        },
        update = { view ->
            if (view.player !== player) {
                view.player = player
            }
        },
        modifier = modifier
    )
}

/**
 * Vídeo de introdução do Quarto (assets/avatar/room_intro.mp4) reproduzido sob TextureView
 * com controles obrigatórios de Pular e Não mostrar novamente, pausa em segundo plano e liberação no dispose.
 */
@Composable
fun RoomIntroVideo(
    onFinish: (dontShowAgain: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var finished by remember { mutableStateOf(false) }

    val player = remember(context) {
        runCatching {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse("asset:///avatar/room_intro.mp4")))
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
                prepare()
            }
        }.getOrNull()
    }

    LaunchedEffect(player) {
        if (player == null && !finished) {
            finished = true
            onFinish(false)
        }
    }

    if (player == null) return

    DisposableEffect(lifecycleOwner, player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && !finished) {
                    finished = true
                    onFinish(false)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!finished) {
                    finished = true
                    onFinish(false)
                }
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    player.playWhenReady = true
                    player.play()
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    player.pause()
                }
                else -> Unit
            }
        }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            player.removeListener(listener)
            player.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                val inflated = LayoutInflater.from(ctx).inflate(R.layout.view_texture_player, null, false)
                (inflated as PlayerView).apply {
                    this.player = player
                }
            },
            update = { view ->
                if (view.player !== player) {
                    view.player = player
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (!finished) {
                            finished = true
                            onFinish(true)
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GabiTokens.TextPrimary)
                ) {
                    Text("Não mostrar novamente", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = {
                        if (!finished) {
                            finished = true
                            onFinish(false)
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GabiTokens.GoldAccent)
                ) {
                    Text("Pular", color = Color.Black, style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun decodeAssetBitmap(context: Context, assetPath: String): ImageBitmap? {
    return try {
        context.assets.open(assetPath).use { stream ->
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
        }
    } catch (_: OutOfMemoryError) {
        null
    } catch (_: Throwable) {
        null
    }
}
