package com.runner.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.runner.app.R

/**
 * Иконки приложения: Material Symbols Rounded.
 *
 * XML лежат в res/drawable/ic_ms_*.xml, только те, которые реально
 * используются. Так мы не тащим в сборку 4000+ иконок из готовой
 * библиотеки и можем менять отдельную иконку, не трогая остальные.
 *
 * Цвет всегда задаётся через tint у Icon, поэтому в XML стоит чёрная
 * заливка, а фактический цвет приходит из темы.
 */
object RunnerIcons {

    val Add: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_add)
    val ArrowBack: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_arrow_back)
    val ArrowDownward: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_arrow_downward)
    val ArrowUpward: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_arrow_upward)
    val Article: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_article)
    val AutoAwesome: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_auto_awesome)
    val BrokenImage: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_broken_image)
    val BugReport: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_bug_report)
    val CameraAlt: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_camera_alt)
    val ChatBubble: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_chat_bubble)
    val Check: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_check)
    val CheckCircle: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_check_circle)
    val Close: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_close)
    val CloseFilled: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_close_filled)
    val Code: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_code)
    val ContentCopy: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_content_copy)
    val ContentPaste: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_content_paste)
    val Delete: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_delete)
    val Edit: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_edit)
    val Error: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_error)
    val Favorite: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_favorite)
    val Folder: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_folder)
    val FolderOpen: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_folder_open)
    val FolderSpecial: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_folder_special)
    val Functions: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_functions)
    val GraphicEq: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_graphic_eq)
    val Image: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_image)
    val Info: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_info)
    val Key: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_key)
    val KeyboardArrowDown: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_keyboard_arrow_down)
    val KeyboardArrowRight: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_keyboard_arrow_right)
    val Layers: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_layers)
    val Menu: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_menu)
    val Mic: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_mic)
    val MoreVert: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_more_vert)
    val MusicNote: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_music_note)
    val NetworkCheck: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_network_check)
    val OpenInNew: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_open_in_new)
    val Pause: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_pause)
    val PauseCircle: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_pause_circle)
    val PlayArrow: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_play_arrow)
    val PlayCircle: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_play_circle)
    val Psychology: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_psychology)
    val Public: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_public)
    val PushPin: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_push_pin)
    val Refresh: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_refresh)
    val RestartAlt: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_restart_alt)
    val Search: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_search)
    val Settings: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_settings)
    val SmartToy: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_smart_toy)
    val Star: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_star)
    val StarFilled: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_star_filled)
    val Stop: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_stop)
    val StopFilled: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_stop_filled)
    val Storage: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_storage)
    val Table: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_table)
    val Terminal: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_terminal)
    val TouchApp: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_touch_app)
    val Tune: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_tune)
    val Undo: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_undo)
    val Videocam: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_videocam)
    val VideoLibrary: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_video_library)
    val Visibility: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_visibility)
    val VisibilityOff: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_visibility_off)
    val VolumeUp: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_volume_up)
    val VpnKey: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_vpn_key)
    val Warning: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ms_warning)

    /** TON (The Open Network) crystal logo. Остаётся кастомным: это бренд. */
    val Ton: ImageVector by lazy {
        ImageVector.Builder(
            name = "Ton",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12.0f, 3.0f)
                lineTo(3.8f, 7.8f)
                lineTo(12.0f, 21.0f)
                lineTo(20.2f, 7.8f)
                close()
                moveTo(12.0f, 5.2f)
                lineTo(18.0f, 8.7f)
                lineTo(12.0f, 12.2f)
                lineTo(6.0f, 8.7f)
                close()
                moveTo(5.3f, 10.2f)
                lineTo(11.2f, 13.6f)
                lineTo(11.2f, 19.0f)
                close()
                moveTo(12.8f, 19.0f)
                lineTo(12.8f, 13.6f)
                lineTo(18.7f, 10.2f)
                close()
            }
        }.build()
    }

    /** Telegram paper plane logo. Тоже бренд, не Material Symbols. */
    val Telegram: ImageVector by lazy {
        ImageVector.Builder(
            name = "Telegram",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(21.4f, 3.4f)
                curveTo(21.1f, 3.1f, 20.6f, 3.0f, 20.1f, 3.2f)
                lineTo(2.7f, 10.0f)
                curveTo(2.1f, 10.2f, 1.8f, 10.7f, 1.8f, 11.3f)
                curveTo(1.8f, 11.9f, 2.3f, 12.4f, 2.9f, 12.6f)
                lineTo(7.6f, 14.1f)
                lineTo(9.5f, 19.8f)
                curveTo(9.7f, 20.3f, 10.2f, 20.6f, 10.7f, 20.6f)
                curveTo(11.1f, 20.6f, 11.4f, 20.4f, 11.7f, 20.1f)
                lineTo(14.2f, 17.6f)
                lineTo(18.4f, 20.7f)
                curveTo(18.7f, 20.9f, 19.1f, 21.0f, 19.5f, 21.0f)
                curveTo(20.1f, 21.0f, 20.6f, 20.6f, 20.8f, 20.0f)
                lineTo(22.2f, 4.7f)
                curveTo(22.3f, 4.2f, 22.0f, 3.7f, 21.4f, 3.4f)
                close()
                moveTo(18.1f, 19.0f)
                lineTo(13.2f, 15.4f)
                lineTo(17.8f, 7.8f)
                lineTo(9.2f, 13.6f)
                lineTo(4.4f, 12.1f)
                lineTo(19.9f, 6.0f)
                close()
            }
        }.build()
    }
}
