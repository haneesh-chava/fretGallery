package com.example.fretgallery.model

enum class VerificationStatus {
    GENUINE_CERTIFIED,      // Cryptographically verified with matching Birth Certificate & Steganography
    TAMPERED_WARNING,       // Certificate found, but pixel hash or steganographic watermark altered / invalid
    UNCERTIFIED_EXTERNAL,   // No FRET Birth Certificate found (imported / external / screenshot)
    LEGACY_CAMERA           // Camera capture verified via device EXIF / DCIM path (fallback)
}

data class AuditCheck(
    val title: String,
    val description: String,
    val passed: Boolean,
    val detailValue: String? = null
)

data class VerificationResult(
    val status: VerificationStatus,
    val certificate: BirthCertificate?,
    val computedSha256: String,
    val auditLog: List<AuditCheck>,
    val verifiedAt: Long = System.currentTimeMillis()
)
