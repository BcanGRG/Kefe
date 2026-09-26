package com.kefe.app.domain.repository

import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.ExpenseEntry
import com.kefe.app.domain.model.IncomeKind
import com.kefe.app.domain.model.MonthBook
import com.kefe.app.domain.model.PlanItem
import com.kefe.app.domain.model.YearMonth
import kotlinx.coroutines.flow.Flow

/**
 * Aylik plan, gelir, gider ve butce.
 *
 * Yalniz KULLANICININ GIRDIGI saklanir. Planin yapilip yapilmadigi, seri ve
 * para akisi islem defterinden turetilir (bkz. domain/model/MonthlyPlan.kt).
 */
interface PlanRepository {

    /** Butun aylarin plan satirlari - seri ve kopyalama gecmise bakar. */
    fun observePlanItems(): Flow<List<PlanItem>>

    /** Ayin elle girilen defteri: gelir, gider, butce. */
    fun observeMonthBook(month: YearMonth): Flow<MonthBook>

    /** Butun aylarin defteri, ay sirasiyla. */
    fun observeAllBooks(): Flow<List<MonthBook>>

    suspend fun upsertPlanItem(item: PlanItem)

    /**
     * Varligi degistirilen satir: kimlik varliktan turedigi icin YENI bir
     * satirdir. Eskisi mezar taslanir, yenisi yazilir - tek islemde; araya
     * giren bir okuma iki satiri ya da hic satiri gormemeli.
     */
    suspend fun replacePlanItem(oldId: String, item: PlanItem)

    /** "Gecen ayi kopyala" - hepsi tek islemde. */
    suspend fun upsertPlanItems(items: List<PlanItem>)

    suspend fun deletePlanItem(id: String)

    /** null ya da sifir/negatif tutar satiri SILER - "girilmedi" ile "0" ayni degil. */
    suspend fun setIncome(month: YearMonth, memberId: String, kind: IncomeKind, amount: Double?)

    suspend fun upsertExpense(entry: ExpenseEntry)

    suspend fun deleteExpense(id: String)

    /** Kategori -> tutar; null ya da sifir kategori butceden cikar. Tek islem. */
    suspend fun setBudgets(month: YearMonth, amounts: Map<ExpenseCategory, Double?>)
}
