package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.components.message.MessageHtmlBlock
import me.rerere.rikkahub.ui.components.ui.PageScaffold

/**
 * 【仅 debug 构建使用】WebView 面板实验室。
 *
 * 用途：在真机上可控地复现五类面板（STATIC / ANIMATED / LIGHT / SEND / BIG），
 * 验证分类判定、快照捕获、冻结唤醒、手势锁与活跃池上限。
 *
 * 注意：这是**测试脚手架**，不进 release 构建。验证完成后应删除或移到 androidTest。
 */
@Composable
fun PanelLabPage() {
    val floors = rememberPanelLabFloors()
    val listState = rememberLazyListState()
    PageScaffold(title = "面板实验室") { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(floors) { floor ->
                Text(
                    text = "楼层 #${floor.index} · ${floor.kind.uppercase()} · ${floor.note}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                MessageHtmlBlock(
                    html = floor.html,
                    messageKey = "lab-floor-${floor.index}",
                    panelTitle = "实验室 ${floor.kind}",
                    isHistoricalFloor = floor.index < floors.lastIndex,
                )
            }
        }
    }
}

internal data class PanelLabFloor(
    val index: Int,
    val kind: String,
    val note: String,
    val html: String,
)

/**
 * 五类面板夹具。每个都刻意做成计划里描述的真实形态：
 * - STATIC：纯 CSS 卡片，无脚本无事件
 * - ANIMATED：CSS keyframes 粒子，无交互
 * - LIGHT：details 折叠 + 表单，不发消息
 * - SEND：按钮调用宿主桥发送
 * - BIG：内部滚动容器 + 多角色切换（近似角色卡大面板）
 */
internal fun rememberPanelLabFloors(): List<PanelLabFloor> {
    // 造 30 楼，让列表真正超出一屏，才能验证「滑出屏幕 -> 休眠 / 淘汰 / 快照」链路。
    // 前 5 楼用五类基础夹具，后续轮转复用，最后一楼是 BIG（模拟"最新一楼是角色卡大面板"）。
    val base = listOf(
        // 横向滚动夹具放第一位：一进来就能测，不必滚动定位。
        Triple("hscroll", "横向 5 标签（左右滑动）", FIXTURE_HSCROLL),
        Triple("static", "纯样式卡片", FIXTURE_STATIC),
        Triple("animated", "CSS 粒子动画", FIXTURE_ANIMATED),
        Triple("light", "details 折叠 + 表单", FIXTURE_LIGHT),
        Triple("send", "按钮调用发送桥", FIXTURE_SEND),
        Triple("big", "内部滚动 + 12 角色切换", FIXTURE_BIG),
    )
    val total = 30
    return (0 until total).map { index ->
        // 最后一楼固定 BIG，模拟真实场景里"最新一楼是角色卡大面板"
        val pick = if (index == total - 1) base[4] else base[index % base.size]
        val suffix = if (index >= base.size) "（第 ${index + 1} 楼）" else ""
        PanelLabFloor(index, pick.first, pick.second + suffix, pick.third)
    }
}

private val FIXTURE_STATIC = """
<style>
  .static-card { border: 1px solid #445; border-radius: 10px; padding: 14px; background: #1b1d24; }
  .static-card h3 { margin: 0 0 8px; font-size: 15px; color: #8fd; }
  .static-card p { margin: 0; font-size: 13px; line-height: 1.6; color: #cdd; }
</style>
<div class="static-card">
  <h3>STATIC 静态美化</h3>
  <p>雨声敲打着便利店的玻璃窗。她抬头看了一眼时钟，还有二十分钟打烊。这是一段纯展示内容，没有脚本、没有事件、没有动画。</p>
</div>
""".trimIndent()

private val FIXTURE_ANIMATED = """
<style>
  @keyframes floatUp { 0% { opacity: 0.2; transform: translateY(0); } 100% { opacity: 1; transform: translateY(-8px); } }
  .particle-stage { position: relative; height: 90px; background: #10131a; border-radius: 10px; overflow: hidden; }
  .particle { position: absolute; animation: floatUp 2s infinite alternate; color: #8cf; font-size: 18px; }
</style>
<div class="particle-stage">
  <span class="particle" style="left:10%;top:60%">✦</span>
  <span class="particle" style="left:30%;top:70%; animation-delay:.3s">✧</span>
  <span class="particle" style="left:50%;top:55%; animation-delay:.6s">✦</span>
  <span class="particle" style="left:70%;top:75%; animation-delay:.9s">✧</span>
</div>
""".trimIndent()

private val FIXTURE_LIGHT = """
<style>
  .light-panel { padding: 12px; background: #1c1f27; border-radius: 10px; color: #dde; font-size: 13px; }
  .light-panel summary { cursor: pointer; color: #9df; }
  .light-panel input { margin-top: 6px; padding: 6px; width: 90%; background: #262a34; color: #eee; border: 1px solid #3a4152; border-radius: 6px; }
</style>
<div class="light-panel">
  <details><summary>思考过程（点击展开）</summary><div>她在犹豫要不要说出口。</div></details>
  <details><summary>好感度状态栏（点击展开）</summary><div>好感度 +3 · 当前 42</div></details>
  <input type="text" placeholder="输入旁注（测试表单值恢复）" value="">
</div>
""".trimIndent()

private val FIXTURE_SEND = """
<style>
  .send-panel { padding: 12px; background: #1d2028; border-radius: 10px; display: flex; gap: 8px; }
  .send-panel button { flex: 1; padding: 9px; background: #2a3550; color: #cfe; border: 1px solid #3a4a6a; border-radius: 8px; }
</style>
<div class="send-panel">
  <button onclick="triggerSlash('/send 继续剧情')">继续剧情</button>
  <button onclick="triggerSlash('/send 换个方向')">换个方向</button>
  <button onclick="triggerSlash('/send 暂停')">暂停</button>
</div>
""".trimIndent()

/**
 * 横向滚动专用夹具（用户诉求：横向 5 个标签，屏幕只能显示 3 个，
 * 左右滑动必须能完整翻到后面 2 个，且松手有惯性）。
 */
private val FIXTURE_HSCROLL = """
<style>
  .tabstrip { display: flex; flex-wrap: nowrap; overflow-x: auto; overflow-y: hidden; gap: 8px;
              padding: 10px; background: #161922; border-radius: 12px; width: 100%;
              -webkit-overflow-scrolling: touch; touch-action: pan-x; }
  .tabstrip .tab { flex: 0 0 auto; width: 30vw; min-width: 30vw; max-width: 30vw; padding: 14px 10px;
                   border-radius: 10px; box-sizing: border-box;
                   background: linear-gradient(135deg,#22304a,#2c3b58); color: #cfe; font-size: 13px;
                   text-align: center; }
  .tabstrip::-webkit-scrollbar { display: none; }
  .hint { color: #8a9; font-size: 11px; padding: 4px 12px 10px; }
</style>
<div class="tabstrip">
  <div class="tab">标签 1<br>力量</div>
  <div class="tab">标签 2<br>敏捷</div>
  <div class="tab">标签 3<br>体质</div>
  <div class="tab">标签 4<br>智力</div>
  <div class="tab">标签 5<br>感知</div>
</div>
<div class="hint" id="hs-diag">测量中…</div>
<script>
  (function(){
    var touchDown = 0, touchMove = 0, lastDx = 0, lastDy = 0;
    function report(){
      var el = document.querySelector('.tabstrip');
      if (!el) return;
      var d = document.getElementById('hs-diag');
      if (d) d.textContent = 'sw=' + el.scrollWidth + ' cw=' + el.clientWidth +
        ' sl=' + Math.round(el.scrollLeft) +
        ' | touchStart=' + touchDown + ' touchMove=' + touchMove +
        ' dx=' + Math.round(lastDx) + ' dy=' + Math.round(lastDy);
    }
    function bindProbe(){
      var el = document.querySelector('.tabstrip');
      if (!el || el.__probeBound) return;
      el.__probeBound = true;
      var sx = 0, sy = 0;
      el.addEventListener('touchstart', function(e){
        touchDown++; if (e.touches[0]) { sx = e.touches[0].clientX; sy = e.touches[0].clientY; }
        report();
      }, {passive:true});
      el.addEventListener('touchmove', function(e){
        touchMove++; if (e.touches[0]) { lastDx = e.touches[0].clientX - sx; lastDy = e.touches[0].clientY - sy; }
        report();
      }, {passive:true});
      el.addEventListener('scroll', report, {passive:true});
    }
    setTimeout(bindProbe, 100);
    setTimeout(bindProbe, 600);
    setTimeout(bindProbe, 1500);
    setTimeout(report, 150);
    setInterval(function(){ bindProbe(); report(); }, 500);
  })();
</script>
""".trimIndent()

private val FIXTURE_BIG = """
<style>
  .big-panel { background: #161922; border-radius: 12px; color: #dde; font-size: 13px; }
  .roster { display: flex; max-height: 300px; overflow-y: auto; flex-direction: column; gap: 6px; padding: 10px; }
  .roster .card { display: flex; align-items: center; gap: 10px; padding: 8px; background: #1e2330; border-radius: 8px; }
  .roster .avatar { width: 38px; height: 38px; border-radius: 50%; background: linear-gradient(135deg,#4a7,#37a); }
  .roster .name { font-weight: 600; }
  .roster .hp { font-size: 11px; color: #8a9; }
  .big-actions { display: flex; gap: 8px; padding: 0 10px 10px; }
  .big-actions button { flex: 1; padding: 8px; background: #26304a; color: #cfe; border: 1px solid #36425f; border-radius: 8px; }
</style>
<div class="big-panel">
  <div class="roster">
    <div class="card"><div class="avatar"></div><div><div class="name">早川雫</div><div class="hp">HP 82 / 100</div></div></div>
    <div class="card"><div class="avatar"></div><div><div class="name">白石澪</div><div class="hp">HP 64 / 100</div></div></div>
    <div class="card"><div class="avatar"></div><div><div class="name">柊木春</div><div class="hp">HP 91 / 100</div></div></div>
    <div class="card"><div class="avatar"></div><div><div class="name">御影千秋</div><div class="hp">HP 55 / 100</div></div></div>
    <div class="card"><div class="avatar"></div><div><div class="name">相川灯</div><div class="hp">HP 78 / 100</div></div></div>
    <div class="card"><div class="avatar"></div><div><div class="name">藤枝凛</div><div class="hp">HP 88 / 100</div></div></div>
    <div class="card"><div class="avatar"></div><div><div class="name">桐生葵</div><div class="hp">HP 70 / 100</div></div></div>
    <div class="card"><div class="avatar"></div><div><div class="name">小暮灯里</div><div class="hp">HP 93 / 100</div></div></div>
    <div class="card"><div class="avatar"></div><div><div class="name">星野結</div><div class="hp">HP 61 / 100</div></div></div>
    <div class="card"><div class="avatar"></div><div><div class="name">水无月透</div><div class="hp">HP 85 / 100</div></div></div>
    <div class="card"><div class="avatar"></div><div><div class="name">叶山玲</div><div class="hp">HP 74 / 100</div></div></div>
    <div class="card"><div class="avatar"></div><div><div class="name">东云しずく</div><div class="hp">HP 96 / 100</div></div></div>
  </div>
  <div class="big-actions">
    <button onclick="document.querySelector('.roster').scrollTop += 120">下翻</button>
    <button onclick="document.querySelector('.roster').scrollTop = 0">回到顶部</button>
  </div>
</div>
""".trimIndent()
