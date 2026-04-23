package app.hikscan.online.ui

import android.net.Uri

data class ScanGroup(
    val id: Long,
    val timestamp: Long,
    val imageUris: List<Uri>,
    val pdfUri: Uri? = null,
    val pdfFilePath: String? = null,
    var name: String = ""
)