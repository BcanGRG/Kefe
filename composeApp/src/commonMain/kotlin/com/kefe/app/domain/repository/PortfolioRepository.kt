package com.kefe.app.domain.repository

import com.kefe.app.domain.backup.BackupFile
import com.kefe.app.domain.model.ActivityEvent
import com.kefe.app.domain.model.DailySnapshot
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.model.Portfolio
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Portfoy verisi. Uygulama yerel-oncelikli calisir: bu arayuzun ardindaki kaynak
 * her zaman cihazdaki veritabanidir, bulut yalniz esitleme katmanidir. Bu yuzden
 * okuma islemleri Flow, yazma islemleri suspend - ag beklemesi yok.
 */
interface PortfolioRepository {

    fun observePortfolio(): Flow<Portfolio>

    fun observeMembers(): Flow<List<Member>>

    /** Profilin adini ve bas harfini degistirir; satirin geri kalanina dokunmaz. */
    suspend fun renameMember(memberId: String, name: String, initials: String)

    fun observePositions(): Flow<List<Position>>

    fun observeGoals(): Flow<List<Goal>>

    /**
     * Varlik -> hedef atamasi (positionId -> goalId).
     *
     * Hedef YALNIZ kendine atanan varliklari sayar (kati atama); atama yoksa
     * ilerleme %0; bkz. [goalWealth]. Bir varlik en fazla bir hedefe atanir.
     */
    fun observeGoalAssets(): Flow<Map<String, String>>

    fun observeActivity(): Flow<List<ActivityEvent>>

    /** Gunluk anlik goruntuler - net deger ve projeksiyon grafiklerinin kaynagi. */
    fun observeSnapshots(): Flow<List<DailySnapshot>>

    fun observeTransactions(positionId: String): Flow<List<Transaction>>

    /** Tum islem defteri - portfoy geneli hesaplar (ornegin "bu ay eklenen") icin. */
    fun observeAllTransactions(): Flow<List<Transaction>>

    /**
     * Islem ekler ve ilgili pozisyonu defterden YENIDEN HESAPLAR: miktar ve
     * maliyet islemlerden turer, elle guncellenmez.
     *
     * Yeni bir varlik ekleniyorsa once [upsertPosition] ile pozisyon tanimlanmali -
     * islem tek basina varligin adini, sinifini, birimini tasimaz.
     */
    suspend fun addTransaction(transaction: Transaction)

    /** Islemi siler ve pozisyonu yeniden hesaplar. Miktar sifirlanirsa pozisyon kalkar. */
    suspend fun deleteTransaction(transactionId: String)

    /**
     * Gunun net degerini kaydeder. Ayni gune ikinci kez yazmak satiri tazeler.
     *
     * Gecmis bir gunun degeri sonradan HESAPLANAMAZ - o gunku fiyatlari da bilmek
     * gerekir, gecmis fiyatlar tutulmuyor. Fotograf o gun cekilmezse o gun kayiptir.
     */
    suspend fun recordSnapshot(snapshot: DailySnapshot)

    /**
     * Kullanici acilis akisini gecti mi?
     *
     * Kimlik dogrulama gelene kadar oturumun yerine gecer: bir kez girildikten
     * sonra uygulama dogrudan Ozet'le acilir. Aksi halde her acilista, hicbir
     * seyi dogrulamayan bir giris ekrani ve uc gereksiz dokunus gelir.
     */
    fun observeOnboarded(): Flow<Boolean>

    suspend fun markOnboarded()

    /**
     * Giris ekranini tekrar gosterilir yapar.
     *
     * Cikis yapan kullaniciyi giris ekranina gondermek yetmiyordu: "bir kez
     * girildiyse giris ekrani atlanir" kurali onu ayni karede iceri geri
     * aliyordu. Isaret temizlenince kural dogru calisir - veri silinmez, sadece
     * bu kapi yeniden kurulur.
     */
    suspend fun clearOnboarded()

    /**
     * Kullanicinin girdigi HER SEYI siler: pozisyonlar, islem defteri, hedefler,
     * aktivite, gunluk fotograflar, elle girilen fiyatlar ve tercihler.
     *
     * Geri alinamaz. Cagiran taraf ONAY ALMADAN cagirmamali.
     */
    suspend fun deleteAllData()

    /**
     * Pozisyonun kimligini/meta bilgisini yazar (ad, sinif, alt tur, ayar, birim,
     * birim fiyat). Miktar ve maliyet buradan DEGIL, islem defterinden gelir.
     */
    suspend fun upsertPosition(position: Position)

    suspend fun deletePosition(positionId: String)

    /** [goalId] null ise atama kaldirilir - varlik yeniden "tum birikim"e doner. */
    suspend fun assignPositionToGoal(positionId: String, goalId: String?)

    /** Kullanicinin girdigi her sey - yedek dosyasinin icerigi. */
    suspend fun exportBackup(takenOn: String): BackupFile

    /**
     * Yedegi geri yukler.
     *
     * TAMAMEN DEGISTIRIR: mevcut veri silinir, yedektekiler yazilir. Birlestirme
     * yapilmaz - hangi kaydin daha yeni oldugunu soyleyecek bir zaman damgasi
     * yok ve yanlis tahmin sessizce cift kayit uretirdi.
     */
    suspend fun restoreBackup(file: BackupFile)

    suspend fun upsertGoal(goal: Goal)

    suspend fun deleteGoal(goalId: String)

    suspend fun reorderGoals(orderedIds: List<String>)
}
