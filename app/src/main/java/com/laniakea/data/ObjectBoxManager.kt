package com.laniakea.data

import android.content.Context
import io.objectbox.Box
import io.objectbox.BoxStore

object ObjectBoxManager {
    lateinit var store: BoxStore
        private set
    
    lateinit var vectorBox: Box<ObjectBoxSentenceVector>
        private set

    fun init(context: Context) {
        if (!this::store.isInitialized) {
            store = MyObjectBox.builder().androidContext(context).build()
            vectorBox = store.boxFor(ObjectBoxSentenceVector::class.java)
        }
    }

    /**
     * Performs a native semantic search using ObjectBox HNSW Index.
     */
    fun search(queryVector: FloatArray, maxResults: Int): List<ObjectBoxSentenceVector> {
        if (!this::store.isInitialized) return emptyList()

        return vectorBox.query(ObjectBoxSentenceVector_.vector.nearestNeighbors(queryVector, maxResults))
            .build()
            .findWithScores()
            .map { it.get() }
    }
}
