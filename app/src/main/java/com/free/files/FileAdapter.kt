package com.free.files
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
class FileAdapter(
    private val items: MutableList<FileItem>,
    private val onClick: (FileItem) -> Unit
) : RecyclerView.Adapter<FileAdapter.VH>() {
    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon)
        val name: TextView = view.findViewById(R.id.name)
        val details: TextView = view.findViewById(R.id.details)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return VH(v)
    }
    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.details.text = if (item.isDir) "Folder"
            else "${FileUtils.formatSize(item.size)} - ${FileUtils.formatDate(item.lastModified)}"
        holder.icon.setImageResource(
            if (item.isDir) android.R.drawable.ic_menu_view
            else android.R.drawable.ic_menu_agenda
        )
        holder.itemView.setOnClickListener { onClick(item) }
    }
    override fun getItemCount() = items.size
    fun update(newItems: List<FileItem>) {
        items.clear(); items.addAll(newItems); notifyDataSetChanged()
    }
}
