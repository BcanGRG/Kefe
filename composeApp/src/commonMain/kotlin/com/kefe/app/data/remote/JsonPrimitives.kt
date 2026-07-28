package com.kefe.app.data.remote

import kotlinx.serialization.json.JsonPrimitive

/**
 * Dis kaynaklar tip konusunda tutarsizdir: ayni alan bir gun sayi, ertesi gun
 * string ya da bos gelebilir. Bu iki yardimci "okuyabiliyorsan oku, yoksa null"
 * davranisini tek yerde toplar - her cagri yerinde try/catch yazmamak icin.
 */
internal fun JsonPrimitive.doubleOrNullSafe(): Double? {
    if (isString && content.isBlank()) return null
    return content.replace(',', '.').toDoubleOrNull()
}

internal fun JsonPrimitive.contentOrNullSafe(): String? = content.takeIf { it.isNotBlank() }
