package com.example.fretgallery.stego

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import com.example.fretgallery.crypto.CryptoSigner
import com.example.fretgallery.model.AuditCheck
import com.example.fretgallery.model.BirthCertificate
import com.example.fretgallery.model.ExifTelemetry
import com.example.fretgallery.model.VerificationResult
import com.example.fretgallery.model.VerificationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object SteganographyEngine {

    private val MAGIC_HEADER = byteArrayOf('F'.code.toByte(), 'R'.code.toByte(), 'E'.code.toByte(), 'T'.code.toByte())
    private const val PROTOCOL_VERSION: Byte = 0x01
    private const val EXIF_CERT_TAG_PREFIX = "fretG_CERT::"

    /**
     * Invisibly embeds the Birth Certificate payload into the Bitmap's pixel LSBs
     * and returns the certified mutable/immutable bitmap.
     */
    fun embedInBitmap(sourceBitmap: Bitmap, certificate: BirthCertificate): Bitmap {
        val mutableBitmap = if (sourceBitmap.isMutable) {
            sourceBitmap
        } else {
            sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        }

        val json = certificate.toJson()
        val compressedPayload = compressString(json)

        val crc = CRC32()
        crc.update(compressedPayload)
        val checksum = crc.value.toInt()

        // Binary frame: MAGIC (4) + VERSION (1) + LENGTH (4) + PAYLOAD (N) + CHECKSUM (4)
        val frameBuffer = ByteBuffer.allocate(4 + 1 + 4 + compressedPayload.size + 4)
        frameBuffer.put(MAGIC_HEADER)
        frameBuffer.put(PROTOCOL_VERSION)
        frameBuffer.putInt(compressedPayload.size)
        frameBuffer.put(compressedPayload)
        frameBuffer.putInt(checksum)

        val frameBytes = frameBuffer.array()
        val totalBits = frameBytes.size * 8

        val maxAvailablePixels = mutableBitmap.width * mutableBitmap.height
        if (totalBits > maxAvailablePixels) {
            // Bitmap too small for pixel LSB; will still have EXIF layer
            return mutableBitmap
        }

        // Embed 1 bit per pixel into the Blue channel LSB
        var bitIndex = 0
        for (y in 0 until mutableBitmap.height) {
            for (x in 0 until mutableBitmap.width) {
                if (bitIndex >= totalBits) break

                val pixel = mutableBitmap.getPixel(x, y)
                val bytePos = bitIndex / 8
                val bitOffset = 7 - (bitIndex % 8)
                val bitVal = (frameBytes[bytePos].toInt() shr bitOffset) and 0x01

                val a = (pixel shr 24) and 0xFF
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                var b = pixel and 0xFF

                // Replace LSB of Blue channel
                b = (b and 0xFE) or bitVal

                val newPixel = (a shl 24) or (r shl 16) or (g shl 8) or b
                mutableBitmap.setPixel(x, y, newPixel)

                bitIndex++
            }
            if (bitIndex >= totalBits) break
        }

        return mutableBitmap
    }

    /**
     * Extracts BirthCertificate from Bitmap pixel matrix
     */
    fun extractFromBitmap(bitmap: Bitmap): BirthCertificate? {
        try {
            val totalPixels = bitmap.width * bitmap.height
            if (totalPixels < 72) return null // Need at least 9 bytes (72 bits) for header

            // Read header (9 bytes = 72 bits)
            val headerBytes = ByteArray(9)
            var bitIndex = 0
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    if (bitIndex >= 72) break
                    val pixel = bitmap.getPixel(x, y)
                    val bitVal = (pixel and 0x01)
                    val bytePos = bitIndex / 8
                    val bitOffset = 7 - (bitIndex % 8)
                    headerBytes[bytePos] = (headerBytes[bytePos].toInt() or (bitVal shl bitOffset)).toByte()
                    bitIndex++
                }
                if (bitIndex >= 72) break
            }

            // Verify Magic
            if (headerBytes[0] != MAGIC_HEADER[0] ||
                headerBytes[1] != MAGIC_HEADER[1] ||
                headerBytes[2] != MAGIC_HEADER[2] ||
                headerBytes[3] != MAGIC_HEADER[3]
            ) {
                return null
            }

            val buffer = ByteBuffer.wrap(headerBytes, 4, 5)
            val version = buffer.get()
            val payloadLength = buffer.int

            if (payloadLength <= 0 || payloadLength > 100_000) return null

            val totalFrameBits = (9 + payloadLength + 4) * 8
            if (totalFrameBits > totalPixels) return null

            val fullFrameBytes = ByteArray(9 + payloadLength + 4)
            System.arraycopy(headerBytes, 0, fullFrameBytes, 0, 9)

            bitIndex = 72
            val startY = 72 / bitmap.width
            val startX = 72 % bitmap.width

            var currBit = 72
            for (y in startY until bitmap.height) {
                val xFrom = if (y == startY) startX else 0
                for (x in xFrom until bitmap.width) {
                    if (currBit >= totalFrameBits) break
                    val pixel = bitmap.getPixel(x, y)
                    val bitVal = (pixel and 0x01)
                    val bytePos = currBit / 8
                    val bitOffset = 7 - (currBit % 8)
                    fullFrameBytes[bytePos] = (fullFrameBytes[bytePos].toInt() or (bitVal shl bitOffset)).toByte()
                    currBit++
                }
                if (currBit >= totalFrameBits) break
            }

            val payloadBuffer = ByteBuffer.wrap(fullFrameBytes, 9, payloadLength + 4)
            val payload = ByteArray(payloadLength)
            payloadBuffer.get(payload)
            val expectedChecksum = payloadBuffer.int

            val crc = CRC32()
            crc.update(payload)
            if (crc.value.toInt() != expectedChecksum) {
                return null
            }

            val json = decompressString(payload)
            return BirthCertificate.fromJson(json)
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Stamped EXIF layer with authenticated JSON signature
     */
    fun stampExifCertificate(exif: ExifInterface, certificate: BirthCertificate) {
        val encodedPayload = EXIF_CERT_TAG_PREFIX + Base64.encodeToString(
            certificate.toJson().toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, encodedPayload)
        exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, "fretG Certified Birth Certificate ID: ${certificate.certificateId}")
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, certificate.cameraExif.software)
        exif.setAttribute(ExifInterface.TAG_MAKE, certificate.deviceManufacturer)
        exif.setAttribute(ExifInterface.TAG_MODEL, certificate.deviceModel)
    }

    /**
     * Extracts certificate from EXIF metadata stream
     */
    fun extractFromExif(inputStream: InputStream): BirthCertificate? {
        return try {
            val exif = ExifInterface(inputStream)
            val userComment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT) ?: ""
            if (userComment.startsWith(EXIF_CERT_TAG_PREFIX)) {
                val base64 = userComment.removePrefix(EXIF_CERT_TAG_PREFIX)
                val jsonBytes = Base64.decode(base64, Base64.NO_WRAP)
                val json = String(jsonBytes, Charsets.UTF_8)
                BirthCertificate.fromJson(json)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Comprehensive Forensic Verification Pipeline
     */
    suspend fun verifyImageIntegrity(
        context: Context,
        uri: Uri,
        relativePath: String = "",
        dataPath: String = ""
    ): VerificationResult = withContext(Dispatchers.IO) {
        val auditLog = mutableListOf<AuditCheck>()
        var certificate: BirthCertificate? = null
        var computedSha256 = ""

        // Step 1: Compute Cryptographic File Hash
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                computedSha256 = CryptoSigner.computeSha256(stream)
            }
            auditLog.add(
                AuditCheck(
                    title = "Cryptographic Digest (SHA-256)",
                    description = "Calculated file hash: ${computedSha256.take(16)}...",
                    passed = true,
                    detailValue = computedSha256
                )
            )
        } catch (e: Exception) {
            auditLog.add(
                AuditCheck(
                    title = "File Digest Calculation",
                    description = "Failed to stream image: ${e.localizedMessage}",
                    passed = false
                )
            )
        }

        // Step 2: Extract Steganographic / Metadata Certificate
        var extractedFromStego = false
        var extractedFromExif = false

        // Try EXIF first (fast non-destructive check)
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                certificate = extractFromExif(stream)
                if (certificate != null) extractedFromExif = true
            }
        } catch (e: Exception) { }

        // Try Pixel LSB Steganography
        if (certificate == null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        certificate = extractFromBitmap(bmp)
                        if (certificate != null) extractedFromStego = true
                    }
                }
            } catch (e: Exception) { }
        }

        // Evaluate extraction status
        if (certificate != null) {
            auditLog.add(
                AuditCheck(
                    title = "Birth Certificate Discovery",
                    description = "Extracted ID: ${certificate!!.certificateId} (${if (extractedFromStego) "Pixel LSB Matrix" else "Authenticated EXIF Container"})",
                    passed = true,
                    detailValue = certificate!!.certificateId
                )
            )

            // Step 3: Validate Hardware Digital Signature
            val payloadForSig = "${certificate!!.certificateId}|${certificate!!.timestamp}|${certificate!!.imageSha256}|${certificate!!.deviceModel}"
            val signatureValid = CryptoSigner.verifySignature(payloadForSig, certificate!!.digitalSignature)
            auditLog.add(
                AuditCheck(
                    title = "Device Hardware Key Signature",
                    description = if (signatureValid) "Hardware-backed Keystore HMAC verified" else "Signature check failed / untrusted signer",
                    passed = signatureValid,
                    detailValue = certificate!!.digitalSignature.take(20) + "..."
                )
            )

            // Step 4: Compare Pristine Capture Hash with Live Hash
            val hashMatch = (computedSha256.equals(certificate!!.imageSha256, ignoreCase = true))

            auditLog.add(
                AuditCheck(
                    title = "Pixel Matrix & Payload Integrity",
                    description = if (hashMatch) "SHA-256 matches exact capture state" else "Tamper Alert: Hash mismatch (image modified or cropped)",
                    passed = hashMatch,
                    detailValue = "Certified: ${certificate!!.imageSha256.take(12)}... vs Live: ${computedSha256.take(12)}..."
                )
            )

            val finalStatus = if (hashMatch && signatureValid) {
                VerificationStatus.GENUINE_CERTIFIED
            } else {
                VerificationStatus.TAMPERED_WARNING
            }

            return@withContext VerificationResult(
                status = finalStatus,
                certificate = certificate,
                computedSha256 = computedSha256,
                auditLog = auditLog
            )
        }

        // Fallback Step: Device Camera Provenance Check
        val isCameraPath = relativePath.contains("DCIM", ignoreCase = true) ||
                relativePath.contains("Camera", ignoreCase = true) ||
                dataPath.contains("DCIM", ignoreCase = true) ||
                dataPath.contains("Camera", ignoreCase = true)

        var hasCameraExif = false
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val make = exif.getAttribute(ExifInterface.TAG_MAKE)
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)
                if (!make.isNullOrBlank() || !model.isNullOrBlank()) {
                    hasCameraExif = true
                }
            }
        } catch (e: Exception) { }

        if (isCameraPath || hasCameraExif) {
            auditLog.add(
                AuditCheck(
                    title = "Camera Provenance (Legacy)",
                    description = "Detected standard camera path/EXIF without cryptographic birth certificate",
                    passed = true,
                    detailValue = "DCIM / Camera MediaStore"
                )
            )
            return@withContext VerificationResult(
                status = VerificationStatus.LEGACY_CAMERA,
                certificate = null,
                computedSha256 = computedSha256,
                auditLog = auditLog
            )
        }

        auditLog.add(
            AuditCheck(
                title = "Birth Certificate Status",
                description = "No cryptographic proof or camera metadata found (Imported / External media)",
                passed = false
            )
        )

        return@withContext VerificationResult(
            status = VerificationStatus.UNCERTIFIED_EXTERNAL,
            certificate = null,
            computedSha256 = computedSha256,
            auditLog = auditLog
        )
    }

    private fun compressString(str: String): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzip ->
            gzip.write(str.toByteArray(Charsets.UTF_8))
        }
        return bos.toByteArray()
    }

    private fun decompressString(compressed: ByteArray): String {
        val bis = ByteArrayInputStream(compressed)
        GZIPInputStream(bis).use { gzip ->
            return gzip.bufferedReader(Charsets.UTF_8).readText()
        }
    }
}
