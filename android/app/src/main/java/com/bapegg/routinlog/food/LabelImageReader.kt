package com.bapegg.routinlog.food

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.bapegg.routinlog.domain.NutritionLabelDraft
import com.bapegg.routinlog.domain.NutritionLabelParser
import com.bapegg.routinlog.domain.LabelTextLine
import com.bapegg.routinlog.domain.labelReadingOrder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Selected image stays on this device. Only user-confirmed nutrition is sent to our food API. */
object LabelImageReader {
    suspend fun read(context: Context, uri: Uri): NutritionLabelDraft = withContext(Dispatchers.IO) {
        val bitmap = decode(context, uri)
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        try {
            // ML Kit's task cannot be cancelled: retain its bitmap until native processing finishes.
            val result = withContext(NonCancellable) { recognizer.process(InputImage.fromBitmap(bitmap, 0)).await() }
            val text = labelReadingOrder(result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                line.boundingBox?.let { LabelTextLine(line.text, it.left, it.top, it.right, it.bottom) }
            })
            require(text.isNotBlank()) { "No text" }
            NutritionLabelParser.parse(text)
        } finally { recognizer.close(); bitmap.recycle() }
    }

    fun decode(context: Context, uri: Uri, maxSide: Int = 2048): Bitmap {
        require(uri.scheme == "content") { "Unsupported image URI" }
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(requireNotNull(it), null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000) { "Image dimensions invalid" }
        var sample = 1
        while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
        val bitmap = resolver.openInputStream(uri).use {
            requireNotNull(BitmapFactory.decodeStream(requireNotNull(it), null, BitmapFactory.Options().apply { inSampleSize = sample }))
        }
        val orientation = runCatching { resolver.openInputStream(uri).use {
            ExifInterface(requireNotNull(it)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply { when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
        } }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
    }
}
