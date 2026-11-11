package ovh.verbois.retrohub.core

/** Code parental : seul son empreinte est gardée sur l’appareil. */
object Pin {
    fun valid(pin: String): Boolean = pin.length in 4..8 && pin.all { it.isDigit() }

    fun hash(pin: String): String = sha256("retrohub-pin:$pin".encodeToByteArray())

    fun matches(pin: String, hash: String): Boolean = hash.isNotEmpty() && hash(pin) == hash
}
