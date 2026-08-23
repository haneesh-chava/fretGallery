package com.example.fretgallery

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.fretgallery.model.GalleryItem
import com.example.fretgallery.model.VerificationStatus
import com.google.gson.Gson

class PhotoAdapter(
    private var items: List<GalleryItem>,
    private val onItemClick: (GalleryItem) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    fun updateData(newItems: List<GalleryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val item = items[position]

        // Load thumbnail with Coil
        holder.imageView.load(item.uri) {
            crossfade(true)
        }

        // Show/hide audit progress
        holder.progressBar.visibility = if (item.isChecking) View.VISIBLE else View.GONE

        // Verification Status Badge
        when (item.status) {
            VerificationStatus.GENUINE_CERTIFIED -> {
                holder.statusBadgeContainer.visibility = View.VISIBLE
                holder.statusBadgeContainer.setBackgroundResource(R.drawable.bg_badge_genuine)
                holder.statusBadgeIcon.setImageResource(R.drawable.ic_shield_check)
                holder.statusBadgeText.text = "GENUINE"
                holder.statusBadgeText.setTextColor(Color.WHITE)
            }
            VerificationStatus.TAMPERED_WARNING -> {
                holder.statusBadgeContainer.visibility = View.VISIBLE
                holder.statusBadgeContainer.setBackgroundResource(R.drawable.bg_badge_tampered)
                holder.statusBadgeIcon.setImageResource(R.drawable.ic_shield_alert)
                holder.statusBadgeText.text = "TAMPERED"
                holder.statusBadgeText.setTextColor(Color.WHITE)
            }
            VerificationStatus.LEGACY_CAMERA -> {
                holder.statusBadgeContainer.visibility = View.VISIBLE
                holder.statusBadgeContainer.setBackgroundResource(R.drawable.bg_badge_legacy)
                holder.statusBadgeIcon.setImageResource(R.drawable.ic_shield_check)
                holder.statusBadgeText.text = "CAMERA"
                holder.statusBadgeText.setTextColor(Color.WHITE)
            }
            VerificationStatus.UNCERTIFIED_EXTERNAL -> {
                holder.statusBadgeContainer.visibility = View.VISIBLE
                holder.statusBadgeContainer.setBackgroundResource(R.drawable.bg_badge_external)
                holder.statusBadgeIcon.setImageResource(R.drawable.ic_fingerprint)
                holder.statusBadgeText.text = "EXTERNAL"
                holder.statusBadgeText.setTextColor(Color.parseColor("#94A3B8"))
            }
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }

        holder.itemView.setOnLongClickListener {
            val result = item.verificationResult
            val cert = result?.certificate
            val msg = if (cert != null) {
                "Birth Certificate: ${cert.certificateId}\nSHA-256: ${cert.imageSha256.take(16)}..."
            } else {
                "SHA-256: ${result?.computedSha256?.take(16) ?: "Calculating..."}"
            }
            Toast.makeText(holder.itemView.context, msg, Toast.LENGTH_LONG).show()
            true
        }
    }

    override fun getItemCount(): Int = items.size

    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        val statusBadgeContainer: LinearLayout = itemView.findViewById(R.id.statusBadgeContainer)
        val statusBadgeIcon: ImageView = itemView.findViewById(R.id.statusBadgeIcon)
        val statusBadgeText: TextView = itemView.findViewById(R.id.statusBadgeText)
        val progressBar: ProgressBar = itemView.findViewById(R.id.cardProgressBar)
    }
}