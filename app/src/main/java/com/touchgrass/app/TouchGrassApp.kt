package com.touchgrass.app

import android.app.Application
import android.content.Context

/**
 * Manual DI container. The app's collaborators are built once here and handed
 * out through the [appState] / [gemini] accessors below - Activities, Services
 * and Receivers are instantiated by the framework, so they can't take them as
 * constructor parameters and have to pull them off the Application instead.
 */
class TouchGrassApp : Application() {
    val appState: AppStateManager by lazy { AppStateManager(this) }
    val gemini: GeminiRepository by lazy { GeminiRepository(BuildConfig.GEMINI_API_KEY) }
}

private val Context.container: TouchGrassApp
    get() = applicationContext as TouchGrassApp

val Context.appState: AppStateManager get() = container.appState
val Context.gemini: GeminiRepository get() = container.gemini
