package com.kefe.app.ui.screens.plan

import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.PlanItemProgress
import com.kefe.app.domain.model.PlanItemStatus
import com.kefe.app.domain.model.PlanStreak
import com.kefe.app.domain.model.PlanTargetMode
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.StreakCell
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.daysInMonth
import com.kefe.app.domain.model.monthName
import com.kefe.app.domain.model.parseAssetKey
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.maxQuantityDecimals
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.round

// Plan sekmesinin metinleri. SAF ve Compose'suz: her cumle commonTest'te sinanir
// (bkz. PlanTextTest).

/**
 * Ay basliginin alt satiri.
 *
 * Bu ay kalan gunu soyler ("Ekim · 9 gün kaldı"; son gun "Ekim · son gün" -
 * "0 gün kaldı" bitmis gibi okunur). Gecmis ayda olcunun ne oldugunu soyler:
 * alimlar islem TARIHINE gore sayilir, sonradan girilen gecmis tarihli bir alim
 * o aya yazilir. Gelecek ayda henuz sayilacak bir sey yoktur.
 */
internal fun headerSubtitle(month: YearMonth, today: KefeDate): String =
    when (monthRelation(month, today)) {
        MonthRelation.Current -> {
            val left = daysInMonth(month.year, month.month) - today.day
            if (left <= 0) "${month.monthName()} · son gün" else "${month.monthName()} · $left gün kaldı"
        }
        MonthRelation.Past -> "Geçmiş ay · alımlar işlem tarihine göre sayılır."
        MonthRelation.Future -> "Gelecek ay · alımlar ay başlayınca sayılır."
    }

// --- Yatirim plani -----------------------------------------------------------

/**
 * Kalem durumunun kelimesi. Rozette ikonla birlikte durur: renk TEK sinyal
 * degil (bkz. PlanScreen StatusBadge).
 */
internal fun PlanItemStatus.label(): String = when (this) {
    PlanItemStatus.Done -> "Tamam"
    PlanItemStatus.Partial -> "Kısmen"
    PlanItemStatus.Waiting -> "Bekliyor"
    PlanItemStatus.Missed -> "Kaçtı"
    PlanItemStatus.Upcoming -> "Yakında"
}

/**
 * Anahtarin miktar birimi: "gr", "adet", "pay", doviz simgesi ("$", "€", "£"),
 * nakitte "₺". Cozulemeyen anahtarda bos - birim uydurulmaz.
 */
internal fun planUnitLabel(assetKey: String): String {
    val info = parseAssetKey(assetKey) ?: return ""
    return when (info.unit) {
        QuantityUnit.Gram -> "gr"
        QuantityUnit.Piece -> "adet"
        QuantityUnit.Share -> "pay"
        QuantityUnit.Lot -> "adet"
        QuantityUnit.Currency ->
            if (info.assetClass == AssetClass.Cash) Money.LIRA else info.currency?.symbol().orEmpty()
    }
}

/** Miktar, birimin tasiyabilecegi haneyle ama yalniz GERCEKTEN tasidigi kadar: "2,5", "10". */
internal fun quantityText(value: Double, unit: QuantityUnit?): String =
    Money.number(value, Money.decimals(value, unit?.maxQuantityDecimals() ?: 2))

/** Miktar + birim; birim bossa yalniz miktar. */
private fun withUnit(text: String, unit: String): String = if (unit.isEmpty()) text else "$text $unit"

/** Anahtarin biriminde miktar: "2 gr", "3 adet", "500 $". */
internal fun quantityLabel(value: Double, assetKey: String): String =
    withUnit(quantityText(value, parseAssetKey(assetKey)?.unit), planUnitLabel(assetKey))

/** Hedef metni: miktar hedefinde birimiyle ("10 gr"), tutar hedefinde TL ("₺3.000"). */
internal fun planTargetText(mode: PlanTargetMode, target: Double, assetKey: String): String = when (mode) {
    PlanTargetMode.Quantity -> quantityLabel(target, assetKey)
    PlanTargetMode.Amount -> Money.tl(target)
}

/** Satirin ilerlemesi: "6 / 10 gr", "300 / 500 $", "₺2.000 / ₺3.000". */
internal fun progressText(p: PlanItemProgress): String = when (p.item.mode) {
    PlanTargetMode.Quantity -> withUnit(
        "${quantityText(p.actual, p.item.unit)} / ${quantityText(p.item.target, p.item.unit)}",
        planUnitLabel(p.item.assetKey),
    )
    PlanTargetMode.Amount -> "${Money.tl(p.actual)} / ${Money.tl(p.item.target)}"
}

/**
 * Satirin notlari: hedefin ustu ve ay icindeki satis.
 *
 * Satis notunun oneki aya gore: bu ayda "Bu ay" (onayli metin), baska aylarda
 * "Ay içinde". NEDEN: sayfa gecmis ayi da gosterir; orada "Bu ay" yanlis aya
 * isaret eder.
 */
internal fun rowNotes(p: PlanItemProgress, relation: MonthRelation): List<String> = buildList {
    val unit = planUnitLabel(p.item.assetKey)
    if (p.over > 0.0) {
        add(
            when (p.item.mode) {
                PlanTargetMode.Quantity -> "+${withUnit(quantityText(p.over, p.item.unit), unit)} fazla"
                PlanTargetMode.Amount -> "+${Money.tl(p.over)} fazla"
            },
        )
    }
    if (p.soldQuantity > 0.0) {
        val prefix = if (relation == MonthRelation.Current) "Bu ay" else "Ay içinde"
        val amount = when (p.item.mode) {
            PlanTargetMode.Quantity -> withUnit(quantityText(p.soldQuantity, p.item.unit), unit)
            PlanTargetMode.Amount -> Money.tl(p.soldTl)
        }
        add("$prefix $amount satıldı")
    }
}

/**
 * Ayin skoru: "%72". Tamamlanmadan "%100" YAZILMAZ - %99,6 "%99" kalir (hedef
 * kartindaki kuralla ayni: yuvarlama tavanda durur). Gelecek ayda "—".
 */
internal fun scoreText(score: Double?): String = when {
    score == null -> "—"
    score >= 1.0 - 1e-6 -> Money.ratio(100.0)
    else -> Money.ratio(min(round(score * 100.0), 99.0))
}

/**
 * Plan kartinin altindaki kopyalama girisi. Ekler sabit kelimelere ("ay",
 * "plan") eklenir, ay adina degil - "Ağustos'tan" gibi bir ek her ayda ayri
 * kural isterdi.
 */
internal fun copyButtonLabel(source: YearMonth, target: YearMonth): String =
    if (source == target.previous()) "Geçen aydan kopyala" else "${source.monthName()} planından kopyala"

/** Bos plan kartinin dugmesi: "Geçen ayı kopyala (Eylül)" | "Ağustos planını kopyala". */
internal fun emptyCopyLabel(source: YearMonth, target: YearMonth): String =
    if (source == target.previous()) "Geçen ayı kopyala (${source.monthName()})" else "${source.monthName()} planını kopyala"

// --- Seri --------------------------------------------------------------------

/**
 * Seri kartinin basligi. Seri 1 iken hangi ayin sayildigi soylenir: bu ay henuz
 * %80'in altindaysa seri GECEN aydan sayilir (ici bulunulan ay seriyi bozmaz, bkz.
 * planStreak) - "Bu ay düzenli" o zaman yanlis olurdu. "1 ay üst üste" kotu okunur.
 */
internal fun streakHeadline(streak: PlanStreak): String = when {
    streak.current >= 2 -> "${streak.current} ay üst üste düzenli"
    streak.current == 1 -> if (streak.grid.lastOrNull()?.second.isRegular()) "Bu ay düzenli" else "Geçen ay düzenli"
    else -> "Bu ay planın %80'i yapılınca seri başlar."
}

/** "En uzun 6 · Son 12 ayda 9/12". */
internal fun streakDetail(streak: PlanStreak): String {
    val window = streak.grid.size
    return "En uzun ${streak.longest} · Son $window ayda ${streak.regularInWindow}/$window"
}

/** Hucrenin ekran okuyucu metni; aciklamadaki kelimeyle ayni ("sürüyor" - "Sürüyor"). */
internal fun StreakCell.spoken(): String = when (this) {
    StreakCell.Full -> "tamamı yapıldı"
    StreakCell.Partial -> "düzenli (%80 ve üstü)"
    StreakCell.Missed -> "kaçtı"
    StreakCell.NoPlan -> "plan yok"
    StreakCell.BeforeStart -> "plan başlamadan önce"
    StreakCell.InProgress -> "sürüyor"
}

/**
 * Plan kartindaki "Seri 4 ay": yalniz bu ayin sayfasinda ve seri >= 2 iken. Seri
 * bugune gore sayilir; baska bir ayin kartinda o ayin serisi gibi okunurdu.
 */
internal fun streakBadgeText(streak: PlanStreak?, relation: MonthRelation): String? =
    streak?.current?.takeIf { relation == MonthRelation.Current && it >= 2 }?.let { "Seri $it ay" }

private fun StreakCell?.isRegular(): Boolean = this == StreakCell.Full || this == StreakCell.Partial

// --- Para akisi --------------------------------------------------------------

/**
 * Turkce iyelik ekiyle yuzde: "%38'i", "%29'u", "%112'si". Yuzdeyi iyelikle
 * yazmanin TEK yolu.
 *
 * Bir kez yuvarlar (yarim yukari, Money.format gibi) ve rakam ile eki AYNI
 * tamsayidan kurar. NEDEN: Money.ratioOf kendi yuvarlar; ek ayri bir sayidan
 * hesaplansaydi 37,6 icin "%38'si" gibi uyumsuz bir cift cikardi.
 */
internal fun trPercentOf(fraction: Double): String {
    val percent = floor(abs(fraction) * 100.0 + 0.5).toInt()
    return Money.ratio(percent.toDouble()) + trPercentSuffix(percent)
}

/**
 * Sayidan sonra 3. tekil iyelik eki - sayinin SESLI okunan son kelimesine gore:
 * 38 "otuz sekiz" -> sekiz-i, 112 "yuz on iki" -> iki-si, 100 "yuz" -> yuz-u,
 * 1000 "bin" -> bin-i. Tablo testi icin internal (bkz. PlanTextTest).
 */
internal fun trPercentSuffix(percent: Int): String {
    val n = abs(percent)
    if (n == 0) return "'ı"
    if (n % 100 == 0) return if (n % 1000 == 0) "'i" else "'ü"
    val ones = n % 10
    if (ones != 0) {
        return when (ones) {
            1, 5, 8 -> "'i"
            2, 7 -> "'si"
            3, 4 -> "'ü"
            6 -> "'sı"
            else -> "'u" // 9 dokuz
        }
    }
    return when ((n / 10) % 10) {
        1, 3 -> "'u" // on, otuz
        2, 5 -> "'si" // yirmi, elli
        4, 6, 9 -> "'ı" // kirk, altmis, doksan
        else -> "'i" // 7 yetmis, 8 seksen
    }
}
