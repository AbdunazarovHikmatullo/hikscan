package app.hikscan.online

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.hikscan.online.ui.ScanAdapter
import app.hikscan.online.ui.ScanGroup
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var btnScan: MaterialButton
    private lateinit var rvGallery: RecyclerView
    private lateinit var emptyState: View
    private lateinit var adapter: ScanAdapter
    
    private val scanHistory = mutableListOf<ScanGroup>()
    private val PREFS_NAME = "hikscan_prefs"
    private val KEY_SCANS = "saved_scans"
    private val KEY_THEME = "theme_mode"
    private val KEY_VIEW_MODE = "view_mode" // true for Grid, false for List

    private val viewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val scanId = result.data?.getLongExtra("scan_id", -1) ?: -1
            val action = result.data?.getStringExtra("action")
            
            if (scanId != -1L) {
                if (action == "DELETE") {
                    deleteScan(scanId)
                } else {
                    val newName = result.data?.getStringExtra("new_name") ?: ""
                    val index = scanHistory.indexOfFirst { it.id == scanId }
                    if (index != -1) {
                        scanHistory[index].name = newName
                        persistScans()
                        adapter.notifyItemChanged(index)
                    }
                }
            }
        }
    }

    private fun deleteScan(id: Long) {
        val index = scanHistory.indexOfFirst { it.id == id }
        if (index != -1) {
            val group = scanHistory[index]
            // Delete physical files
            group.pdfFilePath?.let { File(it).delete() }
            group.imageUris.forEach { uri -> 
                uri.path?.let { File(it).delete() }
            }
            
            scanHistory.removeAt(index)
            persistScans()
            updateUI()
            Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
        }
    }

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.let { it ->
                val imageUris = it.pages?.map { page -> page.imageUri } ?: emptyList()
                val pdfResult = it.pdf
                if (pdfResult != null) {
                    saveScanLocally(imageUris, pdfResult.uri)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean(KEY_THEME, false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        btnScan = findViewById(R.id.btn_scan)
        rvGallery = findViewById(R.id.rv_gallery)
        emptyState = findViewById(R.id.tv_empty)

        loadSavedScans()
        setupRecyclerView()

        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        val scanner = GmsDocumentScanning.getClient(options)

        btnScan.setOnClickListener {
            scanner.getStartScanIntent(this)
                .addOnSuccessListener { intentSender ->
                    scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
                .addOnFailureListener { e ->
                    Log.e("ScannerError", "Error starting scanner: ${e.message}")
                }
        }
        
        updateUI()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        
        val themeItem = menu?.findItem(R.id.action_theme)
        val isDarkMode = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        themeItem?.setIcon(if (isDarkMode) R.drawable.ic_sun else R.drawable.ic_moon)
        
        val viewItem = menu?.findItem(R.id.action_view_mode)
        val isGrid = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_VIEW_MODE, true)
        viewItem?.setIcon(if (isGrid) R.drawable.ic_list else R.drawable.ic_grid)
        
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_theme -> {
                toggleTheme()
                return true
            }
            R.id.action_view_mode -> {
                toggleViewMode(item)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun toggleTheme() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean(KEY_THEME, false)
        val newMode = !isDarkMode
        prefs.edit().putBoolean(KEY_THEME, newMode).apply()
        AppCompatDelegate.setDefaultNightMode(
            if (newMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun toggleViewMode(item: MenuItem) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isGrid = prefs.getBoolean(KEY_VIEW_MODE, true)
        val newGrid = !isGrid
        prefs.edit().putBoolean(KEY_VIEW_MODE, newGrid).apply()
        
        item.setIcon(if (newGrid) R.drawable.ic_list else R.drawable.ic_grid)
        updateLayoutManager(newGrid)
    }

    private fun updateLayoutManager(isGrid: Boolean) {
        rvGallery.layoutManager = if (isGrid) GridLayoutManager(this, 2) else LinearLayoutManager(this)
    }

    private fun setupRecyclerView() {
        val isGrid = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_VIEW_MODE, true)
        adapter = ScanAdapter(scanHistory) { group ->
            val intent = Intent(this, ViewActivity::class.java).apply {
                putStringArrayListExtra("image_uris", ArrayList(group.imageUris.map { it.toString() }))
                putExtra("pdf_path", group.pdfFilePath)
                putExtra("scan_id", group.id)
                putExtra("doc_name", group.name)
            }
            viewLauncher.launch(intent)
        }
        updateLayoutManager(isGrid)
        rvGallery.adapter = adapter
    }

    private fun saveScanLocally(imageUris: List<Uri>, tempPdfUri: Uri) {
        try {
            val timestamp = System.currentTimeMillis()
            val scansDir = File(filesDir, "scans")
            if (!scansDir.exists()) scansDir.mkdirs()

            val pdfFile = File(scansDir, "scan_$timestamp.pdf")
            contentResolver.openInputStream(tempPdfUri)?.use { input ->
                FileOutputStream(pdfFile).use { output -> input.copyTo(output) }
            }

            val savedImageUris = mutableListOf<Uri>()
            imageUris.forEachIndexed { index, uri ->
                val imgFile = File(scansDir, "scan_${timestamp}_$index.jpg")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(imgFile).use { output -> input.copyTo(output) }
                }
                savedImageUris.add(Uri.fromFile(imgFile))
            }

            val newGroup = ScanGroup(
                id = timestamp,
                timestamp = timestamp,
                imageUris = savedImageUris,
                pdfFilePath = pdfFile.absolutePath,
                pdfUri = Uri.fromFile(pdfFile)
            )

            scanHistory.add(0, newGroup)
            persistScans()
            updateUI()
        } catch (e: Exception) {
            Log.e("SaveError", e.message ?: "")
        }
    }

    private fun persistScans() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        scanHistory.forEach { group ->
            val jsonObj = JSONObject().apply {
                put("id", group.id)
                put("timestamp", group.timestamp)
                put("pdfPath", group.pdfFilePath)
                put("name", group.name)
                put("images", JSONArray(group.imageUris.map { it.toString() }))
            }
            jsonArray.put(jsonObj)
        }
        prefs.edit().putString(KEY_SCANS, jsonArray.toString()).apply()
    }

    private fun loadSavedScans() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedData = prefs.getString(KEY_SCANS, null) ?: return
        try {
            val jsonArray = JSONArray(savedData)
            scanHistory.clear()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val imgArray = obj.getJSONArray("images")
                val imgUris = mutableListOf<Uri>()
                for (j in 0 until imgArray.length()) {
                    imgUris.add(Uri.parse(imgArray.getString(j)))
                }
                scanHistory.add(ScanGroup(
                    id = obj.getLong("id"),
                    timestamp = obj.getLong("timestamp"),
                    imageUris = imgUris,
                    pdfFilePath = obj.optString("pdfPath"),
                    pdfUri = Uri.fromFile(File(obj.optString("pdfPath"))),
                    name = obj.optString("name", "")
                ))
            }
        } catch (e: Exception) {
            Log.e("LoadError", e.message ?: "")
        }
    }

    private fun updateUI() {
        if (scanHistory.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            rvGallery.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            rvGallery.visibility = View.VISIBLE
            adapter.updateData(scanHistory)
        }
    }
}