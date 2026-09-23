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
      private val allPackages: List<String>,
      private val curatedPackages: List<String>,
      private val selectedPackages: MutableSet<String>,
      private val onToggle: (String, Boolean) -> Unit
  ) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

      private var visiblePackages: List<String> = curatedPackages
      private val labelCache = mutableMapOf<String, String>()

          private fun labelFor(pkg: String): String = labelCache.getOrPut(pkg) {
                    val appInfo = try { pm.getApplicationInfo(pkg, 0) } catch (e: Exception) { null }
                            (appInfo?.let { pm.getApplicationLabel(it) } ?: pkg).toString()
          }

              fun filter(query: String) {
                        visiblePackages = if (query.isBlank()) {
                                      curatedPackages
                        } else {
                                      allPackages.filter {
                                                        labelFor(it).contains(query, ignoreCase = true) ||
                                                            it.contains(query, ignoreCase = true)
                                      }
                        }
                                notifyDataSetChanged()
              }

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
                                    val pkg = visiblePackages[position]
                                    holder.name.text = labelFor(pkg)
                                            try {
                                                          holder.icon.setImageDrawable(pm.getApplicationIcon(pkg))
                                            } catch (e: Exception) { /* ignore */ }

                                                    holder.checkbox.setOnCheckedChangeListener(null)
                                                            holder.checkbox.isChecked = pkg in selectedPackages
                                    holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                                                  if (isChecked) selectedPackages.add(pkg) else selectedPackages.remove(pkg)
                                                              onToggle(pkg, isChecked)
                                    }
                          }

                              override fun getItemCount() = visiblePackages.size
}
