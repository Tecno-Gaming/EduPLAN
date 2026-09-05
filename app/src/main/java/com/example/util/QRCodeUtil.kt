package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.example.data.model.HomeworkEntity
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object QRCodeUtil {

    private const val GZIP_PREFIX = "EPGZ:"

    /**
     * Sıkıştırma: Metni GZIP ile sıkıştırıp Base64 formatına çevirir.
     * QR kod piksel yoğunluğunu azaltarak taramayı son derece kolaylaştırır.
     */
    fun compressGzip(data: String): String {
        return try {
            val bos = ByteArrayOutputStream()
            GZIPOutputStream(bos).use { gzip ->
                gzip.write(data.toByteArray(Charsets.UTF_8))
            }
            val compressedBytes = bos.toByteArray()
            val base64Str = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
            "$GZIP_PREFIX$base64Str"
        } catch (e: Exception) {
            data // Sıkıştırma başarısız olursa ham metni döndür
        }
    }

    /**
     * Sıkıştırmayı Açma: GZIP + Base64 verisini orijinal JSON metnine çevirir.
     */
    fun decompressGzip(compressedData: String): String {
        val cleanData = compressedData.trim()
        if (!cleanData.startsWith(GZIP_PREFIX)) {
            return cleanData // Sıkıştırılmamış ham JSON verisi
        }

        return try {
            val base64Part = cleanData.removePrefix(GZIP_PREFIX)
            val gzipBytes = Base64.decode(base64Part, Base64.NO_WRAP)
            val bis = ByteArrayInputStream(gzipBytes)
            val gis = GZIPInputStream(bis)
            gis.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            cleanData // Açma başarısızsa orijinal veriyi dene
        }
    }

    /**
     * Yaklaşan (tarihi geçmemiş) ödevleri filtreleyip kompakt JSON formatında hazırlar
     * ve varsayılan olarak GZIP sıkıştırması uygular.
     */
    fun encodeHomeworksToCompressedPayload(
        homeworks: List<HomeworkEntity>,
        filterUpcomingOnly: Boolean = true
    ): String {
        val todayEpochDay = LocalDate.now().toEpochDay()
        val filteredHomeworks = if (filterUpcomingOnly) {
            homeworks.filter { it.dueDateEpochDay >= todayEpochDay }
        } else {
            homeworks
        }

        if (filteredHomeworks.isEmpty()) return ""

        val jsonArray = JSONArray()
        filteredHomeworks.forEach { hw ->
            val obj = JSONObject().apply {
                put("s", hw.subject)
                put("d", hw.dueDateEpochDay)
                put("df", hw.difficulty)
                put("p", hw.isProject)
                put("n", hw.notes)
                put("c", hw.isCompleted)
            }
            jsonArray.put(obj)
        }

        val wrapper = JSONObject().apply {
            put("app", "EduPLAN")
            put("version", 1)
            put("timestamp", System.currentTimeMillis())
            put("data", jsonArray)
        }

        val jsonStr = wrapper.toString()
        return compressGzip(jsonStr)
    }

    /**
     * Çaba gerektirmeyen tekil/çoklu QR parçalama desteği.
     */
    fun encodeHomeworksToChunks(
        homeworks: List<HomeworkEntity>,
        maxItemsPerChunk: Int = 4,
        filterUpcomingOnly: Boolean = true
    ): List<String> {
        val todayEpochDay = LocalDate.now().toEpochDay()
        val filtered = if (filterUpcomingOnly) {
            homeworks.filter { it.dueDateEpochDay >= todayEpochDay }
        } else homeworks

        if (filtered.isEmpty()) return emptyList()

        // Eğer ödev sayısı azsa tek sıkıştırılmış paket dön
        if (filtered.size <= maxItemsPerChunk) {
            return listOf(encodeHomeworksToCompressedPayload(filtered, filterUpcomingOnly = false))
        }

        val sessionId = System.currentTimeMillis().toString()
        val chunks = filtered.chunked(maxItemsPerChunk)
        val totalChunks = chunks.size

        return chunks.mapIndexed { index, hwSubList ->
            val jsonArray = JSONArray()
            hwSubList.forEach { hw ->
                val obj = JSONObject().apply {
                    put("s", hw.subject)
                    put("d", hw.dueDateEpochDay)
                    put("df", hw.difficulty)
                    put("p", hw.isProject)
                    put("n", hw.notes)
                    put("c", hw.isCompleted)
                }
                jsonArray.put(obj)
            }
            val wrapper = JSONObject().apply {
                put("app", "EduPLAN")
                put("version", 1)
                put("sessionId", sessionId)
                put("totalChunks", totalChunks)
                put("chunkIndex", index)
                put("data", jsonArray)
            }
            compressGzip(wrapper.toString())
        }
    }

    /**
     * ZXing kullanarak QR Kod Bitmap görseli üretir.
     */
    fun generateQRCodeBitmap(text: String, width: Int = 512, height: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height, hints)
        val matrixWidth = bitMatrix.width
        val matrixHeight = bitMatrix.height

        val pixels = IntArray(matrixWidth * matrixHeight)
        for (y in 0 until matrixHeight) {
            val offset = y * matrixWidth
            for (x in 0 until matrixWidth) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }

        val bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)
        return bitmap
    }

    /**
     * Görsel Bitmap üzerinden QR koddaki veriyi çözer.
     */
    fun readQRCodeFromBitmap(bitmap: Bitmap): String? {
        val decoded = tryDecodeBitmap(bitmap)
        if (decoded != null) return decoded

        return try {
            val maxDimension = 800
            if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
                tryDecodeBitmap(scaled)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun tryDecodeBitmap(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val luminanceSource = RGBLuminanceSource(width, height, pixels)
        val reader = QRCodeReader()

        val hints = mapOf(
            DecodeHintType.CHARACTER_SET to "UTF-8",
            DecodeHintType.TRY_HARDER to true
        )

        try {
            val binaryBitmap = BinaryBitmap(HybridBinarizer(luminanceSource))
            val result = reader.decode(binaryBitmap, hints)
            if (!result.text.isNullOrBlank()) return result.text
        } catch (_: Exception) {}

        try {
            val binaryBitmap = BinaryBitmap(GlobalHistogramBinarizer(luminanceSource))
            val result = reader.decode(binaryBitmap, hints)
            if (!result.text.isNullOrBlank()) return result.text
        } catch (_: Exception) {}

        return null
    }
}

data class QRParseResult(
    val homeworks: List<HomeworkEntity> = emptyList(),
    val totalChunks: Int = 1,
    val chunkIndex: Int = 0,
    val sessionId: String = "",
    val isComplete: Boolean = true,
    val errorMessage: String? = null
)

class MultiPartQRAccumulator {
    private var activeSessionId: String? = null
    private var expectedTotalChunks: Int = 1
    private val chunksMap = mutableMapOf<Int, List<HomeworkEntity>>()

    fun reset() {
        activeSessionId = null
        expectedTotalChunks = 1
        chunksMap.clear()
    }

    /**
     * Taranan veya yapıştırılan ham/sıkıştırılmış metni çözer ve veritabanı ID çakışmasını önlemek için
     * id = 0 olarak yeni HomeworkEntity nesneleri oluşturur.
     */
    fun processChunk(rawInput: String): QRParseResult {
        return try {
            val jsonStr = QRCodeUtil.decompressGzip(rawInput.trim())

            val validJsonIndex = jsonStr.indexOf("{")
            val lastJsonIndex = jsonStr.lastIndexOf("}")
            val parsedStr = if (validJsonIndex >= 0 && lastJsonIndex > validJsonIndex) {
                jsonStr.substring(validJsonIndex, lastJsonIndex + 1)
            } else jsonStr

            val wrapper = JSONObject(parsedStr)
            val app = wrapper.optString("app", "")
            val version = wrapper.optInt("version", -1)

            if (app != "EduPLAN" || version != 1) {
                return QRParseResult(errorMessage = "Geçersiz QR Kod! Bu QR kodu EduPLAN verisi içermiyor.")
            }

            val sessionId = wrapper.optString("sessionId", "single")
            val totalChunks = wrapper.optInt("totalChunks", 1)
            val chunkIndex = wrapper.optInt("chunkIndex", 0)

            if (activeSessionId != null && activeSessionId != sessionId) {
                return QRParseResult(errorMessage = "Bu QR kodu taradığınız ödev serisine ait değil.")
            }

            if (activeSessionId == null) {
                activeSessionId = sessionId
                expectedTotalChunks = totalChunks
            }

            val dataArray = wrapper.optJSONArray("data")
                ?: return QRParseResult(errorMessage = "QR kodunda ödev verisi bulunamadı.")

            val chunkHomeworks = mutableListOf<HomeworkEntity>()
            for (i in 0 until dataArray.length()) {
                val obj = dataArray.getJSONObject(i)
                val subject = obj.optString("s", "")
                if (subject.isEmpty()) {
                    return QRParseResult(errorMessage = "Eksik veya bozuk ödev verisi algılandı.")
                }
                val dueDateEpochDay = obj.optLong("d", LocalDate.now().toEpochDay())
                val difficulty = obj.optString("df", "NORMAL")
                val isProject = obj.optBoolean("p", false)
                val notes = obj.optString("n", "")
                val isCompleted = obj.optBoolean("c", false)

                // CRITICAL: Room Veritabanı ID Çakışmasını önlemek için id = 0 (auto-generate) atanır.
                chunkHomeworks.add(
                    HomeworkEntity(
                        id = 0, // Auto-generate primary key
                        subject = subject,
                        dueDateEpochDay = dueDateEpochDay,
                        difficulty = difficulty,
                        isProject = isProject,
                        notes = notes,
                        isCompleted = isCompleted,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }

            chunksMap[chunkIndex] = chunkHomeworks

            val isComplete = chunksMap.size == expectedTotalChunks
            val allHomeworks = if (isComplete) {
                (0 until expectedTotalChunks).flatMap { chunksMap[it] ?: emptyList() }
            } else emptyList()

            QRParseResult(
                homeworks = allHomeworks,
                totalChunks = expectedTotalChunks,
                chunkIndex = chunkIndex,
                sessionId = sessionId,
                isComplete = isComplete
            )
        } catch (e: Exception) {
            QRParseResult(errorMessage = "QR kod okunamadı veya veri biçimi geçersiz: ${e.localizedMessage}")
        }
    }
}
