package com.laniakea.manager

import com.laniakea.data.DiaryDatabase
import com.laniakea.data.DiaryEntry
import com.laniakea.data.ObjectBoxManager
import com.laniakea.data.ObjectBoxSentenceVector_
import com.laniakea.engine.SentenceEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SemanticManager(
    private val db: DiaryDatabase,
    private val embedder: SentenceEmbedder,
    private val securityManager: SecurityManager,
    private val isEngineActive: () -> Boolean
) {

    suspend fun semanticSearch(query: String, limit: Int = 5): List<DiaryEntry> {
        return withContext(Dispatchers.IO) {
            // Try semantic search if the engine is active
            if (isEngineActive()) {
                val vector = embedder.embed(query)
                if (vector != null) {
                    val similarVectors = ObjectBoxManager.search(vector, limit)
                    val similarIds = similarVectors.map { it.entryId }
                    
                    if (similarIds.isNotEmpty()) {
                        val entries = db.diaryDao().getEntriesByIds(similarIds)
                        val entryMap = entries.associateBy { it.id }
                        return@withContext similarIds.mapNotNull { entryMap[it] }.map { securityManager.decryptEntry(it) }
                    }
                }
            }
            
            // Fallback: plaintext search across all entries
            val allEntries = db.diaryDao().getAllEntries()
            val lowerQuery = query.lowercase()
            allEntries
                .map { securityManager.decryptEntry(it) }
                .filter { it.content.lowercase().contains(lowerQuery) }
                .take(limit)
        }
    }

    suspend fun findSimilarEntries(entryId: Long, limit: Int = 5): List<DiaryEntry> {
        return withContext(Dispatchers.IO) {
            val vectorObj = ObjectBoxManager.vectorBox.query(ObjectBoxSentenceVector_.entryId.equal(entryId)).build().findFirst()
            val vector = vectorObj?.vector ?: return@withContext emptyList()
            
            val similarVectors = ObjectBoxManager.search(vector, limit + 1)
            val similarIds = similarVectors.map { it.entryId }.filter { it != entryId }.take(limit)
            
            if (similarIds.isEmpty()) return@withContext emptyList()
            
            val entries = db.diaryDao().getEntriesByIds(similarIds)
            val entryMap = entries.associateBy { it.id }
            similarIds.mapNotNull { entryMap[it] }.map { securityManager.decryptEntry(it) }
        }
    }

    suspend fun getThemeClusters(): Map<String, List<DiaryEntry>> {
        if (!isEngineActive()) return emptyMap()
        return withContext(Dispatchers.IO) {
            val themes = listOf(
                "Relationships, Love, Friends",
                "Career, Work, Goals, Success",
                "Mental Health, Anxiety, Stress, Depression",
                "Physical Health, Exercise, Body, Sleep",
                "Personal Growth, Learning, Philosophy"
            )
            
            val themeClusters = mutableMapOf<String, List<DiaryEntry>>()
            
            for (theme in themes) {
                val themeVector = embedder.embed(theme) ?: continue
                val similarVectors = ObjectBoxManager.search(themeVector, 3)
                val similarIds = similarVectors.map { it.entryId }
                
                if (similarIds.isNotEmpty()) {
                    val entries = db.diaryDao().getEntriesByIds(similarIds)
                    val entryMap = entries.associateBy { it.id }
                    val decryptedEntries = similarIds.mapNotNull { entryMap[it] }.map { securityManager.decryptEntry(it) }
                    
                    val shortTheme = theme.split(",").first()
                    themeClusters[shortTheme] = decryptedEntries
                }
            }
            
            themeClusters
        }
    }

}
