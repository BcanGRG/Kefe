package com.kefe.app.data.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Masaustunde dosya secimi Swing diyaloglariyla yapilir.
 *
 * Compose Multiplatform'un kendi dosya seciciyi yok; Swing zaten JVM hedefinde
 * hazir ve isletim sisteminin yerel diyalogunu acar.
 */
actual class FileTransfer actual constructor() {

    actual suspend fun share(fileName: String, mimeType: String, content: String) {
        val chooser = JFileChooser().apply {
            dialogTitle = "Yedeği kaydet"
            selectedFile = File(fileName)
            fileFilter = filterFor(mimeType)
        }
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return

        val target = chooser.selectedFile
        withContext(Dispatchers.IO) {
            runCatching { target.writeText(content) }
                .getOrElse { throw FileTransferException("Dosya yazılamadı: ${it.message}", it) }
        }
    }

    actual suspend fun pickText(): String? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Yedek dosyasını seç"
            fileFilter = FileNameExtensionFilter("Kefe yedeği (*.json)", "json")
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return null

        val source = chooser.selectedFile ?: return null
        return withContext(Dispatchers.IO) {
            runCatching { source.readText() }
                .getOrElse { throw FileTransferException("Dosya okunamadı: ${it.message}", it) }
        }
    }

    private fun filterFor(mimeType: String): FileNameExtensionFilter = when (mimeType) {
        CsvMimeType -> FileNameExtensionFilter("CSV (*.csv)", "csv")
        else -> FileNameExtensionFilter("Kefe yedeği (*.json)", "json")
    }
}
