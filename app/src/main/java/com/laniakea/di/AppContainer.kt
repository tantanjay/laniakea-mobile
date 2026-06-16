package com.laniakea.di

import android.content.Context
import com.laniakea.data.DiaryDatabase
import com.laniakea.manager.SecurityManager
import com.laniakea.engine.SentenceEmbedder
import com.laniakea.manager.AnalyticsManager
import com.laniakea.manager.SemanticManager
import com.laniakea.manager.VibeManager
import com.laniakea.manager.PeriodDigestManager
import com.laniakea.engine.AnomalyDetector
import com.laniakea.engine.CognitiveTracker

class AppContainer(private val context: Context) {
    val database: DiaryDatabase by lazy { DiaryDatabase.getDatabase(context) }
    val securityManager: SecurityManager by lazy { SecurityManager(context) }
    val embedder: SentenceEmbedder by lazy { SentenceEmbedder(context, database, securityManager) }
    
    val analyticsManager: AnalyticsManager by lazy { AnalyticsManager(database, securityManager) }
    
    var engineActiveProvider: () -> Boolean = { false }
    private val delegatedEngineActive: () -> Boolean = { engineActiveProvider() }
    
    val semanticManager: SemanticManager by lazy { SemanticManager(database, embedder, securityManager, delegatedEngineActive) }
    val vibeManager: VibeManager by lazy { VibeManager(embedder, delegatedEngineActive) }
    val digestManager: PeriodDigestManager by lazy { PeriodDigestManager(database, securityManager, delegatedEngineActive) }
    
    val anomalyDetector: AnomalyDetector by lazy { AnomalyDetector(database.diaryDao()) }
    val cognitiveTracker: CognitiveTracker by lazy { CognitiveTracker() }
}
