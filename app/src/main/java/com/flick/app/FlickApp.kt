package com.flick.app

import android.app.Application
import android.content.Context
import com.flick.app.data.FlickDatabase
import com.flick.app.data.NotesRepository
import com.flick.app.data.SettingsStore
import com.flick.app.haptics.HapticEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FlickApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = FlickDatabase.build(context)
    val repo = NotesRepository(db, appScope, context.applicationContext)
    val settings = SettingsStore(context.applicationContext)
    val haptics = HapticEngine(context.applicationContext)

    init {
        appScope.launch { settings.haptics.collect { haptics.enabled = it } }
        appScope.launch {
            repo.seedIfEmpty()
            repo.rebuildSearchIndexIfNeeded()
            repo.purgeOldTrash()
        }
    }
}
