package app.hikscan.online

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.EditText
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class ViewActivity : AppCompatActivity() {

    private var pdfPath: String? = null
    private var scanId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view)

        pdfPath = intent.getStringExtra("pdf_path")
        scanId = intent.getLongExtra("scan_id", -1)
        val initialName = intent.getStringExtra("doc_name") ?: ""

        val toolbar = findViewById<Toolbar>(R.id.toolbar_view)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val etName = findViewById<EditText>(R.id.et_document_name)
        etName.setText(initialName)
        etName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveNewName(etName.text.toString())
                etName.clearFocus()
                true
            } else false
        }

        val imageUris = intent.getStringArrayListExtra("image_uris")?.map { Uri.parse(it) } ?: emptyList()

        val rvPages = findViewById<RecyclerView>(R.id.rv_pages)
        rvPages.layoutManager = LinearLayoutManager(this)
        rvPages.adapter = PageAdapter(imageUris)
    }

    private fun saveNewName(newName: String) {
        val intent = Intent().apply {
            putExtra("scan_id", scanId)
            putExtra("new_name", newName)
        }
        setResult(RESULT_OK, intent)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_view, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_share -> {
                sharePdf()
                return true
            }
            R.id.action_delete -> {
                showDeleteConfirmation()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showDeleteConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Document")
            .setMessage("Are you sure you want to delete this document permanently?")
            .setPositiveButton("Delete") { _, _ ->
                val intent = Intent().apply {
                    putExtra("scan_id", scanId)
                    putExtra("action", "DELETE")
                }
                setResult(RESULT_OK, intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sharePdf() {
        pdfPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Share PDF"))
            }
        }
    }

    class PageAdapter(private val uris: List<Uri>) : RecyclerView.Adapter<PageAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivPage: ImageView = view.findViewById(R.id.iv_page)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_page, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            Glide.with(holder.ivPage.context).load(uris[position]).into(holder.ivPage)
        }

        override fun getItemCount() = uris.size
    }
}