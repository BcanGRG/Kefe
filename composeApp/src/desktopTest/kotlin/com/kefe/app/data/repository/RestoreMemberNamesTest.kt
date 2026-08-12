package com.kefe.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.backup.BackupFile
import com.kefe.app.domain.backup.BackupMember
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * Geri yukleme profil ISIMLERINI de getirir.
 *
 * Getirmiyordu. Kurulum iki uyeyi ("Ben" / "Eş") kimlikleriyle birlikte
 * tohumluyor; geri yukleme ise `INSERT OR IGNORE` kullaniyordu, yani ayni
 * kimlikler cakisinca satir SESSIZCE atlaniyordu. Yedekte "Burak" ve "Merve"
 * yazsa bile ekranda tohumun varsayilan adlari kaliyordu ve kullanicinin
 * yedekten donmesine ragmen isimleri kayboluyordu.
 */
class RestoreMemberNamesTest {

    /**
     * Depo VE veritabani birlikte doner: testin kurulumun tohumladigi uye
     * satirlarini gercekten yazmasi gerekiyor. `renameMember` yetmez - o bir
     * UPDATE, olmayan satiri yaratmaz, dolayisiyla geri yuklemede cakisma da
     * dogmaz ve test hatali kodda da gecerdi.
     */
    private fun newRepository(): Pair<SqlDelightPortfolioRepository, KefeDatabase> {
        val driver = JdbcSqliteDriver(url = JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        val db = createKefeDatabase(driver)
        return SqlDelightPortfolioRepository(
            db,
            FixedKefeClock(millis = 1_000L),
            NoPrices(),
        ) to db
    }

    /** Kurulumun yaptigi sey: uye satirini varsayilan adiyla yazar. */
    private fun KefeDatabase.seedMember(id: String, name: String, initials: String, order: Long) {
        portfolioQueries.insertOrIgnorePortfolio(
            id = "local",
            name = "Birikimlerim",
            currency = "TRY",
        )
        portfolioQueries.insertOrIgnoreMember(
            id = id,
            portfolioId = "local",
            name = name,
            initials = initials,
            sortOrder = order,
        )
    }

    private fun backup(vararg members: BackupMember) = BackupFile(
        takenOn = "2026-08-12",
        portfolioName = "Birikimlerim",
        members = members.toList(),
    )

    @Test
    fun geriYuklemeVARolanUyeninAdiniGunceller() = runTest {
        val (repo, db) = newRepository()
        // Kurulumun tohumladigi uye - ayni kimlik, varsayilan ad.
        db.seedMember("member_owner", name = "Ben", initials = "B", order = 0L)

        repo.restoreBackup(
            backup(BackupMember(id = "member_owner", name = "Burak", initials = "BC")),
        )

        val uye = repo.observeMembers().first().first { it.id == "member_owner" }
        assertEquals("Burak", uye.name, "yedekteki ad alinmadi")
        assertEquals("BC", uye.initials)
    }

    @Test
    fun geriYuklemeYENIuyeyiEkler() = runTest {
        val (repo, _) = newRepository()

        repo.restoreBackup(backup(BackupMember(id = "member_partner", name = "Merve", initials = "M")))

        val uyeler = repo.observeMembers().first()
        assertEquals("Merve", uyeler.first { it.id == "member_partner" }.name)
    }

    @Test
    fun birdenFazlaUyeSIRASIYLAYazilir() = runTest {
        val (repo, db) = newRepository()
        db.seedMember("member_owner", name = "Ben", initials = "B", order = 0L)
        db.seedMember("member_partner", name = "Eş", initials = "E", order = 1L)

        repo.restoreBackup(
            backup(
                BackupMember(id = "member_owner", name = "Burak", initials = "BC"),
                BackupMember(id = "member_partner", name = "Merve", initials = "MG"),
            ),
        )

        val adlar = repo.observeMembers().first().associate { it.id to it.name }
        assertEquals("Burak", adlar["member_owner"])
        assertEquals("Merve", adlar["member_partner"])
    }
}
