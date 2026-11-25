package com.phantom.checker.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * #498 桌面端單一向量圖示來源（零額外依賴、支援高 DPI 與 DesktopThemeTokens / accentPreset 動態著色）。
 */
object DesktopIcons {

    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White), pathBuilder = block)
        }.build()

    val Messages: ImageVector by lazy {
        icon("Messages") {
            moveTo(20f, 2f)
            horizontalLineTo(4f)
            curveTo(2.9f, 2f, 2f, 2.9f, 2f, 4f)
            verticalLineTo(22f)
            lineTo(6f, 18f)
            horizontalLineTo(20f)
            curveTo(21.1f, 18f, 22f, 17.1f, 22f, 16f)
            verticalLineTo(4f)
            curveTo(22f, 2.9f, 21.1f, 2f, 20f, 2f)
            close()
            moveTo(20f, 16f)
            horizontalLineTo(5.17f)
            lineTo(4f, 17.17f)
            verticalLineTo(4f)
            horizontalLineTo(20f)
            verticalLineTo(16f)
            close()
        }
    }

    val Stats: ImageVector by lazy {
        icon("Stats") {
            moveTo(5f, 9.2f)
            horizontalLineTo(8f)
            verticalLineTo(19f)
            horizontalLineTo(5f)
            close()
            moveTo(10.6f, 5f)
            horizontalLineTo(13.4f)
            verticalLineTo(19f)
            horizontalLineTo(10.6f)
            close()
            moveTo(16.2f, 13f)
            horizontalLineTo(19f)
            verticalLineTo(19f)
            horizontalLineTo(16.2f)
            close()
        }
    }

    val Todos: ImageVector by lazy {
        icon("Todos") {
            moveTo(19f, 3f)
            horizontalLineTo(5f)
            curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
            verticalLineTo(19f)
            curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
            horizontalLineTo(19f)
            curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
            verticalLineTo(5f)
            curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
            close()
            moveTo(10f, 17f)
            lineTo(5f, 12f)
            lineTo(6.41f, 10.59f)
            lineTo(10f, 14.17f)
            lineTo(17.59f, 6.58f)
            lineTo(19f, 8f)
            lineTo(10f, 17f)
            close()
        }
    }

    val Chat: ImageVector by lazy {
        icon("Chat") {
            moveTo(12f, 2f)
            lineTo(14.5f, 8.5f)
            lineTo(21f, 11f)
            lineTo(14.5f, 13.5f)
            lineTo(12f, 20f)
            lineTo(9.5f, 13.5f)
            lineTo(3f, 11f)
            lineTo(9.5f, 8.5f)
            close()
        }
    }

    val Rules: ImageVector by lazy {
        icon("Rules") {
            moveTo(12f, 1f)
            lineTo(3f, 5f)
            verticalLineTo(11f)
            curveTo(3f, 16.55f, 6.84f, 21.74f, 12f, 23f)
            curveTo(17.16f, 21.74f, 21f, 16.55f, 21f, 11f)
            verticalLineTo(5f)
            lineTo(12f, 1f)
            close()
            moveTo(12f, 11.99f)
            horizontalLineTo(19f)
            curveTo(18.47f, 16.11f, 15.72f, 19.78f, 12f, 20.93f)
            verticalLineTo(12f)
            horizontalLineTo(5f)
            verticalLineTo(6.3f)
            lineTo(12f, 3.19f)
            verticalLineTo(11.99f)
            close()
        }
    }

    val Settings: ImageVector by lazy {
        icon("Settings") {
            moveTo(19.14f, 12.94f)
            curveTo(19.18f, 12.64f, 19.2f, 12.33f, 19.2f, 12f)
            curveTo(19.2f, 11.68f, 19.18f, 11.36f, 19.13f, 11.06f)
            lineTo(21.16f, 9.48f)
            curveTo(21.34f, 9.34f, 21.39f, 9.07f, 21.28f, 8.87f)
            lineTo(19.36f, 5.55f)
            curveTo(19.24f, 5.33f, 18.99f, 5.26f, 18.77f, 5.33f)
            lineTo(16.38f, 6.29f)
            curveTo(15.88f, 5.91f, 15.35f, 5.59f, 14.76f, 5.35f)
            lineTo(14.4f, 2.81f)
            curveTo(14.36f, 2.57f, 14.16f, 2.4f, 13.92f, 2.4f)
            horizontalLineTo(10.08f)
            curveTo(9.84f, 2.4f, 9.65f, 2.57f, 9.61f, 2.81f)
            lineTo(9.25f, 5.35f)
            curveTo(8.66f, 5.59f, 8.12f, 5.92f, 7.63f, 6.29f)
            lineTo(5.24f, 5.33f)
            curveTo(5.02f, 5.25f, 4.77f, 5.33f, 4.65f, 5.55f)
            lineTo(2.74f, 8.87f)
            curveTo(2.62f, 9.08f, 2.66f, 9.34f, 2.86f, 9.48f)
            lineTo(4.89f, 11.06f)
            curveTo(4.84f, 11.36f, 4.8f, 11.69f, 4.8f, 12f)
            curveTo(4.8f, 12.31f, 4.82f, 12.64f, 4.87f, 12.94f)
            lineTo(2.84f, 14.52f)
            curveTo(2.66f, 14.66f, 2.61f, 14.93f, 2.72f, 15.13f)
            lineTo(4.64f, 18.45f)
            curveTo(4.76f, 18.67f, 5.01f, 18.74f, 5.23f, 18.67f)
            lineTo(7.62f, 17.71f)
            curveTo(8.12f, 18.09f, 8.65f, 18.41f, 9.24f, 18.65f)
            lineTo(9.6f, 21.19f)
            curveTo(9.65f, 21.43f, 9.84f, 21.6f, 10.08f, 21.6f)
            horizontalLineTo(13.92f)
            curveTo(14.16f, 21.6f, 14.36f, 21.43f, 14.39f, 21.19f)
            lineTo(14.75f, 18.65f)
            curveTo(15.34f, 18.41f, 15.88f, 18.09f, 16.37f, 17.71f)
            lineTo(18.76f, 18.67f)
            curveTo(18.98f, 18.75f, 19.23f, 18.67f, 19.35f, 18.45f)
            lineTo(21.27f, 15.13f)
            curveTo(21.39f, 14.91f, 21.34f, 14.66f, 21.15f, 14.52f)
            lineTo(19.14f, 12.94f)
            close()
            moveTo(12f, 15.6f)
            curveTo(10.02f, 15.6f, 8.4f, 13.98f, 8.4f, 12f)
            curveTo(8.4f, 10.02f, 10.02f, 8.4f, 12f, 8.4f)
            curveTo(13.98f, 8.4f, 15.6f, 10.02f, 15.6f, 12f)
            curveTo(15.6f, 13.98f, 13.98f, 15.6f, 12f, 15.6f)
            close()
        }
    }

    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            moveTo(17.65f, 6.35f)
            curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
            curveTo(7.58f, 4f, 4.01f, 7.58f, 4.01f, 12f)
            curveTo(4.01f, 16.42f, 7.58f, 20f, 12f, 20f)
            curveTo(15.73f, 20f, 18.84f, 17.45f, 19.73f, 14f)
            horizontalLineTo(17.65f)
            curveTo(16.83f, 16.33f, 14.61f, 18f, 12f, 18f)
            curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
            curveTo(6f, 8.69f, 8.69f, 6f, 12f, 6f)
            curveTo(13.66f, 6f, 15.14f, 6.69f, 16.22f, 7.78f)
            lineTo(13f, 11f)
            horizontalLineTo(20f)
            verticalLineTo(4f)
            lineTo(17.65f, 6.35f)
            close()
        }
    }

    val MarkRead: ImageVector by lazy {
        icon("MarkRead") {
            moveTo(18f, 7f)
            lineTo(16.59f, 5.59f)
            lineTo(10.25f, 11.93f)
            lineTo(11.66f, 13.34f)
            lineTo(18f, 7f)
            close()
            moveTo(22.24f, 5.59f)
            lineTo(11.66f, 16.17f)
            lineTo(7.48f, 12f)
            lineTo(6.07f, 13.41f)
            lineTo(11.66f, 19f)
            lineTo(23.66f, 7f)
            lineTo(22.24f, 5.59f)
            close()
            moveTo(0.41f, 13.41f)
            lineTo(6f, 19f)
            lineTo(7.41f, 17.59f)
            lineTo(1.83f, 12f)
            lineTo(0.41f, 13.41f)
            close()
        }
    }

    val Search: ImageVector by lazy {
        icon("Search") {
            moveTo(15.5f, 14f)
            horizontalLineTo(14.71f)
            lineTo(14.43f, 13.73f)
            curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f)
            curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f)
            curveTo(5.91f, 3f, 3f, 5.91f, 3f, 9.5f)
            curveTo(3f, 13.09f, 5.91f, 16f, 9.5f, 16f)
            curveTo(11.11f, 16f, 12.59f, 15.41f, 13.73f, 14.43f)
            lineTo(14f, 14.71f)
            verticalLineTo(15.5f)
            lineTo(19f, 20.49f)
            lineTo(20.49f, 19f)
            lineTo(15.5f, 14f)
            close()
            moveTo(9.5f, 14f)
            curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f)
            curveTo(5f, 7.01f, 7.01f, 5f, 9.5f, 5f)
            curveTo(11.99f, 5f, 14f, 7.01f, 14f, 9.5f)
            curveTo(14f, 11.99f, 11.99f, 14f, 9.5f, 14f)
            close()
        }
    }

    val Star: ImageVector by lazy {
        icon("Star") {
            moveTo(12f, 17.27f)
            lineTo(18.18f, 21f)
            lineTo(16.54f, 13.97f)
            lineTo(22f, 9.24f)
            lineTo(14.81f, 8.63f)
            lineTo(12f, 2f)
            lineTo(9.19f, 8.63f)
            lineTo(2f, 9.24f)
            lineTo(7.46f, 13.97f)
            lineTo(5.82f, 21f)
            close()
        }
    }

    val Warning: ImageVector by lazy {
        icon("Warning") {
            moveTo(1f, 21f)
            horizontalLineTo(23f)
            lineTo(12f, 2f)
            lineTo(1f, 21f)
            close()
            moveTo(13f, 18f)
            horizontalLineTo(11f)
            verticalLineTo(16f)
            horizontalLineTo(13f)
            verticalLineTo(18f)
            close()
            moveTo(13f, 14f)
            horizontalLineTo(11f)
            verticalLineTo(10f)
            horizontalLineTo(13f)
            verticalLineTo(14f)
            close()
        }
    }

    val Bell: ImageVector by lazy {
        icon("Bell") {
            moveTo(12f, 22f)
            curveTo(13.1f, 22f, 14f, 21.1f, 14f, 20f)
            horizontalLineTo(10f)
            curveTo(10f, 21.1f, 10.9f, 22f, 12f, 22f)
            close()
            moveTo(18f, 16f)
            verticalLineTo(11f)
            curveTo(18f, 7.93f, 16.36f, 5.36f, 13.5f, 4.68f)
            verticalLineTo(4f)
            curveTo(13.5f, 3.17f, 12.83f, 2.5f, 12f, 2.5f)
            curveTo(11.17f, 2.5f, 10.5f, 3.17f, 10.5f, 4f)
            verticalLineTo(4.68f)
            curveTo(7.63f, 5.36f, 6f, 7.92f, 6f, 11f)
            verticalLineTo(16f)
            lineTo(4f, 18f)
            verticalLineTo(19f)
            horizontalLineTo(20f)
            verticalLineTo(18f)
            lineTo(18f, 16f)
            close()
        }
    }

    val Note: ImageVector by lazy {
        icon("Note") {
            moveTo(19f, 3f)
            horizontalLineTo(5f)
            curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
            verticalLineTo(19f)
            curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
            horizontalLineTo(15f)
            lineTo(21f, 15f)
            verticalLineTo(5f)
            curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
            close()
            moveTo(14f, 19.5f)
            verticalLineTo(14f)
            horizontalLineTo(19.5f)
            lineTo(14f, 19.5f)
            close()
        }
    }

    val Tag: ImageVector by lazy {
        icon("Tag") {
            moveTo(21.41f, 11.58f)
            lineTo(12.41f, 2.58f)
            curveTo(12.05f, 2.22f, 11.55f, 2f, 11f, 2f)
            horizontalLineTo(4f)
            curveTo(2.9f, 2f, 2f, 2.9f, 2f, 4f)
            verticalLineTo(11f)
            curveTo(2f, 11.55f, 2.22f, 12.05f, 2.59f, 12.42f)
            lineTo(11.59f, 21.42f)
            curveTo(11.95f, 21.78f, 12.45f, 22f, 13f, 22f)
            curveTo(13.55f, 22f, 14.05f, 21.78f, 14.41f, 21.41f)
            lineTo(21.41f, 14.41f)
            curveTo(21.78f, 14.05f, 22f, 13.55f, 22f, 13f)
            curveTo(22f, 12.45f, 21.77f, 11.94f, 21.41f, 11.58f)
            close()
            moveTo(5.5f, 7f)
            curveTo(4.67f, 7f, 4f, 6.33f, 4f, 5.5f)
            curveTo(4f, 4.67f, 4.67f, 4f, 5.5f, 4f)
            curveTo(6.33f, 4f, 7f, 4.67f, 7f, 5.5f)
            curveTo(7f, 6.33f, 6.33f, 7f, 5.5f, 7f)
            close()
        }
    }

    val Group: ImageVector by lazy {
        icon("Group") {
            moveTo(16f, 11f)
            curveTo(17.66f, 11f, 18.99f, 9.66f, 18.99f, 8f)
            curveTo(18.99f, 6.34f, 17.66f, 5f, 16f, 5f)
            curveTo(14.34f, 5f, 13f, 6.34f, 13f, 8f)
            curveTo(13f, 9.66f, 14.34f, 11f, 16f, 11f)
            close()
            moveTo(8f, 11f)
            curveTo(9.66f, 11f, 10.99f, 9.66f, 10.99f, 8f)
            curveTo(10.99f, 6.34f, 9.66f, 5f, 8f, 5f)
            curveTo(6.34f, 5f, 5f, 6.34f, 5f, 8f)
            curveTo(5f, 9.66f, 6.34f, 11f, 8f, 11f)
            close()
            moveTo(8f, 13f)
            curveTo(5.67f, 13f, 1f, 14.17f, 1f, 16.5f)
            verticalLineTo(19f)
            horizontalLineTo(15f)
            verticalLineTo(16.5f)
            curveTo(15f, 14.17f, 10.33f, 13f, 8f, 13f)
            close()
            moveTo(16f, 13f)
            curveTo(15.71f, 13f, 15.38f, 13.02f, 15.03f, 13.05f)
            curveTo(16.19f, 13.89f, 17f, 15.02f, 17f, 16.5f)
            verticalLineTo(19f)
            horizontalLineTo(23f)
            verticalLineTo(16.5f)
            curveTo(23f, 14.17f, 18.33f, 13f, 16f, 13f)
            close()
        }
    }

    val Send: ImageVector by lazy {
        icon("Send") {
            moveTo(2.01f, 21f)
            lineTo(23f, 12f)
            lineTo(2.01f, 3f)
            lineTo(2f, 10f)
            lineTo(17f, 12f)
            lineTo(2f, 14f)
            close()
        }
    }

    val Pin: ImageVector by lazy {
        icon("Pin") {
            moveTo(16f, 12f)
            verticalLineTo(4f)
            horizontalLineTo(17f)
            verticalLineTo(2f)
            horizontalLineTo(7f)
            verticalLineTo(4f)
            horizontalLineTo(8f)
            verticalLineTo(12f)
            lineTo(6f, 14f)
            verticalLineTo(16f)
            horizontalLineTo(11.2f)
            verticalLineTo(22f)
            horizontalLineTo(12.8f)
            verticalLineTo(16f)
            horizontalLineTo(18f)
            verticalLineTo(14f)
            lineTo(16f, 12f)
            close()
        }
    }

    val Link: ImageVector by lazy {
        icon("Link") {
            moveTo(3.9f, 12f)
            curveTo(3.9f, 10.29f, 5.29f, 8.9f, 7f, 8.9f)
            horizontalLineTo(11f)
            verticalLineTo(7f)
            horizontalLineTo(7f)
            curveTo(4.24f, 7f, 2f, 9.24f, 2f, 12f)
            curveTo(2f, 14.76f, 4.24f, 17f, 7f, 17f)
            horizontalLineTo(11f)
            verticalLineTo(15.1f)
            horizontalLineTo(7f)
            curveTo(5.29f, 15.1f, 3.9f, 13.71f, 3.9f, 12f)
            close()
            moveTo(8f, 13f)
            horizontalLineTo(16f)
            verticalLineTo(11f)
            horizontalLineTo(8f)
            verticalLineTo(13f)
            close()
            moveTo(17f, 7f)
            horizontalLineTo(13f)
            verticalLineTo(8.9f)
            horizontalLineTo(17f)
            curveTo(18.71f, 8.9f, 20.1f, 10.29f, 20.1f, 12f)
            curveTo(20.1f, 13.71f, 18.71f, 15.1f, 17f, 15.1f)
            horizontalLineTo(13f)
            verticalLineTo(17f)
            horizontalLineTo(17f)
            curveTo(19.76f, 17f, 22f, 14.76f, 22f, 12f)
            curveTo(22f, 9.24f, 19.76f, 7f, 17f, 7f)
            close()
        }
    }
}
