package com.kefe.app.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phoenix/Realtime protokolunun bizim urettigimiz yani. Soket acmadan
 * dogrulanabilen tek kisim burasi - cerceveler yanlissa sunucu sessizce hicbir
 * sey gondermez (join basarili doner, olay gelmez), yani hata ekranda
 * gorunmez. Bu testler o sessiz basarisizligi onceden yakalar.
 */
class RealtimeProtocolTest {

    private val json = Json

    @Test
    fun `soket adresi https yerine wss kullanir`() {
        val url = realtimeSocketUrl("https://abc.supabase.co", "anon-key")

        assertTrue(url.startsWith("wss://abc.supabase.co/realtime/v1/websocket"), url)
        assertTrue(url.contains("apikey=anon-key"), url)
        // vsn 1.0.0: cerceveler nesne bicimindedir. 2.0.0 dizi bekler ve
        // gonderdiklerimizi hic anlamazdi.
        assertTrue(url.contains("vsn=1.0.0"), url)
    }

    @Test
    fun `adresin sonundaki egik cizgi cift kalmaz`() {
        val url = realtimeSocketUrl("https://abc.supabase.co/", "k")

        assertFalse(url.contains(".co//"), url)
    }

    @Test
    fun `join cercevesi yedi tabloyu da abone eder`() {
        val frame = json.parseToJsonElement(joinFrame(RealtimeTables, "jwt", ref = 1))
            .jsonObject

        val changes = frame["payload"]!!.jsonObject["config"]!!
            .jsonObject["postgres_changes"]!!.jsonArray

        assertEquals(7, changes.size)
        assertEquals(
            RealtimeTables.toSet(),
            changes.map { it.jsonObject["table"]!!.jsonPrimitive.content }.toSet(),
        )
        // "*": ekleme kadar guncelleme ve silme (mezar tasi) de degisikliktir.
        assertTrue(changes.all { it.jsonObject["event"]!!.jsonPrimitive.content == "*" })
        assertTrue(changes.all { it.jsonObject["schema"]!!.jsonPrimitive.content == "public" })
    }

    @Test
    fun `join cercevesi KULLANICININ jetonunu tasir`() {
        // Jetonsuz join'de RLS istegin kimin adina geldigini bilemez ve akis
        // sessizce bos kalir - adim 9'daki "Bearer = kullanicinin jetonu" ile ayni.
        val frame = json.parseToJsonElement(joinFrame(RealtimeTables, "jwt-123", ref = 1))
            .jsonObject

        assertEquals(
            "jwt-123",
            frame["payload"]!!.jsonObject["access_token"]!!.jsonPrimitive.content,
        )
        assertEquals("phx_join", frame["event"]!!.jsonPrimitive.content)
        assertEquals(RealtimeTopic, frame["topic"]!!.jsonPrimitive.content)
    }

    @Test
    fun `heartbeat phoenix topicine gider`() {
        // Veri topic'ine gonderilen heartbeat sayilmaz; sunucu ~30 sn sonra
        // baglantiyi duserur ve kopus sebebi hicbir yerde yazmaz.
        val frame = json.parseToJsonElement(heartbeatFrame(ref = 7)).jsonObject

        assertEquals("phoenix", frame["topic"]!!.jsonPrimitive.content)
        assertEquals("heartbeat", frame["event"]!!.jsonPrimitive.content)
        assertEquals("7", frame["ref"]!!.jsonPrimitive.content)
    }

    @Test
    fun `jeton tazeleme cercevesi veri topicine gider`() {
        val frame = json.parseToJsonElement(accessTokenFrame("yeni-jwt", ref = 9)).jsonObject

        assertEquals(RealtimeTopic, frame["topic"]!!.jsonPrimitive.content)
        assertEquals("access_token", frame["event"]!!.jsonPrimitive.content)
        assertEquals(
            "yeni-jwt",
            frame["payload"]!!.jsonObject["access_token"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `yalniz veri degisikligi sinyal sayilir`() {
        assertTrue(isPostgresChange("""{"event":"postgres_changes","payload":{}}"""))

        // Join cevabi ve heartbeat cevabi her baglantida gelir; sinyal sayilsalardi
        // her yeniden baglanma sebepsiz bir pull tetiklerdi.
        assertFalse(isPostgresChange("""{"event":"phx_reply","payload":{}}"""))
        assertFalse(isPostgresChange("""{"event":"presence_state"}"""))
        assertFalse(isPostgresChange("bozuk json"))
        assertFalse(isPostgresChange(""))
    }

    @Test
    fun `join cevabindaki tablo sayisi okunur`() {
        val reply = """
            {"event":"phx_reply","topic":"$RealtimeTopic","payload":{"status":"ok",
             "response":{"postgres_changes":[{"id":1,"table":"positions"},{"id":2,"table":"goals"}]}}}
        """.trimIndent()

        assertEquals(2, joinReplyTableCount(reply))
        // Heartbeat cevabi bizim topic'imizde degil - join cevabi sayilmamali.
        assertEquals(null, joinReplyTableCount(heartbeatFrame(ref = 1)))
        assertEquals(null, joinReplyTableCount("""{"event":"postgres_changes"}"""))
        assertEquals(null, joinReplyTableCount("bozuk"))
    }

    @Test
    fun `yayina eklenmemis tablo abonelik reddi olarak okunur`() {
        // Emulatorde birebir gelen mesaj: join "status ok" doner, abonelik yine
        // de reddedilir. Tek gercek olcu bu satirdir - sessiz basarisizligin
        // gorunur oldugu yer.
        val systemError = """
            {"event":"system","payload":{"status":"error",
             "message":"Unable to subscribe to changes with given parameters. Please check Realtime is enabled for the given connect parameters: [event: *, schema: public, table: activity_events, filters: [], select: nil]"}}
        """.trimIndent()

        assertTrue(realtimeProblem(systemError)!!.contains("activity_events"))
        // Basarili cevaplar ve olaylar hata degildir.
        assertEquals(null, realtimeProblem("""{"event":"phx_reply","payload":{"status":"ok"}}"""))
        assertEquals(null, realtimeProblem("""{"event":"postgres_changes","payload":{}}"""))
        assertEquals(null, realtimeProblem("bozuk"))
    }

    @Test
    fun `bekleme ustel artar ve tavanda durur`() {
        assertEquals(2_000L, nextBackoff(1_000L))
        assertEquals(4_000L, nextBackoff(2_000L))
        assertEquals(MaxBackoffMillis, nextBackoff(MaxBackoffMillis))
        assertEquals(MaxBackoffMillis, nextBackoff(MaxBackoffMillis * 4))
    }
}
