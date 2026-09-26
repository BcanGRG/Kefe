package com.kefe.app.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cikis YALNIZ bu cihazi kapatir.
 *
 * NEDEN. Kapsamsiz cikis GoTrue'da `global`: iki telefonun paylastigi hesapta
 * birinin "Hesaptan çık"i ya da Ozet'teki "Vazgeç"i, digerinin yenileme
 * jetonunu da iptal ediyor ve o telefon bir sure sonra "Oturum kapandı"ya
 * dusuyordu.
 */
class LogoutUrlTest {

    @Test
    fun `cikis yalniz bu cihazin oturumunu kapatir`() {
        assertEquals(
            "https://x.supabase.co/auth/v1/logout?scope=local",
            logoutUrl("https://x.supabase.co"),
        )
    }
}
