package app.hikscan.online.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import app.hikscan.online.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanAdapter(
    private var items: List<ScanGroup>,
    private val onItemClick: (ScanGroup) -> Unit
) : RecyclerView.Adapter<ScanAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPreview: ImageView = view.findViewById(R.id.iv_preview)
        val tvCount: TextView = view.findViewById(R.id.tv_count)
        val tvDate: TextView = view.findViewById(R.id.tv_date)
        val tvTime: TextView = view.findViewById(R.id.tv_time)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scan_group, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        
        // Animation removed for clean theme switching

        if (item.imageUris.isNotEmpty()) {
            Glide.with(holder.ivPreview.context)
                .load(item.imageUris[0])
                .centerCrop()
                .into(holder.ivPreview)
        }

        holder.tvCount.text = "${item.imageUris.size} pg"
        
        val dateSdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val date = Date(item.timestamp)
        
        // Show custom name if exists, otherwise show date
        holder.tvDate.text = if (item.name.isNotEmpty()) item.name else dateSdf.format(date)
        holder.tvTime.text = timeSdf.format(date)

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<ScanGroup>) {
        items = newItems
        notifyDataSetChanged()
    }
}