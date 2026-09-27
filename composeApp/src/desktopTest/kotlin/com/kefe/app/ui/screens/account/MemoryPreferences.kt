package com.kefe.app.ui.screens.account

import com.kefe.app.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Bellekte tercih deposu: veritabani gerektirmeyen VM testleri icin. putAll
 * tek adimda yazar (gercek depodaki tek islem gibi).
 */
internal class MemoryPreferences(initial: Map<String, String> = emptyMap()) : PreferencesRepository {
    val values = MutableStateFlow(initial)

    override fun observeAll(): Flow<Map<String, String>> = values

    override suspend fun put(key: String, value: String) {
        values.value = values.value + (key to value)
    }

    override suspend fun get(key: String): String? = values.value[key]

    override suspend fun putAll(changes: Map<String, String?>) {
        val next = values.value.toMutableMap()
        changes.forEach { (key, value) -> if (value == null) next.remove(key) else next[key] = value }
        values.value = next
    }
}
