package ovh.verbois.retrohub.core

/** Case- and accent-insensitive search text, without intermediate character lists. */
fun searchKey(text: String): String = buildString(text.length) {
    for (character in text.lowercase()) {
        val normalized = when (character) {
            'à', 'â', 'ä', 'á' -> 'a'
            'é', 'è', 'ê', 'ë' -> 'e'
            'î', 'ï', 'í' -> 'i'
            'ô', 'ö', 'ó' -> 'o'
            'ù', 'û', 'ü', 'ú' -> 'u'
            'ç' -> 'c'
            else -> character
        }
        if (normalized.isLetterOrDigit() || normalized == ' ') append(normalized)
    }
}
