package com.example.fretgallery

import com.example.fretgallery.crypto.CryptoSigner
import com.example.fretgallery.model.BirthCertificate
import com.example.fretgallery.model.ExifTelemetry
import com.example.fretgallery.model.LocationTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoStegoTest {

    @Test
    fun testCertificateSerializationRoundTrip() {
        val now = System.currentTimeMillis()
        val cert = BirthCertificate(
            certificateId = "FRETG-2026-TEST123456",
            timestamp = now,
            formattedTimestamp = BirthCertificate.createTimestampString(now),
            deviceModel = "TestPhone",
            deviceManufacturer = "TestMake",
            androidVersion = "Android 14",
            hardwareKeyAlias = "TestMasterKey",
            imageSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            pixelMatrixHash = "a1b2c3d4e5f6",
            cameraExif = ExifTelemetry(
                iso = "ISO 100",
                aperture = "f/1.8",
                exposureTime = "1/120s"
            ),
            location = LocationTelemetry(
                latitude = 37.7749,
                longitude = -122.4194
            ),
            digitalSignature = "TestSignatureBase64",
            stegoSeed = now
        )

        val json = cert.toJson()
        assertNotNull(json)

        val reconstructed = BirthCertificate.fromJson(json)
        assertNotNull(reconstructed)
        assertEquals(cert.certificateId, reconstructed?.certificateId)
        assertEquals(cert.imageSha256, reconstructed?.imageSha256)
        assertEquals(cert.cameraExif.aperture, reconstructed?.cameraExif?.aperture)
        assertEquals(cert.location?.latitude, reconstructed?.location?.latitude)
    }

    @Test
    fun testSha256Hashing() {
        val testBytes = "fretG_provenance_verification_string".toByteArray(Charsets.UTF_8)
        val hash = CryptoSigner.computeSha256(testBytes)
        assertNotNull(hash)
        assertEquals(64, hash.length)
    }

    @Test
    fun testCertificateIdGeneration() {
        val id1 = CryptoSigner.generateCertificateId()
        val id2 = CryptoSigner.generateCertificateId()
        assertTrue(id1.startsWith("FRETG-"))
        assertTrue(id2.startsWith("FRETG-"))
        assertTrue(id1 != id2)
    }
}
