package com.example.fretgallery.camera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.example.fretgallery.R
import com.example.fretgallery.crypto.CryptoSigner
import com.example.fretgallery.model.BirthCertificate
import com.example.fretgallery.model.ExifTelemetry
import com.example.fretgallery.stego.SteganographyEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var btnShutter: FrameLayout
    private lateinit var shutterInner: View
    private lateinit var btnFlashToggle: ImageView
    private lateinit var btnCameraSwitch: ImageView
    private lateinit var mintingOverlay: FrameLayout
    private lateinit var txtTelemetryLive: TextView
    private lateinit var zoom1x: TextView
    private lateinit var zoom2x: TextView

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var flashMode = ImageCapture.FLASH_MODE_AUTO
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        viewFinder = findViewById(R.id.viewFinder)
        btnShutter = findViewById(R.id.btnShutter)
        shutterInner = findViewById(R.id.shutterInner)
        btnFlashToggle = findViewById(R.id.btnFlashToggle)
        btnCameraSwitch = findViewById(R.id.btnCameraSwitch)
        mintingOverlay = findViewById(R.id.mintingOverlay)
        txtTelemetryLive = findViewById(R.id.txtTelemetryLive)
        zoom1x = findViewById(R.id.zoom1x)
        zoom2x = findViewById(R.id.zoom2x)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CODE_PERMISSIONS
            )
        }

        findViewById<View>(R.id.btnCameraClose).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.btnGalleryShortcut).setOnClickListener {
            finish()
        }

        btnShutter.setOnClickListener {
            animateShutterAndCapture()
        }

        btnCameraSwitch.setOnClickListener {
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            startCamera()
        }

        btnFlashToggle.setOnClickListener {
            cycleFlashMode()
        }

        zoom1x.setOnClickListener {
            camera?.cameraControl?.setLinearZoom(0f)
            zoom1x.setBackgroundResource(R.drawable.bg_glass_pill_active)
            zoom1x.setTextColor(ContextCompat.getColor(this, R.color.ios_pill_active_text))
            zoom2x.background = null
            zoom2x.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }

        zoom2x.setOnClickListener {
            camera?.cameraControl?.setLinearZoom(0.5f)
            zoom2x.setBackgroundResource(R.drawable.bg_glass_pill_active)
            zoom2x.setTextColor(ContextCompat.getColor(this, R.color.ios_pill_active_text))
            zoom1x.background = null
            zoom1x.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }
    }

    private fun cycleFlashMode() {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_AUTO -> {
                btnFlashToggle.setImageResource(R.drawable.ic_flash_auto)
                ImageCapture.FLASH_MODE_ON
            }
            ImageCapture.FLASH_MODE_ON -> {
                btnFlashToggle.setImageResource(R.drawable.ic_flash_auto)
                ImageCapture.FLASH_MODE_OFF
            }
            else -> {
                btnFlashToggle.setImageResource(R.drawable.ic_flash_auto)
                ImageCapture.FLASH_MODE_AUTO
            }
        }
        imageCapture?.flashMode = flashMode
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(flashMode)
                .build()

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Failed to bind camera: ${exc.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun animateShutterAndCapture() {
        shutterInner.animate()
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(80)
            .withEndAction {
                shutterInner.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(120)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
            }
            .start()

        takePhotoAndMintCertificate()
    }

    private fun takePhotoAndMintCertificate() {
        val imageCapture = imageCapture ?: return

        mintingOverlay.visibility = View.VISIBLE

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    processCapturedImage(image)
                }

                override fun onError(exception: ImageCaptureException) {
                    mintingOverlay.visibility = View.GONE
                    Toast.makeText(this@CameraActivity, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun processCapturedImage(imageProxy: ImageProxy) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val buffer: ByteBuffer = imageProxy.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                imageProxy.close()

                var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                // Correct rotation
                if (rotationDegrees != 0) {
                    val matrix = Matrix()
                    matrix.postRotate(rotationDegrees.toFloat())
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                }

                val now = System.currentTimeMillis()
                val certId = CryptoSigner.generateCertificateId()

                val exifTelemetry = ExifTelemetry(
                    make = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
                    model = Build.MODEL,
                    iso = "ISO 100",
                    aperture = "f/1.8",
                    exposureTime = "1/120s",
                    focalLength = "24mm",
                    flash = if (flashMode == ImageCapture.FLASH_MODE_ON) "On" else "Off",
                    width = bitmap.width,
                    height = bitmap.height,
                    software = "fretG Optical Provenance Engine 1.0"
                )

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
                    location = null,
                    digitalSignature = tempSig,
                    stegoSeed = now
                )

                // Embed into pixel LSBs
                val stegoBitmap = SteganographyEngine.embedInBitmap(bitmap, tempCert)

                // Compress to JPEG
                val bos = ByteArrayOutputStream()
                stegoBitmap.compress(Bitmap.CompressFormat.JPEG, 95, bos)
                val imageBytes = bos.toByteArray()
                val finalSha256 = CryptoSigner.computeSha256(imageBytes)

                val finalPayload = "$certId|$now|$finalSha256|${Build.MODEL}"
                val finalSignature = CryptoSigner.signPayload(finalPayload)

                val finalCert = tempCert.copy(
                    imageSha256 = finalSha256,
                    pixelMatrixHash = CryptoSigner.computeBitmapPixelHash(stegoBitmap),
                    digitalSignature = finalSignature
                )

                // Save to MediaStore
                val fileName = "FRETG_${System.currentTimeMillis()}.jpg"
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/fretG")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    val tempFile = File(cacheDir, fileName)
                    FileOutputStream(tempFile).use { out ->
                        stegoBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }

                    // Stamp EXIF layer
                    val exif = ExifInterface(tempFile.absolutePath)
                    SteganographyEngine.stampExifCertificate(exif, finalCert)
                    exif.saveAttributes()

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

                withContext(Dispatchers.Main) {
                    mintingOverlay.visibility = View.GONE
                    Toast.makeText(this@CameraActivity, "Birth Certificate Minted: $certId", Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    mintingOverlay.visibility = View.GONE
                    Toast.makeText(this@CameraActivity, "Minting error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permissions required for capture.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 200
    }
}
