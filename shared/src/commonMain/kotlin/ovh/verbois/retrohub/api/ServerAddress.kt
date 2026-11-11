package ovh.verbois.retrohub.api

/**
 * Normalise ce que le joueur tape : « retrohub.example.org » devient « https://retrohub.example.org ».
 * Le clair n’est accepté que si le joueur l’écrit explicitement (serveur de test local).
 */
object ServerAddress {
    fun normalize(input: String): String? {
        var value = input.trim().trimEnd('/')
        if (value.isEmpty() || value.any { it.isWhitespace() }) return null
        if (!value.contains("://")) value = "https://$value"
        val scheme = value.substringBefore("://").lowercase()
        if (scheme != "https" && scheme != "http") return null
        var rest = value.substringAfter("://")
        // RetroHub est servi à la racine : l’adresse d’une page du site ou de l’API s’y ramène.
        rest = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = rest.substringBefore('/').substringBefore(':')
        if (host.isEmpty() || host.startsWith('.') || host.endsWith('.')) return null
        return "$scheme://$rest"
    }

    /** Une adresse relative du serveur (« /api/v1/… ») devient absolue. */
    fun resolve(server: String, url: String): String = when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("/") -> server.trimEnd('/') + url
        else -> server.trimEnd('/') + "/" + url
    }
}
