package com.kefe.app.data.db

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import com.kefe.app.db.Activity_events
import com.kefe.app.db.Cached_prices
import com.kefe.app.db.Goals
import com.kefe.app.db.KefeDatabase
import com.kefe.app.db.Positions
import com.kefe.app.db.Transactions
import com.kefe.app.domain.repository.PreferenceKeys

/**
 * Veritabani nesnesini kurar.
 *
 * Enum kolonlari diske ADIYLA yazilir (EnumColumnAdapter): sira degeri yazilsaydi
 * enum'a ortadan yeni bir deger eklemek eski kayitlarin anlamini kaydirirdi.
 */
fun createKefeDatabase(driver: SqlDriver): KefeDatabase = KefeDatabase(
    driver = driver,
    activity_eventsAdapter = Activity_events.Adapter(
        kindAdapter = EnumColumnAdapter(),
    ),
    cached_pricesAdapter = Cached_prices.Adapter(
        sourceAdapter = EnumColumnAdapter(),
        assetClassAdapter = EnumColumnAdapter(),
    ),
    goalsAdapter = Goals.Adapter(
        unitAdapter = EnumColumnAdapter(),
        statusAdapter = EnumColumnAdapter(),
    ),
    positionsAdapter = Positions.Adapter(
        assetClassAdapter = EnumColumnAdapter(),
        subtypeAdapter = EnumColumnAdapter(),
        karatAdapter = EnumColumnAdapter(),
        unitAdapter = EnumColumnAdapter(),
    ),
    transactionsAdapter = Transactions.Adapter(
        sideAdapter = EnumColumnAdapter(),
        syncStateAdapter = EnumColumnAdapter(),
    ),
)

/** Ilk acilisin kurdugu portfoy. */
const val LocalPortfolioId: String = "portfolio_local"

/** Ilk acilisin kurdugu sahip uye - islemlerin "kim ekledi" alani buna baglanir. */
const val LocalOwnerMemberId: String = "member_owner"

/**
 * Ikinci profil.
 *
 * KIMLIKLER SABIT olmali: iki cihaz da kendi bootstrap'ini calistiriyor. Id'ler
 * deterministik oldugu icin senkron geldiginde iki cihazin "es" profili ayni
 * satira dusuyor - birlesme, cakisma degil. Rastgele bir kimlik verseydik iki
 * cihazda dort profil olurdu.
 */
const val LocalPartnerMemberId: String = "member_partner"

internal const val DefaultPortfolioName: String = "Birikimlerim"
internal const val DefaultCurrency: String = "TRY"

/**
 * Kurulumun iki profilinin YER TUTUCU adlari. Damgalari 0 kalir (adlandirilmamis,
 * bkz. Member.isNamed); ekranlar bunlari yazilmis bir ad saymaz.
 */
internal const val DefaultOwnerName: String = "Ben"
internal const val DefaultPartnerName: String = "Eşim"

/**
 * Iki profili kurulumun adsiz haline dondurur (ad varsayilan, damga 0).
 * Satirlar yoksa dokunmaz - eksik profili kurulum ekler.
 *
 * NEDEN. "Bu cihazı sıfırla" ve "Hesaptakileri kullan"dan sonra cihazda eski
 * adlar DAMGALI kaliyordu. Sifirlanan cihaz baska bir hesaba baglaninca o adlar
 * yeni hesaba itiliyor, kurulum da "Bu cihazda iki profil var" diye eski
 * kisilerin adlarini soruyordu. Adsiz satiri hesap devralir, yerel ad hicbir
 * yere gitmez.
 */
internal fun KefeDatabase.resetMembersToDefaults() {
    portfolioQueries.resetMember(
        name = DefaultOwnerName,
        initials = DefaultOwnerName.take(1),
        id = LocalOwnerMemberId,
    )
    portfolioQueries.resetMember(
        name = DefaultPartnerName,
        initials = DefaultPartnerName.take(1),
        id = LocalPartnerMemberId,
    )
}

internal const val BootstrapKey: String = "bootstrapVersion"
internal const val BootstrapValue: String = "1"

/**
 * Ilk acilis kurulumu: bos bir portfoy ve tek sahip uye.
 *
 * ORNEK VERI TOHUMLANMAZ - kullanici kendi islemlerini girecek, aradan ayiklamak
 * zorunda kalmasin. Buradaki iki satir veri degil KIMLIKTIR: islem eklerken
 * "kim ekledi" bir uyeye baglanir, uye yoksa aktivite satirlari adsiz kalir.
 *
 * Tek seferlik bayrak (settings tablosu) sart: "tablo bossa kur" deseydi
 * kullanici uyeyi silince kurulum geri gelirdi.
 */
fun KefeDatabase.bootstrapIfNeeded() {
    transaction {
        if (settingQueries.selectSetting(BootstrapKey).executeAsOneOrNull() != null) {
            return@transaction
        }

        portfolioQueries.insertOrIgnorePortfolio(
            id = LocalPortfolioId,
            name = DefaultPortfolioName,
            currency = DefaultCurrency,
        )
        // Iki profil de bastan kurulur. Isimler VARSAYILAN - "bu telefon kimin"
        // adimi (ProfileSetup) gercek adlari yazar ve hangisinin bu cihaz oldugunu
        // secer. Iki profili burada kurmak, ikinci telefonun senkrondan once bile
        // dogru iskeleti gormesini saglar.
        // role/permission/lastSeen sorguda sabit; bkz. Portfolio.sq.
        portfolioQueries.insertOrIgnoreMember(
            id = LocalOwnerMemberId,
            portfolioId = LocalPortfolioId,
            name = DefaultOwnerName,
            initials = DefaultOwnerName.take(1),
            sortOrder = 0L,
        )
        portfolioQueries.insertOrIgnoreMember(
            id = LocalPartnerMemberId,
            portfolioId = LocalPortfolioId,
            name = DefaultPartnerName,
            initials = DefaultPartnerName.take(1),
            sortOrder = 1L,
        )
        writeNewDatabaseDefaults()
        settingQueries.upsertSetting(
            settingKey = BootstrapKey,
            settingValue = BootstrapValue,
        )
    }
}

/**
 * Yeni bir veritabaninin cihaz tercihleri. Kurulum, ornek veri tohumu ve
 * "Tüm verileri sil" ayni yerden gecer ki ucu ayrismasin.
 *
 * ACILIS KILIDI KAPALI yazilir. NEYDI: kilit varsayilan olarak acikti; yeni
 * kurulumda "Atla" deyip profili olusturan ve uygulamayi kapatan kullanici bir
 * sonraki acilista, hic istemedigi bir "Kefe kilitli" ekraniyla karsilasiyordu.
 * Kilit artik istege bagli: kullanici Ayarlar'dan acar.
 *
 * ANAHTAR YOKSA yazilir, varsa dokunulmaz: kullanicinin actigi kilit bir
 * yeniden kurulumda (bootstrapVersion silinip kurulum tekrar calisirsa) sessizce
 * kapanmamali. Eski kurulumlar bootstrapVersion'i zaten tasidigi icin buraya hic
 * gelmez; anahtarlari eksik kalir ve `lockEnabled()` onlari "acik" okur.
 */
internal fun KefeDatabase.writeNewDatabaseDefaults() {
    if (settingQueries.selectSetting(PreferenceKeys.BiometricLock).executeAsOneOrNull() == null) {
        settingQueries.upsertSetting(
            settingKey = PreferenceKeys.BiometricLock,
            settingValue = false.toString(),
        )
    }
}
