package com.kefe.app.data.db

import com.kefe.app.data.sample.SampleData
import com.kefe.app.data.sample.SampleSeries
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.model.KefeDate

/**
 * Ornek portfoyu veritabanina yazar - YALNIZ GELISTIRME ICIN.
 *
 * Varsayilan acilis bos: kullanici kendi islemlerini girecek ve tasarim
 * teslimindeki 13 pozisyon + 21 islem arasindan kendi kaydini ayiklamak zorunda
 * kalmamali. Ama ornek veri dolu ekranlarin (grafik, gruplar, aktivite akisi)
 * nasil gorundugunu denemek icin duruyor: [com.kefe.app.di.SeedSampleData]
 * bayragi acilip veritabani dosyasi silindiginde bu tohum uygulanir.
 *
 * Ayni tek seferlik bayragi kullanir: tohum uygulandiysa varsayilan kurulum
 * (bos portfoy + sahip uye) calismaz, iki portfoy olusmaz.
 */
fun KefeDatabase.seedSampleDataIfEmpty(today: KefeDate) {
    transaction {
        if (settingQueries.selectSetting(BootstrapKey).executeAsOneOrNull() != null) {
            return@transaction
        }

        val portfolio = SampleData.portfolio
        portfolioQueries.insertOrIgnorePortfolio(
            id = portfolio.id,
            name = portfolio.name,
            currency = portfolio.currency,
        )
        // Uye satiri portfoye baglidir; sira ekrandaki avatar sirasini belirler.
        SampleData.members.forEachIndexed { index, member ->
            portfolioQueries.insertOrIgnoreMember(
                id = member.id,
                portfolioId = portfolio.id,
                name = member.name,
                initials = member.initials,
                sortOrder = index.toLong(),
            )
        }

        // Pozisyonlar islemlerden ONCE: defterin yabanci anahtari pozisyona bakar.
        SampleData.positions.forEach { position ->
            positionQueries.insertOrIgnorePosition(
                id = position.id,
                name = position.name,
                assetClass = position.assetClass,
                subtype = position.subtype,
                karat = position.karat,
                unit = position.unit,
                unitPrice = position.unitPrice,
                manualPrice = position.manualPrice,
                // Ornek veri: zaman damgasi 0, esitlemede en eski sayilir.
                updatedAt = 0L,
                // Kolon NOT NULL; deger okuma aninda fiyat tahtasindan bindiriliyor.
                dailyChangePercent = position.dailyChangePercent ?: 0.0,
            )
            // Ornek pozisyonlarin cogunda defter yok; miktar/maliyet defterden
            // hesaplansaydi hepsi sifirlanip listeden duserdi. Bu yuzden ornek
            // rakamlar dogrudan yazilir. Deger carpimla yazilir ki okuma tarafi
            // (miktar x birim fiyat) ayni sonucu versin.
            positionQueries.updatePositionComputed(
                position.quantity,
                position.cost,
                position.quantity * position.unitPrice,
                position.id,
            )
        }

        // Ayni gun ici sira tohumlama sirasindan gelir (bkz. 8.sqm); ornek
        // defterde ayni tarihli kayitlar bu yuzden karismaz.
        SampleData.transactions.forEachIndexed { index, entry ->
            transactionQueries.insertTransaction(
                id = entry.id,
                positionId = entry.positionId,
                dateYear = entry.date.year.toLong(),
                dateMonth = entry.date.month.toLong(),
                dateDay = entry.date.day.toLong(),
                side = entry.side,
                quantity = entry.quantity,
                unitPrice = entry.unitPrice,
                fee = entry.fee,
                note = entry.note,
                storage = entry.storage,
                addedByMemberId = entry.addedByMemberId,
                // Ornek veri: zaman damgasi 0, esitlemede en eski sayilir.
                updatedAt = 0L,
                syncState = entry.syncState,
                createdAt = index.toLong() + 1L,
                // Ornek defterde atama katkisi yok: atamalar dogrudan
                // yaziliyor, islemlerden turemiyor.
                goalId = null,
                goalDelta = 0.0,
            )
        }

        // Tohumlama bos veritabanina yazar: tek adim yeterli.
        SampleData.goals.forEach { goal ->
            goalQueries.insertOrIgnoreGoal(
                id = goal.id,
                name = goal.name,
                iconKey = goal.iconKey,
                amount = goal.amount,
                unit = goal.unit,
                targetYear = goal.targetDate.year.toLong(),
                targetMonth = goal.targetDate.month.toLong(),
                targetDay = goal.targetDate.day.toLong(),
                monthlyContribution = goal.monthlyContribution,
                isMain = goal.isMain,
                status = goal.status,
                sortOrder = goal.order.toLong(),
                // Ornek veri: zaman damgasi 0, esitlemede en eski sayilir.
                updatedAt = 0L,
            )
        }

        // Ornek akisin gun bilgisi ekran metninde ("2 sa önce") saklidir, veride
        // degil; hepsi bugune yazilir, gun basliklari okurken tarihten uretilir.
        SampleData.activity.reversed().forEach { event ->
            activityQueries.insertActivity(
                id = event.id,
                memberId = event.memberId,
                kind = event.kind,
                description = event.description,
                amount = event.amount,
                isManualPrice = event.isManualPrice,
                occurredYear = today.year.toLong(),
                occurredMonth = today.month.toLong(),
                occurredDay = today.day.toLong(),
                timeLabel = event.timeLabel,
                // Ornek veri: zaman damgasi 0, esitlemede en eski sayilir.
                updatedAt = 0L,
            )
        }

        SampleSeries.netWorthSnapshots.forEach { snapshot ->
            snapshotQueries.upsertSnapshot(
                dateYear = snapshot.date.year.toLong(),
                dateMonth = snapshot.date.month.toLong(),
                dateDay = snapshot.date.day.toLong(),
                totalValue = snapshot.totalValue,
                // Ornek veri: zaman damgasi 0, esitlemede en eski sayilir.
                updatedAt = 0L,
                principal = snapshot.principal,
            )
        }

        settingQueries.upsertSetting(
            settingKey = BootstrapKey,
            settingValue = BootstrapValue,
        )
    }
}
