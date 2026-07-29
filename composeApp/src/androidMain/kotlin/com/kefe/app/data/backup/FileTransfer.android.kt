package com.kefe.app.data.backup

import android.content.Intent
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android dosya kopruleri.
 *
 * Paylasim icin FileProvider sart: Android 7'den beri `file://` URI'si baska bir
 * uygulamaya verilemez (FileUriExposedException). Dosya onbellege yazilir ve
 * icerik URI'siyle paylasilir - onbellek, cunku kopya kullanicinin sectigi yere
 * gittikten sonra bizde kalmasinin bir anlami yok.
 *
 * Secici ise Activity sonucu ister; launcher [AndroidFileBridge] uzerinden
 * MainActivity'de kaydedilir. onCreate icinde kaydedilmezse Android
 * "LifecycleOwners must call register before they are STARTED" diye atar.
 */
actual class FileTransfer actual constructor() {

    actual suspend fun share(fileName: String, mimeType: String, content: String) {
        val activity = AndroidFileBridge.activity
            ?: throw FileTransferException("Ekran hazır değil.")

        val uri = withContext(Dispatchers.IO) {
            val dir = File(activity.cacheDir, "backups").apply { mkdirs() }
            val file = File(dir, fileName)
            file.writeText(content)
            FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(intent, "Yedeği kaydet"))
    }

    actual suspend fun pickText(): String? {
        val activity = AndroidFileBridge.activity
            ?: throw FileTransferException("Ekran hazır değil.")
        val launcher = AndroidFileBridge.openLauncher
            ?: throw FileTransferException("Dosya seçici hazır değil.")

        val pending = CompletableDeferred<Uri?>()
        AndroidFileBridge.pending = pending
        // Yedek JSON'u bazi dosya yoneticilerinde octet-stream olarak gelir;
        // yalniz application/json istenirse dosya secilemez halde gorunur.
        launcher.launch(arrayOf("application/json", "text/plain", "*/*"))

        val uri = pending.await() ?: return null
        return withContext(Dispatchers.IO) {
            activity.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: throw FileTransferException("Dosya açılamadı.")
        }
    }
}

/**
 * Activity ve dosya secici koprusu.
 *
 * KefePlatform veritabani icin ne yapiyorsa bu da secici icin onu yapar: ortak
 * kod Activity'yi goremez, giris noktasi burayi doldurur.
 */
object AndroidFileBridge {

    internal var activity: FragmentActivity? = null
    internal var openLauncher: ActivityResultLauncher<Array<String>>? = null
    internal var pending: CompletableDeferred<Uri?>? = null

    fun attach(activity: FragmentActivity, launcher: ActivityResultLauncher<Array<String>>) {
        this.activity = activity
        this.openLauncher = launcher
    }

    /** Activity yok edilirken birakilir - sizinti olmasin. */
    fun detach(activity: FragmentActivity) {
        if (this.activity === activity) {
            this.activity = null
            this.openLauncher = null
        }
    }

    fun deliver(uri: Uri?) {
        pending?.complete(uri)
        pending = null
    }
}
