package com.kefe.app.data.remote

import com.kefe.app.domain.repository.AuthRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Supabase Realtime'in kullandigimiz kadari: "sunucuda bir senkron tablosu
 * degisti" sinyali.
 *
 * NEDEN VAR: adim 10'a kadar pull yalniz IKI anda tetikleniyordu - giriste bir
 * kez ve her push'tan sonra. Yani karsi cihazin yazdigi veri, sen kendi
 * telefonunda bir sey yazana kadar gelmiyordu. Bu akis o eksik ucuncu tetigi
 * verir; yoklama (poll) degil, sunucu iter.
 *
 * NEDEN ELLE YAZILDI: [PostgrestApi] ile ayni gerekce - supabase-kt kendi Ktor
 * surumunu dayatir, ihtiyacimiz tek soketten ibaret.
 *
 * SINYAL, VERI DEGIL: gelen mesajin icindeki satir OKUNMAZ. Olay yalnizca
 * "degisti" demektir, uygulamayi [com.kefe.app.data.sync.PullEngine] yapar -
 * tam cekim + LWW. Boylece cakisma cozumu tek yerde kalir; payload'i dogrudan
 * uygulamak ikinci bir dogruluk yuzeyi acardi (kacirilan olay = kalici
 * tutarsizlik).
 */
interface RealtimeApi {

    /**
     * Senkron tablolarindan biri SUNUCUDA degistiginde emit eder.
     *
     * Akis toplanildigi surece soket acik kalir, iptal edilince kapanir -
     * "ne zaman dinlenecegi" karari cagirana (SyncCoordinator) aittir.
     * Bulut yapilandirilmamissa bos akis doner.
     */
    fun serverChanges(): Flow<Unit>
}

/** Sunucuya tasidigimiz yedi tablo (bkz. supabase/schema.sql). */
internal val RealtimeTables: List<String> = listOf(
    "members",
    "positions",
    "transactions",
    "goals",
    "goal_assets",
    "daily_snapshots",
    "activity_events",
)

class SupabaseRealtimeApi(
    private val client: HttpClient,
    private val authRepository: AuthRepository,
    private val baseUrl: String = SupabaseConfig.Url,
    private val anonKey: String = SupabaseConfig.AnonKey,
) : RealtimeApi {

    override fun serverChanges(): Flow<Unit> {
        if (baseUrl.isBlank() || anonKey.isBlank()) return emptyFlow()
        val url = realtimeSocketUrl(baseUrl, anonKey)

        // channelFlow: cerceveler soketin kendi is parcaciginda okunuyor, sinyal
        // oradan gonderiliyor. conflate: sinyaller birbirinden ayirt edilemez,
        // pull surerken gelen ikinci sinyalin beklemesi gerekmez - sonuncusu
        // yeter, cunku pull zaten TAM cekim yapar.
        return channelFlow {
            // Ayri isim: soketin kendi `send`'i CERCEVE gonderiyor, bu sinyali.
            val signals = channel
            var backoff = InitialBackoffMillis
            var ref = 0L

            while (true) {
                // Her denemede TAZE jeton: suresi dolmussa validAccessToken
                // kendisi yeniler. Ayri bir jeton zamanlayicisi yok.
                val token = authRepository.validAccessToken()
                if (token == null) {
                    delay(backoff)
                    backoff = nextBackoff(backoff)
                    continue
                }

                try {
                    client.webSocket(url) {
                        // Baglanti kuruldu: bekleme sifirlanir, yoksa bir kez
                        // uzayan bekleme oturum boyunca uzun kalirdi.
                        backoff = InitialBackoffMillis
                        send(Frame.Text(joinFrame(RealtimeTables, token, ++ref)))
                        // Tanisal: acilmayan bir soket ile hic olay uretmeyen bir
                        // soket disaridan ayni goruntuyu verir.
                        println("Kefe senkron: realtime bagli")

                        // Heartbeat SOKETIN scope'unda: soket kapaninca
                        // kendiliginden iptal olur. Phoenix ~30 sn'de bir
                        // heartbeat gormezse baglantiyi duserur - bu bir
                        // uygulama ticker'i degil, soketin keepalive'i ve
                        // yalniz soket acikken (girisli + on planda) yasar.
                        val heartbeat = launch {
                            var sentToken = token
                            while (isActive) {
                                delay(HeartbeatMillis)
                                send(Frame.Text(heartbeatFrame(++ref)))
                                // Ayni tikta jeton tazeligi: validAccessToken
                                // yalniz SURESI DOLMUSSA aga cikar, yani bu bir
                                // yoklama degil. Jeton yenilendiyse sunucuya
                                // bildirilir, yoksa RLS'li akis susardi.
                                val fresh = authRepository.validAccessToken()
                                if (fresh != null && fresh != sentToken) {
                                    send(Frame.Text(accessTokenFrame(fresh, ++ref)))
                                    sentToken = fresh
                                }
                            }
                        }

                        try {
                            for (frame in incoming) {
                                val text = (frame as? Frame.Text)?.readText() ?: continue
                                // phx_reply / presence / heartbeat cevaplari yutulur.
                                if (isPostgresChange(text)) {
                                    signals.send(Unit)
                                    continue
                                }
                                // Bu iki satir olmadan "baglandi ama hicbir olay
                                // gelmiyor" durumu disaridan calisan bir soketle
                                // ayni gorunur.
                                //
                                // DIKKAT: join yaniti KABUL KANITI DEGIL - Supabase
                                // istenen yapilandirmayi oldugu gibi geri yansitir,
                                // tablo yayinda olmasa bile. Gercek karar bir alttaki
                                // "system/error" satirindan gelir.
                                joinReplyTableCount(text)?.let {
                                    println("Kefe senkron: realtime join yaniti - $it/${RealtimeTables.size} tablo istendi")
                                }
                                realtimeProblem(text)?.let {
                                    println("Kefe senkron: realtime abonelik REDDEDILDI - $it")
                                }
                            }
                        } finally {
                            heartbeat.cancel()
                        }
                    }
                } catch (cancellation: CancellationException) {
                    // Dinleme birakildi (cikis, arka plan): yukari gecer.
                    throw cancellation
                } catch (error: Exception) {
                    // Sessiz bir senkron, calisan bir senkrondan ayirt edilemez.
                    println("Kefe senkron: realtime koptu - ${error.message}")
                }

                // Kopus sonrasi ustel bekleme. Retry.kt'deki retryOnTransient
                // KULLANILMAZ: o sonlu denemeli TEK istek icindir, burada uzun
                // omurlu bir baglanti dongusu var.
                delay(backoff)
                backoff = nextBackoff(backoff)
            }
        }.conflate()
    }
}

// --- Protokol yardimcilari (saf fonksiyonlar; testler bunlara bakar) ---

private val realtimeJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** Tek topic yeter: yedi tablo ayni kanalda dinlenir. */
internal const val RealtimeTopic: String = "realtime:kefe"

internal const val HeartbeatMillis: Long = 25_000
internal const val InitialBackoffMillis: Long = 1_000
internal const val MaxBackoffMillis: Long = 60_000

/**
 * `https://xyz.supabase.co` -> `wss://xyz.supabase.co/realtime/v1/websocket?...`
 * apikey anon anahtaridir (projeyi tanitir); yetkiyi join'deki access_token tasir.
 */
internal fun realtimeSocketUrl(baseUrl: String, anonKey: String): String {
    val host = baseUrl.trim().trimEnd('/')
        .replaceFirst("https://", "wss://")
        .replaceFirst("http://", "ws://")
    return "$host/realtime/v1/websocket?apikey=$anonKey&vsn=1.0.0"
}

/**
 * BEARER = KULLANICININ JETONU, anonKey degil - [PostgrestApi]'deki gerekcenin
 * aynisi: RLS'in "auth.uid() = user_id" kurali ancak istegin kimin adina
 * geldigini bilirse calisir. Jetonsuz join'de akis sessizce bos kalirdi.
 */
internal fun joinFrame(tables: List<String>, accessToken: String, ref: Long): String =
    buildJsonObject {
        put("topic", RealtimeTopic)
        put("event", "phx_join")
        putJsonObject("payload") {
            putJsonObject("config") {
                putJsonArray("postgres_changes") {
                    tables.forEach { table ->
                        addJsonObject {
                            // Ekleme, guncelleme ve silme - mezar tasi da bir degisikliktir.
                            put("event", "*")
                            put("schema", "public")
                            put("table", table)
                        }
                    }
                }
            }
            put("access_token", accessToken)
        }
        put("ref", ref.toString())
    }.toString()

internal fun heartbeatFrame(ref: Long): String =
    buildJsonObject {
        put("topic", "phoenix")
        put("event", "heartbeat")
        put("payload", JsonObject(emptyMap()))
        put("ref", ref.toString())
    }.toString()

/** Jeton yenilendiginde soketi kapatmadan yetkiyi tazeler. */
internal fun accessTokenFrame(accessToken: String, ref: Long): String =
    buildJsonObject {
        put("topic", RealtimeTopic)
        put("event", "access_token")
        putJsonObject("payload") { put("access_token", accessToken) }
        put("ref", ref.toString())
    }.toString()

/** Yalniz veri degisikligi mesajlari sinyal sayilir. Bozuk metin sinyal degildir. */
internal fun isPostgresChange(text: String): Boolean = runCatching {
    realtimeJson.parseToJsonElement(text).jsonObject["event"]?.jsonPrimitive?.content
}.getOrNull() == "postgres_changes"

/**
 * Join cevabinda yansitilan tablo sayisi; join cevabi degilse null.
 *
 * KABUL KANITI DEGIL: Supabase istedigimiz yapilandirmayi tablo yayinda olmasa
 * da oldugu gibi geri yansitir (emulatorde dogrulandi: "7/7" der, hemen ardindan
 * system/error ile aboneligi reddeder). Cerceve gidip geldi demenin olcusu;
 * kabul/red icin [realtimeProblem]'e bakilir.
 */
internal fun joinReplyTableCount(text: String): Int? = runCatching {
    val root = realtimeJson.parseToJsonElement(text).jsonObject
    if (root["event"]?.jsonPrimitive?.content != "phx_reply") return@runCatching null
    if (root["topic"]?.jsonPrimitive?.content != RealtimeTopic) return@runCatching null
    root["payload"]?.jsonObject
        ?.get("response")?.jsonObject
        ?.get("postgres_changes")?.jsonArray
        ?.size
}.getOrNull()

/** Sunucunun bildirdigi hata metni; hata mesaji degilse null. */
internal fun realtimeProblem(text: String): String? = runCatching {
    val root = realtimeJson.parseToJsonElement(text).jsonObject
    val payload = root["payload"]?.jsonObject ?: return@runCatching null
    if (payload["status"]?.jsonPrimitive?.content != "error") return@runCatching null
    payload["message"]?.jsonPrimitive?.content
        ?: payload["response"]?.toString()
        ?: "bilinmeyen realtime hatasi"
}.getOrNull()

internal fun nextBackoff(current: Long): Long = (current * 2).coerceAtMost(MaxBackoffMillis)
