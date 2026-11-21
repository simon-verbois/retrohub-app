package ovh.verbois.retrohub.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Original monochrome cartridge silhouettes matching the bundled console icon weight. */
internal object XmbIcons {
    val Catalogue: ImageVector by lazy {
        ImageVector.Builder("Catalogue", 40.dp, 40.dp, 64f, 64f).apply {
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
                moveTo(10f, 10f)
                lineTo(42f, 10f)
                lineTo(42f, 15f)
                lineTo(15f, 15f)
                lineTo(15f, 47f)
                lineTo(10f, 47f)
                close()
                moveTo(18f, 18f)
                lineTo(50f, 18f)
                lineTo(50f, 23f)
                lineTo(23f, 23f)
                lineTo(23f, 55f)
                lineTo(18f, 55f)
                close()
                moveTo(27f, 27f)
                lineTo(57f, 27f)
                lineTo(57f, 57f)
                lineTo(27f, 57f)
                close()
                moveTo(33f, 32f)
                lineTo(51f, 32f)
                lineTo(51f, 43f)
                lineTo(33f, 43f)
                close()
                moveTo(33f, 49f)
                lineTo(45f, 49f)
                lineTo(45f, 52f)
                lineTo(33f, 52f)
                close()
            }
        }.build()
    }
}
