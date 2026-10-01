package com.kefe.app.data.sync

import com.kefe.app.data.remote.PostgrestApi
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.kefeDateOfEpochDay
import com.kefe.app.domain.model.toEpochDay
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.domain.repository.PreferencesRepository
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Gunluk fiyat gecmisini HESAP uzerinden paylasir.
 *
 * NEYDI: haftalik/aylik degisim cihazdaki price_history'den 7 ve 30 gun onceki
 * fiyati arar; o tablo yalniz uygulamanin ACILDIGI gunleri tutuyordu ve
 * paylasilmiyordu. Telefon degisince 25 Agu - 7 Eyl arasi bos kaldi, "Ay" altin
 * ve dovizde "—" gorundu; iki telefon ayni varlik icin farkli degisim yazabiliyordu.
 *
 * NASIL: her telefon gordugu gunluk fiyatlari yazar; oteki telefon GUNDE BIR KEZ
 * ceker ve YALNIZ KENDINDE OLMAYAN gunleri doldurur - kendi gozlemini ezmez.
 * Esitleme motorundan AYRI: satirlar kullanicinin kaydi degil gozlem; mezar
 * tasi, LWW, "Hesaptakileri kullan" gerekmez (sunucu: 20261001_price_history.sql).
 *
 * Isaretler hesaba gore tutulur ("<kullanici>:<epochDay>"): baska bir hesaba
 * baglanan cihaz bastan baslar.
 */
class PriceHistorySync(
    private val database: KefeDatabase,
    private val postgrest: PostgrestApi,
    private val authRepository: AuthRepository,
    private val preferences: PreferencesRepository,
    private val clock: KefeClock,
    private val dispatcher: CoroutineContext = Dispatchers.Default,
) {
    private val queries = database.priceQueries
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun syncOnce(userId: String) {
        val token = authRepository.validAccessToken() ?: return
        val today = clock.marketToday()
        push(userId, token, today)
        pull(userId, token, today)
    }

    /**
     * Son itilen gunden [RepushDays] once baslayarak yerel satirlar gonderilir:
     * ayni gun sonradan tazelenen fiyat da gider. Ilk sefer butun gecmis.
     */
    private suspend fun push(userId: String, token: String, today: KefeDate) {
        val last = markOf(PreferenceKeys.PriceHistoryPushed, userId)
        val since = kefeDateOfEpochDay(last?.minus(RepushDays) ?: (today.toEpochDay() - FullWindowDays))
        val rows = withContext(dispatcher) {
            queries.selectRecentPriceHistory(dateKeyOf(since)).executeAsList()
        }.map { row ->
            PriceHistoryRow(
                userId = userId,
                assetKey = row.assetKey,
                day = isoOf(KefeDate(row.dateYear.toInt(), row.dateMonth.toInt(), row.dateDay.toInt())),
                price = row.price,
            )
        }
        rows.chunked(PushChunk).forEach { chunk ->
            postgrest.upsert(Table, json.encodeToString(chunk), token)
        }
        setMark(PreferenceKeys.PriceHistoryPushed, userId, today)
    }

    /**
     * Gunde bir kez: ilk sefer butun gecmis, sonra son [PullWindowDays] gun.
     * Sunucu sayfa basina 1000 satir doner; sayfalanir.
     */
    private suspend fun pull(userId: String, token: String, today: KefeDate) {
        val last = markOf(PreferenceKeys.PriceHistoryPulled, userId)
        if (last == today.toEpochDay()) return
        val since = kefeDateOfEpochDay(today.toEpochDay() - if (last == null) FullWindowDays else PullWindowDays)
        val rows = mutableListOf<PriceHistoryRow>()
        var offset = 0
        while (true) {
            val page = postgrest.select(
                table = Table,
                query = "select=asset_key,day,price&day=gte.${isoOf(since)}" +
                    "&order=day.asc,asset_key.asc&limit=$PageSize&offset=$offset",
                accessToken = token,
            )
            val list = json.decodeFromString<List<PriceHistoryRow>>(page)
            rows += list
            if (list.size < PageSize) break
            offset += PageSize
        }
        withContext(dispatcher) {
            database.transaction {
                rows.forEach { row ->
                    val date = parseIso(row.day) ?: return@forEach
                    if (row.price <= 0.0) return@forEach
                    queries.insertPriceHistoryIfMissing(
                        assetKey = row.assetKey,
                        dateYear = date.year.toLong(),
                        dateMonth = date.month.toLong(),
                        dateDay = date.day.toLong(),
                        price = row.price,
                    )
                }
            }
        }
        setMark(PreferenceKeys.PriceHistoryPulled, userId, today)
    }

    private suspend fun markOf(key: String, userId: String): Long? =
        preferences.get(key)
            ?.takeIf { it.startsWith("$userId:") }
            ?.substringAfter(':')
            ?.toLongOrNull()

    private suspend fun setMark(key: String, userId: String, day: KefeDate) {
        preferences.put(key, "$userId:${day.toEpochDay()}")
    }

    private companion object {
        const val Table = "price_history"

        /** Ilk itme/cekim: yerel gecmisin saklandigi iki yildan biraz fazla. */
        const val FullWindowDays = 800L

        /** Sonraki itmelerde geriye bakilan gun - gun icinde tazelenen fiyat da gitsin. */
        const val RepushDays = 3L

        /** Gunluk cekimin penceresi: aylik degisimin ihtiyacinin (en cok 60 gun) ustu. */
        const val PullWindowDays = 70L

        const val PageSize = 1000
        const val PushChunk = 500
    }
}

@Serializable
internal data class PriceHistoryRow(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("asset_key") val assetKey: String,
    /** "2026-09-24". */
    val day: String,
    val price: Double,
)

private fun isoOf(date: KefeDate): String =
    "${date.year}-${date.month.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"

private fun parseIso(text: String): KefeDate? {
    val parts = text.take(10).split('-')
    if (parts.size != 3) return null
    val (y, m, d) = parts.map { it.toIntOrNull() ?: return null }
    return KefeDate(y, m, d)
}

/** price_history'nin tarih anahtari (bkz. SqlDelightPriceRepository.dateKeyOf). */
private fun dateKeyOf(date: KefeDate): Long =
    date.year * 10_000L + date.month * 100L + date.day
