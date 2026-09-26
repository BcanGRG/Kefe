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

    // coerceInputValues: sunucuda sonradan eklenen bir kolon (goal_delta,
    // created_at) eski satirlarda acik bir NULL tasiyabilir. DTO alani null
    // kabul etmiyorsa tek bir satir BUTUN pull'u dusururdu; boyle bir deger
    // DTO'nun varsayilanina iner (bkz. SyncDtos: sonradan gelen alanlarin hepsi
    // varsayilanli).
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    // Iki pull cakismasin (giris + push-sonrasi ust uste gelebilir). "Bu cihazı
    // sıfırla" da ayni kilidi tutar (bkz. [exclusive]).
    private val mutex = Mutex()

    /**
     * Uygulanan (yerelden yeni) satir sayisini dondurur. Girisli degilse 0.
     *
     * [adoptServerMembers]: profil adlari sunucudan alinir, yereldeki damga daha
     * yeni olsa bile (bkz. [ApplyMode.Adopt]). Bagli cihazin olagan pull'u
     * false'tur; ilk baglanti artik [fetch] + onay + [SyncLocalSink.commitLink]
     * yolundan gecer (bkz. AccountLinker).
     *
     * [stillWanted]: indirme BITTIKTEN sonra, uygulamadan hemen once sorulur;
     * false ise hicbir sey yazilmaz. NEDEN: indirme yedi ardisik istek, saniyeler
     * surebilir. O arada cihaz hesaptan cikip sifirlanirsa, eski jetonla
     * baslamis bu tur hesabin butun satirlarini silinmis veritabanina geri
     * yaziyordu - sifirlama kendiliginden geri aliniyordu.
     */
    suspend fun pullOnce(
        adoptServerMembers: Boolean = false,
        stillWanted: suspend () -> Boolean = { true },
    ): Int = mutex.withLock {
        val batch = fetch() ?: return@withLock 0
        if (!stillWanted()) return@withLock 0
        apply(batch, if (adoptServerMembers) ApplyMode.Adopt else ApplyMode.Lww)
    }

    /**
     * [block]'u pull'larla AYNI kilitte calistirir: suren bir pull once biter,
     * [block] bitene kadar yenisi uygulanmaz.
     *
     * "Bu cihazı sıfırla" icin. NEYDI: silme kilitsiz calisiyordu; o anda suren
     * bir pull (karsi telefonun realtime sinyali, push-sonrasi pull, on plana
     * donus) indirmesini silmeden SONRA bitirip hesabi bos veritabanina geri
     * yaziyordu. Cihaz "Yalnız bu cihazda" gorunuyor ama hesabin tamamini
     * tasiyordu.
     */
    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }

    /**
     * Hesabin TUM satirlarini ceker, HICBIR SEY YAZMAZ. Jeton yoksa null.
     *
     * NEDEN AYRI. pull tek adimdi: indir ve hemen uygula. Hesaba ilk baglanirken
     * bu, kullanici "bu cihazda da kayit var, ne olsun?" sorusunu gormeden
     * hesabin satirlarini cihazdakilerle karistiriyordu - geri donusu olmayan
     * bir birlestirme. Artik baglanti adimi once bakar (bkz. classifyLink),
     * karar verilince uygular.
     */
    suspend fun fetch(): PullBatch? {
        val token = authRepository.validAccessToken() ?: return null
        return PullBatch(
            members = decode(postgrest.selectAll("members", token)),
            positions = decode(postgrest.selectAll("positions", token)),
            transactions = decode(postgrest.selectAll("transactions", token)),
            goals = decode(postgrest.selectAll("goals", token)),
            goalAssets = decode(postgrest.selectAll("goal_assets", token)),
            snapshots = decode(postgrest.selectAll("daily_snapshots", token)),
            activity = decode(postgrest.selectAll("activity_events", token)),
        )
    }

    /** Cekilmis satirlari [mode] ile yerele uygular (bkz. [SyncLocalSink.apply]). */
    suspend fun apply(batch: PullBatch, mode: ApplyMode = ApplyMode.Lww): Int = sink.apply(batch, mode)

    private inline fun <reified T> decode(jsonArray: String): List<T> =
        json.decodeFromString<List<T>>(jsonArray)
}
