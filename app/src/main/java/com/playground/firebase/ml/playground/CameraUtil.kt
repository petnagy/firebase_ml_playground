package com.playground.firebase.ml.playground

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView

fun loadBitmapFromCamera(imageView: ImageView, currentPhotoPath: String): Bitmap? {
    // Get the dimensions of the View
    val targetW = 720
    val targetH = 1280

    val bmOptions = BitmapFactory.Options().apply {
        // Get the dimensions of the bitmap
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(currentPhotoPath, this)
        val photoW: Int = outWidth
        val photoH: Int = outHeight

        // Determine how much to scale down the image
        val scaleFactor: Int = Math.min(photoW / targetW, photoH / targetH)

        // Decode the image file into a Bitmap sized to fill the View
        inJustDecodeBounds = false
        inSampleSize = scaleFactor
        inPurgeable = true
    }
    var convertBitmap: Bitmap? = null
    BitmapFactory.decodeFile(currentPhotoPath, bmOptions)?.also { bitmap ->
        imageView.setImageBitmap(bitmap)
        convertBitmap = bitmap
    }
    return convertBitmap
}