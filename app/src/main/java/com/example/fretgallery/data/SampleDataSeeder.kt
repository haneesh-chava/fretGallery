package com.example.fretgallery.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.example.fretgallery.crypto.CryptoSigner
import com.example.fretgallery.model.BirthCertificate
import com.example.fretgallery.model.ExifTelemetry
import com.example.fretgallery.model.LocationTelemetry
import com.example.fretgallery.stego.SteganographyEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object SampleDataSeeder {

    enum class SeedType {
        GENUINE_CERTIFIED,
        TAMPERED_DEMO,
        UNCERTIFIED
    }

    suspend fun seedSamplePhoto(
        context: Context,
        seedType: SeedType = SeedType.GENUINE_CERTIFIED,
        titleSuffix: String = "",
        folderSubpath: String = Environment.DIRECTORY_DCIM + "/fretG"
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val width = 1080
            val height = 1080
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Draw rich red aesthetic radial/linear gradient background
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val gradient = when (seedType) {
                SeedType.GENUINE_CERTIFIED -> LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(Color.parseColor("#1A050A"), Color.parseColor("#E11D48"), Color.parseColor("#FF1E42")),
                    null, Shader.TileMode.CLAMP
                )
                SeedType.TAMPERED_DEMO -> LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(Color.parseColor("#2E0812"), Color.parseColor("#9F1239"), Color.parseColor("#FF3B56")),
                    null, Shader.TileMode.CLAMP
                )
                SeedType.UNCERTIFIED -> LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(Color.parseColor("#0F141F"), Color.parseColor("#1E293B"), Color.parseColor("#334155")),
                    null, Shader.TileMode.CLAMP
                )
            }
            paint.shader = gradient
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null

            // Draw decorative grid & elements
            val gridPaint = Paint().apply {
                color = Color.parseColor("#33FFFFFF")
                strokeWidth = 2f
                style = Paint.Style.STROKE
            }
            for (i in 0..width step 120) {
                canvas.drawLine(i.toFloat(), 0f, i.toFloat(), height.toFloat(), gridPaint)
                canvas.drawLine(0f, i.toFloat(), width.toFloat(), i.toFloat(), gridPaint)
            }

            // Draw Glass Card inside preview
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#26FFFFFF")
                style = Paint.Style.FILL
            }
            val cardRect = RectF(100f, 250f, width - 100f, height - 250f)
            canvas.drawRoundRect(cardRect, 48f, 48f, cardPaint)

            // Card stroke
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#55FFFFFF")
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            canvas.drawRoundRect(cardRect, 48f, 48f, strokePaint)

            // Text Titles
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 64f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }
            val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E2E8F0")
                textSize = 36f
                textAlign = Paint.Align.CENTER
            }

            val badgeText = when (seedType) {
                SeedType.GENUINE_CERTIFIED -> "fretG CERTIFIED"
                SeedType.TAMPERED_DEMO -> "TAMPERED DEMO"
                SeedType.UNCERTIFIED -> "MEDIA SAMPLE"
            }
            canvas.drawText(badgeText, width / 2f, height / 2f - 40f, textPaint)
            canvas.drawText("Cryptographic Provenance Engine", width / 2f, height / 2f + 40f, subTextPaint)

            val now = System.currentTimeMillis()
            val certId = CryptoSigner.generateCertificateId()

            var processedBitmap = bitmap

            // Generate Birth Certificate for certified / tampered demo
            var certificate: BirthCertificate? = null
            if (seedType != SeedType.UNCERTIFIED) {
                val exifTelemetry = ExifTelemetry(
                    make = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
                    model = Build.MODEL,
                    iso = "ISO 100",
                    aperture = "f/1.78",
                    exposureTime = "1/240s",
                    focalLength = "24mm (1x)",
                    flash = "Off",
                    width = width,
                    height = height,
                    software = "fretG Birth Certificate Engine 1.0"
                )
                val location = LocationTelemetry(
                    latitude = 37.7749,
                    longitude = -122.4194,
                    accuracyMeters = 3.5f,
                    placeName = "San Francisco, CA"
                )

                // First embed in bitmap to finalize pixel buffer
                val preSigPayload = "$certId|$now||${Build.MODEL}"
                val tempSig = CryptoSigner.signPayload(preSigPayload)

                val tempCert = BirthCertificate(
                    certificateId = certId,
                    timestamp = now,
                    formattedTimestamp = BirthCertificate.createTimestampString(now),
                    deviceModel = Build.MODEL,
                    deviceManufacturer = Build.MANUFACTURER,
                    androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    hardwareKeyAlias = "fretG_Keystore_MasterSeal",
                    imageSha256 = "",
                    pixelMatrixHash = "",
                    cameraExif = exifTelemetry,
                    location = location,
                    digitalSignature = tempSig,
                    stegoSeed = now
                )

                // Embed into bitmap LSBs
                val stegoBitmap = SteganographyEngine.embedInBitmap(bitmap, tempCert)

                // If TAMPERED_DEMO, intentionally alter some pixels AFTER embedding
                if (seedType == SeedType.TAMPERED_DEMO) {
                    val tamperPaint = Paint().apply {
                        color = Color.RED
                        textSize = 48f
                        textAlign = Paint.Align.CENTER
                    }
                    val tamperCanvas = Canvas(stegoBitmap)
                    tamperCanvas.drawText("TAMPERED PIXELS MODIFIED", width / 2f, height / 2f + 140f, tamperPaint)
                }

                // Compute final image bytes and SHA-256
                val bos = ByteArrayOutputStream()
                stegoBitmap.compress(Bitmap.CompressFormat.JPEG, 95, bos)
                val imageBytes = bos.toByteArray()
                val finalSha256 = CryptoSigner.computeSha256(imageBytes)

                val finalPayload = "$certId|$now|$finalSha256|${Build.MODEL}"
                val finalSignature = CryptoSigner.signPayload(finalPayload)

                // For genuine, the certificate has the exact matching final SHA-256
                // For tampered, simulate mismatch by setting certificate sha to original pristine hash
                val certSha = if (seedType == SeedType.TAMPERED_DEMO) {
                    "0000000000000000000000000000000000000000000000000000000000000000"
                } else {
                    finalSha256
                }

                certificate = tempCert.copy(
                    imageSha256 = certSha,
                    pixelMatrixHash = CryptoSigner.computeBitmapPixelHash(stegoBitmap),
                    digitalSignature = finalSignature
                )

                processedBitmap = stegoBitmap
            }

            // Save to MediaStore
            val fileName = "FRETG_${seedType.name}_${System.currentTimeMillis()}${if (titleSuffix.isNotEmpty()) "_$titleSuffix" else ""}.jpg"
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, folderSubpath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                // Write bitmap to temporary cache file first to attach EXIF
                val tempFile = File(context.cacheDir, fileName)
                FileOutputStream(tempFile).use { out ->
                    processedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }

                // If certified, stamp EXIF onto file
                if (certificate != null) {
                    val exif = ExifInterface(tempFile.absolutePath)
                    SteganographyEngine.stampExifCertificate(exif, certificate)
                    exif.saveAttributes()
                }

                // Copy temp file with EXIF into MediaStore
                resolver.openOutputStream(uri)?.use { out ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                tempFile.delete()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            }

            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
