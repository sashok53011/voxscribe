package com.example.modules.storage_search

import android.content.Context
import com.example.modules.speaker_diarization.SpeakerDiarizerModule.SpeakerGender
import com.example.modules.speaker_diarization.SpeakerDiarizerModule.SpeakerSegment
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

/**
 * =========================================================================================
 * МОДУЛЬ: TranscriptionRepository (Репозиторий локального хранилища и поиска)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Единая точка доступа к локальной базе данных Room, конвертация структур данных
 * и обеспечение оффлайн-целостности.
 * 
 * ВХОДНЫЕ ДАННЫЕ (Inputs):
 * - context (Context): Для инициализации базы данных Room.
 * - поисковые запросы, идентификаторы папок, объекты TranscriptionEntity.
 * 
 * ВЫХОДНЫЕ ДАННЫЕ (Outputs):
 * - Flow<List<TranscriptionEntity>>, Flow<List<FolderEntity>>.
 */
class TranscriptionRepository(private val context: Context) {

    private val database = AppDatabase.getInstance(context)
    val folderDao = database.folderDao()
    val transcriptionDao = database.transcriptionDao()

    val allFoldersFlow: Flow<List<FolderEntity>> = folderDao.getAllFoldersFlow()
    val allTranscriptionsFlow: Flow<List<TranscriptionEntity>> = transcriptionDao.getAllTranscriptionsFlow()

    fun getTranscriptionsByFolder(folderId: String): Flow<List<TranscriptionEntity>> {
        return transcriptionDao.getTranscriptionsByFolderFlow(folderId)
    }

    fun searchTranscriptions(query: String): Flow<List<TranscriptionEntity>> {
        return transcriptionDao.searchTranscriptionsFlow(query.trim())
    }

    suspend fun getTranscriptionById(id: String): TranscriptionEntity? {
        return transcriptionDao.getTranscriptionById(id)
    }

    suspend fun insertOrUpdateTranscription(transcription: TranscriptionEntity) {
        transcriptionDao.insertTranscription(transcription)
    }

    suspend fun deleteTranscription(transcription: TranscriptionEntity) {
        transcriptionDao.deleteTranscription(transcription)
    }

    suspend fun insertFolder(name: String, colorHex: String = "#1A237E", iconName: String = "folder"): FolderEntity {
        val folder = FolderEntity(
            name = name,
            colorHex = colorHex,
            iconName = iconName
        )
        folderDao.insertFolder(folder)
        return folder
    }

    suspend fun deleteFolder(folder: FolderEntity) {
        folderDao.deleteFolder(folder)
    }

    /**
     * Начальное заполнение стандартными папками при первом запуске
     */
    suspend fun seedDefaultFoldersIfEmpty() {
        val existing = folderDao.getAllFolders()
        if (existing.isEmpty()) {
            folderDao.insertFolder(FolderEntity(id = "folder_meetings", name = "Встречи и звонки", colorHex = "#1A237E", iconName = "business"))
            folderDao.insertFolder(FolderEntity(id = "folder_interviews", name = "Интервью", colorHex = "#0D47A1", iconName = "mic"))
            folderDao.insertFolder(FolderEntity(id = "folder_notes", name = "Голосовые заметки", colorHex = "#00796B", iconName = "notes"))
            folderDao.insertFolder(FolderEntity(id = "folder_lectures", name = "Лекции и семинары", colorHex = "#6A1B9A", iconName = "school"))
        }
    }

    /**
     * Сериализация списка сегментов спикеров в компактный JSON, включая пословные таймкоды
     */
    fun serializeSegments(segments: List<SpeakerSegment>): String {
        val array = JSONArray()
        for (seg in segments) {
            val obj = JSONObject().apply {
                put("segmentId", seg.segmentId)
                put("speakerId", seg.speakerId)
                put("speakerDisplayName", seg.speakerDisplayName)
                put("gender", seg.gender.name)
                put("pitchHz", seg.pitchHz.toDouble())
                put("text", seg.text)
                put("translatedText", seg.translatedText)
                put("startTimeMs", seg.startTimeMs)
                put("endTimeMs", seg.endTimeMs)

                if (seg.words.isNotEmpty()) {
                    val wordsArr = JSONArray()
                    for (w in seg.words) {
                        wordsArr.put(JSONObject().apply {
                            put("w", w.word)
                            put("s", w.startMs)
                            put("e", w.endMs)
                            if (w.speakerId.isNotBlank()) put("spk", w.speakerId)
                        })
                    }
                    put("words", wordsArr)
                }
            }
            array.put(obj)
        }
        return array.toString()
    }

    /**
     * Десериализация JSON в список сегментов спикеров с пословными таймкодами
     */
    fun deserializeSegments(json: String): List<SpeakerSegment> {
        val list = mutableListOf<SpeakerSegment>()
        if (json.isBlank()) return list

        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val genderStr = obj.optString("gender", "UNKNOWN")
                val gender = try {
                    SpeakerGender.valueOf(genderStr)
                } catch (e: Exception) {
                    SpeakerGender.UNKNOWN
                }

                val text = obj.optString("text", "")
                val startMs = obj.optLong("startTimeMs", 0L)
                val endMs = obj.optLong("endTimeMs", 0L)
                val speakerId = obj.optString("speakerId", "speaker_1")

                val wordsList = mutableListOf<com.example.modules.word_timestamps.WordTimestamp>()
                val wordsArr = obj.optJSONArray("words")
                if (wordsArr != null && wordsArr.length() > 0) {
                    for (wIdx in 0 until wordsArr.length()) {
                        val wObj = wordsArr.getJSONObject(wIdx)
                        wordsList.add(
                            com.example.modules.word_timestamps.WordTimestamp(
                                word = wObj.optString("w", wObj.optString("word", "")),
                                startMs = wObj.optLong("s", wObj.optLong("startMs", 0L)),
                                endMs = wObj.optLong("e", wObj.optLong("endMs", 0L)),
                                speakerId = wObj.optString("spk", speakerId)
                            )
                        )
                    }
                } else if (text.isNotBlank()) {
                    // Эвристическое авто-выравнивание если в базе запись старой версии
                    wordsList.addAll(
                        com.example.modules.word_timestamps.WordTimestampModule.alignWordsToDuration(
                            text = text,
                            segmentStartMs = startMs,
                            segmentEndMs = endMs,
                            speakerId = speakerId
                        )
                    )
                }

                list.add(
                    SpeakerSegment(
                        segmentId = obj.optString("segmentId", java.util.UUID.randomUUID().toString()),
                        speakerId = speakerId,
                        speakerDisplayName = obj.optString("speakerDisplayName", "Спикер"),
                        gender = gender,
                        pitchHz = obj.optDouble("pitchHz", 150.0).toFloat(),
                        text = text,
                        translatedText = obj.optString("translatedText", ""),
                        startTimeMs = startMs,
                        endTimeMs = endMs,
                        words = wordsList
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("TranscriptionRepo", "Ошибка парсинга сегментов: ${e.message}")
        }
        return list
    }

    /**
     * Извлечение всех пословных таймкодов записи для подсветки в плеере
     */
    fun extractAllWords(transcription: TranscriptionEntity): List<com.example.modules.word_timestamps.WordTimestamp> {
        val fromJson = com.example.modules.word_timestamps.WordTimestampModule.deserializeWords(transcription.wordTimestampsJson)
        if (fromJson.isNotEmpty()) return fromJson

        val segments = deserializeSegments(transcription.segmentsJson)
        val allWords = segments.flatMap { it.words }
        return if (allWords.isNotEmpty()) {
            allWords
        } else {
            com.example.modules.word_timestamps.WordTimestampModule.alignWordsToDuration(
                text = transcription.fullText,
                segmentStartMs = 0L,
                segmentEndMs = (transcription.durationSeconds * 1000L).coerceAtLeast(1000L)
            )
        }
    }
}
