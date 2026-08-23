package com.example.fretgallery.model

import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Represents the immutable Birth Certificate of an image minted at the exact moment of capture.
 */
data class BirthCertificate(
    val certificateId: String,
    val timestamp: Long,
    val formattedTimestamp: String,
    val deviceModel: String,
    val deviceManufacturer: String,
    val androidVersion: String,
    val hardwareKeyAlias: String,
    val imageSha256: String,
    val pixelMatrixHash: String,
    val cameraExif: ExifTelemetry,
    val location: LocationTelemetry? = null,
    val digitalSignature: String,
    val stegoSeed: Long,
    val version: String = "1.0-FRETG"
) {
    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): BirthCertificate? {
            return try {
                Gson().fromJson(json, BirthCertificate::class.java)
            } catch (e: Exception) {
                null
            }
        }

        fun createTimestampString(epochMillis: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date(epochMillis))
        }
    }
}

data class ExifTelemetry(
    val make: String = "",
    val model: String = "",
    val iso: String = "Auto",
    val aperture: String = "f/1.8",
    val exposureTime: String = "1/120s",
    val focalLength: String = "24mm",
    val flash: String = "Off",
    val width: Int = 0,
    val height: Int = 0,
    val software: String = "fretG Optical Engine 1.0"
)

data class LocationTelemetry(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 0f,
    val placeName: String? = null
)
