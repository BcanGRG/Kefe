package com.kefe.app.data.db

import com.kefe.app.db.Activity_events
import com.kefe.app.db.Cached_prices
import com.kefe.app.db.Daily_snapshots
import com.kefe.app.db.Goals
import com.kefe.app.db.Members
import com.kefe.app.db.Portfolios
import com.kefe.app.db.Positions
import com.kefe.app.db.Transactions
import com.kefe.app.domain.model.ActivityEvent
import com.kefe.app.domain.model.DailySnapshot
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.model.Portfolio
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.PriceSource
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.model.daysSince
import com.kefe.app.domain.model.formatLong

// Satir -> alan cevrimleri.
//
// Tarihler uc INTEGER kolonda duruyor ve SQLDelight bunlari Long dondurur;
// KefeDate Int aldigi icin her cevrimde toInt() gerekir. Tek epochDay kolonu
// secilseydi geri donusum yazilmasi gerekirdi - KefeDate'in tersi yok.

internal fun Positions.toDomain(): Position = Position(
    id = id,
    name = name,
    assetClass = assetClass,
    subtype = subtype,
    karat = karat,
    quantity = quantity,
    unit = unit,
    unitPrice = unitPrice,
    // Kolondaki `value` OKUNMAZ, carpim yeniden yapilir. Iki nedenle: deger zaten
    // miktar x birim fiyat olarak yaziliyor (ayni sonuc), ve `value` Kotlin'de
    // degistirici anahtar kelime oldugu icin uretilen alan adi surumden surume
    // degisebilen bir kacis adina (value_) dusebilir.
    value = quantity * unitPrice,
    cost = cost,
    manualPrice = manualPrice,
    dailyChangePercent = dailyChangePercent,
)

internal fun Transactions.toDomain(): Transaction = Transaction(
    id = id,
    positionId = positionId,
    date = KefeDate(dateYear.toInt(), dateMonth.toInt(), dateDay.toInt()),
    side = side,
    quantity = quantity,
    unitPrice = unitPrice,
    fee = fee,
    note = note,
    storage = storage,
    addedByMemberId = addedByMemberId,
    syncState = syncState,
    createdAt = createdAt,
    goalId = goalId,
    goalDelta = goalDelta,
)

internal fun Goals.toDomain(): Goal = Goal(
    id = id,
    name = name,
    iconKey = iconKey,
    amount = amount,
    unit = unit,
    targetDate = KefeDate(targetYear.toInt(), targetMonth.toInt(), targetDay.toInt()),
    monthlyContribution = monthlyContribution,
    isMain = isMain,
    allocation = allocation,
    status = status,
    order = sortOrder.toInt(),
    estimatedArrival = kefeDateOrNull(estimatedYear, estimatedMonth, estimatedDay),
)

internal fun Members.toDomain(): Member = Member(
    id = id,
    name = name,
    initials = initials,
)

internal fun Portfolios.toDomain(memberIds: List<String>): Portfolio = Portfolio(
    id = id,
    name = name,
    currency = currency,
    memberIds = memberIds,
)

internal fun Daily_snapshots.toDomain(): DailySnapshot = DailySnapshot(
    date = KefeDate(dateYear.toInt(), dateMonth.toInt(), dateDay.toInt()),
    totalValue = totalValue,
    principal = principal,
)

/**
 * `isManual` kolon DEGIL, kaynaktan turer: kullanicinin elle girdigi degerler
 * ayri tabloda durur ve okuma sirasinda bu satirin uzerine bindirilir.
 */
internal fun Cached_prices.toDomain(): Price = Price(
    assetKey = assetKey,
    label = label,
    bid = bid,
    ask = ask,
    changePercent = changePercent,
    timestamp = timestamp,
    source = source,
    isManual = source == PriceSource.Manual,
    assetClass = assetClass,
    nativePrice = nativePrice,
    nativeCurrency = nativeCurrency,
    quoteDate = quoteDateKey?.let(::dateOfKey),
)

/** 20260731 -> 2026-07-31. Gun anahtarinin tersi (bkz. 7.sqm). */
private fun dateOfKey(key: Long): KefeDate = KefeDate(
    year = (key / 10_000L).toInt(),
    month = ((key / 100L) % 100L).toInt(),
    day = (key % 100L).toInt(),
)

/**
 * Aktivite satiri.
 *
 * `dayGroup` KOLON DEGIL: diske "BUGÜN" yazilsaydi kayit yarin da "BUGÜN" derdi.
 * Gruplama etiketi gercek tarihin bugunle farkindan uretilir.
 */
internal fun Activity_events.toDomain(today: KefeDate): ActivityEvent {
    val occurred = KefeDate(occurredYear.toInt(), occurredMonth.toInt(), occurredDay.toInt())
    return ActivityEvent(
        id = id,
        memberId = memberId,
        kind = kind,
        description = description,
        amount = amount,
        // Elde gercek saat yok (KefeClock yalniz gun veriyor); bos etiket satirda
        // hic cizilmez, uydurma bir saat yazmaktan iyidir.
        timeLabel = timeLabel.orEmpty(),
        dayGroup = dayGroupOf(occurred, today),
        isManualPrice = isManualPrice,
    )
}

private fun dayGroupOf(date: KefeDate, today: KefeDate): String {
    val days = today.daysSince(date)
    return when {
        // Ileri tarihli kayit da bugune yazilir: gelecege ait bir gun basligi
        // ("-2 GÜN ÖNCE") ekranda anlamsiz olurdu.
        days <= 0L -> "BUGÜN"
        days == 1L -> "DÜN"
        days < 7L -> "$days GÜN ÖNCE"
        else -> date.formatLong()
    }
}

/** Uc kolonun ucu de doluysa tarih vardir; biri bile bossa alan NULL demektir. */
private fun kefeDateOrNull(year: Long?, month: Long?, day: Long?): KefeDate? {
    if (year == null || month == null || day == null) return null
    return KefeDate(year.toInt(), month.toInt(), day.toInt())
}
