package com.kefe.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Kimlik ucunun sozlesmesi.
 *
 * Fiyat tarafindaki [PriceRemoteDataSource] ile ayni gerekce: oturum kurallari -
 * jeton ne zaman yenilenir, yenileme patlarsa ne olur - gercek bir sunucuya
 * cikmadan sinanabilmeli. Bu kurallar ekranda gorunmuyor, elle denenemiyor.
 */
interface AuthApi {
    suspend fun sendCode(email: String)
    suspend fun verifyCode(email: String, code: String): AuthTokens
    suspend fun refreshSession(refreshToken: String): AuthTokens
    suspend fun signOut(accessToken: String)
}

/**
 * Supabase GoTruth (auth) ucunun kullandigimiz kadari.
 *
 * NEDEN ELLE YAZILDI: supabase-kt kutuphanesi kendi Ktor surumunu dayatir; bu
 * projede Ktor 3.5.1 ve Kotlin 2.4.10 var, ikisi de yeni. Ihtiyacimiz uc uctan
 * ibaret ve depoda zaten elle yazilmis uc HTTP istemcisi (serbest piyasa, TCMB,
 * TEFAS) duruyor - dordunculuk kutuphane bagimliligi getirmeye degmez.
 *
 * PAROLA YOK. Kimlik dogrulama e-postaya gonderilen tek kullanimlik kod ile
 * yapilir: hatirlanacak parola, sifirlama akisi, parola saklama sorumlulugu
 * olmaz. Iki kisilik bir uygulamada dogru takas budur.
 *
 * KOD, BAGLANTI DEGIL: Supabase ayni e-postada hem tiklanabilir bir baglanti hem
 * de alti haneli bir kod gonderebiliyor. Baglantiyi yakalamak her platformda
 * ayri is demek - Android'de intent filter, iOS'ta universal link, masaustunde
 * ise dogru duzgun bir karsiligi bile yok. Kod, uc platformda da ayni sekilde
 * calisir: kullanici okur, yazar.
 */
class SupabaseAuthApi(
    private val client: HttpClient,
    private val baseUrl: String = SupabaseConfig.Url,
    private val anonKey: String = SupabaseConfig.AnonKey,
) : AuthApi {

    /**
     * E-postaya giris kodu gonderir. Hesap yoksa OLUSTURULUR - iki kisilik bir
     * uygulamada ayri bir "kayit ol" adimi gereksiz surtunmedir.
     */
    override suspend fun sendCode(email: String) {
        val response = client.post("$baseUrl/auth/v1/otp") {
            authHeaders()
            setBody(
                buildJsonObject {
                    put("email", email)
                    put("create_user", true)
                }.toString()
            )
        }
        if (!response.status.isSuccess()) throw response.toError()
    }

    /** Kodu oturuma cevirir. */
    override suspend fun verifyCode(email: String, code: String): AuthTokens {
        val response = client.post("$baseUrl/auth/v1/verify") {
            authHeaders()
            setBody(
                buildJsonObject {
                    put("type", "email")
                    put("email", email)
                    put("token", code)
                }.toString()
            )
        }
        if (!response.status.isSuccess()) throw response.toError()
        return response.readTokens()
    }

    /** Suresi dolan erisim jetonunu yeniler. */
    override suspend fun refreshSession(refreshToken: String): AuthTokens {
        val response = client.post("$baseUrl/auth/v1/token?grant_type=refresh_token") {
            authHeaders()
            setBody(
                buildJsonObject { put("refresh_token", refreshToken) }.toString()
            )
        }
        if (!response.status.isSuccess()) throw response.toError()
        return response.readTokens()
    }

    /**
     * Sunucudaki oturumu kapatir.
     *
     * Basarisiz olmasi cagirani DURDURMAZ: kullanici cikis dedigi anda cihazdaki
     * kayit zaten silinir. Ag yoksa "cikis yapamadiniz" demek anlamsiz olurdu.
     */
    override suspend fun signOut(accessToken: String) {
        runCatching {
            client.post("$baseUrl/auth/v1/logout") {
                authHeaders()
                header("Authorization", "Bearer $accessToken")
            }
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authHeaders() {
        header("apikey", anonKey)
        header("Authorization", "Bearer $anonKey")
        contentType(ContentType.Application.Json)
        // Ortak istemcide expectSuccess = true - fiyat kaynaklari icin dogru,
        // burada DEGIL: Ktor 4xx'i biz govdeyi okumadan firlatiyordu ve "kod
        // gecersiz" gibi anlatilabilir her hata kullaniciya "internet
        // bağlantınızı kontrol edin" diye cikiyordu. Durum kodunu kendimiz
        // yorumluyoruz.
        expectSuccess = false
    }

    private suspend fun HttpResponse.readTokens(): AuthTokens {
        val body = json.parseToJsonElement(bodyAsText()) as JsonObject
        val user = body["user"] as? JsonObject
        return AuthTokens(
            accessToken = body["access_token"]?.jsonPrimitive?.content
                ?: throw AuthException("Yanitta erisim jetonu yok"),
            refreshToken = body["refresh_token"]?.jsonPrimitive?.content
                ?: throw AuthException("Yanitta yenileme jetonu yok"),
            expiresInSeconds = body["expires_in"]?.jsonPrimitive?.content?.toLongOrNull()
                ?: DefaultExpirySeconds,
            userId = user?.get("id")?.jsonPrimitive?.content.orEmpty(),
            email = user?.get("email")?.jsonPrimitive?.content.orEmpty(),
        )
    }

    /**
     * Sunucunun anlattigi sebebi tasir.
     *
     * Supabase hatayi bazen "error_description", bazen "msg", bazen "message"
     * alaninda veriyor; ucunu de deneriz. Hicbiri yoksa durum kodu yazilir -
     * "bir hata olustu" demek kullaniciyi da bizi de bir yere goturmez.
     */
    private suspend fun HttpResponse.toError(): AuthException {
        val body = runCatching { json.parseToJsonElement(bodyAsText()) as JsonObject }.getOrNull()

        // Sik gorulen durumlar TURKCE yazilir. Sunucunun kendi metni ingilizce
        // ("Token has expired or is invalid") ve uygulamanin geri kalani turkce;
        // kullanicinin en cok gorecegi hata da bu.
        val code = body?.get("error_code")?.jsonPrimitive?.content
        translate(code)?.let { return AuthException(it) }

        val reason = body?.let {
            listOf("error_description", "msg", "message", "error")
                .firstNotNullOfOrNull { key ->
                    it[key]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
                }
        }
        return AuthException(reason ?: "Sunucu ${status.value} dondu")
    }

    private fun translate(errorCode: String?): String? = when (errorCode) {
        "otp_expired" -> "Kod geçersiz ya da süresi dolmuş — yeni kod isteyin"
        "over_email_send_rate_limit", "over_request_rate_limit" ->
            "Çok fazla deneme yapıldı — birkaç dakika sonra tekrar deneyin"
        "validation_failed", "email_address_invalid" -> "E-posta adresi geçersiz görünüyor"
        "email_address_not_authorized" -> "Bu e-posta adresine gönderim yapılamıyor"
        "signup_disabled" -> "Yeni kayıtlar şu an kapalı"
        else -> null
    }

    private companion object {
        val json = Json { isLenient = true; ignoreUnknownKeys = true }

        /** Supabase varsayilani bir saat; yanitta gelmezse bunu varsayariz. */
        const val DefaultExpirySeconds = 3600L
    }
}

/** Sunucudan donen ham oturum. */
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val userId: String,
    val email: String,
)

/** Kimlik dogrulama hatasi. Mesaji kullaniciya gosterilebilir. */
class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)
