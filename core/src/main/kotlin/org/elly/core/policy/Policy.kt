package org.elly.core.policy

import org.elly.core.model.*
import kotlinx.datetime.*

interface SalaryDetectionPolicy {
    fun detectSalary(tx: List<TxRecord>, windowDays: Int = 10): Boolean
}

class HeuristicSalaryDetection : SalaryDetectionPolicy {
    override fun detectSalary(tx: List<TxRecord>, windowDays: Int): Boolean {
        // простая эвристика: входящий платеж в коридоре суммы и дат + ключевые слова
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        return tx.any { it.amount.amount > 0 && it.description?.contains("зарп", ignoreCase = true) == true &&
                (now.dayOfMonth in (20 downTo 5).map { ((it - 1) % 31) + 1 }) } // упростили “окно” — улучшим позже
    }
}
