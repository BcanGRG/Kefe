package com.kefe.app.security

/**
 * Hassas metni cihaza sertlestirerek yazar/okur.
 *
 * NEDEN SIMDI: oturum jetonu su ana kadar veritabaninda DUZ METIN duruyordu.
 * Icinde veri olmayan bir hesaba erisim verdigi surece bu kabul edilebilirdi;
 * senkron acilinca jeton tum birikim gecmisi demek olacak. Android Keystore ile
 * sifreleyip yine ayni tabloda (sifreli) saklariz - yeni tablo, migration yok.
 *
 * [protect] sifreler, [reveal] cozer. Cozemedigi metni OLDUGU GIBI dondurur:
 * bu surumden onceki duz-metin oturumlar boylece patlamadan okunur ve ilk
 * yenilemede kendiliginden sifreliye doner.
 *
 * Masaustunde donanim destekli guvenli depo yok; oradaki actual metni oldugu
 * gibi birakir (belgelenmis). Asil hedef cihaz Android.
 */
expect class SecureStore() {
    fun protect(plain: String): String
    fun reveal(stored: String): String
}
