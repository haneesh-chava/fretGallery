package com.example.fretgallery

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: View
    private val images = mutableListOf<ImageItem>()
    private val hashMap = mutableMapOf<Uri, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        emptyText = findViewById(R.id.emptyText)
        findViewById<View>(R.id.seedButton).setOnClickListener { seedTestData() }
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = PhotoAdapter(images)

        if (hasPermission()) {
            loadPhotos()
        } else {
            requestPermission()
        }
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermission() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        ActivityCompat.requestPermissions(this, permissions, 100)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadPhotos()
        } else {
            Toast.makeText(this, "Permission required to display photos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun seedTestData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "FRET_TEST_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { stream ->
                        val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        val paint = Paint()
                        paint.color = Color.BLUE
                        canvas.drawRect(0f, 0f, 500f, 500f, paint)
                        paint.color = Color.WHITE
                        paint.textSize = 50f
                        canvas.drawText("FRET TEST", 100f, 250f, paint)
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Photo seeded to DCIM/Camera", Toast.LENGTH_SHORT).show()
                        loadPhotos()
                    }
                }
            } catch (e: Exception) {
                Log.e("FRET", "Seed failed", e)
            }
        }
    }

    private fun loadPhotos() {
        Log.d("FRET", "loadPhotos started")
        images.clear()
        hashMap.clear()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATA
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            Log.d("FRET", "Cursor count: ${cursor.count}")
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val relativePathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                val relativePath = cursor.getString(relativePathColumn) ?: ""
                val dataPath = cursor.getString(dataColumn) ?: ""

                val isCameraPhoto = isCameraPhoto(relativePath, dataPath, contentUri)
                images.add(ImageItem(contentUri, isCameraPhoto))

                if (isCameraPhoto) {
                    // Compute hash in background
                    CoroutineScope(Dispatchers.IO).launch {
                        val hash = computeHash(contentUri)
                        if (hash != null) {
                            hashMap[contentUri] = hash
                        }
                    }
                }
            }
        }
        recyclerView.adapter?.notifyDataSetChanged()
        emptyText.visibility = if (images.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun isCameraPhoto(relativePath: String, dataPath: String, uri: Uri): Boolean {
        // Check path
        if (relativePath.contains("DCIM", ignoreCase = true) ||
            relativePath.contains("Camera", ignoreCase = true) ||
            dataPath.contains("DCIM", ignoreCase = true) ||
            dataPath.contains("Camera", ignoreCase = true)
        ) {
            return true
        }

        // Fallback: check EXIF
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val exif = ExifInterface(inputStream)
                val make = exif.getAttribute(ExifInterface.TAG_MAKE)
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)
                val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)
                inputStream.close()
                !make.isNullOrBlank() || !model.isNullOrBlank() || !software.isNullOrBlank()
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun computeHash(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            inputStream.close()
            val hashBytes = digest.digest()
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    data class ImageItem(val uri: Uri, val isVerified: Boolean)

    inner class PhotoAdapter(private val items: List<ImageItem>) :
        RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_photo, parent, false)
            return PhotoViewHolder(view)
        }

        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            val item = items[position]
            holder.imageView.load(item.uri) {
                crossfade(true)
            }
            if (item.isVerified) {
                holder.badge.visibility = View.VISIBLE
            } else {
                holder.badge.visibility = View.GONE
            }

            holder.imageView.setOnClickListener {
                val intent = Intent(this@MainActivity, FullScreenActivity::class.java)
                intent.putExtra("imageUri", item.uri.toString())
                intent.putExtra("isVerified", item.isVerified)
                startActivity(intent)
            }

            holder.imageView.setOnLongClickListener {
                if (item.isVerified) {
                    val hash = hashMap[item.uri] ?: "Hash not computed yet"
                    Toast.makeText(this@MainActivity, "SHA-256: $hash", Toast.LENGTH_LONG).show()
                }
                true
            }
        }

        override fun getItemCount(): Int = items.size

        inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = itemView.findViewById(R.id.imageView)
            val badge: ImageView = itemView.findViewById(R.id.fretBadge)
        }
    }
}
