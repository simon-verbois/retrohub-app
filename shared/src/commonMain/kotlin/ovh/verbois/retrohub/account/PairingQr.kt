package ovh.verbois.retrohub.account

/** The server's one-use pairing code, never a password, bearer token or arbitrary URL. */
fun pairingCodeFromQr(payload: String): String? {
    if (payload.length > 32) return null
    val code = payload.trim().uppercase().replace("-", "").replace(" ", "")
    if (code.length != 8 || code.any { it !in "ABCDEFGHJKMNPQRSTUVWXYZ23456789" }) return null
    return code.take(4) + "-" + code.drop(4)
}

class QrCameraException : Exception()
