package com.kefe.app.data.backup

/**
 * Yedek dosyasini disari verme ve iceri alma.
 *
 * Uygulama kendi klasorune yazip "kaydettim" DEMEZ: oradaki dosya uygulama
 * silininceye kadar yasar, yani silinen uygulamayla birlikte gider ve yedegin
 * tek isi tam o durumda ise yaramaktir. Bu yuzden [share] dosyayi kullanicinin
 * secebilecegi bir yere (Drive, Dosyalar, WhatsApp) gonderir.
 *
 * [pickText] geri yukleme icin dosya sectirir; iptal edilirse null doner.
 */
expect class FileTransfer() {
    suspend fun share(fileName: String, mimeType: String, content: String)
    suspend fun pickText(): String?
}

/** Dosya isleminin platform tarafinda basarisiz olmasi. */
class FileTransferException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

const val JsonMimeType: String = "application/json"
const val CsvMimeType: String = "text/csv"
