package com.example.fretgallery.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.fretgallery.R
import com.example.fretgallery.model.AuditCheck
import com.example.fretgallery.model.BirthCertificate
import com.example.fretgallery.model.VerificationResult
import com.example.fretgallery.model.VerificationStatus
import com.example.fretgallery.stego.SteganographyEngine
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BirthCertificateBottomSheet : BottomSheetDialogFragment() {

    private var imageUri: Uri? = null
    private var verificationResult: VerificationResult? = null

    companion object {
        fun newInstance(uri: Uri, result: VerificationResult?): BirthCertificateBottomSheet {
            val sheet = BirthCertificateBottomSheet()
            sheet.imageUri = uri
            sheet.verificationResult = result
            return sheet
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.sheet_birth_certificate, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindData(view)
    }

    private fun bindData(view: View) {
        val result = verificationResult
        val cert = result?.certificate

        val bannerIcon = view.findViewById<ImageView>(R.id.bannerIcon)
        val bannerStatusTitle = view.findViewById<TextView>(R.id.bannerStatusTitle)
        val bannerStatusSubtitle = view.findViewById<TextView>(R.id.bannerStatusSubtitle)

        val txtCertId = view.findViewById<TextView>(R.id.txtCertId)
        val txtTimestamp = view.findViewById<TextView>(R.id.txtTimestamp)
        val txtDeviceSeal = view.findViewById<TextView>(R.id.txtDeviceSeal)

        val txtSha256 = view.findViewById<TextView>(R.id.txtSha256)
        val txtStegoStatus = view.findViewById<TextView>(R.id.txtStegoStatus)
        val iconStegoCheck = view.findViewById<ImageView>(R.id.iconStegoCheck)

        val txtCameraLens = view.findViewById<TextView>(R.id.txtCameraLens)
        val txtExposureIso = view.findViewById<TextView>(R.id.txtExposureIso)
        val txtDimensions = view.findViewById<TextView>(R.id.txtDimensions)
        val txtCaptureSoftware = view.findViewById<TextView>(R.id.txtCaptureSoftware)

        val auditLogContainer = view.findViewById<LinearLayout>(R.id.auditLogContainer)
        val btnCopyCertId = view.findViewById<ImageView>(R.id.btnCopyCertId)
        val btnCopySha = view.findViewById<ImageView>(R.id.btnCopySha)
        val btnLiveAudit = view.findViewById<Button>(R.id.btnLiveAudit)
        val btnCloseSheet = view.findViewById<Button>(R.id.btnCloseSheet)

        // Status banner styling
        when (result?.status) {
            VerificationStatus.GENUINE_CERTIFIED -> {
                bannerIcon.setImageResource(R.drawable.ic_shield_check)
                bannerStatusTitle.text = "VERIFIED GENUINE"
                bannerStatusTitle.setTextColor(Color.parseColor("#10B981"))
                bannerStatusSubtitle.text = "Cryptographic signature & pixel steganography match"
            }
            VerificationStatus.TAMPERED_WARNING -> {
                bannerIcon.setImageResource(R.drawable.ic_shield_alert)
                bannerStatusTitle.text = "TAMPER DETECTED"
                bannerStatusTitle.setTextColor(Color.parseColor("#EF4444"))
                bannerStatusSubtitle.text = "Hash mismatch: image was modified or corrupted after capture"
            }
            VerificationStatus.LEGACY_CAMERA -> {
                bannerIcon.setImageResource(R.drawable.ic_shield_check)
                bannerStatusTitle.text = "CAMERA PHOTO (LEGACY)"
                bannerStatusTitle.setTextColor(Color.parseColor("#F59E0B"))
                bannerStatusSubtitle.text = "Detected camera metadata without cryptographic birth certificate"
            }
            else -> {
                bannerIcon.setImageResource(R.drawable.ic_fingerprint)
                bannerStatusTitle.text = "UNCERTIFIED MEDIA"
                bannerStatusTitle.setTextColor(Color.parseColor("#94A3B8"))
                bannerStatusSubtitle.text = "No cryptographic proof found (Imported / External photo)"
            }
        }

        // Populate Certificate Info
        if (cert != null) {
            txtCertId.text = cert.certificateId
            txtTimestamp.text = "${cert.formattedTimestamp} (${cert.deviceModel})"
            txtDeviceSeal.text = "${cert.hardwareKeyAlias} • HMAC-SHA256"
            txtSha256.text = cert.imageSha256.ifEmpty { result.computedSha256 }
            txtStegoStatus.text = "LSB Binary Frame & CRC32 Verified"
            iconStegoCheck.setImageResource(R.drawable.ic_check)

            val exif = cert.cameraExif
            txtCameraLens.text = "${exif.focalLength} • ${exif.aperture}"
            txtExposureIso.text = "${exif.iso} • ${exif.exposureTime}"
            txtDimensions.text = "${exif.width} x ${exif.height}"
            txtCaptureSoftware.text = exif.software
        } else {
            txtCertId.text = "N/A (Uncertified)"
            txtTimestamp.text = "Capture timestamp not cryptographically sealed"
            txtDeviceSeal.text = "No hardware key seal"
            txtSha256.text = result?.computedSha256?.ifEmpty { "Digest calculation pending" } ?: "N/A"
            txtStegoStatus.text = "No embedded steganographic watermark found"
            iconStegoCheck.setImageResource(R.drawable.ic_close)

            txtCameraLens.text = "Unknown"
            txtExposureIso.text = "Unknown"
            txtDimensions.text = "Original"
            txtCaptureSoftware.text = "External"
        }

        // Copy actions
        btnCopyCertId.setOnClickListener {
            copyToClipboard("Certificate ID", txtCertId.text.toString())
        }
        btnCopySha.setOnClickListener {
            copyToClipboard("SHA-256 Digest", txtSha256.text.toString())
        }

        // Populate Audit Log
        populateAuditLog(auditLogContainer, result?.auditLog ?: emptyList())

        // Live Audit Button
        btnLiveAudit.setOnClickListener {
            runLiveAudit(view)
        }

        btnCloseSheet.setOnClickListener {
            dismiss()
        }
    }

    private fun populateAuditLog(container: LinearLayout, logs: List<AuditCheck>) {
        container.removeAllViews()
        if (logs.isEmpty()) {
            val empty = TextView(context).apply {
                text = "No audit records generated"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
            }
            container.addView(empty)
            return
        }

        for ((index, check) in logs.withIndex()) {
            val itemLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                if (index > 0) {
                    setPadding(0, 16, 0, 0)
                }
            }

            val icon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(40, 40)
                setImageResource(if (check.passed) R.drawable.ic_check else R.drawable.ic_close)
            }

            val textLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 20
                }
            }

            val title = TextView(context).apply {
                text = check.title
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val desc = TextView(context).apply {
                text = check.description
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11f
            }

            textLayout.addView(title)
            textLayout.addView(desc)

            itemLayout.addView(icon)
            itemLayout.addView(textLayout)

            container.addView(itemLayout)
        }
    }

    private fun runLiveAudit(view: View) {
        val uri = imageUri ?: return
        val context = context ?: return

        val btnLiveAudit = view.findViewById<Button>(R.id.btnLiveAudit)
        btnLiveAudit.isEnabled = false
        btnLiveAudit.text = "Auditing Bitstream & Signature..."

        CoroutineScope(Dispatchers.Main).launch {
            val freshResult = withContext(Dispatchers.IO) {
                SteganographyEngine.verifyImageIntegrity(context, uri)
            }
            verificationResult = freshResult
            bindData(view)
            btnLiveAudit.isEnabled = true
            btnLiveAudit.text = "Re-Scan Complete"
            Toast.makeText(context, "Forensic audit complete: ${freshResult.status.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
    }
}
