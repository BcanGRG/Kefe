package com.kefe.app.domain

import com.kefe.app.domain.model.KefeDate
import java.util.Calendar

/**
 * java.time yerine Calendar: minSdk 24'te java.time yalniz desugaring ile gelir,
 * projede desugaring acik degil. Calendar her surumde var.
 */
actual fun currentDate(): KefeDate {
    val now = Calendar.getInstance()
    return KefeDate(
        year = now.get(Calendar.YEAR),
        // Calendar.MONTH sifir tabanli.
        month = now.get(Calendar.MONTH) + 1,
        day = now.get(Calendar.DAY_OF_MONTH),
    )
}

actual fun currentEpochMillis(): Long = System.currentTimeMillis()
