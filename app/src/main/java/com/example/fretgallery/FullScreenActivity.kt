package com.example.fretgallery

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import coil.load

class FullScreenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen)

        val imageView = findViewById<ImageView>(R.id.fullscreenImage)
        val badge = findViewById<ImageView>(R.id.fullscreenBadge)
        val backButton = findViewById<Button>(R.id.backButton)

        val imageUri = intent.getStringExtra("imageUri")
        val isVerified = intent.getBooleanExtra("isVerified", false)

        if (imageUri != null) {
            imageView.load(imageUri)
        }

        if (isVerified) {
            badge.visibility = ImageView.VISIBLE
        } else {
            badge.visibility = ImageView.GONE
        }

        backButton.setOnClickListener {
            finish()
        }
    }
}
