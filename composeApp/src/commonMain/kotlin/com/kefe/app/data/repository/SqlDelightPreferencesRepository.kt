package com.kefe.app.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class SqlDelightPreferencesRepository(
    database: KefeDatabase,
    private val dispatcher: CoroutineContext = Dispatchers.Default,
) : PreferencesRepository {

    private val settingQueries = database.settingQueries

    override fun observeAll(): Flow<Map<String, String>> =
        settingQueries.selectAllSettings().asFlow().mapToList(dispatcher)
            .map { rows -> rows.associate { it.settingKey to it.settingValue } }

    override suspend fun put(key: String, value: String) {
        withContext(dispatcher) {
            settingQueries.upsertSetting(settingKey = key, settingValue = value)
        }
    }

    override suspend fun get(key: String): String? =
        withContext(dispatcher) {
            settingQueries.selectSetting(key).executeAsOneOrNull()
        }
}
