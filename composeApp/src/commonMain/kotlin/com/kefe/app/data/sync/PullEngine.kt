package com.kefe.app.data.sync

import com.kefe.app.data.remote.PostgrestApi
import com.kefe.app.domain.repository.AuthRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Pull: sunucudaki degisiklikleri cihaza ceken yon. Ikinci telefonun senkronu
 * asil burada gorunur - bir cihazda eklenen altin, digerinde belirir.
 *
 * TAM CEKIM: her tablonun tum satirlari cekilir (RLS o hesaba kisitlar), gelenler
 * LWW ile uygulanir (bkz. [SyncLocalSink]). Artan cekim (watermark) yerine tam
 * cekim, saat-kaymasi ve gec-gelen satir tuzaklarini bastan atlar; iki kisilik
 * kucuk veri icin bedeli onemsiz. Veri buyurse sunucu-tarafi damgayla (trigger)
 * artana gecilir.
 */
class PullEngine(
    private val authRepository: AuthRepository,
    private val postgrest: PostgrestApi,
    private val sink: SyncLocalSink,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Iki pull cakismasin (giris + push-sonrasi ust uste gelebilir).
    private val mutex = Mutex()

    /** Uygulanan (yerelden yeni) satir sayisini dondurur. Girisli degilse 0. */
    suspend fun pullOnce(): Int = mutex.withLock {
        val token = authRepository.validAccessToken() ?: return@withLock 0

        val batch = PullBatch(
            members = decode(postgrest.selectAll("members", token)),
            positions = decode(postgrest.selectAll("positions", token)),
            transactions = decode(postgrest.selectAll("transactions", token)),
            goals = decode(postgrest.selectAll("goals", token)),
            goalAssets = decode(postgrest.selectAll("goal_assets", token)),
            snapshots = decode(postgrest.selectAll("daily_snapshots", token)),
            activity = decode(postgrest.selectAll("activity_events", token)),
        )
        sink.apply(batch)
    }

    private inline fun <reified T> decode(jsonArray: String): List<T> =
        json.decodeFromString<List<T>>(jsonArray)
}
