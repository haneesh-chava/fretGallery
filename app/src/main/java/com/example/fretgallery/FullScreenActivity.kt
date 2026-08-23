package com.example.fretgallery

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.example.fretgallery.model.VerificationResult
import com.example.fretgallery.model.VerificationStatus
import com.example.fretgallery.stego.SteganographyEngine
import com.example.fretgallery.ui.BirthCertificateBottomSheet
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FullScreenActivity : AppCompatActivity() {

    private var imageUri: Uri? = null
    private var verificationResult: VerificationResult? = null

    private lateinit var fullscreenImage: ImageView
    private lateinit var txtImageName: TextView
    private lateinit var txtImageDetails: TextView
    private lateinit var topStatusBadge: LinearLayout
    private lateinit var topStatusIcon: ImageView
    private lateinit var topStatusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen)

        fullscreenImage = findViewById(R.id.fullscreenImage)
        txtImageName = findViewById(R.id.txtImageName)
        txtImageDetails = findViewById(R.id.txtImageDetails)
        topStatusBadge = findViewById(R.id.topStatusBadge)
        topStatusIcon = findViewById(R.id.topStatusIcon)
        topStatusText = findViewById(R.id.topStatusText)

        val uriString = intent.getStringExtra("imageUri")
        val resultJson = intent.getStringExtra("verificationResultJson")
        val imageName = intent.getStringExtra("imageName") ?: "Photo Preview"

        txtImageName.text = imageName

        if (uriString != null) {
            imageUri = Uri.parse(uriString)
            fullscreenImage.load(imageUri) {
                crossfade(true)
            }
        }

        if (!resultJson.isNullOrBlank()) {
            try {
                verificationResult = Gson().fromJson(resultJson, VerificationResult::class.java)
                updateStatusBadge(verificationResult?.status)
            } catch (e: Exception) { }
        }

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.btnInspectCertificate).setOnClickListener {
            showCertificateSheet()
        }

        // If verification result not available, audit now
        if (verificationResult == null && imageUri != null) {
            auditImage()
        }
    }

    private fun auditImage() {
        val uri = imageUri ?: return
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                SteganographyEngine.verifyImageIntegrity(this@FullScreenActivity, uri)
            }
            verificationResult = result
            updateStatusBadge(result.status)
        }
    }

    private fun updateStatusBadge(status: VerificationStatus?) {
        when (status) {
            VerificationStatus.GENUINE_CERTIFIED -> {
                topStatusBadge.setBackgroundResource(R.drawable.bg_badge_genuine)
                topStatusIcon.setImageResource(R.drawable.ic_shield_check)
                topStatusText.text = "GENUINE"
                topStatusText.setTextColor(Color.WHITE)
                txtImageDetails.text = "Cryptographically Certified Birth Certificate"
            }
            VerificationStatus.TAMPERED_WARNING -> {
                topStatusBadge.setBackgroundResource(R.drawable.bg_badge_tampered)
                topStatusIcon.setImageResource(R.drawable.ic_shield_alert)
                topStatusText.text = "TAMPERED"
                topStatusText.setTextColor(Color.WHITE)
                txtImageDetails.text = "Integrity Warning: Pixel Digest Mismatch"
            }
            VerificationStatus.LEGACY_CAMERA -> {
                topStatusBadge.setBackgroundResource(R.drawable.bg_badge_legacy)
                topStatusIcon.setImageResource(R.drawable.ic_shield_check)
                topStatusText.text = "CAMERA"
                topStatusText.setTextColor(Color.WHITE)
                txtImageDetails.text = "Standard Camera Metadata"
            }
            else -> {
                topStatusBadge.setBackgroundResource(R.drawable.bg_badge_external)
                topStatusIcon.setImageResource(R.drawable.ic_fingerprint)
                topStatusText.text = "EXTERNAL"
                topStatusText.setTextColor(Color.parseColor("#94A3B8"))
                txtImageDetails.text = "No Embedded Provenance"
            }
        }
    }

    private fun showCertificateSheet() {
        val uri = imageUri ?: return
        val sheet = BirthCertificateBottomSheet.newInstance(uri, verificationResult)
        sheet.show(supportFragmentManager, "BirthCertificateSheet")
    }
}
