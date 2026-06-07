package com.laniakea.data

import android.content.Context
import io.objectbox.Box
import io.objectbox.BoxStore

data class ScoredVector(
    val vector: ObjectBoxSentenceVector,
    val score: Double
)

object ObjectBoxManager {
    lateinit var store: BoxStore
        private set
    
    lateinit var vectorBox: Box<ObjectBoxSentenceVector>
        private set
        
    lateinit var themeBox: Box<ObjectBoxThemeCentroid>
        private set

    fun init(context: Context) {
        if (!this::store.isInitialized) {
            store = MyObjectBox.builder().androidContext(context).build()
            vectorBox = store.boxFor(ObjectBoxSentenceVector::class.java)
            themeBox = store.boxFor(ObjectBoxThemeCentroid::class.java)
        }
    }

    /**
     * Performs a native semantic search using ObjectBox HNSW Index.
     * Returns results with their distance scores (lower = more similar).
     * For normalized vectors: 0.0 = identical, ~1.0 = weakly related, ~1.41 = unrelated.
     */
    fun search(queryVector: FloatArray, maxResults: Int): List<ScoredVector> {
        if (!this::store.isInitialized) return emptyList()

        return vectorBox.query(ObjectBoxSentenceVector_.vector.nearestNeighbors(queryVector, maxResults))
            .build()
            .findWithScores()
            .map { ScoredVector(it.get(), it.score) }
    }
}
