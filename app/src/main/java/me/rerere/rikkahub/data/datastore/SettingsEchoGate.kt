// [v229 A4/P0-2] 设置写回「旧回声门控」—— 纯逻辑，可单测。
package me.rerere.rikkahub.data.datastore

import java.util.concurrent.atomic.AtomicLong

/**
 * [v229 A4 / P0-2] DataStore 旧回声门控 = 「本地权威值 + 落盘确认」。
 *
 * 真机根因（v227/v228 两轮「松手后自己滑 / 开关弹回」都落在这里）：
 *   `settingsFlow.value = next`（乐观写）之后，`dataStore.data` 仍会把**更早那笔写**的旧快照
 *   解码后推给同一个 `settingsFlow`。上一笔的发射晚于这一笔的本地写到达 -> UI 刚设的值被拉回；
 *   滑块连续拖动时表现为「松手后数值自己慢慢挪」。
 *
 * 语义：
 *  - [mark]：记下本地权威值（每笔本地写调用一次）；
 *  - [accept]：上游发射 == 权威值 -> 已落盘确认，接受并清除待确认；
 *             上游发射 != 权威值 -> 判定为旧回声，丢弃；
 *             没有待确认写 -> 直接接受（冷启动 / 外部导入照常回灌）。
 *
 * 为什么不会永久卡住：写入口唯一（`PreferencesStore` 的 `update()` / `patchKey()` 是
 * DataStore 的唯一写者，已核查），所以待确认值必然会被自己那笔写的发射确认。
 */
internal class SettingsEchoGate {

    /** 最近一笔本地写的值（权威值）；null = 没有待确认的本地写。 */
    @Volatile
    private var pending: Settings? = null

    /** 被丢弃的旧回声累计计数（真机取证用）。 */
    private val droppedCounter = AtomicLong(0)

    /** 本地写入口调用：立权威值。必须在 `settingsFlow.value = next` **之前**调用。 */
    fun mark(next: Settings) {
        pending = next
    }

    /** 上游回灌判据：true = 接受（并清除待确认；若原本就无待确认则保持无），false = 丢弃旧回声。 */
    fun accept(incoming: Settings): Boolean {
        val p = pending ?: return true
        if (p == incoming) {
            pending = null
            return true
        }
        droppedCounter.incrementAndGet()
        return false
    }

    /** 当前是否还有待确认的本地写（供诊断/测试）。 */
    fun hasPending(): Boolean = pending != null

    /** 被丢弃的旧回声计数（供诊断/测试）。 */
    fun droppedCount(): Long = droppedCounter.get()
}
