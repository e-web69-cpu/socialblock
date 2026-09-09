package com.example.socialblock

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppListAdapter(
    private val pm: PackageManager,
    private val packages: List<String>,
    private val blockedSet: MutableSet<String>,
    private val onToggle: (String, Boolean) -> Unit
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.itemIcon)
        val name: TextView = view.findViewById(R.id.itemName)
        val checkbox: CheckBox = view.findViewById(R.id.itemCheckbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pkg = packages[position]
        val appInfo = try { pm.getApplicationInfo(pkg, 0) } catch (e: Exception) { null }
        holder.name.text = appInfo?.let { pm.getApplicationLabel(it) } ?: pkg
        try {
            holder.icon.setImageDrawable(pm.getApplicationIcon(pkg))
        } catch (e: Exception) { /* ignore */ }

        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = pkg in blockedSet
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            onToggle(pkg, isChecked)
        }
    }

    override fun getItemCount() = packages.size
}
