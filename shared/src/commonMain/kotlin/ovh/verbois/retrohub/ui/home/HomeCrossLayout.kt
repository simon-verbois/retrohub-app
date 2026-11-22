package ovh.verbois.retrohub.ui.home

import kotlin.math.ceil

/** Logical positions in dp. The category band stays free while previous items pass above it. */
internal class HomeCrossLayout private constructor(
    val width: Float,
    val height: Float,
    val categoryCenter: Float,
    val categoryHeight: Float,
    val selectedHeight: Float,
    val itemHeight: Float,
    val pitch: Float,
    val gap: Float,
    val coverBoxSize: Float,
    val categoryBoxSize: Float,
    val categoryPitch: Float,
    private val minTitleWidth: Float,
    private val previewAvailable: Boolean,
) {
    val selectedCenter = categoryCenter + categoryHeight / 2 + selectedHeight / 2 + gap
    val coverSize = coverBoxSize * 0.86f
    val categoryIconSize = categoryBoxSize * 0.72f

    // Keep the selected cover exposed beside the widest modal tool panel.
    val anchor = (width * 0.22f).coerceIn(coverBoxSize / 2 + gap, (width * 0.30f - coverBoxSize / 2 - gap).coerceAtLeast(coverBoxSize / 2 + gap))
    val rowStart = anchor - coverBoxSize / 2
    val inset = (width * 0.025f).coerceIn(12f, 40f)
    val categoryLabelWidth = minOf(width - inset * 2, categoryPitch * 2.6f, (anchor - inset) * 2).coerceAtLeast(0f)
    val previewWidth = minOf(
        width * 0.44f,
        height * 0.86f * HOME_PREVIEW_ASPECT,
        (width - inset - anchor - coverBoxSize / 2 - minTitleWidth - HOME_GAME_TEXT_PADDING * 2 - gap).coerceAtLeast(0f),
    ).takeIf { previewAvailable && it >= 120f } ?: 0f
    val previewStart = width - inset - previewWidth
    val laneWidth = (previewStart - gap - rowStart).coerceAtLeast(coverBoxSize)

    fun horizontalEdgeAlpha(center: Float): Float = edgeAlpha(center, width, (width * 0.04f).coerceIn(12f, 40f))

    // Lower the whole cover opacity as it recedes beneath the header mask.
    fun verticalEdgeAlpha(center: Float): Float {
        val fade = (height * 0.16f).coerceIn(32f, 72f)
        return edgeAlpha(center, height, fade)
    }

    fun center(relativeIndex: Int): Float = when {
        relativeIndex < 0 -> categoryCenter - categoryHeight / 2 - itemHeight / 2 - gap + (relativeIndex + 1) * pitch
        relativeIndex > 0 -> selectedCenter + selectedHeight / 2 + itemHeight / 2 + gap + (relativeIndex - 1) * pitch
        else -> selectedCenter
    }

    /** Include one extra item on either edge for interrupted transitions; never scan the library. */
    fun visibleIndices(selectedIndex: Int, count: Int): IntRange {
        if (count == 0) return IntRange.EMPTY
        val above = ceil(categoryCenter / pitch).toInt() + 1
        val below = ceil((height - selectedCenter).coerceAtLeast(0f) / pitch).toInt() + 2
        return (selectedIndex - above).coerceAtLeast(0)..(selectedIndex + below).coerceAtMost(count - 1)
    }

    companion object {
        /** Large text and the keyboard may reduce the available height; prioritize the selection. */
        fun fit(
            width: Float,
            height: Float,
            categoryLabelHeight: Float = 32f,
            selectedTextHeight: Float = 102f,
            minTitleWidth: Float = 175f,
            previewAvailable: Boolean = true,
        ): HomeCrossLayout {
            val gap = (height * 0.022f).coerceIn(4f, 12f)
            val categoryBox = (height * 0.15f).coerceIn(48f, 64f)
            val categoryHeight = categoryBox + categoryLabelHeight + gap
            val preferredCoverBox = (height * 0.27f).coerceIn(64f, 132f)
            val fittedHeight = maxOf(preferredCoverBox, selectedTextHeight)
                .coerceAtMost((height - categoryHeight - gap).coerceAtLeast(48f))
            val coverBox = preferredCoverBox.coerceAtMost(fittedHeight)
            val itemHeight = (coverBox * 0.58f).coerceAtLeast(48f)
            val headerClearance = minOf(48f, (height - categoryHeight - fittedHeight - gap).coerceAtLeast(0f))
            val minimum = categoryHeight / 2 + headerClearance
            val maximum = (height - categoryHeight / 2 - fittedHeight - gap).coerceAtLeast(minimum)
            return HomeCrossLayout(
                width = width,
                height = height,
                categoryCenter = (height * 0.12f + 40f).coerceIn(minimum, maximum),
                categoryHeight = categoryHeight,
                selectedHeight = fittedHeight,
                itemHeight = itemHeight,
                pitch = itemHeight + gap,
                gap = gap,
                coverBoxSize = coverBox,
                categoryBoxSize = categoryBox,
                categoryPitch = (width * 0.115f).coerceIn(88f, 136f),
                minTitleWidth = minTitleWidth,
                previewAvailable = previewAvailable,
            )
        }

        private fun edgeAlpha(center: Float, extent: Float, fade: Float): Float = minOf(center / fade, (extent - center) / fade).coerceIn(0f, 1f)
    }
}

internal const val HOME_PREVIEW_ASPECT = 1.6f
internal const val HOME_GAME_TEXT_PADDING = 12f
