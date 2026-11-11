package ovh.verbois.retrohub.core

/** Versions « année.semaine.numéro » comparées nombre par nombre (docs/native-api.md). */
fun compareVersions(a: String, b: String): Int {
    val left = a.split('.').map { it.toIntOrNull() ?: 0 }
    val right = b.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(left.size, right.size)) {
        val diff = left.getOrElse(i) { 0 } - right.getOrElse(i) { 0 }
        if (diff != 0) return diff
    }
    return 0
}

enum class UpdateLevel { NONE, AVAILABLE, REQUIRED }

fun updateLevel(current: String, latest: String?, minimum: String?): UpdateLevel = when {
    minimum != null && compareVersions(current, minimum) < 0 -> UpdateLevel.REQUIRED
    latest != null && compareVersions(current, latest) < 0 -> UpdateLevel.AVAILABLE
    else -> UpdateLevel.NONE
}
