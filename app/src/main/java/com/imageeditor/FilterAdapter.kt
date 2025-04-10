package com.imageeditor

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FilterAdapter(
    private val recyclerView: RecyclerView,
    private val filters: List<FilterType>, 
    private val onFilterSelected: (FilterType) -> Unit
) : RecyclerView.Adapter<FilterAdapter.FilterViewHolder>() {

    private var selectedFilter = FilterType.ORIGINAL
    private var currentImageUri: Uri? = null

    inner class FilterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val preview: ImageView = itemView.findViewById(R.id.filterPreview)
        val name: TextView = itemView.findViewById(R.id.filterName)
        val progress: ProgressBar = itemView.findViewById(R.id.filterProgress)
        val overlay: View = itemView.findViewById(R.id.filterSelectedOverlay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.filter_item, parent, false)
        return FilterViewHolder(view)
    }

    override fun onBindViewHolder(holder: FilterViewHolder, position: Int) {
        val filter = filters[position]
        
        holder.name.text = when (filter) {
            FilterType.ORIGINAL -> "Original"
            FilterType.BLACK_WHITE -> "B&W"
            FilterType.SEPIA -> "Sepia"
            FilterType.VINTAGE -> "Vintage"
            FilterType.VIGNETTE -> "Vignette"
            FilterType.BRIGHT -> "Bright"
            FilterType.CONTRAST -> "Contrast"
            FilterType.SATURATION -> "Vibrant"
            FilterType.HUE -> "Hue"
            FilterType.SHARPEN -> "Sharpen"
            FilterType.RETRO -> "Retro"
            FilterType.FILM -> "Film"
            FilterType.DRAMATIC -> "Dramatic"
            FilterType.GOLDEN -> "Golden"
            FilterType.TEAL_ORANGE -> "Cinematic"
        }

        // Show selected state
        holder.overlay.visibility = if (filter == selectedFilter) View.VISIBLE else View.GONE

        // Click listener
        holder.itemView.setOnClickListener {
            onFilterSelected(filter)
            setSelectedFilter(filter)
        }
    }

    override fun getItemCount() = filters.size

    fun setSelectedFilter(filterType: FilterType) {
        val oldPosition = filters.indexOf(selectedFilter)
        val newPosition = filters.indexOf(filterType)
        selectedFilter = filterType
        notifyItemChanged(oldPosition)
        notifyItemChanged(newPosition)
    }

    suspend fun updatePreviews(uri: Uri, imageProcessor: ImageProcessor) = withContext(Dispatchers.Main) {
        currentImageUri = uri
        
        filters.forEachIndexed { index, filter ->
            val holder = getViewHolder(index)
            holder?.let {
                it.progress.visibility = View.VISIBLE
                it.preview.setImageBitmap(null)
                
                // Generate filter preview in background
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val bitmap = imageProcessor.loadImage(uri)
                        val previewBitmap = bitmap?.let { imageProcessor.applyFilter(filter, it) }
                        
                        withContext(Dispatchers.Main) {
                            it.preview.setImageBitmap(previewBitmap)
                            it.progress.visibility = View.GONE
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            it.progress.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun getViewHolder(position: Int): FilterViewHolder? {
        return try {
            // Find any existing view holder for the position
            var holder: FilterViewHolder? = null
            for (i in 0 until itemCount) {
                val viewHolder = findViewHolderForAdapterPosition(i)
                if (viewHolder is FilterViewHolder && viewHolder.adapterPosition == position) {
                    holder = viewHolder
                    break
                }
            }
            holder
        } catch (e: Exception) {
            null
        }
    }
}
