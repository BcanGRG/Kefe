package com.kefe.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * Supabase veri ucunun (PostgREST) kullandigimiz kadari: bir tabloya toplu upsert.
 *
 * NEDEN ELLE YAZILDI: [AuthApi] ile ayni gerekce - supabase-kt kendi Ktor surumunu
 * dayatir, ihtiyacimiz tek uctan ibaret. Depoda zaten elle yazilmis HTTP
 * istemcileri (auth, fiyat kaynaklari) var.
 *
 * BEARER = KULLANICININ JETONU, anonKey degil: RLS'in "auth.uid() = user_id"
 * kurali ancak istegin kimin adina geldigini bilirse calisir. apikey yine anon -
 * o yalniz projeyi tanitir, yetkiyi Authorization tasir.
 */
interface PostgrestApi {

    /**
     * [rowsJson] bir JSON DIZISI (satirlar). Cakisan birincil anahtarlar
     * GUNCELLENIR (merge-duplicates) - iki cihaz ayni satiri ittiginde ikincisi
     * ezmez, birlestirir. Basarisizsa [SyncException] atar; cagiran watermark'i
     * ilerletmez ve bir sonraki tetikte yeniden dener.
     */
    suspend fun upsert(table: String, rowsJson: String, accessToken: String)

    /**
     * Tablonun TUM satirlarini (mezar taslari dahil) JSON dizisi olarak ceker.
     * RLS zaten satirlari o hesaba kisitlar; ayrica filtre gerekmez. Kucuk veri
     * icin tam cekim, artan cekimin saat-kaymasi/gec-gelen tuzaklarini atlar.
     * Basarisizsa [SyncException] atar.
     */
    suspend fun selectAll(table: String, accessToken: String): String
}

class SupabasePostgrestApi(
    private val client: HttpClient,
    private val baseUrl: String = SupabaseConfig.Url,
    private val anonKey: String = SupabaseConfig.AnonKey,
) : PostgrestApi {

    override suspend fun upsert(table: String, rowsJson: String, accessToken: String) {
        val response = client.post("$baseUrl/rest/v1/$table") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            // merge-duplicates: birincil anahtar cakismasi = guncelle.
            // return=minimal: govde istemeyiz, agi bosuna doldurmasin.
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            contentType(ContentType.Application.Json)
            // Ortak istemcide expectSuccess = true; burada kapatiyoruz ki 4xx'te
            // sunucunun anlattigi sebebi (kolon uyusmazligi, RLS reddi) okuyabilelim.
            expectSuccess = false
            setBody(rowsJson)
        }
        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
            throw SyncException("$table upsert ${response.status.value}: ${body.take(300)}")
        }
    }

    override suspend fun selectAll(table: String, accessToken: String): String {
        val response = client.get("$baseUrl/rest/v1/$table?select=*") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            expectSuccess = false
        }
        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
            throw SyncException("$table select ${response.status.value}: ${body.take(300)}")
        }
        return response.bodyAsText()
    }
}

/** Senkron hatasi. Kullaniciya gosterilmez - loglanir, push bir sonraki tetikte yenilenir. */
class SyncException(message: String, cause: Throwable? = null) : Exception(message, cause)
