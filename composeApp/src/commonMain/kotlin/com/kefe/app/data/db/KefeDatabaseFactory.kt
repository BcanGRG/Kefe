package com.kefe.app.data.db

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import com.kefe.app.db.Activity_events
import com.kefe.app.db.Cached_prices
import com.kefe.app.db.Goals
import com.kefe.app.db.KefeDatabase
import com.kefe.app.db.Members
import com.kefe.app.db.Positions
import com.kefe.app.db.Transactions
import com.kefe.app.domain.model.MemberPermission
import com.kefe.app.domain.model.MemberRole

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
        allocationAdapter = EnumColumnAdapter(),
        statusAdapter = EnumColumnAdapter(),
    ),
    membersAdapter = Members.Adapter(
        roleAdapter = EnumColumnAdapter(),
        permissionAdapter = EnumColumnAdapter(),
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

internal const val DefaultPortfolioName: String = "Birikimlerim"
internal const val DefaultCurrency: String = "TRY"

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
        portfolioQueries.insertOrIgnoreMember(
            id = LocalOwnerMemberId,
            portfolioId = LocalPortfolioId,
            name = "Ben",
            initials = "B",
            role = MemberRole.Owner,
            permission = MemberPermission.CanEdit,
            // Serbest metin - gercek zaman damgasi degil.
            lastSeen = "şu anda çevrimiçi",
            sortOrder = 0L,
        )
        settingQueries.upsertSetting(
            settingKey = BootstrapKey,
            settingValue = BootstrapValue,
        )
    }
}
