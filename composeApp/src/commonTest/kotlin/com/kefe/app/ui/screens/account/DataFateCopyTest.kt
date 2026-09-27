package com.kefe.app.ui.screens.account

import com.kefe.app.data.db.LocalOwnerMemberId
import com.kefe.app.data.db.LocalPartnerMemberId
import com.kefe.app.data.sync.CloudMode
import com.kefe.app.data.sync.CloudStatus
import com.kefe.app.data.sync.ConflictChoice
import com.kefe.app.ui.format.trGenitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Baglanma, cikis, sifirlama ve geri yukleme VERIYE NE OLACAGINI soyler.
 *
 * NEYDI: "Çıkış yap" dogrudan cikiyor, "Tüm verileri sil" hesapli cihazda da
 * ayni metni gosteriyordu (ve silinenler bir pull'la geri iniyordu), geri
 * yukleme bagli cihazda yedegin eski halini hesabin ustune yaziyordu. Metinler
 * saf fonksiyonlarda; burada her modun metni sabitlenir.
 */
class DataFateCopyTest {

    private val cloud = CloudMode.Cloud("e@k.app", CloudStatus.Synced)
    private val pending = CloudMode.LinkPending("e@k.app")
    private val lost = CloudMode.SessionLost("e@k.app")

    // --- Hesaptan cikis ------------------------------------------------------

    @Test
    fun `cikis onayi kayitlarin kaldigini ve esin telefonunu soyler`() {
        val d = signOutDialog(partnerName = "Merve", unsentChanges = false)
        assertEquals("Hesaptan çıkılsın mı?", d.title)
        assertEquals(
            "Eşitleme bu cihazda durur. Kayıtlarınız bu cihazda kalır, hesapsız kullanmaya devam " +
                "edersiniz; Merve'nin telefonu hesapla eşitlenmeye devam eder.",
            d.message,
        )
        assertEquals("Hesaptan çık", d.confirmLabel)
    }

    @Test
    fun `gitmemis degisiklik varsa cikis onayi uyarir`() {
        val d = signOutDialog(partnerName = null, unsentChanges = true)
        assertTrue("eşinizin telefonu" in d.message, d.message)
        assertTrue(
            d.message.endsWith("Hesaba henüz gönderilmemiş değişiklikler var; çıkarsanız yalnız bu cihazda kalır."),
            d.message,
        )
    }

    // --- Sifirlama / silme ------------------------------------------------------

    @Test
    fun `hesapsiz cihazda silme tek kopyayi hatirlatir`() {
        assertEquals("Tüm verileri sil", deleteRowLabel(CloudMode.Local))
        assertEquals("Tüm verileri sil", deleteRowLabel(null))
        val d = deleteDialog(CloudMode.Local, partnerName = "Merve")
        assertEquals("Tüm verileri sil", d.title)
        assertEquals(
            "Bu cihazdaki varlıklar, işlemler, hedefler, planlar ve tercihler silinir. Hesap " +
                "kullanmadığınız için başka bir kopyası yok — önce yedek almak isteyebilirsiniz. " +
                "Bu işlem geri alınamaz.",
            d.message,
        )
        assertEquals("Sil", d.confirmLabel)
    }

    @Test
    fun `hesapla ilgili her modda satir bu cihazi sifirlar`() {
        for (mode in listOf(cloud, pending, lost)) {
            assertTrue(resetsAccount(mode), "$mode")
            assertEquals("Bu cihazı sıfırla", deleteRowLabel(mode))
            val d = deleteDialog(mode, partnerName = "Burak Can")
            assertEquals("Bu cihaz sıfırlansın mı?", d.title)
            assertEquals(
                "Hesaptan çıkılır ve bu cihazdaki kayıtlar ile tercihler silinir. Hesabınızdaki " +
                    "kayıtlar ve Burak Can'ın telefonu etkilenmez; aynı e-postayla girdiğinizde geri gelir.",
                d.message,
            )
            assertEquals("Sıfırla", d.confirmLabel)
        }
        assertFalse(resetsAccount(CloudMode.Local))
        assertFalse(resetsAccount(null))
    }

    /**
     * Plan tablolari bir sonraki adimda esitlenecek: "yalnız bu cihazda" diyen
     * bir metin o gun yalan olurdu. Hicbir metin bunu soylememeli.
     */
    @Test
    fun `hicbir metin plan tablolarini cihaza bagli saymaz`() {
        val texts = buildList {
            for (mode in listOf(CloudMode.Local, cloud, pending, lost, null)) {
                add(deleteDialog(mode, "Merve").message)
                dataFootnote(mode, cloudConfigured = true)?.let(::add)
                add(restoreLockedMessage(mode))
            }
            add(signOutDialog("Merve", true).message)
        }
        texts.forEach { text ->
            assertFalse("Plan, gelir" in text, text)
            assertFalse("yalnız bu cihazda" in text && "Plan" in text, text)
        }
    }

    // --- Geri yukleme ve not -----------------------------------------------------

    @Test
    fun `bagliyken geri yukleme kapali`() {
        assertTrue(restoreLocked(cloud))
        assertTrue(restoreLocked(lost), "oturumu dusen cihaz ayni hesaba sorusuz doner")
        assertFalse(restoreLocked(CloudMode.Local))
        assertFalse(restoreLocked(pending), "yarim baglantida sonraki baglanti soru sorar")
        assertFalse(restoreLocked(null))
        assertEquals("Hesaba bağlıyken kapalı", RestoreLockedValue)
        assertEquals(
            "Hesaba bağlıyken yüklenen yedek hesaptaki kayıtların yerine geçemez, onlarla karışır. " +
                "Yedeği yüklemek için önce Hesaptan çık.",
            restoreLockedMessage(cloud),
        )
        assertTrue(restoreLockedMessage(lost).endsWith("önce Hesapsız devam et."))
    }

    @Test
    fun `veri notu moda gore`() {
        assertEquals(
            "Hesap kullanmadığınız için kayıtların tek kopyası bu cihazda. Ara sıra yedek alın.",
            dataFootnote(CloudMode.Local, cloudConfigured = true),
        )
        assertEquals("Kayıtlarınız hesabınızda da saklanıyor.", dataFootnote(cloud, cloudConfigured = true))
        assertNull(dataFootnote(pending, cloudConfigured = true))
        assertNull(dataFootnote(CloudMode.Local, cloudConfigured = false))
        assertNull(dataFootnote(null, cloudConfigured = true))
    }

    @Test
    fun `tamlayan eki`() {
        assertEquals("Merve'nin", "Merve".trGenitive())
        assertEquals("Burak Can'ın", "Burak Can".trGenitive())
        assertEquals("Ayşe'nin", "Ayşe".trGenitive())
        assertEquals("Volkan'ın", "Volkan".trGenitive())
        assertEquals("Onur'un", "Onur".trGenitive())
        assertEquals("Gül'ün", "Gül".trGenitive())
        assertEquals("Ali'nin", "Ali".trGenitive())
        assertEquals("Doğu'nun", "Doğu".trGenitive())
        assertEquals("IŞIK'ın", "IŞIK".trGenitive())
        assertEquals("eşinizin telefonu", partnerPhone(null))
        assertEquals("eşinizin telefonu", partnerPhone(" "))
        assertEquals("Merve'nin telefonu", partnerPhone("Merve"))
    }

    // --- Baglanti adimi ------------------------------------------------------------

    @Test
    fun `cakisma metni`() {
        val c = conflictCopy(localRecords = 12, serverRecords = 30)
        assertEquals("Bu cihazda da, hesabınızda da kayıt var", c.title)
        assertEquals("Bu cihaz: 12 kayıt · Hesap: 30 kayıt", c.body)
        assertEquals("Hesaptakileri kullan", c.useAccountTitle)
        assertEquals("Bu cihazdaki kayıtlar silinir.", c.useAccountNote)
        assertEquals("Birleştir", c.mergeTitle)
        assertEquals(
            "Aynı alımı iki cihaza da girdiyseniz iki kez sayılır; sonra Aktivite'den silebilirsiniz.",
            c.mergeNote,
        )
    }

    @Test
    fun `secim degisince cihazdaki kayitlarin aktarimi soylenir`() {
        val base = ProfileSetupUiState(
            linking = true,
            ownerName = "Burak Can",
            partnerName = "Merve",
            previousMemberId = LocalOwnerMemberId,
            localOnlyByAuthor = mapOf(LocalOwnerMemberId to 4),
        )
        assertNull(base.copy(thisDeviceIsOwner = null).remapNote(), "secim yokken not yok")
        assertNull(base.copy(thisDeviceIsOwner = true).remapNote(), "ayni profil: aktarim yok")
        assertEquals(
            "Bu cihazda daha önce girilen 4 kayıt da Merve adına aktarılır.",
            base.copy(thisDeviceIsOwner = false).remapNote(),
        )
        assertEquals(
            "Bu cihazda daha önce girilen 4 kayıt da 2. profil adına aktarılır.",
            base.copy(thisDeviceIsOwner = false, partnerName = "").remapNote(),
        )
        assertNull(base.copy(thisDeviceIsOwner = false, linking = false).remapNote())
        assertNull(base.copy(thisDeviceIsOwner = false, localOnlyByAuthor = emptyMap()).remapNote())
        assertNull(base.copy(thisDeviceIsOwner = false, previousMemberId = null).remapNote())
        assertNull(
            base.copy(thisDeviceIsOwner = true, previousMemberId = LocalPartnerMemberId).remapNote(),
            "onceki profilin cihaza ozgu kaydi yok",
        )
    }

    @Test
    fun `cakismadan gelen secimin notu`() {
        assertNull(ProfileSetupUiState().choiceNote())
        assertEquals(
            "Devam edince bu cihazdaki kayıtlar silinir, hesaptakiler gelir.",
            ProfileSetupUiState(conflictChoice = ConflictChoice.UseAccount).choiceNote(),
        )
        assertEquals(
            "Devam edince bu cihazdaki kayıtlar hesaptakilerle birleştirilir.",
            ProfileSetupUiState(conflictChoice = ConflictChoice.Merge).choiceNote(),
        )
    }
}
