package com.kefe.app.data.remote

import com.kefe.app.domain.model.KefeDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * TEFAS'in `tarih` alani. Bicim CANLI SONDA ile olculdu, tahmin edilmedi:
 * `-Pprobe --tests "*LivePriceProbeTest"` -> `date=2026-07-31`.
 *
 * Ayristirici yaniltici olmaktansa sessiz kalmali: bicim degisirse null doner,
 * o satir gecmise yazilmaz ve fon da altin/doviz gibi yerel gecmise duser.
 * Yanlis bir tarih yazmak, haftalik degisimi sessizce bozardi.
 */
class TefasDateTest {

    @Test
    fun canliSondadanGelenBicim() {
        assertEquals(KefeDate(2026, 7, 31), parseIsoDate("2026-07-31"))
    }

    @Test
    fun bosluklarKirpilir() {
        assertEquals(KefeDate(2026, 1, 5), parseIsoDate("  2026-01-05  "))
    }

    @Test
    fun tanimadigimizBicimNullDoner() {
        assertNull(parseIsoDate("31.07.2026"))
        assertNull(parseIsoDate("1785110400000"))
        assertNull(parseIsoDate("2026-07"))
        assertNull(parseIsoDate(""))
        assertNull(parseIsoDate(null))
    }

    @Test
    fun anlamsizAyVeGunNullDoner() {
        assertNull(parseIsoDate("2026-13-01"))
        assertNull(parseIsoDate("2026-07-32"))
        assertNull(parseIsoDate("2026-00-10"))
    }
}
