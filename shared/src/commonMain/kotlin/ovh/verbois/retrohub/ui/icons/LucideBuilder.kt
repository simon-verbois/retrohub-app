package ovh.verbois.retrohub.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Lucide draws 24×24 strokes of width 2 with round caps; the tint comes from Icon(). */
internal fun lucide(name: String, vararg paths: String): ImageVector {
    val builder =
        ImageVector.Builder(
            name = "lucide.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
    for (data in paths) {
        builder.addPath(
            pathData = PathParser().parsePathString(data).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

internal fun filledIcon(name: String, path: String): ImageVector = ImageVector.Builder(
    name,
    24.dp,
    24.dp,
    24f,
    24f,
).addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black)).build()
