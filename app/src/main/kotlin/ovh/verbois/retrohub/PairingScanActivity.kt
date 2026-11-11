package ovh.verbois.retrohub

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView

/** Bundled, offline QR decoder. Frames and decoded codes are never saved or logged. */
class PairingScanActivity : CaptureActivity() {
    override fun initializeContent(): DecoratedBarcodeView = super.initializeContent().also { scanner ->
        ViewCompat.setOnApplyWindowInsetsListener(scanner) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(scanner)
    }
}
