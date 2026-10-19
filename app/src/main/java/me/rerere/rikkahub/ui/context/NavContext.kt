package me.rerere.rikkahub.ui.context

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavKey
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import kotlin.uuid.Uuid

class Navigator(
    private val backStack: MutableList<NavKey>,
    private val fallbackScreenProvider: () -> Screen = { Screen.Chat(Uuid.random().toString()) },
) {
    val size: Int
        get() = synchronized(backStack) { backStack.size }

    val currentScreen: NavKey?
        get() = synchronized(backStack) { backStack.lastOrNull() }

    /**
     * Ensures the navigation backStack never becomes empty, preventing
     * `IllegalArgumentException: NavDisplay backstack cannot be empty` during recomposition.
     */
    fun ensureNonEmpty(fallback: NavKey? = null): NavKey {
        return synchronized(backStack) {
            Snapshot.withMutableSnapshot {
                if (backStack.isEmpty()) {
                    val root = fallback ?: fallbackScreenProvider()
                    backStack.add(root)
                    runCatching {
                        TavernRuntimeManager.appendLog(
                            "warn",
                            "[nav] ensureNonEmpty restored empty backStack with root=$root",
                        )
                    }
                }
                backStack.last()
            }
        }
    }

    fun navigate(screen: Screen, builder: NavigateOptionsBuilder.() -> Unit = {}) {
        val options = NavigateOptionsBuilder().apply(builder)
        synchronized(backStack) {
            Snapshot.withMutableSnapshot {
                options.popUpToScreen?.let { target ->
                    val targetIndex = backStack.indexOfLast { it == target }
                    if (targetIndex != -1) {
                        val removeFromIndex = if (options.popUpToInclusive) targetIndex else targetIndex + 1
                        val safeRemoveFrom = removeFromIndex.coerceIn(0, backStack.size)
                        // If popping down to index 0 (emptying the stack), replace in-place so size never hits 0
                        if (safeRemoveFrom == 0) {
                            backStack.add(screen)
                            repeat(backStack.size - 1) {
                                backStack.removeAt(0)
                            }
                            runCatching {
                                TavernRuntimeManager.appendLog(
                                    "info",
                                    "[nav] navigate(popUpTo root inclusive) -> $screen size=${backStack.size}",
                                )
                            }
                            return@withMutableSnapshot
                        }
                        repeat(backStack.size - safeRemoveFrom) {
                            backStack.removeLastOrNull()
                        }
                    }
                }

                if (options.launchSingleTop && backStack.lastOrNull() == screen) {
                    ensureNonEmpty(screen)
                    return@withMutableSnapshot
                }

                backStack.add(screen)
                runCatching {
                    TavernRuntimeManager.appendLog(
                        "info",
                        "[nav] navigate -> $screen size=${backStack.size}",
                    )
                }
            }
        }
    }

    fun clearAndNavigate(screen: Screen) {
        synchronized(backStack) {
            Snapshot.withMutableSnapshot {
                // Append the new root BEFORE removing previous entries so SnapshotStateList size never drops to 0.
                backStack.add(screen)
                while (backStack.size > 1) {
                    backStack.removeAt(0)
                }
                runCatching {
                    TavernRuntimeManager.appendLog(
                        "info",
                        "[nav] clearAndNavigate -> $screen size=${backStack.size}",
                    )
                }
            }
        }
    }

    fun popBackStack(): Boolean {
        return synchronized(backStack) {
            Snapshot.withMutableSnapshot {
                if (backStack.size > 1) {
                    val popped = backStack.removeLastOrNull()
                    ensureNonEmpty()
                    runCatching {
                        TavernRuntimeManager.appendLog(
                            "info",
                            "[nav] popBackStack popped=$popped remaining=${backStack.size}",
                        )
                    }
                    popped != null
                } else {
                    ensureNonEmpty()
                    runCatching {
                        TavernRuntimeManager.appendLog(
                            "debug",
                            "[nav] popBackStack ignored at root (size=${backStack.size})",
                        )
                    }
                    false
                }
            }
        }
    }
}

class NavigateOptionsBuilder {
    internal var popUpToScreen: Screen? = null
    internal var popUpToInclusive: Boolean = false
    var launchSingleTop: Boolean = false

    fun popUpTo(screen: Screen, builder: PopUpToBuilder.() -> Unit = {}) {
        val options = PopUpToBuilder().apply(builder)
        popUpToScreen = screen
        popUpToInclusive = options.inclusive
    }
}

class PopUpToBuilder {
    var inclusive: Boolean = false
}

val LocalNavController = compositionLocalOf<Navigator> {
    error("No Navigator provided")
}
