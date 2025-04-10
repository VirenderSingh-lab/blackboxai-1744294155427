package com.imageeditor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.imageeditor.databinding.ActivityMainBinding
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var filterAdapter: FilterAdapter
    
    private val PERMISSION_CODE = 1000
    private val IMAGE_PICK_CODE = 1001
    private val CROP_CODE = 1002
    
    private var currentImageUri: Uri? = null
    private var isProcessing = false
    private var currentFilterType = FilterType.ORIGINAL
    private val editHistory = mutableListOf<Bitmap>()
    private var currentHistoryPosition = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize components
        setupUI()
        setupBottomSheet()
        setupFilterRecyclerView()
        setupAdjustments()
        
        // Initialize image processor
        imageProcessor = ImageProcessor(this)

        // Show image picker on start
        pickImageFromGallery()
    }

    private fun setupUI() {
        binding.apply {
            btnBack.setOnClickListener {
                onBackPressed()
            }

            btnSave.setOnClickListener {
                if (!isProcessing) {
                    saveImage()
                }
            }

            btnMore.setOnClickListener {
                showMoreOptions()
            }

            imagePreview.setOnClickListener {
                if (!isProcessing) {
                    pickImageFromGallery()
                }
            }

            // Edit controls
            btnUndo.setOnClickListener {
                if (!isProcessing && canUndo()) {
                    undo()
                }
            }

            btnRedo.setOnClickListener {
                if (!isProcessing && canRedo()) {
                    redo()
                }
            }

            btnCrop.setOnClickListener {
                if (!isProcessing) {
                    startCrop()
                }
            }

            btnRotate.setOnClickListener {
                if (!isProcessing) {
                    rotateImage()
                }
            }

            updateEditControlsState()
        }
    }

    private fun showMoreOptions() {
        val popup = PopupMenu(this, binding.btnMore)
        popup.menuInflater.inflate(R.menu.edit_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_flip_horizontal -> {
                    flipImage(horizontal = true)
                    true
                }
                R.id.menu_flip_vertical -> {
                    flipImage(horizontal = false)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun addToHistory(bitmap: Bitmap) {
        // Remove any redo history
        if (currentHistoryPosition < editHistory.size - 1) {
            editHistory.subList(currentHistoryPosition + 1, editHistory.size).clear()
        }
        
        editHistory.add(bitmap.copy(bitmap.config, true))
        currentHistoryPosition = editHistory.size - 1
        updateEditControlsState()
    }

    private fun canUndo() = currentHistoryPosition > 0

    private fun canRedo() = currentHistoryPosition < editHistory.size - 1

    private fun undo() {
        if (canUndo()) {
            currentHistoryPosition--
            binding.imagePreview.setImageBitmap(editHistory[currentHistoryPosition])
            updateEditControlsState()
        }
    }

    private fun redo() {
        if (canRedo()) {
            currentHistoryPosition++
            binding.imagePreview.setImageBitmap(editHistory[currentHistoryPosition])
            updateEditControlsState()
        }
    }

    private fun updateEditControlsState() {
        binding.apply {
            btnUndo.isEnabled = canUndo()
            btnUndo.alpha = if (canUndo()) 1f else 0.5f
            
            btnRedo.isEnabled = canRedo()
            btnRedo.alpha = if (canRedo()) 1f else 0.5f
        }
    }

    private fun startCrop() {
        currentImageUri?.let { uri ->
            val options = UCrop.Options().apply {
                setToolbarColor(ContextCompat.getColor(this@MainActivity, R.color.background_dark))
                setStatusBarColor(ContextCompat.getColor(this@MainActivity, R.color.background_dark))
                setToolbarWidgetColor(ContextCompat.getColor(this@MainActivity, R.color.white))
                setActiveControlsWidgetColor(ContextCompat.getColor(this@MainActivity, R.color.primary))
            }

            val destinationUri = Uri.fromFile(File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg"))
            UCrop.of(uri, destinationUri)
                .withAspectRatio(0f, 0f)
                .withOptions(options)
                .start(this)
        }
    }

    private fun rotateImage() {
        if (isProcessing) return
        
        isProcessing = true
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val rotatedBitmap = imageProcessor.rotateImage(90f)
                binding.imagePreview.setImageBitmap(rotatedBitmap)
                addToHistory(rotatedBitmap)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error rotating image", Toast.LENGTH_SHORT).show()
            } finally {
                isProcessing = false
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun flipImage(horizontal: Boolean) {
        if (isProcessing) return
        
        isProcessing = true
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val flippedBitmap = imageProcessor.flipImage(horizontal)
                binding.imagePreview.setImageBitmap(flippedBitmap)
                addToHistory(flippedBitmap)
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Error flipping image ${if (horizontal) "horizontally" else "vertically"}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isProcessing = false
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheetBehavior.apply {
            peekHeight = resources.getDimensionPixelSize(R.dimen.bottom_sheet_peek_height)
            this.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    private fun setupFilterRecyclerView() {
        filterAdapter = FilterAdapter(FilterType.values().toList()) { filterType ->
            if (!isProcessing) {
                currentFilterType = filterType
                applyFilterWithLoading(filterType)
            }
        }
        
        binding.filterRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = filterAdapter
        }
    }

    private fun setupAdjustments() {
        binding.apply {
            seekBarBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!isProcessing && fromUser) {
                        adjustBrightnessWithLoading(progress)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            seekBarContrast.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!isProcessing && fromUser) {
                        adjustContrastWithLoading(progress)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            seekBarSaturation.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!isProcessing && fromUser) {
                        adjustSaturationWithLoading(progress)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }

    private fun applyFilterWithLoading(filterType: FilterType) {
        if (currentImageUri == null) return
        
        isProcessing = true
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val filteredBitmap = imageProcessor.applyFilter(filterType)
                binding.imagePreview.setImageBitmap(filteredBitmap)
                filterAdapter.setSelectedFilter(filterType)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error applying filter", Toast.LENGTH_SHORT).show()
            } finally {
                isProcessing = false
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun adjustBrightnessWithLoading(progress: Int) {
        if (currentImageUri == null) return
        
        isProcessing = true
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val adjustedBitmap = imageProcessor.adjustBrightness(progress)
                binding.imagePreview.setImageBitmap(adjustedBitmap)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error adjusting brightness", Toast.LENGTH_SHORT).show()
            } finally {
                isProcessing = false
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun adjustContrastWithLoading(progress: Int) {
        if (currentImageUri == null) return
        
        isProcessing = true
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val adjustedBitmap = imageProcessor.adjustContrast(progress)
                binding.imagePreview.setImageBitmap(adjustedBitmap)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error adjusting contrast", Toast.LENGTH_SHORT).show()
            } finally {
                isProcessing = false
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun adjustSaturationWithLoading(progress: Int) {
        if (currentImageUri == null) return
        
        isProcessing = true
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val adjustedBitmap = imageProcessor.adjustSaturation(progress)
                binding.imagePreview.setImageBitmap(adjustedBitmap)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error adjusting saturation", Toast.LENGTH_SHORT).show()
            } finally {
                isProcessing = false
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun saveImage() {
        if (currentImageUri == null) return
        
        isProcessing = true
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val savedFile = imageProcessor.saveImage()
                savedFile?.let {
                    MediaScannerConnection.scanFile(
                        this@MainActivity,
                        arrayOf(it.absolutePath),
                        arrayOf("image/jpeg")
                    ) { _, uri ->
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Image saved successfully", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error saving image", Toast.LENGTH_SHORT).show()
            } finally {
                isProcessing = false
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            PERMISSION_CODE
        )
    }

    private fun pickImageFromGallery() {
        if (checkPermission()) {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, IMAGE_PICK_CODE)
        } else {
            requestPermission()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    pickImageFromGallery()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                IMAGE_PICK_CODE -> {
                    data?.data?.let { uri ->
                        currentImageUri = uri
                        binding.imagePreview.setImageURI(uri)
                        lifecycleScope.launch {
                            val bitmap = imageProcessor.loadImage(uri)
                            bitmap?.let { addToHistory(it) }
                            filterAdapter.updatePreviews(uri, imageProcessor)
                        }
                    }
                }
                UCrop.REQUEST_CROP -> {
                    val resultUri = UCrop.getOutput(data!!)
                    resultUri?.let { uri ->
                        currentImageUri = uri
                        lifecycleScope.launch {
                            imageProcessor.handleCropResult(uri)
                            val bitmap = imageProcessor.getCurrentBitmap()
                            bitmap?.let {
                                binding.imagePreview.setImageBitmap(it)
                                addToHistory(it)
                            }
                        }
                    }
                }
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            val error = UCrop.getError(data!!)
            Toast.makeText(this, "Error cropping image: ${error?.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
