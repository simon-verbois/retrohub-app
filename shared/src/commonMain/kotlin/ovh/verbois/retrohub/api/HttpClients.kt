package ovh.verbois.retrohub.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import io.ktor.http.Url

/** Moteur HTTP natif de la plateforme : OkHttp sur Android et JVM, NSURLSession sur iOS. */
expect fun platformHttpEngine(): HttpClientEngineFactory<*>

fun HttpClientConfig<*>.retroHubDefaults() {
    expectSuccess = false
    followRedirects = true
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 60_000
    }
}

fun apiHttpClient(engine: HttpClientEngineFactory<*> = platformHttpEngine()): HttpClient = HttpClient(engine) { retroHubDefaults() }

/**
 * Client des illustrations : le jeton n’est envoyé qu’au serveur RetroHub, jamais à un autre hôte.
 */
fun artworkHttpClient(session: ApiSession, engine: HttpClientEngineFactory<*> = platformHttpEngine()): HttpClient = HttpClient(engine) {
    retroHubDefaults()
    install(
        createClientPlugin("RetroHubArtworkAuth") {
            onRequest { request, _ ->
                val server = session.server ?: return@onRequest
                val token = session.token ?: return@onRequest
                val target = request.url.build()
                val home = Url(server)
                if (target.host == home.host && target.port == home.port) {
                    request.headers.append(HttpHeaders.Authorization, "Bearer $token")
                }
            }
        },
    )
}
