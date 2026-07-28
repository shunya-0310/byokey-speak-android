package com.byokeylab.speak

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

object TutorialTargets {
    const val CHATS_DAILY_NEWS = "chats_daily_news"
    const val CHATS_NEW_CHAT = "chats_new_chat"
    const val CHAT_AUTO_MODE = "chat_auto_mode"
    const val CHAT_QUICK_ASSIST = "chat_quick_assist"
    const val CHAT_WEB_SEARCH = "chat_web_search"
    const val CHAT_MIC = "chat_mic"
    const val SETTINGS_API_KEY = "settings_api_key"
    const val SETTINGS_HELP = "settings_help"
    const val SETTINGS_COACH = "settings_coach"
    const val SETTINGS_CEFR = "settings_cefr"
    const val SETTINGS_COACH_SKILL = "settings_coach_skill"
}

class TutorialAnchorRegistry {
    val bounds = mutableStateMapOf<String, Rect>()

    fun update(target: String, rect: Rect) {
        bounds[target] = rect
    }

    fun remove(target: String) {
        bounds.remove(target)
    }
}

val LocalTutorialAnchors = staticCompositionLocalOf<TutorialAnchorRegistry> {
    error("TutorialAnchorRegistry is not provided")
}

fun Modifier.tutorialAnchor(target: String): Modifier = composed {
    val registry = LocalTutorialAnchors.current
    DisposableEffect(registry, target) {
        onDispose { registry.remove(target) }
    }
    onGloballyPositioned { coordinates ->
        registry.update(target, coordinates.boundsInRoot())
    }
}
