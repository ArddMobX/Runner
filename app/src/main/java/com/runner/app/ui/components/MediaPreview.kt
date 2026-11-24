package com.runner.app.ui.components
import com.runner.app.ui.theme.RunnerShapes
import com.runner.app.ui.theme.TypeScale

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.runner.app.ui.theme.MotionTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.runner.app.ui.components.RunnerIcons

/** Что за файл по расширению. От этого зависит, что показывать в строке. */
enum class MediaKind { IMAGE, AUDIO, VIDEO, OTHER }

private val IMAGE_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif"
)

private val AUDIO_EXTENSIONS = setOf(
    "mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac", "amr", "mid", "midi", "3gp"
)

private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "ts")

fun mediaKindOf(fileName: String): MediaKind {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        in IMAGE_EXTENSIONS -> MediaKind.IMAGE
        in AUDIO_EXTENSIONS -> MediaKind.AUDIO
        in VIDEO_EXTENSIONS -> MediaKind.VIDEO
        else -> MediaKind.OTHER
    }
}

private val ABSOLUTE_ROOT_SEGMENTS = setOf(
    "storage", "sdcard", "data", "mnt", "system", "vendor",
    "product", "proc", "sys", "dev", "cache", "oem", "apex"
)

internal fun resolveFileEntryAbsolutePath(folder: String?, name: String): String? {
    val cleanName = name.trim()
    if (cleanName.isEmpty()) return null
    val folderPart = folder?.trim()?.trim('/').orEmpty()
    return when {
        folderPart.isNotEmpty() -> {
            val firstSegment = folderPart.substringBefore('/').lowercase()
            if (firstSegment in ABSOLUTE_ROOT_SEGMENTS) {
                "/$folderPart/$cleanName"
            } else {
                "/storage/emulated/0/$folderPart/$cleanName"
            }
        }
        cleanName.startsWith("/") -> cleanName
        else -> null
    }
}

/**
 * Абсолютный путь к файлу. В FileEntry папка хранится без ведущего слэша,
 * потому что парсер его срезает для читаемости. Относительные папки вроде
 * `DCIM/Camera` резолвятся относительно `/storage/emulated/0/`.
 */
val FileEntry.absolutePath: String?
    get() = resolveFileEntryAbsolutePath(folder, name)

/**
 * Плеер для превью из чата. Держит один MediaPlayer: одновременно играет
 * только один трек, иначе строки с кнопками превратились бы в какофонию.
 */
object AudioPreviewPlayer {

    private var player: MediaPlayer? = null

    var activePath by mutableStateOf<String?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var isPreparing by mutableStateOf(false)
        private set

    var lastError by mutableStateOf<String?>(null)
        private set

    var errorEvent by mutableStateOf<Pair<String, String>?>(null)
        private set

    fun consumeError() {
        errorEvent = null
    }

    fun toggle(path: String) {
        if (activePath == path && player != null) {
            when {
                isPreparing -> release()
                isPlaying -> pause()
                else -> resume()
            }
            return
        }
        release()
        try {
            val created = MediaPlayer()
            player = created
            activePath = path
            isPreparing = true
            isPlaying = false
            lastError = null
            errorEvent = null

            created.setDataSource(path)
            // Явный получатель обязателен: у самого MediaPlayer есть
            // синтетическое свойство isPlaying без сеттера, и без префикса
            // компилятор ругается «Val cannot be reassigned»
            created.setOnPreparedListener { mp ->
                if (player !== mp) {
                    runCatching { mp.release() }
                    return@setOnPreparedListener
                }
                AudioPreviewPlayer.isPreparing = false
                try {
                    mp.start()
                    AudioPreviewPlayer.isPlaying = true
                } catch (e: Exception) {
                    val msg = e.localizedMessage ?: "Не удалось воспроизвести файл"
                    AudioPreviewPlayer.lastError = msg
                    AudioPreviewPlayer.errorEvent = path to msg
                    release()
                }
            }
            created.setOnCompletionListener { mp ->
                if (player === mp) {
                    release()
                } else {
                    runCatching { mp.release() }
                }
            }
            created.setOnErrorListener { mp, _, _ ->
                if (player === mp) {
                    val msg = "Не удалось воспроизвести файл"
                    AudioPreviewPlayer.lastError = msg
                    AudioPreviewPlayer.errorEvent = path to msg
                    release()
                } else {
                    runCatching { mp.release() }
                }
                true
            }
            created.prepareAsync()
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Не удалось открыть файл"
            lastError = msg
            errorEvent = path to msg
            release()
        }
    }

    private fun pause() {
        runCatching { player?.pause() }
        isPlaying = false
    }

    private fun resume() {
        try {
            player?.start()
            isPlaying = true
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Не удалось продолжить воспроизведение"
            lastError = msg
            activePath?.let { errorEvent = it to msg }
            release()
        }
    }

    fun stop() = release()

    private fun release() {
        val toRelease = player
        player = null
        isPreparing = false
        isPlaying = false
        activePath = null
        toRelease?.let { runCatching { it.release() } }
    }
}

/** Кнопка воспроизведения слева от строки файла. */
@Composable
fun AudioPlayButton(path: String?, enabled: Boolean) {
    val context = LocalContext.current
    val isActive = path != null && AudioPreviewPlayer.activePath == path
    val isPlaying = isActive && AudioPreviewPlayer.isPlaying
    val isPreparing = isActive && AudioPreviewPlayer.isPreparing

    LaunchedEffect(AudioPreviewPlayer.errorEvent, path) {
        val event = AudioPreviewPlayer.errorEvent
        if (event != null && event.first == path) {
            Toast.makeText(context, event.second, Toast.LENGTH_SHORT).show()
            AudioPreviewPlayer.consumeError()
        }
    }

    val background by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = MotionTokens.effectsFast(),
        label = "play_background"
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            !enabled || path == null -> MaterialTheme.colorScheme.outline
            isActive -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MotionTokens.effectsFast(),
        label = "play_tint"
    )

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled && path != null) {
                if (path != null) {
                    AudioPreviewPlayer.toggle(path)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isPreparing) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = iconTint
            )
        } else {
            // Иконка меняется через масштаб, а не подменой: глаз успевает
            // заметить, что состояние переключилось
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = { MotionTokens.iconSwapTransition() },
                label = "play_pause_icon"
            ) { playing ->
                Icon(
                    imageVector = if (playing) RunnerIcons.Pause else RunnerIcons.PlayArrow,
                    contentDescription = if (playing) "Пауза" else "Воспроизвести",
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Значок типа файла для тех форматов, которые в чате не проиграть. */
@Composable
fun MediaKindBadge(kind: MediaKind) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (kind) {
                MediaKind.VIDEO -> RunnerIcons.Videocam
                else -> RunnerIcons.GraphicEq
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp)
        )
    }
}

sealed interface MediaThumbnailState {
    data object Loading : MediaThumbnailState
    data class Ready(val bitmap: ImageBitmap) : MediaThumbnailState
    data object Failed : MediaThumbnailState
}

/**
 * Уменьшенная копия картинки с явным состоянием загрузки, чтобы не мигать
 * иконкой битого изображения во время декодирования на IO-потоке.
 */
@Composable
fun rememberThumbnailState(path: String?, targetSize: Int): MediaThumbnailState {
    val state by produceState<MediaThumbnailState>(
        initialValue = if (path == null) MediaThumbnailState.Failed else MediaThumbnailState.Loading,
        path,
        targetSize
    ) {
        value = if (path == null) {
            MediaThumbnailState.Failed
        } else {
            val decoded = withContext(Dispatchers.IO) {
                decodeDownsampled(path, targetSize)
            }
            if (decoded != null) MediaThumbnailState.Ready(decoded) else MediaThumbnailState.Failed
        }
    }
    return state
}

/**
 * Уменьшенная копия картинки. Декодируем с даунсэмплингом: полноразмерный
 * снимок с телефона иначе съест память на первом же списке.
 */
@Composable
fun rememberThumbnail(path: String?, targetSize: Int): ImageBitmap? =
    (rememberThumbnailState(path, targetSize) as? MediaThumbnailState.Ready)?.bitmap

internal fun computeDownsampleSize(width: Int, height: Int, targetSize: Int): Int {
    if (width <= 0 || height <= 0 || targetSize <= 0) return 1
    var sample = 1
    while (width / sample > targetSize || height / sample > targetSize) {
        sample *= 2
    }
    return sample
}

private fun decodeDownsampled(path: String, targetSize: Int): ImageBitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        null
    } else {
        val sample = computeDownsampleSize(bounds.outWidth, bounds.outHeight, targetSize)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, options)?.asImageBitmap()
    }
} catch (_: Exception) {
    null
}

/** Миниатюра в строке списка. Тап открывает картинку целиком. */
@Composable
fun ImageThumbnail(path: String?) {
    var showFull by remember { mutableStateOf(false) }
    val thumbState = rememberThumbnailState(path, targetSize = 160)

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RunnerShapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = thumbState is MediaThumbnailState.Ready) { showFull = true },
        contentAlignment = Alignment.Center
    ) {
        when (thumbState) {
            is MediaThumbnailState.Ready -> {
                Image(
                    bitmap = thumbState.bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            MediaThumbnailState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.8.dp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            MediaThumbnailState.Failed -> {
                Icon(
                    imageVector = RunnerIcons.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    if (showFull && path != null) {
        ImageViewerDialog(path = path, onDismiss = { showFull = false })
    }
}

/** Просмотр картинки целиком. */
@Composable
fun ImageViewerDialog(path: String, onDismiss: () -> Unit) {
    val thumbState = rememberThumbnailState(path, targetSize = 1440)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = RunnerIcons.Close,
                        contentDescription = "Закрыть",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (thumbState) {
                is MediaThumbnailState.Ready -> {
                    Image(
                        bitmap = thumbState.bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RunnerShapes.medium)
                    )
                }
                MediaThumbnailState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                MediaThumbnailState.Failed -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Не удалось открыть изображение",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = TypeScale.body
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = path.substringAfterLast('/'),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = TypeScale.caption,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}
