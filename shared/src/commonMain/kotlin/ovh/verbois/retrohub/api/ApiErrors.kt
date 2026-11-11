package ovh.verbois.retrohub.api

/**
 * Erreurs de l’API, déjà traduites en situations que l’application sait traiter.
 * Les messages sont ceux du serveur (en français, affichables) ou des textes de l’app.
 */
sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Jeton absent, invalide ou révoqué, ou identifiants faux. */
    class Unauthorized(message: String) : ApiException(message)

    /** Serveur en maintenance (503) : réessayer après [retryAfterSeconds]. */
    class Maintenance(val retryAfterSeconds: Long) : ApiException("Le serveur est en maintenance. La synchronisation reprendra toute seule.")

    /** Conflit de révision de sauvegarde (409 avec current_revision). */
    class SaveConflict(val currentRevision: Int, message: String) : ApiException(message)

    /** Réseau indisponible, serveur injoignable ou réponse coupée. */
    class Offline(cause: Throwable? = null) :
        ApiException("Serveur injoignable. Vérifiez la connexion ; vos parties restent jouables hors ligne.", cause)

    /** Serveur trouvé, mais d’une version d’API inconnue, ou qui n’est pas RetroHub. */
    class UnsupportedServer(message: String) : ApiException(message)

    /** Toute autre réponse en erreur, avec le message du serveur. */
    class Http(val status: Int, message: String, val retryAfterSeconds: Long? = null) : ApiException(message)
}

val ApiException.isNotFound: Boolean get() = this is ApiException.Http && status == 404
