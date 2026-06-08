package com.laniakea.manager

import android.app.Application
import android.net.Uri
import android.util.Base64
import android.util.JsonReader
import android.util.JsonWriter
import android.util.Log
import com.laniakea.data.AppSettings
import com.laniakea.data.DiaryDatabase
import com.laniakea.data.DiaryEntry
import com.laniakea.data.ObjectBoxManager
import com.laniakea.data.ObjectBoxSentenceVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Locale

class VaultManager(
    private val application: Application,
    private val db: DiaryDatabase,
    private val securityManager: SecurityManager,
    private val onProgress: (Int, Int) -> Unit,
    private val onStateChange: (isBackingUp: Boolean, isRestoring: Boolean, isImporting: Boolean) -> Unit
) {

    suspend fun exportDataStream(uri: Uri, password: String, onComplete: (Boolean) -> Unit) {
        withContext(Dispatchers.Main) {
            onStateChange(true, false, false)
            onProgress(0, 0)
        }
        try {
            val outputStream = application.contentResolver.openOutputStream(uri) ?: throw Exception("Failed to open URI")
            val encryptedStream = securityManager.getEncryptingStream(outputStream, password.toCharArray())
            val writer = JsonWriter(withContext(Dispatchers.IO) {
                OutputStreamWriter(encryptedStream, "UTF-8")
            })

            writer.beginObject()

            val dao = db.diaryDao()

            // Settings
            val settings = dao.getSettings()
            if (settings != null) {
                writer.name("settings")
                writer.beginObject()
                writer.name("userName").value(settings.userName)
                writer.name("theme").value(settings.theme)
                settings.privacySeed?.let {
                    writer.name("privacySeed").value(securityManager.decrypt(it))
                }
                writer.endObject()
            }

            // Entries
            writer.name("entries")
            writer.beginArray()
            val entries = dao.getAllEntries()
            val totalCount = entries.size
            entries.forEachIndexed { index, entry ->
                writer.beginObject()
                writer.name("id").value(entry.id)
                writer.name("dateTime").value(entry.dateTime)
                val decryptedEntry = securityManager.decryptEntry(entry)
                writer.name("content").value(decryptedEntry.content)
                writer.name("mood").value(decryptedEntry.mood)
                writer.name("category").value(decryptedEntry.category)
                writer.name("weather").value(decryptedEntry.weather)
                writer.name("activities").value(decryptedEntry.activities)
                writer.name("numericMood").value(entry.numericMood)
                writer.name("latentVibe").value(entry.latentVibe)
                writer.name("isVectorized").value(entry.isVectorized)
                writer.endObject()
                withContext(Dispatchers.Main) { onProgress(index + 1, totalCount) }
            }
            writer.endArray()

            // Vectors
            writer.name("vectors")
            writer.beginArray()
            val vectors = ObjectBoxManager.vectorBox.all
            vectors.forEach { vector ->
                val floatArray = vector.vector
                if (floatArray != null) {
                    writer.beginObject()
                    writer.name("entryId").value(vector.entryId)
                    
                    val byteBuffer = java.nio.ByteBuffer.allocate(floatArray.size * 4)
                    floatArray.forEach { byteBuffer.putFloat(it) }
                    val byteArray = byteBuffer.array()
                    
                    writer.name("vector").value(Base64.encodeToString(byteArray, Base64.DEFAULT))
                    writer.endObject()
                }
            }
            writer.endArray()
            writer.endObject()
            writer.close()

            withContext(Dispatchers.Main) { onComplete(true) }
        } catch (e: Exception) {
            e.message?.let { Log.e("VaultManager", it) }
            e.printStackTrace()
            withContext(Dispatchers.Main) { onComplete(false) }
        } finally {
            withContext(Dispatchers.Main) { onStateChange(false, false, false) }
        }
    }

    suspend fun importDataStream(uri: Uri, password: String, onComplete: (Boolean) -> Unit) {
        withContext(Dispatchers.Main) {
            onStateChange(false, true, false)
            onProgress(0, 0)
        }
        try {
            val inputStream = application.contentResolver.openInputStream(uri) ?: throw Exception("Failed to open URI")
            val decryptedStream = securityManager.getDecryptingStream(inputStream, password.toCharArray())
            val reader = JsonReader(withContext(Dispatchers.IO) {
                InputStreamReader(decryptedStream, "UTF-8")
            })

            val dao = db.diaryDao()
            dao.clearDatabase()
            ObjectBoxManager.vectorBox.removeAll()

            val oldToNewIdMap = mutableMapOf<Long, Long>()

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "settings" -> {
                        reader.beginObject()
                        val currentSettings = dao.getSettings() ?: AppSettings()
                        var userName = currentSettings.userName
                        var theme = currentSettings.theme
                        var privacySeed = currentSettings.privacySeed

                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "userName" -> userName = reader.nextString()
                                "theme" -> theme = reader.nextString()
                                "privacySeed" -> privacySeed = securityManager.encrypt(reader.nextString())
                                else -> reader.skipValue()
                            }
                        }
                        dao.saveSettings(currentSettings.copy(
                            userName = userName,
                            theme = theme,
                            privacySeed = privacySeed
                        ))
                        reader.endObject()
                    }
                    "entries" -> {
                        reader.beginArray()
                        var count = 0
                        while (reader.hasNext()) {
                            reader.beginObject()
                            var oldId = -1L
                            var dateTime = 0L
                            var content = ""
                            var mood = ""
                            var category = ""
                            var weather = ""
                            var activities = ""
                            var numericMood = 0.0
                            var latentVibe = 0.0
                            var isVectorized = false

                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "id" -> oldId = reader.nextLong()
                                    "dateTime" -> dateTime = reader.nextLong()
                                    "content" -> content = reader.nextString()
                                    "mood" -> mood = reader.nextString()
                                    "category" -> category = reader.nextString()
                                    "weather" -> weather = reader.nextString()
                                    "activities" -> activities = reader.nextString()
                                    "numericMood" -> numericMood = reader.nextDouble()
                                    "latentVibe" -> latentVibe = reader.nextDouble()
                                    "isVectorized" -> isVectorized = reader.nextBoolean()
                                    else -> reader.skipValue()
                                }
                            }

                            val rawEntry = DiaryEntry(
                                dateTime = dateTime,
                                content = content,
                                mood = mood,
                                category = category,
                                weather = weather,
                                activities = activities,
                                numericMood = numericMood,
                                latentVibe = latentVibe,
                                isVectorized = isVectorized
                            )
                            val entryToSave = securityManager.encryptEntry(rawEntry)
                            val newId = dao.insertEntry(entryToSave)
                            if (oldId != -1L) oldToNewIdMap[oldId] = newId

                            count++
                            withContext(Dispatchers.Main) { onProgress(count, 0) }
                            reader.endObject()
                        }
                        reader.endArray()
                    }
                    "vectors" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            reader.beginObject()
                            var oldEntryId = -1L
                            var vectorBase64 = ""
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "entryId" -> oldEntryId = reader.nextLong()
                                    "vector" -> vectorBase64 = reader.nextString()
                                    else -> reader.skipValue()
                                }
                            }
                            val newEntryId = oldToNewIdMap[oldEntryId]
                            if (newEntryId != null && vectorBase64.isNotEmpty()) {
                                val byteArray = Base64.decode(vectorBase64, Base64.DEFAULT)
                                val byteBuffer = java.nio.ByteBuffer.wrap(byteArray)
                                val floats = FloatArray(byteArray.size / 4)
                                for (i in floats.indices) floats[i] = byteBuffer.getFloat()
                                
                                ObjectBoxManager.vectorBox.put(ObjectBoxSentenceVector(
                                    entryId = newEntryId,
                                    vector = floats
                                ))
                            }
                            reader.endObject()
                        }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            reader.close()

            withContext(Dispatchers.Main) {
                onComplete(true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) { onComplete(false) }
        } finally {
            withContext(Dispatchers.Main) { onStateChange(false, false, false) }
        }
    }

    suspend fun importXlsxStream(uri: Uri, onComplete: (Boolean) -> Unit) {
        withContext(Dispatchers.Main) {
            onStateChange(false, false, true)
            onProgress(0, 0)
        }
        try {
            val inputStream = application.contentResolver.openInputStream(uri) ?: throw Exception("Failed to open URI")
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)
            val totalRows = sheet.lastRowNum

            val dao = db.diaryDao()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            for (i in 1..totalRows) { // Skip header
                val row = sheet.getRow(i) ?: continue

                try {
                    val dateCell = row.getCell(0)
                    val dateStr = getCellDateStringValue(dateCell, "yyyy-MM-dd")
                        ?: getCellStringValue(dateCell).substringBefore(".")

                    val timeCell = row.getCell(1)
                    val timeStr = getCellDateStringValue(timeCell, "HH:mm:ss")
                        ?: getCellStringValue(timeCell).substringBefore(".")

                    val mood = getCellStringValue(row.getCell(2))
                    val category = getCellStringValue(row.getCell(3))
                    val weather = getCellStringValue(row.getCell(4))
                    val activity = getCellStringValue(row.getCell(5))
                    val content = getCellStringValue(row.getCell(6))

                    if (dateStr.isBlank() || timeStr.isBlank() || mood.isBlank()) continue

                    val dateTime = try {
                        dateFormat.parse("$dateStr $timeStr")?.time ?: continue
                    } catch (_: Exception) { continue }

                    val words = content.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    if (words.size < 5) continue

                    val numericMood = when(mood.lowercase()) {
                        "joy", "happy", "great", "excellent" -> 2.0
                        "good", "pleasant" -> 1.0
                        "neutral", "ok" -> 0.0
                        "sad", "bad", "unhappy" -> -1.0
                        "miserable", "terrible", "awful" -> -2.0
                        else -> 0.0
                    }

                    val rawEntry = DiaryEntry(
                        dateTime = dateTime,
                        content = content,
                        mood = mood,
                        category = category,
                        weather = weather,
                        activities = activity,
                        numericMood = numericMood,
                        latentVibe = 0.0,
                        isVectorized = false
                    )
                    dao.insertEntry(securityManager.encryptEntry(rawEntry))
                } catch (e: Exception) {
                    Log.e("VaultManager", "Error importing row $i", e)
                }

                withContext(Dispatchers.Main) { onProgress(i, totalRows) }
            }

            workbook.close()
            withContext(Dispatchers.IO) {
                inputStream.close()
            }

            withContext(Dispatchers.Main) {
                onComplete(true)
            }
        } catch (e: Exception) {
            Log.e("VaultManager", "XLSX Import failed", e)
            withContext(Dispatchers.Main) { onComplete(false) }
        } finally {
            withContext(Dispatchers.Main) { onStateChange(false, false, false) }
        }
    }

    private fun getCellDateStringValue(cell: Cell?, pattern: String): String? {
        if (cell == null) return null
        if (cell.cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            val date = cell.dateCellValue
            if (date != null) {
                return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
            }
        }
        return null
    }

    private fun getCellStringValue(cell: Cell?): String {
        return when (cell?.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                val value = cell.numericCellValue
                if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> cell.cellFormula
            else -> cell?.toString() ?: ""
        }
    }
}
