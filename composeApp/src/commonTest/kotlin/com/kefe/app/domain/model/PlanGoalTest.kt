package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Planin hedefle yumusak bagi.
 *
 * Plan mevcut atamayi ASLA ezmez: baska hedefe atanmis bir varlikta planin
 * hedefini secmek varligi tasir ve eski hedef butun atamasini kaybeder.
 */
class PlanGoalTest {

    private val goals = setOf("ev", "araba")

    @Test
    fun atamasizVarliktaPlaninHedefiOnsecilir() {
        val s = planGoalSelection(null, "ev", isBuy = true, isEditing = false, knownGoalIds = goals)
        assertEquals("ev", s.selectedGoalId)
        assertNull(s.conflictGoalId)
    }

    @Test
    fun baskaHedefteyseEZILMEZCakismaBildirilir() {
        val s = planGoalSelection("araba", "ev", isBuy = true, isEditing = false, knownGoalIds = goals)
        assertEquals("araba", s.selectedGoalId)
        assertEquals("araba", s.conflictGoalId)
    }

    @Test
    fun satistaVeDuzenlemedeDokunulmaz() {
        assertNull(planGoalSelection(null, "ev", isBuy = false, isEditing = false, knownGoalIds = goals).selectedGoalId)
        assertNull(planGoalSelection(null, "ev", isBuy = true, isEditing = true, knownGoalIds = goals).selectedGoalId)
    }

    @Test
    fun silinmisHedefOnsecilmez() {
        assertNull(planGoalSelection(null, "silinmis", isBuy = true, isEditing = false, knownGoalIds = goals).selectedGoalId)
    }

    private fun goal(amount: Double, target: KefeDate, status: GoalStatus = GoalStatus.Active) = Goal(
        id = "ev", name = "Ev", iconKey = "home", amount = amount, unit = GoalUnit.Try,
        targetDate = target, monthlyContribution = 0.0, status = status,
    )

    @Test
    fun gerekenAylikKalanAylaraBolunur() {
        val g = goal(1_000_000.0, KefeDate(2027, 10))
        assertEquals(50_000.0, g.requiredMonthly(400_000.0, KefeDate(2026, 10, 15))!!, 1e-9)
    }

    @Test
    fun hedefAyindaTekAySayilir() {
        val g = goal(100.0, KefeDate(2026, 10))
        assertEquals(40.0, g.requiredMonthly(60.0, KefeDate(2026, 10, 20))!!, 1e-9)
    }

    @Test
    fun ulasilmisTamamlanmisGecikmis() {
        assertEquals(0.0, goal(100.0, KefeDate(2027, 1)).requiredMonthly(150.0, KefeDate(2026, 10)))
        assertNull(goal(100.0, KefeDate(2027, 1), GoalStatus.Completed).requiredMonthly(10.0, KefeDate(2026, 10)))
        assertNull(goal(100.0, KefeDate(2026, 8)).requiredMonthly(10.0, KefeDate(2026, 10)))
    }
}
