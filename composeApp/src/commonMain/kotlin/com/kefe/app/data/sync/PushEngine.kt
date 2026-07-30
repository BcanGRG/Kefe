package com.kefe.app.data.sync

import com.kefe.app.data.remote.PostgrestApi
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.domain.repository.PreferencesRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Push: yereldeki degisiklikleri Supabase'e YAZAN yon.
 *
 * WATERMARK ile calisir: her satirin updatedAt'i var; cihaz "en son suraya kadar
 * ittim" bilgisini ([PreferenceKeys.LastPushedAt]) tutar ve yalniz o damgadan
 * yeni satirlari gonderir. Ilk push'ta damga 0'dir, yani her sey gider - 2.sqm'de
 * "ilk esitleme PUSH yonunde" karari buydu.
 *
 * BUTUNLUK: watermark ancak TUM tablolar basariyla gittikten SONRA ilerler. Ilk
 * upsert patlarsa (ag, RLS) [PostgrestApi] firlatir, damga durur ve bir sonraki
 * tetik ayni noktadan bastan dener. Upsert idempotent oldugu icin tekrar zararsiz.
 */
class PushEngine(
    private val authRepository: AuthRepository,
    private val localSource: SyncLocalSource,
    private val postgrest: PostgrestApi,
    private val preferences: PreferencesRepository,
    private val clock: KefeClock,
) {

    // Iki tetik ust uste gelirse push'lar cakismasin: biri biterken oteki
    // watermark'i yari yolda okumasin.
    private val mutex = Mutex()

    suspend fun pushOnce(userId: String): Unit = mutex.withLock {
        // Jeton yoksa girisli degiliz - sessizce cik. Suresi dolduysa validAccessToken
        // yeniler; o da patlarsa null doner ve push ertelenir.
        val token = authRepository.validAccessToken() ?: return@withLock

        val since = preferences.get(PreferenceKeys.LastPushedAt)?.toLongOrNull() ?: 0L
        // Damgayi okumadan ONCE simdiyi yakala: push surerken yazilan satirlar
        // (updatedAt > syncStart) bu tura degil bir sonrakine dahil olsun.
        val syncStart = clock.nowEpochMillis()

        val batches = localSource.changesSince(since, userId)
        for (batch in batches) {
            postgrest.upsert(batch.table, batch.rowsJson, token)
        }

        preferences.put(PreferenceKeys.LastPushedAt, syncStart.toString())
    }
}
