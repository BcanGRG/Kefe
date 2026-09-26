package com.kefe.app.domain.backup

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

/** Plan alanlari olmayan ESKI bir yedek hala okunur; listeler bos gelir. */
class PlanBackupCodecTest {

    @Test
    fun eskiYedekBosListelerleOkunur() {
        val old = """{"version":1,"takenOn":"2026-08-01","portfolioName":"Ev"}"""
        val file = Json { ignoreUnknownKeys = true }.decodeFromString(BackupFile.serializer(), old)
        assertTrue(file.planItems.isEmpty())
        assertTrue(file.incomes.isEmpty())
        assertTrue(file.expenses.isEmpty())
        assertTrue(file.budgets.isEmpty())
    }
}
