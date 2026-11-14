package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Chaquopy 解释器的**幂等**启动器。
 *
 * ## 为什么需要它
 *
 * Chaquopy 的 `Python.start()` 对同一个进程只能成功一次，第二次调用会抛
 * `IllegalStateException: Python already started`。
 *
 * 旧实现在 [CalculatorTool] 和 [PythonTools] 里各写了一遍
 * `if (!Python.isStarted()) { Python.start(...) }` —— 这是典型的 check-then-act 竞态：
 * 模型经常在**同一个 step 里并行发起多个** calculator 调用（测试 A 实测 5 个并行），
 * 5 个协程同时读到 `isStarted() == false`，于是全部去 start()，后 4 个全部抛异常。
 * 真机表现就是模型报告「5 个计算器有 4 个报 Python already started 错误」。
 *
 * ## 修法
 *
 * 用 [Mutex] 串行化启动过程，进入临界区后**再判一次** `isStarted()`
 * （双重检查），保证无论多少个调用并发进来，`Python.start()` 都只执行一次。
 * 启动本身仍必须切到主线程 —— 这是 Chaquopy 的硬性要求。
 */
internal object PythonRuntime {
    private val startMutex = Mutex()

    /** 保证解释器已启动；可被任意多个协程并发调用。 */
    suspend fun ensureStarted(context: Context) {
        if (Python.isStarted()) return
        startMutex.withLock {
            // 双重检查：可能已有别的协程在等锁期间完成了启动
            if (Python.isStarted()) return
            withContext(Dispatchers.Main) {
                // 主线程上再判一次：避免极少数情况下 Main 调度期间被其他线程抢先启动
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(context.applicationContext))
                }
            }
        }
    }

    /** 供非挂起上下文（工具 execute 里的阻塞段）使用。 */
    fun ensureStartedBlocking(context: Context) {
        if (Python.isStarted()) return
        runBlocking { ensureStarted(context) }
    }
}
