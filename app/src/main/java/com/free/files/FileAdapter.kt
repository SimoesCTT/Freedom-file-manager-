package com.free.files

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FileAdapter(
    private val items: MutableList<FileItem>,
    private val onClick: (FileItem) -> Unit,
    private val onLongClick: (FileItem) -> Unit,
    private val isSelected: (FileItem) -> Boolean
) : RecyclerView.Adapter<FileAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view
        val icon: ImageView = view.findViewById(R.id.icon)
        val name: TextView = view.findViewById(R.id.name)
        val details: TextView = view.findViewById(R.id.details)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        val vh = VH(v)
        v.setOnClickListener {
            val pos = vh.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(items[pos])
        }
        v.setOnLongClickListener {
            val pos = vh.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onLongClick(items[pos])
                true
            } else false
        }
        return vh
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
        holder.root.setBackgroundColor(
            if (isSelected(item)) 0x801565C0.toInt() else Color.TRANSPARENT
        )
    }

    override fun getItemCount() = items.size

    fun update(newItems: List<FileItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
