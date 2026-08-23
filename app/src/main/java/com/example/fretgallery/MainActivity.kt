package com.example.fretgallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fretgallery.camera.CameraActivity
import com.example.fretgallery.data.SampleDataSeeder
import com.example.fretgallery.model.GalleryItem
import com.example.fretgallery.model.VerificationStatus
import com.example.fretgallery.stego.SteganographyEngine
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var headerStats: TextView
    private lateinit var tabAll: TextView
    private lateinit var tabCertified: TextView
    private lateinit var tabTampered: TextView
    private lateinit var tabUncertified: TextView

    private val allGalleryItems = mutableListOf<GalleryItem>()
    private val displayedItems = mutableListOf<GalleryItem>()
    private lateinit var adapter: PhotoAdapter

    private var currentFilter = FilterTab.ALL
    private var auditJob: Job? = null

    enum class FilterTab {
        ALL, CERTIFIED, TAMPERED, UNCERTIFIED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        emptyState = findViewById(R.id.emptyState)
        headerStats = findViewById(R.id.headerStats)
        tabAll = findViewById(R.id.tabAll)
        tabCertified = findViewById(R.id.tabCertified)
        tabTampered = findViewById(R.id.tabTampered)
        tabUncertified = findViewById(R.id.tabUncertified)

        adapter = PhotoAdapter(displayedItems) { item ->
            openFullScreen(item)
        }

        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = adapter

        setupListeners()

        if (hasPermissions()) {
            loadGalleryPhotos()
        } else {
            requestPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasPermissions()) {
            loadGalleryPhotos()
        }
    }

    private fun setupListeners() {
        // Tab Filters
        tabAll.setOnClickListener { selectTab(FilterTab.ALL) }
        tabCertified.setOnClickListener { selectTab(FilterTab.CERTIFIED) }
        tabTampered.setOnClickListener { selectTab(FilterTab.TAMPERED) }
        tabUncertified.setOnClickListener { selectTab(FilterTab.UNCERTIFIED) }

        // Floating Bottom Actions
        findViewById<View>(R.id.btnLaunchCamera).setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }

        findViewById<View>(R.id.btnSeedDemo).setOnClickListener {
            seedDemoPhotos()
        }

        findViewById<View>(R.id.btnRefresh).setOnClickListener {
            loadGalleryPhotos()
        }

        findViewById<View>(R.id.btnAuditAll).setOnClickListener {
            auditAllPhotos()
        }

        // Empty state buttons
        findViewById<Button>(R.id.btnEmptyCapture).setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }

        findViewById<Button>(R.id.btnEmptySeed).setOnClickListener {
            seedDemoPhotos()
        }
    }

    private fun selectTab(tab: FilterTab) {
        currentFilter = tab

        val activeBg = R.drawable.bg_glass_pill_active
        val activeTextColor = ContextCompat.getColor(this, R.color.ios_pill_active_text)
        val inactiveTextColor = ContextCompat.getColor(this, R.color.ios_pill_inactive_text)

        tabAll.background = null
        tabAll.setTextColor(inactiveTextColor)
        tabCertified.background = null
        tabCertified.setTextColor(inactiveTextColor)
        tabTampered.background = null
        tabTampered.setTextColor(inactiveTextColor)
        tabUncertified.background = null
        tabUncertified.setTextColor(inactiveTextColor)

        when (tab) {
            FilterTab.ALL -> {
                tabAll.setBackgroundResource(activeBg)
                tabAll.setTextColor(activeTextColor)
            }
            FilterTab.CERTIFIED -> {
                tabCertified.setBackgroundResource(activeBg)
                tabCertified.setTextColor(activeTextColor)
            }
            FilterTab.TAMPERED -> {
                tabTampered.setBackgroundResource(activeBg)
                tabTampered.setTextColor(activeTextColor)
            }
            FilterTab.UNCERTIFIED -> {
                tabUncertified.setBackgroundResource(activeBg)
                tabUncertified.setTextColor(activeTextColor)
            }
        }

        applyFilter()
    }

    private fun applyFilter() {
        val filtered = when (currentFilter) {
            FilterTab.ALL -> allGalleryItems
            FilterTab.CERTIFIED -> allGalleryItems.filter {
                it.status == VerificationStatus.GENUINE_CERTIFIED || it.status == VerificationStatus.LEGACY_CAMERA
            }
            FilterTab.TAMPERED -> allGalleryItems.filter {
                it.status == VerificationStatus.TAMPERED_WARNING
            }
            FilterTab.UNCERTIFIED -> allGalleryItems.filter {
                it.status == VerificationStatus.UNCERTIFIED_EXTERNAL
            }
        }

        displayedItems.clear()
        displayedItems.addAll(filtered)
        adapter.notifyDataSetChanged()

        updateHeaderStats()
        emptyState.visibility = if (displayedItems.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateHeaderStats() {
        val total = allGalleryItems.size
        val certified = allGalleryItems.count { it.status == VerificationStatus.GENUINE_CERTIFIED }
        val tampered = allGalleryItems.count { it.status == VerificationStatus.TAMPERED_WARNING }

        tabAll.text = "All ($total)"
        tabCertified.text = "Certified ($certified)"
        tabTampered.text = "Tampered ($tampered)"

        headerStats.text = "$certified Certified Proofs • $total Total Media"
    }

    private fun loadGalleryPhotos() {
        allGalleryItems.clear()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATA
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val relCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Image"
                    val dateAdded = cursor.getLong(dateCol)
                    val size = cursor.getLong(sizeCol)
                    val relPath = cursor.getString(relCol) ?: ""
                    val dataPath = cursor.getString(dataCol) ?: ""

                    val contentUri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )

                    val item = GalleryItem(
                        uri = contentUri,
                        name = name,
                        dateAdded = dateAdded,
                        sizeBytes = size,
                        isChecking = true
                    )
                    allGalleryItems.add(item)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        applyFilter()
        auditAllPhotos()
    }

    private fun auditAllPhotos() {
        auditJob?.cancel()
        auditJob = CoroutineScope(Dispatchers.IO).launch {
            for ((index, item) in allGalleryItems.withIndex()) {
                val result = SteganographyEngine.verifyImageIntegrity(this@MainActivity, item.uri)
                item.verificationResult = result
                item.isChecking = false

                withContext(Dispatchers.Main) {
                    if (displayedItems.contains(item)) {
                        adapter.notifyItemChanged(displayedItems.indexOf(item))
                    }
                    updateHeaderStats()
                }
            }
        }
    }

    private fun seedDemoPhotos() {
        Toast.makeText(this, "Minting sample cryptographic photos...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            // 1. Seed Genuine Certified Photo
            SampleDataSeeder.seedSamplePhoto(this@MainActivity, SampleDataSeeder.SeedType.GENUINE_CERTIFIED, "GENUINE")
            // 2. Seed Tampered Demo Photo
            SampleDataSeeder.seedSamplePhoto(this@MainActivity, SampleDataSeeder.SeedType.TAMPERED_DEMO, "TAMPERED")
            // 3. Seed Uncertified Photo
            SampleDataSeeder.seedSamplePhoto(this@MainActivity, SampleDataSeeder.SeedType.UNCERTIFIED, "EXTERNAL")

            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Demo Photos Minted to DCIM/fretG!", Toast.LENGTH_SHORT).show()
                loadGalleryPhotos()
            }
        }
    }

    private fun openFullScreen(item: GalleryItem) {
        val intent = Intent(this, FullScreenActivity::class.java).apply {
            putExtra("imageUri", item.uri.toString())
            putExtra("imageName", item.name)
            item.verificationResult?.let {
                putExtra("verificationResultJson", Gson().toJson(it))
            }
        }
        startActivity(intent)
    }

    private fun hasPermissions(): Boolean {
        val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        return mediaPermission
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
        }
        ActivityCompat.requestPermissions(this, permissions, 101)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadGalleryPhotos()
        } else {
            Toast.makeText(this, "Storage permission is required to display your gallery", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        auditJob?.cancel()
    }
}
