package com.kefe.app.data.backup

/**
 * iOS tarafi henuz kurulmadi.
 *
 * Paylasim `UIActivityViewController`, secim `UIDocumentPickerViewController`
 * ister; ikisi de bir UIViewController referansi gerektiriyor ve derlenip
 * denenebilecegi bir Mac yok. Sessizce hicbir sey yapmak yerine ACIKCA atar -
 * kullanici "yedek aldim" sanmasin.
 */
actual class FileTransfer actual constructor() {

    actual suspend fun share(fileName: String, mimeType: String, content: String) {
        throw FileTransferException("Yedekleme iOS'ta henüz hazır değil.")
    }

    actual suspend fun pickText(): String? {
        throw FileTransferException("Geri yükleme iOS'ta henüz hazır değil.")
    }
}
