package com.imageeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class FilterType {
    ORIGINAL,
    BLACK_WHITE,
    SEPIA,
    VINTAGE,
    VIGNETTE,
    BRIGHT,
    CONTRAST,
    SATURATION,
    HUE,
    SHARPEN,
    RETRO,
    FILM,
    DRAMATIC,
    GOLDEN,
    TEAL_ORANGE
}

class ImageProcessor(private val context: Context) {
    private val gpuImage = GPUImage(context)
    private var currentBitmap: Bitmap? = null
    private var currentFilter: GPUImageFilter = GPUImageFilter()
    private var brightness = 0f
    private var contrast = 1f
    private var saturation = 1f

    suspend fun loadImage(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            gpuImage.setImage(uri)
            currentBitmap = gpuImage.bitmapWithFilterApplied
            currentBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun applyFilter(filterType: FilterType, bitmap: Bitmap): Bitmap? {
        gpuImage.setImage(bitmap)
        currentFilter = when (filterType) {
            FilterType.ORIGINAL -> GPUImageFilter()
            FilterType.BLACK_WHITE -> GPUImageGrayscaleFilter()
            FilterType.SEPIA -> GPUImageSepiaToneFilter()
            FilterType.VINTAGE -> createVintageFilter()
            FilterType.VIGNETTE -> GPUImageVignetteFilter(
                PointF(0.5f, 0.5f),
                floatArrayOf(0.0f, 0.0f, 0.0f),
                0.3f,
                0.75f
            )
            FilterType.BRIGHT -> GPUImageBrightnessFilter(0.5f)
            FilterType.CONTRAST -> GPUImageContrastFilter(1.5f)
            FilterType.SATURATION -> GPUImageSaturationFilter(1.5f)
            FilterType.HUE -> GPUImageHueFilter(90f)
            FilterType.SHARPEN -> GPUImageSharpenFilter(2.0f)
            FilterType.RETRO -> createRetroFilter()
            FilterType.FILM -> createFilmFilter()
            FilterType.DRAMATIC -> createDramaticFilter()
            FilterType.GOLDEN -> createGoldenFilter()
            FilterType.TEAL_ORANGE -> createTealOrangeFilter()
        }
        
        gpuImage.setFilter(currentFilter)
        return gpuImage.bitmapWithFilterApplied
    }

    private fun createVintageFilter(): GPUImageFilterGroup {
        val filters = ArrayList<GPUImageFilter>()
        filters.add(GPUImageContrastFilter(1.3f))
        filters.add(GPUImageRGBFilter(1.0f, 0.9f, 0.7f))
        filters.add(GPUImageColorMatrixFilter(0.9f, floatArrayOf(
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.2f, 0.0f,
            0.0f, 0.0f, 0.8f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
        )))
        return GPUImageFilterGroup(filters)
    }

    private fun createRetroFilter(): GPUImageFilterGroup {
        val filters = ArrayList<GPUImageFilter>()
        filters.add(GPUImageContrastFilter(1.1f))
        filters.add(GPUImageRGBFilter(1.3f, 1.1f, 0.9f))
        filters.add(GPUImageVignetteFilter(PointF(0.5f, 0.5f), floatArrayOf(0.0f, 0.0f, 0.0f), 0.2f, 0.8f))
        return GPUImageFilterGroup(filters)
    }

    private fun createFilmFilter(): GPUImageFilterGroup {
        val filters = ArrayList<GPUImageFilter>()
        filters.add(GPUImageSaturationFilter(0.8f))
        filters.add(GPUImageContrastFilter(1.2f))
        filters.add(GPUImageColorMatrixFilter(1.0f, floatArrayOf(
            1.1f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.9f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
        )))
        return GPUImageFilterGroup(filters)
    }

    private fun createDramaticFilter(): GPUImageFilterGroup {
        val filters = ArrayList<GPUImageFilter>()
        filters.add(GPUImageContrastFilter(1.4f))
        filters.add(GPUImageBrightnessFilter(-0.1f))
        filters.add(GPUImageSaturationFilter(1.2f))
        return GPUImageFilterGroup(filters)
    }

    private fun createGoldenFilter(): GPUImageFilterGroup {
        val filters = ArrayList<GPUImageFilter>()
        filters.add(GPUImageRGBFilter(1.2f, 1.0f, 0.8f))
        filters.add(GPUImageContrastFilter(1.1f))
        filters.add(GPUImageBrightnessFilter(0.1f))
        return GPUImageFilterGroup(filters)
    }

    private fun createTealOrangeFilter(): GPUImageFilterGroup {
        val filters = ArrayList<GPUImageFilter>()
        filters.add(GPUImageContrastFilter(1.1f))
        filters.add(GPUImageColorMatrixFilter(1.0f, floatArrayOf(
            1.2f, -0.1f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.1f, 0.0f,
            -0.1f, 0.1f, 1.1f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
        )))
        return GPUImageFilterGroup(filters)
    }

    fun adjustBrightness(progress: Int): Bitmap? {
        brightness = (progress - 50) / 50f
        applyAdjustments()
        return currentBitmap
    }

    fun adjustContrast(progress: Int): Bitmap? {
        contrast = progress / 50f
        applyAdjustments()
        return currentBitmap
    }

    fun adjustSaturation(progress: Int): Bitmap? {
        saturation = progress / 50f
        applyAdjustments()
        return currentBitmap
    }

    private fun applyAdjustments() {
        val adjustmentGroup = GPUImageFilterGroup().apply {
            addFilter(currentFilter)
            addFilter(GPUImageBrightnessFilter(brightness))
            addFilter(GPUImageContrastFilter(contrast))
            addFilter(GPUImageSaturationFilter(saturation))
        }
        gpuImage.setFilter(adjustmentGroup)
        currentBitmap = gpuImage.bitmapWithFilterApplied
    }

    suspend fun saveImage(): File? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.getExternalFilesDir(null), "edited_image_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                currentBitmap?.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getCurrentBitmap(): Bitmap? = currentBitmap

    suspend fun rotateImage(degrees: Float): Bitmap = withContext(Dispatchers.IO) {
        val filter = GPUImageTransformFilter().apply {
            setRotation(degrees)
        }
        gpuImage.setFilter(filter)
        val rotatedBitmap = gpuImage.bitmapWithFilterApplied
        currentBitmap = rotatedBitmap
        rotatedBitmap
    }

    suspend fun flipImage(horizontal: Boolean): Bitmap = withContext(Dispatchers.IO) {
        val filter = GPUImageTransformFilter().apply {
            if (horizontal) {
                setScaleX(-1f)
            } else {
                setScaleY(-1f)
            }
        }
        gpuImage.setFilter(filter)
        val flippedBitmap = gpuImage.bitmapWithFilterApplied
        currentBitmap = flippedBitmap
        flippedBitmap
    }

    fun handleCropResult(uri: Uri) {
        gpuImage.setImage(uri)
        currentBitmap = gpuImage.bitmapWithFilterApplied
        applyFilter(currentFilterType)
    }
}
