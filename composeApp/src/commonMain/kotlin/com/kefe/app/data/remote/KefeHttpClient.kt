package com.kefe.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Ortak Ktor istemcisi.
 *
 * Motor (engine) BELIRTILMEZ: Ktor calisma zamaninda siniftaki motoru kendisi bulur.
 * Her platform kendi motorunu getirir - Android okhttp, iOS darwin, masaustu cio -
 * ve boylece expect/actual yazmaya gerek kalmaz.
 */
fun createKefeHttpClient(enableLogging: Boolean = false): HttpClient = HttpClient {
    expectSuccess = true

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
            }
        )
    }

    install(HttpTimeout) {
        // Fiyat cekme kullaniciyi bekletmez; yerel veri zaten ekranda.
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 10_000
    }

    defaultRequest {
        headers.append(HttpHeaders.Accept, "application/json")
    }
}
