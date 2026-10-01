package com.bapegg.routinlog

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.bapegg.routinlog.food.LabelImageReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.math.BigDecimal

class NutritionLabelRecognitionTest {
    @Test fun bundledKoreanRecognizerReadsAnActualImageAndPreservesAbsentFiber() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "nutrition-labels").apply { mkdirs() }
        val file = File.createTempFile("ocr-test-", ".png", directory)
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 44f }
            listOf("영양정보", "80g당 160kcal", "탄수화물 20g", "단백질 20g", "지방 0g").forEachIndexed { index, row ->
                canvas.drawText(row, 70f, 120f + index * 140, paint)
            }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.label-photos", file)
            val draft = LabelImageReader.read(context, uri)
            assertEquals(0, BigDecimal("80").compareTo(draft.basisGrams))
            assertEquals(0, BigDecimal("160").compareTo(draft.nutrition.kcal))
            assertEquals(0, BigDecimal("20").compareTo(draft.nutrition.proteinG))
            assertEquals(BigDecimal.ZERO, draft.nutrition.fatG)
            assertNull(draft.nutrition.fiberG)
        } finally { bitmap.recycle(); file.delete() }
    }
}
