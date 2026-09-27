package com.kefe.app.ui.screens.transaction

import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalUnit
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.MonthPlanProgress
import com.kefe.app.domain.model.MonthVerdict
import com.kefe.app.domain.model.PlanItem
import com.kefe.app.domain.model.PlanItemProgress
import com.kefe.app.domain.model.PlanItemStatus
import com.kefe.app.domain.model.PlanTargetMode
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.planItemId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlanHintTextTest {

    private val sep = YearMonth(2026, 9)
    private val goals = listOf(goal("g_home", "Ev"), goal("g_car", "Araba"))

    @Test
    fun `plandaki varlik hedefi, hedefi ve kalani soyler`() {
        val hint = planHintOf(month(line("gold_gram", 10.0, actual = 6.0, goalId = "g_home")), "gold_gram", goals, null)!!
        assertEquals("Eylül planında: 10 gr · Ev · kalan 4 gr", hint.text)
        assertEquals(true, hint.inPlan)
        assertNull(hint.conflict)
    }

    @Test
    fun `tutar satiri TL ile, biten satir tamamlandi`() {
        val amount = line("fund_afa", 3_000.0, actual = 1_800.0, mode = PlanTargetMode.Amount)
        assertEquals("Eylül planında: ₺3.000 · kalan ₺1.200", planHintOf(month(amount), "fund_afa", goals, null)!!.text)

        val done = line("gold_gram", 10.0, actual = 12.0)
        assertEquals("Eylül planında: 10 gr · tamamlandı", planHintOf(month(done), "gold_gram", goals, null)!!.text)
    }

    @Test
    fun `planda olmayan varlik plan disi, plansiz ayda satir yok`() {
        val hint = planHintOf(month(line("gold_gram", 10.0, actual = 0.0)), "silver_gram", goals, null)!!
        assertEquals("Eylül planında yok · plan dışı sayılır", hint.text)
        assertEquals(false, hint.inPlan)

        assertNull(planHintOf(month(), "gold_gram", goals, null))
    }

    @Test
    fun `cakisma varligin hedefini ve planin hedefini adlariyla soyler`() {
        val hint = planHintOf(month(line("gold_gram", 10.0, actual = 0.0, goalId = "g_home")), "gold_gram", goals, "g_car")!!
        assertEquals(
            "Bu varlık şu an “Araba” hedefinde; plan “Ev” diyor. Değiştirmek için hedefi aşağıdan seçin.",
            hint.conflict,
        )
    }

    private fun month(vararg lines: PlanItemProgress) = MonthPlanProgress(
        month = sep,
        items = lines.toList(),
        extras = emptyList(),
        score = null,
        plannedTl = 0.0,
        extrasTl = 0.0,
        soldTl = 0.0,
        verdict = MonthVerdict.InProgress,
    )

    private fun line(
        key: String,
        target: Double,
        actual: Double,
        mode: PlanTargetMode = PlanTargetMode.Quantity,
        goalId: String? = null,
    ) = PlanItemProgress(
        item = PlanItem(planItemId(sep, key), sep, key, key, mode, target, goalId),
        boughtQuantity = actual,
        boughtTl = actual,
        soldQuantity = 0.0,
        soldTl = 0.0,
        actual = actual,
        ratio = (actual / target).coerceAtMost(1.0),
        remaining = (target - actual).coerceAtLeast(0.0),
        over = (actual - target).coerceAtLeast(0.0),
        plannedTl = null,
        status = PlanItemStatus.Partial,
    )

    private fun goal(id: String, name: String) = Goal(
        id = id,
        name = name,
        iconKey = "home",
        amount = 1_000_000.0,
        unit = GoalUnit.Try,
        targetDate = KefeDate(2027, 12, 1),
        monthlyContribution = 10_000.0,
    )
}
