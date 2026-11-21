package ovh.verbois.retrohub.ui

import org.junit.Test
import ovh.verbois.retrohub.ui.home.HOME_GAME_TEXT_PADDING
import ovh.verbois.retrohub.ui.home.HOME_PREVIEW_ASPECT
import ovh.verbois.retrohub.ui.home.HomeCrossLayout
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeCrossLayoutTest {
    private val sizes = listOf(480f to 180f, 640f to 240f, 960f to 340f, 1280f to 720f, 1600f to 300f)
    private val layout = HomeCrossLayout.fit(960f, 340f)

    @Test fun previousItemsLeaveTheCategoryBandFree() {
        for ((width, height) in sizes) {
            val fitted = HomeCrossLayout.fit(width, height)
            assertTrue(fitted.center(-1) + fitted.itemHeight / 2 < fitted.categoryCenter - fitted.categoryHeight / 2)
            assertTrue(fitted.center(0) - fitted.selectedHeight / 2 > fitted.categoryCenter + fitted.categoryHeight / 2)
            assertTrue(fitted.center(1) - fitted.itemHeight / 2 > fitted.center(0) + fitted.selectedHeight / 2)
            assertTrue(fitted.center(-2) < fitted.center(-1))
        }
    }

    @Test fun aDistantSelectionDoesNotIncreaseTheRenderedWindow() {
        for ((width, height) in sizes) {
            val fitted = HomeCrossLayout.fit(width, height)
            val indices = fitted.visibleIndices(5000, 10000)
            assertTrue(5000 in indices)
            assertTrue(indices.count() < 30)
            assertEquals(fitted.visibleIndices(50, 100).count(), indices.count())
        }
    }

    @Test fun libraryEdgesNeverCreateInvalidIndices() {
        assertTrue(layout.visibleIndices(0, 0).isEmpty())
        assertEquals(0..0, layout.visibleIndices(0, 1))
        assertEquals(0, layout.visibleIndices(0, 10000).first)
        assertEquals(9999, layout.visibleIndices(9999, 10000).last)
    }

    @Test fun largeTextKeepsTheActiveCoverAndRowInsideAShortViewport() {
        for ((width, height) in sizes) {
            val fitted = HomeCrossLayout.fit(width, height, categoryLabelHeight = 52f, selectedTextHeight = 164f, minTitleWidth = 280f)
            assertTrue(fitted.center(0) + fitted.selectedHeight / 2 <= height + 0.001f)
            assertTrue(fitted.center(0) - fitted.selectedHeight / 2 > fitted.categoryCenter + fitted.categoryHeight / 2)
            assertTrue(fitted.coverBoxSize <= fitted.selectedHeight)
            assertTrue(fitted.rowStart >= 0f)
            assertTrue(fitted.rowStart + fitted.laneWidth <= width)
        }
    }

    @Test fun tabletsGrowTheSceneWithoutMakingCoversUnbounded() {
        val phone = HomeCrossLayout.fit(960f, 340f)
        val tablet = HomeCrossLayout.fit(1280f, 720f)
        val large = HomeCrossLayout.fit(1920f, 1080f)
        assertTrue(tablet.coverSize > phone.coverSize)
        assertTrue(tablet.categoryIconSize > phone.categoryIconSize)
        assertTrue(tablet.anchor > phone.anchor)
        assertEquals(tablet.coverSize, large.coverSize)
        assertTrue(tablet.anchor + tablet.coverBoxSize / 2 < tablet.width * 0.30f)
        assertTrue(tablet.itemHeight >= 48f)
    }

    @Test fun previewsLeaveRoomForTextAndFitBothWindowDimensions() {
        for ((width, height) in sizes) {
            for (titleWidth in listOf(175f, 280f)) {
                val fitted = HomeCrossLayout.fit(width, height, minTitleWidth = titleWidth)
                assertTrue(fitted.previewStart + fitted.previewWidth <= width)
                assertTrue(fitted.previewWidth / HOME_PREVIEW_ASPECT <= height * 0.86f)
                assertTrue(fitted.rowStart + fitted.laneWidth < fitted.previewStart)
                if (fitted.previewWidth > 0f) assertTrue(fitted.laneWidth - fitted.coverBoxSize - HOME_GAME_TEXT_PADDING * 2 >= titleWidth - 0.001f)
            }
        }
    }

    @Test fun theComposedWindowReachesBothViewportEdges() {
        for ((width, height) in sizes) {
            val fitted = HomeCrossLayout.fit(width, height)
            val indices = fitted.visibleIndices(5000, 10000)
            assertTrue(fitted.center(indices.first - 5000) - fitted.itemHeight / 2 <= 0f)
            assertTrue(fitted.center(indices.last - 5000) + fitted.itemHeight / 2 >= height)
        }
    }

    @Test fun gamesWithoutArtworkGiveTheTextTheRemainingWidth() {
        val fitted = HomeCrossLayout.fit(640f, 260f, previewAvailable = false)
        assertEquals(0f, fitted.previewWidth)
        assertEquals(fitted.width - fitted.inset - fitted.gap, fitted.rowStart + fitted.laneWidth, 0.001f)
        assertTrue(fitted.categoryLabelWidth > fitted.categoryPitch * 2)
    }

    @Test fun edgeFadesAreBoundedAndLeaveTheSelectionVisible() {
        assertEquals(0f, layout.horizontalEdgeAlpha(-10f))
        assertEquals(0f, layout.verticalEdgeAlpha(layout.height + 10f))
        assertEquals(1f, layout.horizontalEdgeAlpha(layout.anchor))
        assertEquals(1f, layout.verticalEdgeAlpha(layout.selectedCenter))
        assertTrue(layout.horizontalEdgeAlpha(4f) in 0f..0.5f)
        assertTrue(layout.verticalEdgeAlpha(4f) in 0f..0.5f)
    }
}
