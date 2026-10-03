package com.runner.app.ui.components

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

/**
 * Абсолютный путь к файлу. В FileEntry папка хранится без ведущего слэша,
 * потому что парсер его срезает для читаемости.
 */
val FileEntry.absolutePath: String?
    get() {
        val folderPart = folder?.trim('/').orEmpty()
        return when {
            folderPart.isNotEmpty() -> "/$folderPart/$name"
            name.startsWith("/") -> name
            else -> null
        }
    }

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

    var lastError by mutableStateOf<String?>(null)
        private set

    fun toggle(path: String) {
        if (activePath == path && player != null) {
            if (isPlaying) pause() else resume()
            return
        }
        release()
        try {
            val created = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener {
                    isPlaying = false
                    activePath = null
                }
                setOnErrorListener { _, _, _ ->
                    lastError = "Не удалось воспроизвести файл"
                    isPlaying = false
                    activePath = null
                    true
                }
                prepare()
                start()
            }
            player = created
            activePath = path
            isPlaying = true
            lastError = null
        } catch (e: Exception) {
            lastError = e.localizedMessage ?: "Не удалось открыть файл"
            release()
        }
    }

    private fun pause() {
        runCatching { player?.pause() }
        isPlaying = false
    }

    private fun resume() {
        runCatching { player?.start() }
        isPlaying = true
    }

    fun stop() = release()

    private fun release() {
        player?.let { runCatching { it.release() } }
        player = null
        isPlaying = false
        activePath = null
    }
}

/** Кнопка воспроизведения слева от строки файла. */
@Composable
fun AudioPlayButton(path: String?, enabled: Boolean) {
    val context = LocalContext.current
    val isActive = path != null && AudioPreviewPlayer.activePath == path
    val isPlaying = isActive && AudioPreviewPlayer.isPlaying

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(
                if (isActive) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
            )
            .clickable(enabled = enabled && path != null) {
                if (path != null) {
                    AudioPreviewPlayer.toggle(path)
                    // Иначе на неудаче кнопка просто молчит и выглядит сломанной
                    AudioPreviewPlayer.lastError?.let { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
            contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
            tint = when {
                !enabled || path == null -> MaterialTheme.colorScheme.outline
                isActive -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(18.dp)
        )
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
                MediaKind.VIDEO -> Icons.Outlined.Videocam
                else -> Icons.Outlined.GraphicEq
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp)
        )
    }
}

/**
 * Уменьшенная копия картинки. Декодируем с даунсэмплингом: полноразмерный
 * снимок с телефона иначе съест память на первом же списке.
 */
@Composable
fun rememberThumbnail(path: String?, targetSize: Int): ImageBitmap? {
    val state by produceState<ImageBitmap?>(initialValue = null, path, targetSize) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            decodeDownsampled(path, targetSize)
        }
    }
    return state
}

private fun decodeDownsampled(path: String, targetSize: Int): ImageBitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        null
    } else {
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetSize &&
            bounds.outHeight / (sample * 2) >= targetSize
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, options)?.asImageBitmap()
    }
} catch (e: Exception) {
    null
}

/** Миниатюра в строке списка. Тап открывает картинку целиком. */
@Composable
fun ImageThumbnail(path: String?) {
    var showFull by remember { mutableStateOf(false) }
    val bitmap = rememberThumbnail(path, targetSize = 160)

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = bitmap != null) { showFull = true },
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    if (showFull && path != null) {
        ImageViewerDialog(path = path, onDismiss = { showFull = false })
    }
}

/** Просмотр картинки целиком. */
@Composable
fun ImageViewerDialog(path: String, onDismiss: () -> Unit) {
    val bitmap = rememberThumbnail(path, targetSize = 1440)

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
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Закрыть",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Text(
                    text = "Не удалось открыть изображение",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = path.substringAfterLast('/'),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}
