package com.example.fretgallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fretgallery.camera.CameraActivity
import com.example.fretgallery.data.SampleDataSeeder
import com.example.fretgallery.model.GalleryItem
import com.example.fretgallery.model.GallerySection
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
    private lateinit var emptyStateTitle: TextView
    private lateinit var emptyStateSubtitle: TextView
    private lateinit var activeSectionHeading: TextView
    private lateinit var headerStats: TextView

    private lateinit var sectionScrollView: HorizontalScrollView
    private lateinit var tabCamera: FrameLayout
    private lateinit var tabScreenshots: FrameLayout
    private lateinit var tabDownloads: FrameLayout
    private lateinit var tabWhatsapp: FrameLayout
    private lateinit var tabInstagram: FrameLayout

    private lateinit var txtTabCamera: TextView
    private lateinit var txtTabScreenshots: TextView
    private lateinit var txtTabDownloads: TextView
    private lateinit var txtTabWhatsapp: TextView
    private lateinit var txtTabInstagram: TextView

    private val allGalleryItems = mutableListOf<GalleryItem>()
    private val displayedItems = mutableListOf<GalleryItem>()
    private lateinit var adapter: PhotoAdapter

    private var currentSection = GallerySection.CAMERA
    private var auditJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        emptyState = findViewById(R.id.emptyState)
        emptyStateTitle = findViewById(R.id.emptyStateTitle)
        emptyStateSubtitle = findViewById(R.id.emptyStateSubtitle)
        activeSectionHeading = findViewById(R.id.activeSectionHeading)
        headerStats = findViewById(R.id.headerStats)

        sectionScrollView = findViewById(R.id.sectionScrollView)
        tabCamera = findViewById(R.id.tabCamera)
        tabScreenshots = findViewById(R.id.tabScreenshots)
        tabDownloads = findViewById(R.id.tabDownloads)
        tabWhatsapp = findViewById(R.id.tabWhatsapp)
        tabInstagram = findViewById(R.id.tabInstagram)

        txtTabCamera = findViewById(R.id.txtTabCamera)
        txtTabScreenshots = findViewById(R.id.txtTabScreenshots)
        txtTabDownloads = findViewById(R.id.txtTabDownloads)
        txtTabWhatsapp = findViewById(R.id.txtTabWhatsapp)
        txtTabInstagram = findViewById(R.id.txtTabInstagram)

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
        // Section tabs
        tabCamera.setOnClickListener { selectSection(GallerySection.CAMERA, tabCamera) }
        tabScreenshots.setOnClickListener { selectSection(GallerySection.SCREENSHOTS, tabScreenshots) }
        tabDownloads.setOnClickListener { selectSection(GallerySection.DOWNLOADS, tabDownloads) }
        tabWhatsapp.setOnClickListener { selectSection(GallerySection.WHATSAPP, tabWhatsapp) }
        tabInstagram.setOnClickListener { selectSection(GallerySection.INSTAGRAM, tabInstagram) }

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

        // Empty state buttons
        findViewById<View>(R.id.btnEmptyCapture).setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }

        findViewById<View>(R.id.btnEmptySeed).setOnClickListener {
            seedDemoPhotos()
        }
    }

    private fun selectSection(section: GallerySection, targetView: View) {
        currentSection = section

        val fontHandjet = ResourcesCompat.getFont(this, R.font.handjet)
        val fontQuicksand = ResourcesCompat.getFont(this, R.font.quicksand)

        val activeBg = R.drawable.bg_liquid_glass_active
        val inactiveBg = R.drawable.bg_liquid_glass_pill

        val activeColor = ContextCompat.getColor(this, R.color.text_primary)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_secondary)

        // Reset all tabs to inactive state
        listOf(
            Triple(tabCamera, txtTabCamera, "Camera"),
            Triple(tabScreenshots, txtTabScreenshots, "Screenshots"),
            Triple(tabDownloads, txtTabDownloads, "Downloads"),
            Triple(tabWhatsapp, txtTabWhatsapp, "WhatsApp"),
            Triple(tabInstagram, txtTabInstagram, "Instagram")
        ).forEach { (tab, text, _) ->
            tab.setBackgroundResource(inactiveBg)
            text.typeface = fontQuicksand
            text.textSize = 14f
            text.setTextColor(inactiveColor)
        }

        // Magnify and activate the selected tab
        when (section) {
            GallerySection.CAMERA -> {
                tabCamera.setBackgroundResource(activeBg)
                txtTabCamera.typeface = fontHandjet
                txtTabCamera.textSize = 20f
                txtTabCamera.setTextColor(activeColor)
                activeSectionHeading.text = "CAMERA VAULT"
            }
            GallerySection.SCREENSHOTS -> {
                tabScreenshots.setBackgroundResource(activeBg)
                txtTabScreenshots.typeface = fontHandjet
                txtTabScreenshots.textSize = 20f
                txtTabScreenshots.setTextColor(activeColor)
                activeSectionHeading.text = "SCREENSHOTS"
            }
            GallerySection.DOWNLOADS -> {
                tabDownloads.setBackgroundResource(activeBg)
                txtTabDownloads.typeface = fontHandjet
                txtTabDownloads.textSize = 20f
                txtTabDownloads.setTextColor(activeColor)
                activeSectionHeading.text = "DOWNLOADS"
            }
            GallerySection.WHATSAPP -> {
                tabWhatsapp.setBackgroundResource(activeBg)
                txtTabWhatsapp.typeface = fontHandjet
                txtTabWhatsapp.textSize = 20f
                txtTabWhatsapp.setTextColor(activeColor)
                activeSectionHeading.text = "WHATSAPP MEDIA"
            }
            GallerySection.INSTAGRAM -> {
                tabInstagram.setBackgroundResource(activeBg)
                txtTabInstagram.typeface = fontHandjet
                txtTabInstagram.textSize = 20f
                txtTabInstagram.setTextColor(activeColor)
                activeSectionHeading.text = "INSTAGRAM"
            }
        }

        // Smoothly center the active tab in the HorizontalScrollView
        sectionScrollView.post {
            val scrollX = targetView.left - (sectionScrollView.width / 2) + (targetView.width / 2)
            sectionScrollView.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
        }

        applySectionFilter()
    }

    private fun applySectionFilter() {
        // Exclusively filter media belonging only to currentSection
        val filtered = allGalleryItems.filter { it.section == currentSection }

        displayedItems.clear()
        displayedItems.addAll(filtered)
        adapter.notifyDataSetChanged()

        val count = displayedItems.size
        val certifiedCount = displayedItems.count { it.status == com.example.fretgallery.model.VerificationStatus.GENUINE_CERTIFIED }

        headerStats.text = "$count Items • $certifiedCount Certified"

        if (displayedItems.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            emptyStateTitle.text = "No items in ${currentSection.name.lowercase().replaceFirstChar { it.uppercase() }}"
            emptyStateSubtitle.text = "Only ${currentSection.name.lowercase()} photos are shown in this section."
        } else {
            emptyState.visibility = View.GONE
        }
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

                    val section = determineSection(relPath, dataPath, name)

                    val item = GalleryItem(
                        uri = contentUri,
                        name = name,
                        dateAdded = dateAdded,
                        sizeBytes = size,
                        section = section,
                        isChecking = true
                    )
                    allGalleryItems.add(item)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        applySectionFilter()
        auditAllPhotos()
    }

    private fun determineSection(relPath: String, dataPath: String, name: String): GallerySection {
        val path = "$relPath $dataPath $name".lowercase()

        return when {
            path.contains("screenshot") -> GallerySection.SCREENSHOTS
            path.contains("whatsapp") -> GallerySection.WHATSAPP
            path.contains("instagram") -> GallerySection.INSTAGRAM
            path.contains("download") -> GallerySection.DOWNLOADS
            else -> GallerySection.CAMERA
        }
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
                    val count = displayedItems.size
                    val certifiedCount = displayedItems.count { it.status == com.example.fretgallery.model.VerificationStatus.GENUINE_CERTIFIED }
                    headerStats.text = "$count Items • $certifiedCount Certified"
                }
            }
        }
    }

    private fun seedDemoPhotos() {
        Toast.makeText(this, "Seeding cryptographic demo photos for all sections...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            // 1. Camera: Genuine Certified & Tampered Demo
            SampleDataSeeder.seedSamplePhoto(this@MainActivity, SampleDataSeeder.SeedType.GENUINE_CERTIFIED, "CAMERA_GENUINE", "DCIM/fretG")
            SampleDataSeeder.seedSamplePhoto(this@MainActivity, SampleDataSeeder.SeedType.TAMPERED_DEMO, "CAMERA_TAMPERED", "DCIM/fretG")
            
            // 2. Screenshots
            SampleDataSeeder.seedSamplePhoto(this@MainActivity, SampleDataSeeder.SeedType.UNCERTIFIED, "SCREENSHOT", "Pictures/Screenshots")
            
            // 3. Downloads
            SampleDataSeeder.seedSamplePhoto(this@MainActivity, SampleDataSeeder.SeedType.UNCERTIFIED, "DOWNLOAD", "Download")
            
            // 4. WhatsApp
            SampleDataSeeder.seedSamplePhoto(this@MainActivity, SampleDataSeeder.SeedType.UNCERTIFIED, "WHATSAPP", "WhatsApp/Media/WhatsApp Images")
            
            // 5. Instagram
            SampleDataSeeder.seedSamplePhoto(this@MainActivity, SampleDataSeeder.SeedType.UNCERTIFIED, "INSTAGRAM", "Pictures/Instagram")

            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Demo Photos Minted across all 5 sections!", Toast.LENGTH_SHORT).show()
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
