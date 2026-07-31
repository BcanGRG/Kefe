package com.kefe.app.data.remote

import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * ELLE CALISTIRILAN SONDA - normal kosuda haric tutulur (bkz. build.gradle.kts,
 * `LivePriceProbeTest` ile ayni gerekce): gercek Supabase'e baglanir, yani
 * testleri internete ve ucuncu taraf calisma suresine baglar.
 *
 *   ./gradlew :composeApp:desktopTest --tests "*RealtimeProbeTest" -i
 *
 * NE KANITLAR: adresin dogru turetildigini, WebSockets eklentisinin kurulu
 * oldugunu, masaustu motorunun (cio) wss'i kaldirdigini ve sunucunun heartbeat'e
 * cevap verdigini.
 *
 * NE KANITLAMAZ: veri olaylarinin aktigini. Onun icin KULLANICININ jetonu ve
 * yayina eklenmis tablolar gerekir - o kanit iki gercek cihazla alinir
 * (bkz. ILERLEME.md adim 11 dogrulamasi).
 */
class RealtimeProbeTest {

    @Test
    fun `soket kurulur ve heartbeat cevaplanir`() {
        if (!SupabaseConfig.isConfigured) {
            println("Realtime sonda ATLANDI: local.properties'te SUPABASE_URL/ANON_KEY yok.")
            return
        }

        val client = createKefeHttpClient()
        val url = realtimeSocketUrl(SupabaseConfig.Url, SupabaseConfig.AnonKey)
        // Anahtar loga YAZILMAZ: depoda tutulmamasinin gerekcesi burada da gecerli.
        println("Realtime sonda: ${url.substringBefore("?apikey=")}")

        val reply = runBlocking {
            withTimeoutOrNull(15_000) {
                var seen: String? = null
                client.webSocket(url) {
                    send(Frame.Text(heartbeatFrame(ref = 1)))
                    for (frame in incoming) {
                        val text = (frame as? Frame.Text)?.readText() ?: continue
                        println("Realtime sonda <- $text")
                        seen = text
                        break
                    }
                }
                seen
            }
        }
        client.close()

        assertTrue(reply != null, "sunucudan cevap gelmedi - adres ya da anahtar yanlis olabilir")
        assertTrue(reply.contains("phx_reply"), "beklenen heartbeat cevabi degil: $reply")
    }
}
