package ovh.verbois.retrohub.account

import android.Manifest
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.verbois.retrohub.PairingScanActivity
import java.io.File

class PairingQrDecoderTest {
    @Test
    fun cameraPreviewCanBeOpenedAndDismissed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        device.setOrientationNatural()
        val intent = ScanOptions().setCaptureActivity(PairingScanActivity::class.java)
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false).setOrientationLocked(false)
            .setPrompt(context.getString(ovh.verbois.retrohub.R.string.qr_camera_prompt)).createScanIntent(context)
        try {
            ActivityScenario.launch<PairingScanActivity>(intent).use { scenario ->
                val deadline = SystemClock.elapsedRealtime() + 20_000
                var ready = false
                while (!ready && SystemClock.elapsedRealtime() < deadline) {
                    scenario.onActivity {
                        ready =
                            it.findViewById<com.journeyapps.barcodescanner.DecoratedBarcodeView>(
                                com.google.zxing.client.android.R.id.zxing_barcode_scanner,
                            ).barcodeView.isPreviewActive
                    }
                    if (!ready) SystemClock.sleep(100)
                }
                assertTrue("Camera preview did not start", ready)
                SystemClock.sleep(500)
                val dir = File(context.getExternalFilesDir(null), "test-screens").apply { mkdirs() }
                File(dir, "qr-camera-portrait.png").outputStream().use { stream ->
                    check(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
                }
                device.pressBack()
            }
        } finally {
            device.setOrientationLandscape()
        }
    }

    @Test
    fun bundledDecoderReadsPairingQrFromAnOffCenterCameraFrame() {
        val code = "ABCD-EFGH"
        val qr = QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, 256, 256)
        val width = 720
        val height = 480
        val frame = IntArray(width * height) { -1 }
        for (y in 0 until qr.height) {
            for (x in 0 until qr.width) {
                frame[(y + 80) * width + x + 310] = if (qr[x, y]) 0xFF000000.toInt() else -1
            }
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, frame)))
        val result = MultiFormatReader().decode(bitmap, mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        assertEquals(BarcodeFormat.QR_CODE, result.barcodeFormat)
        assertEquals(code, pairingCodeFromQr(result.text))
    }
}
