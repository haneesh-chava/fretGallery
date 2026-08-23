package com.example.fretgallery.model

import android.net.Uri

data class GalleryItem(
    val uri: Uri,
    val name: String,
    val dateAdded: Long,
    val sizeBytes: Long,
    var verificationResult: VerificationResult? = null,
    var isChecking: Boolean = false
) {
    val status: VerificationStatus
        get() = verificationResult?.status ?: VerificationStatus.UNCERTIFIED_EXTERNAL
}
