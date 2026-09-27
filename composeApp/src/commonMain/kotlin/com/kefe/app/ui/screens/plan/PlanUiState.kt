package com.kefe.app.ui.screens.plan

import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.CopyRow
import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.FundKeyPrefix
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.PlanAssetOption
import com.kefe.app.domain.model.PlanItemStatus
import com.kefe.app.domain.model.PlanTargetMode
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.StockKeyPrefix
import com.kefe.app.domain.model.StreakCell
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.parseAssetKey
import com.kefe.app.domain.model.toItems
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.parseTrAmountOrNull

// Bu dosya Compose'a BAGLI DEGIL: durum, niyet ve etki saf Kotlin; turetim ve
// metinler commonTest'te ekransiz sinanir (bkz. PlanTextTest, PlanViewModelTest).

enum class PlanStage { Loading, Ready }

/** Gosterilen ayin bugune gore yeri - basligi, notlari ve "Al"i belirler. */
enum class MonthRelation { Past, Current, Future }

data class PlanUiState(
    val stage: PlanStage = PlanStage.Loading,
    val content: PlanContent = PlanContent(),
    /** Acik sheet - ayni anda en fazla bir tane; kabuk cizer (bkz. PlanSheets). */
    val sheet: PlanSheet? = null,
)

/** Veriden TURETILEN her sey; toplayici YALNIZ bunu degistirir, acik sheet'e dokunmaz. */
data class PlanContent(
    val header: PlanHeader = PlanHeader(),
    /** Ayin yatirim plani; kalem yoksa null ve yerine [emptyPlan] cizilir. */
    val investment: InvestmentCard? = null,
    val emptyPlan: EmptyPlanCard? = null,
    /** Plan disi alimlar; yoksa null (kart gizli). */
    val extras: ExtrasCard? = null,
    /** Ayin kalemlerinin baglandigi hedefler; bossa kart gizli. */
    val goalContributions: List<GoalContributionRow> = emptyList(),
    /** "Para akışı": plan ve gerceklesen yan yana; gelir satirlari giris noktasi oldugu icin hep var. */
    val flow: MoneyFlowCard? = null,
    /** "Giderler": bos ayda da cizilir - harcama ve butcenin giris noktasi. */
    val expenses: ExpensesCard? = null,
    /** Kac aydir duzenli; hic plan yoksa null (kart gizli). Gosterilen aydan bagimsiz, bugune gore. */
    val streak: StreakCard? = null,
    /**
     * Yan menudeki Plan rozeti: BU AYIN tamamlanmamis kalemleri - secili ayin degil.
     * Rozet her ekranda gorunur; gecmis bir aya bakmak onu degistirmemeli.
     */
    val currentMonthOpenCount: Int = 0,
)

/**
 * Ay basligi: "< Ekim 2026 >" ve alt satir.
 *
 * Gecis sinirlari ([canGoBack], [canGoForward]) veriden gelir: geriye ilk
 * plan/islem/defter ayina kadar, ileriye EN FAZLA bir ay (onayli karar).
 */
data class PlanHeader(
    val month: YearMonth? = null,
    /** "Ekim 2026". */
    val title: String = "",
    /** "Ekim · 9 gün kaldı" | "Geçmiş ay · alımlar işlem tarihine göre sayılır." */
    val subtitle: String = "",
    val relation: MonthRelation = MonthRelation.Current,
    val canGoBack: Boolean = false,
    /** En fazla 1 ay ileri. */
    val canGoForward: Boolean = false,
    /** Baska bir aydayken "Bu ay" cipi - tek dokunusla geri donus. */
    val showThisMonthChip: Boolean = false,
)

// --- Yatirim plani -----------------------------------------------------------

data class InvestmentCard(
    /** "%72" | "—" (gelecek ay: henuz olculecek bir sey yok). */
    val scoreText: String,
    /** 0..1; null -> cubuk cizilmez (gelecek ay). */
    val score: Float?,
    /**
     * "Seri 4 ay" - YALNIZ bu ayin sayfasinda ve seri >= 2 iken. Seri bugune gore
     * sayilir; gecmis ya da gelecek ayin kartinda o ayin serisi gibi okunurdu.
     */
    val streakText: String? = null,
    /** "3/5 kalem · ₺70.330 planlandı". */
    val summary: String,
    val rows: List<PlanRowUi>,
    /**
     * Alttaki kesikli "kopyala" karti. Kaynak ay yoksa YA DA kaynagin her varligi
     * bu ayda zaten planliysa null: ikinci bir kopya ayni eksigi bir daha tasirdi.
     */
    val copyLabel: String?,
)

data class PlanRowUi(
    val itemId: String,
    val assetKey: String,
    /** Ikon ve renk; anahtar cozulemezse null (cuzdan ikonu). */
    val assetClass: AssetClass?,
    /** Eldeki en buyuk pozisyonun adi; elde yoksa planlandigi andaki ad. */
    val name: String,
    /** Hedef cipi; hedef silinmisse null. */
    val goalName: String?,
    /** "6 / 10 gr" | "₺2.000 / ₺3.000". */
    val progressText: String,
    val ratio: Float,
    val status: PlanItemStatus,
    /** "+2 gr fazla", "Bu ay 1 gr satıldı" | "Ay içinde 1 gr satıldı". */
    val notes: List<String>,
    /** "Al": yalniz bu ay ve kalem tamamlanmadiysa (ekleme sayfasi her alimi bugune yazar). */
    val canBuy: Boolean,
)

data class EmptyPlanCard(
    /** "Ekim için plan yok". */
    val title: String,
    val body: String,
    /** "Geçen ayı kopyala (Eylül)" | "Ağustos planını kopyala" | null (kaynak yok). */
    val copyLabel: String?,
)

/** "Plan dışı alımlar": planlanmamis alimlar ve hedefi asan kisimlar. */
data class ExtrasCard(val total: String, val rows: List<ExtraRowUi>)

data class ExtraRowUi(
    val assetKey: String,
    val assetClass: AssetClass?,
    val name: String,
    /** "Planda yok · 2 gr" | "Hedefin üstünde". */
    val detail: String,
    val amount: String,
)

/** "Hedeflere katkı" satiri - ayin kalemlerinin bir hedefe dusen kismi. */
data class GoalContributionRow(
    val goalId: String,
    val name: String,
    val iconKey: String,
    /** "planlanan ₺40.000 / aylık katkı ₺50.000 · gereken ₺61.200". */
    val line: String,
    /** planlanan / aylik katki, 1'de kirpilir; katki ya da planlanan bilinmiyorsa null. */
    val ratio: Float?,
)

// --- Para akisi ve giderler ----------------------------------------------------

/**
 * "Para akışı" karti. Hic girilmemis bir rakam "—" kalir, 0 degil: gider
 * girilmemis bir ay "Tasarruf oranı %100" demez (bkz. PlanDerive.moneyFlowCard).
 */
data class MoneyFlowCard(
    /** Gelir, Gider, Yatırım, Kalan; karsilastiracak hicbir sey yoksa null (tablo cizilmez). */
    val table: List<FlowRow>?,
    /** "Tasarruf oranı %32" | "Gider gelirin %112'si" | null (gelir ya da gider yok, gelecek ay). */
    val savingsLine: String?,
    /** "Plan gelirin %38'i". */
    val planShareLine: String?,
    /** "Satışlar ₺12.000" - net yatirim ile brut alim arasindaki farki aciklar. */
    val salesLine: String?,
    /** Her uye - gelir girisinin kapisi, girilmemisse tutar "—". */
    val incomeRows: List<IncomeRowUi>,
)

data class FlowRow(
    val label: String,
    /** Bilinmeyen "—". */
    val planned: String,
    val actual: String,
    /** Ekran okuyucu tek cumle okur: "Gelir: plan ₺85.000, gerçekleşen ₺85.000"; "—" -> "yok". */
    val spoken: String,
)

data class IncomeRowUi(
    val memberId: String,
    val name: String,
    val initials: String,
    /** Avatar rengi - uye listesindeki sira. */
    val index: Int,
    val amount: String,
)

data class ExpensesCard(
    /** "₺18.400 / ₺25.000" | "₺18.400" | "Harcama girilmedi.". */
    val totalLine: String,
    /** Harcanan / butce; butce yoksa null (cubuk cizilmez). */
    val totalRatio: Float?,
    /** "₺2.300 aşıldı" - asimin sinyali metindir, renk yalniz eslik eder. */
    val totalOverText: String?,
    /** Harcamasi VE butcesi olmayan kategori listede yok. */
    val categories: List<CategoryRowUi>,
    /** En yeni 5 giris. */
    val recent: List<ExpenseRowUi>,
) {
    /**
     * Ayda ne harcama ne butce var: [totalLine] bir TUTAR degil, bir not.
     * NEYDI: "Harcama girilmedi." tutar basligi boyutunda (h2) ciziliyor,
     * kartin en buyuk yazisi bos durumun kendisi oluyordu.
     */
    val isEmpty: Boolean get() = totalRatio == null && categories.isEmpty() && recent.isEmpty()
}

data class CategoryRowUi(
    val category: ExpenseCategory,
    val label: String,
    /** "₺4.200 / ₺5.000" | "₺4.200". */
    val amounts: String,
    val ratio: Float?,
    val overText: String?,
)

data class ExpenseRowUi(
    val id: String,
    val title: String,
    /** "14 Eki · market" - gun, kisa ay ve varsa not. */
    val subtitle: String,
    val amount: String,
)

// --- Seri ----------------------------------------------------------------------

/** "Seri" karti: kac ay ust uste duzenli ve son 12 ayin izgarasi. */
data class StreakCard(
    /** "4 ay üst üste düzenli" | "Bu ay düzenli" | "Geçen ay düzenli" | seri 0 metni. */
    val headline: String,
    /** "En uzun 6 · Son 12 ayda 9/12". */
    val detail: String,
    /** Eskiden yeniye; son hucre bu ay. */
    val cells: List<StreakCellUi>,
    val rule: String = "Planın en az %80'i yapılan ay düzenli sayılır.",
)

data class StreakCellUi(
    /** "Eki" - kisa ay adi. */
    val label: String,
    val cell: StreakCell,
    /** Ekran okuyucu: "Ekim 2026: sürüyor". */
    val description: String,
    val isCurrent: Boolean,
)

// --- Sheet'ler (ayni anda en fazla bir tane) ------------------------------------

sealed interface PlanSheet {
    data class Item(val editor: PlanItemEditor) : PlanSheet
    data class Copy(val draft: CopyDraftUi) : PlanSheet
    data class Income(val editor: IncomeEditor) : PlanSheet
    data class Expense(val editor: ExpenseEditor) : PlanSheet
    data class Budget(val editor: BudgetEditor) : PlanSheet
}

/**
 * Plan kalemi editoru.
 *
 * Kullanicinin yazdigi alanlar ([targetText], [codeText]) HAM tutulur ve hicbir
 * veri emisyonu onlari degistirmez; alttaki baglam alanlari ([options] ve
 * sonrasi) her emisyonda tazelenir (bkz. PlanDerive.withContext).
 */
data class PlanItemEditor(
    /** Sheet'in acildigi ay; gun donse de degismez - kayit bu aya yazilir. */
    val month: YearMonth,
    /** Duzenlenen kalem; null = yeni. */
    val editingId: String? = null,
    /** Secili katalog/elde varligi (cip). Kod alani acikken null. */
    val assetKey: String? = null,
    /** Fund/Stock: kod alani acik ("+ Fon kodu" / "+ Hisse sembolü"). */
    val codeClass: AssetClass? = null,
    val codeText: String = "",
    val mode: PlanTargetMode = PlanTargetMode.Quantity,
    /** HAM ("3000", "2,5"); binlik ayrac yalniz cizimde. */
    val targetText: String = "",
    val goalId: String? = null,
    /** Secilen varlik bu ay zaten planliydi; editor o kaleme gecti. */
    val switchedToExisting: Boolean = false,
    /**
     * Varlik secili olsa da liste acik mi ("Değiştir"e basildi).
     *
     * NEYDI: varlik listesi her zaman acikti. Eldekiler + katalog + fonlar
     * telefonda bir ekrandan uzundu; miktar ve hedef alanlari listenin ALTINDA
     * kaliyordu, her kalemde en asagi kaydirmak gerekiyordu. Artik secilince
     * liste tek satira iner.
     */
    val pickerOpen: Boolean = false,
    val assetError: String? = null,
    val targetError: String? = null,
    // --- baglam: her veri emisyonunda tazelenir ---
    val options: List<PlanAssetOption> = emptyList(),
    val goalChips: List<GoalChipUi> = emptyList(),
    /** Guncel ALIS birim fiyati; bilinmiyorsa null (tahmin gizlenir). */
    val unitPrice: Double? = null,
    /** Varlik su an baska bir hedefe atanmis - uyari. */
    val conflictGoalName: String? = null,
) {
    val isNew: Boolean get() = editingId == null

    /** Liste tek satira inmis: katalogdan bir varlik secili ve liste acilmadi. */
    val pickerCollapsed: Boolean get() = assetKey != null && codeClass == null && !pickerOpen
}

data class GoalChipUi(val goalId: String, val name: String)

/**
 * "Kopyala/Devir" taslagi.
 *
 * [carry] "Taşı" secilen varliklar; VARSAYILAN BOS = hepsi "Bırak" (onayli
 * karar: devir sorulur, kendiliginden olmaz).
 */
data class CopyDraftUi(
    val source: YearMonth,
    val target: YearMonth,
    val rows: List<CopyRow>,
    val carry: Set<String> = emptySet(),
) {
    /** Kaynakta olup hedefte henuz olmayan bir varlik var mi - kopyanin anlami bu. */
    val hasNewRows: Boolean get() = rows.any { !it.alreadyInTarget }
}

/**
 * Bir uyenin ayin geliri. Metinler HAM; bos alan kayitta satiri siler -
 * "girilmedi" ile "0" ayni degil.
 */
data class IncomeEditor(
    val month: YearMonth,
    val memberId: String,
    /** Yalniz alt baslikta ("Burak · Ekim 2026"): "Ben'in geliri" gibi bir ek bozuk okunurdu. */
    val memberName: String,
    val salaryText: String,
    val extraText: String,
    /** Bir onceki ayin girisi - "Geçen ay: ₺85.000 — aynısı" cipi; yoksa null. */
    val lastSalary: Double?,
    val lastExtra: Double?,
)

/**
 * Tek harcama. Kimlik editor ACILIRKEN uretilir, kayitta degil: cift dokunus
 * ayni satiri iki kez yazar, iki satir olusturmaz.
 */
data class ExpenseEditor(
    val month: YearMonth,
    val id: String,
    val isNew: Boolean,
    val category: ExpenseCategory?,
    val amountText: String,
    val note: String,
    /** Bu ayda bugun, baska ayda o ayin son gunu (onayli kural); duzenlemede kayitli tarih. */
    val date: KefeDate,
    /** 0 = yeni; depo kayitta ani damgalar. Duzenlemede sira (en yeni) degismesin diye korunur. */
    val createdAt: Long = 0L,
    val addedByMemberId: String?,
    val categoryError: Boolean = false,
    val amountError: Boolean = false,
)

/** Ayin kategori butceleri. Bos metin o kategoriyi butceden cikarir. */
data class BudgetEditor(
    val month: YearMonth,
    val texts: Map<ExpenseCategory, String>,
    /** Gecen ay kategoride harcanan - alan ipucu. */
    val lastSpent: Map<ExpenseCategory, Double>,
    /** Gecen ayin butcesi - "Geçen aydan kopyala". */
    val lastBudgets: Map<ExpenseCategory, Double>,
    /** Ayin geliri; girilmemisse null (toplamin gelire orani yazilmaz). */
    val income: Double?,
)

sealed interface PlanIntent {
    data object PreviousMonth : PlanIntent
    data object NextMonth : PlanIntent

    /** "Bu ay" cipi: secim bosaltilir, sayfa gun donunce kendiliginden ilerler. */
    data object ThisMonth : PlanIntent

    data object DismissSheet : PlanIntent

    // --- Yatirim plani ---
    data object AddItem : PlanIntent
    data class EditItem(val itemId: String) : PlanIntent

    /** "Al": 4/5'te mevcut ekleme sayfasi acilir (on doldurma 5/5'te). */
    data class Buy(val itemId: String) : PlanIntent
    data object OpenCopy : PlanIntent

    // --- Kalem editoru ---
    data class ItemSelectAsset(val assetKey: String) : PlanIntent

    /** Tek satira inmis varlik listesini yeniden acar. */
    data object ItemChangeAsset : PlanIntent

    /** Fund | Stock: katalogda olmayan bir fon kodu / hisse sembolu yazilacak. */
    data class ItemOpenCode(val assetClass: AssetClass) : PlanIntent
    data class ItemCode(val value: String) : PlanIntent
    data class ItemMode(val mode: PlanTargetMode) : PlanIntent
    data class ItemTarget(val value: String) : PlanIntent
    data class ItemStep(val up: Boolean) : PlanIntent
    data class ItemGoal(val goalId: String?) : PlanIntent
    data object SaveItem : PlanIntent
    data object DeleteItem : PlanIntent

    // --- Kopyala/Devir ---
    data class CopyCarry(val assetKey: String, val carry: Boolean) : PlanIntent
    data object ConfirmCopy : PlanIntent

    // --- Defter: gelir ---
    data class EditIncome(val memberId: String) : PlanIntent
    data class IncomeSalary(val value: String) : PlanIntent
    data class IncomeExtra(val value: String) : PlanIntent
    data object IncomeUseLastSalary : PlanIntent
    data object IncomeUseLastExtra : PlanIntent
    data object SaveIncome : PlanIntent

    // --- Defter: gider ---
    data object AddExpense : PlanIntent
    data class EditExpense(val id: String) : PlanIntent
    data class ExpenseSelectCategory(val category: ExpenseCategory) : PlanIntent
    data class ExpenseAmount(val value: String) : PlanIntent
    data class ExpenseNote(val value: String) : PlanIntent
    data object SaveExpense : PlanIntent
    data object DeleteExpense : PlanIntent

    // --- Defter: butce ---
    data object EditBudget : PlanIntent
    data class BudgetAmount(val category: ExpenseCategory, val value: String) : PlanIntent
    data object BudgetCopyLastMonth : PlanIntent
    data object SaveBudget : PlanIntent
}

/** Kabukta karsilanir (bkz. App.kt): ekleme sayfasi ve serit kabugun. */
sealed interface PlanEffect {
    /** "Al": 4/5'te mevcut ekleme sayfasi; varlik eldeyse onunla, degilse varlik secimiyle. */
    data class OpenAddTransaction(val positionId: String?) : PlanEffect

    /** Kabugun saveError seridi. */
    data class Message(val text: String) : PlanEffect
}

// --- Saf okuyucular (ekran ve VM ayni kurali kullanir) ---------------------------

/**
 * Kaydedilecek varlik anahtari: secili cip, yoksa yazilan kod.
 *
 * Kod, pozisyon anahtariyla AYNI bicimde olmali (bkz. AssetKey.kt CodePattern):
 * "afa" -> "fund_afa", "THYAO.IS" -> "stock_thyao.is". Bicime uymayan kod null
 * doner - kayit "Geçerli bir kod girin." der.
 */
fun PlanItemEditor.resolvedKey(): String? {
    assetKey?.let { return it }
    val prefix = when (codeClass) {
        AssetClass.Fund -> FundKeyPrefix
        AssetClass.Stock -> StockKeyPrefix
        else -> return null
    }
    val code = codeText.trim()
    return if (PlanCodePattern.matches(code)) prefix + code.lowercase() else null
}

/** Secili varligin sinifi (cip ya da acik kod alani); hicbiri yoksa null. */
fun PlanItemEditor.selectedClass(): AssetClass? =
    codeClass ?: assetKey?.let { parseAssetKey(it)?.assetClass }

/** Secili varligin miktar birimi; bilinmiyorsa null. */
fun PlanItemEditor.selectedUnit(): QuantityUnit? = resolvedKey()?.let { parseAssetKey(it)?.unit }

/** Hedef alaninin birim etiketi: tutarda "₺", miktarda varligin birimi ("gr", "adet", "$"). */
fun PlanItemEditor.unitLabel(): String = when (mode) {
    PlanTargetMode.Amount -> Money.LIRA
    PlanTargetMode.Quantity -> resolvedKey()?.let(::planUnitLabel).orEmpty()
}

/** Hedef alaninin etiketi - segmentle ayni kelime; "Hedef" hedef ciplerinin adi. */
fun PlanItemEditor.targetLabel(): String = when (mode) {
    PlanTargetMode.Quantity -> "Miktar"
    PlanTargetMode.Amount -> "Tutar"
}

/** Nakitte miktar zaten TL'dir - Miktar/Tutar secimi anlamsiz. */
fun PlanItemEditor.showModeSwitch(): Boolean = selectedClass() != AssetClass.Cash

/**
 * "≈ ₺67.330 (güncel fiyatla)" - yalniz miktar hedefinde ve fiyat biliniyorsa.
 * Tutar hedefinde rakam zaten TL; fiyati olmayan fonda tahmin uydurulmaz.
 */
fun PlanItemEditor.estimateText(): String? {
    if (mode != PlanTargetMode.Quantity) return null
    val price = unitPrice?.takeIf { it > 0.0 } ?: return null
    val target = targetText.parseTrAmountOrNull()?.takeIf { it > 0.0 } ?: return null
    return "≈ ${Money.tl(target * price)} (güncel fiyatla)"
}

/**
 * Secenekler sinif sirasiyla gruplu (Altin, Gumus, Doviz, Fon, Hisse, Nakit).
 * Fon ve Hisse grubu bos olsa da vardir: "+ Fon kodu" / "+ Hisse sembolü"
 * cipleri orada durur - hic fon tutmayan biri de fon planlayabilmeli.
 */
fun PlanItemEditor.groups(): List<Pair<AssetClass, List<PlanAssetOption>>> =
    AssetClass.entries
        .map { cls -> cls to options.filter { it.assetClass == cls } }
        .filter { (cls, list) -> list.isNotEmpty() || cls == AssetClass.Fund || cls == AssetClass.Stock }

/**
 * Onaylaninca yazilacak satir sayisi. Zaten planli olup devri secilmeyen satir
 * yazilmaz (bkz. toItems) - fiyat sayimi etkilemez.
 */
fun CopyDraftUi.writableCount(): Int = rows.toItems(target, carry) { null }.size

/**
 * Butce sayfasinin alt satiri: "Toplam ₺25.000 · gelirin %29'u". Gelir yoksa
 * yalniz toplam - oran uydurulmaz.
 */
fun BudgetEditor.totalLine(): String {
    val total = texts.values.sumOf { it.parseTrAmountOrNull()?.takeIf { v -> v > 0.0 } ?: 0.0 }
    val share = income?.takeIf { it > 0.0 }?.let { " · gelirin ${trPercentOf(total / it)}" }.orEmpty()
    return "Toplam ${Money.tl(total)}$share"
}

/** Pozisyon anahtarindaki kod bicimi (AssetKey.kt ile ayni). */
private val PlanCodePattern = Regex("^[A-Za-z0-9][A-Za-z0-9.\\-]{1,14}$")
