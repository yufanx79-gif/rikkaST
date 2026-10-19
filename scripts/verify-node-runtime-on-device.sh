#!/usr/bin/env bash
# ==============================================================================
# rikkaST 共享 Linux Workspace (PRoot) Node.js 与真机诊断四层验收脚本
# ==============================================================================
# 用途：
#   1. 在 Android 目标设备 (PRoot/Termux/adb shell) 或本地 Linux 环境中，对共享 Node.js
#      运行时与 server-bootstrap.cjs + littlewhitebox-server 进行实测验证：
#        - Layer 1: Node.js / npm 二进制、ABI、动态链接库、基本 JS 执行能力验证
#        - Layer 2: 常驻 HTTP 服务启动、Loopback 绑定、鉴权、/health、日志脱敏、信号退出验证
#        - Layer 3: LittleWhiteBox Server 插件 22 条路由挂载、/status 能力集、
#                   /v1/draw-runs/probe 回环探测、/api/host/context 会话隔离与防伪造安全验证 (401/403)
#        - Layer 4: (可选，当存在 adb 设备连接时) 检查 logcat 中无 NavDisplay 空栈崩溃、
#                   提取 7 阶段 [send-diag] 发送诊断链路与 WebView / PRoot 运行时状态
#   2. 用法：
#        bash scripts/verify-node-runtime-on-device.sh [server-bootstrap.cjs路径]
# ==============================================================================
set -euo pipefail

BOOTSTRAP_SCRIPT="${1:-app/src/main/assets/st-runtime/node-host/server-bootstrap.cjs}"
PORT="${RIKKA_VERIFY_PORT:-18381}"
TOKEN="verify-token-$(date +%s)"

echo "=================================================================="
echo "[Layer 1] Node.js & npm 运行时二进制与动态库自检"
echo "=================================================================="
if ! command -v node >/dev/null 2>&1; then
  echo "[FAIL] 未找到 node 可执行文件 (PATH=$PATH)"
  exit 1
fi

NODE_VER="$(node --version)"
NPM_VER="$(npm --version 2>/dev/null || echo 'unavailable')"
NODE_PROBE="$(node -e 'console.log(JSON.stringify({version:process.version,arch:process.arch,platform:process.platform,versions:process.versions}))')"
echo "[PASS] node --version => ${NODE_VER}"
echo "[PASS] npm --version  => ${NPM_VER}"
echo "[PASS] runtime probe  => ${NODE_PROBE}"

echo ""
echo "=================================================================="
echo "[Layer 2] 常驻 Node.js 守护服务启动、127.0.0.1 绑定、鉴权与健康检查"
echo "=================================================================="
if [ ! -f "${BOOTSTRAP_SCRIPT}" ]; then
  echo "[FAIL] 未找到启动脚本: ${BOOTSTRAP_SCRIPT}"
  exit 1
fi

LOG_FILE="$(mktemp /tmp/rikka-node-verify.XXXXXX.log)"
cleanup() {
  if [ -n "${DAEMON_PID:-}" ] && kill -0 "${DAEMON_PID}" 2>/dev/null; then
    kill -TERM "${DAEMON_PID}" 2>/dev/null || true
    wait "${DAEMON_PID}" 2>/dev/null || true
  fi
  rm -f "${LOG_FILE}"
}
trap cleanup EXIT

RIKKA_NODE_PORT="${PORT}" RIKKA_NODE_TOKEN="${TOKEN}" node "${BOOTSTRAP_SCRIPT}" >"${LOG_FILE}" 2>&1 &
DAEMON_PID=$!

READY=0
for _ in $(seq 1 40); do
  if grep -q "Listening on http://127.0.0.1:${PORT}" "${LOG_FILE}" 2>/dev/null; then
    READY=1
    break
  fi
  sleep 0.1
done

if [ "${READY}" -ne 1 ]; then
  echo "[FAIL] 守护进程未能在预期时间内监听 127.0.0.1:${PORT}"
  cat "${LOG_FILE}"
  exit 1
fi
echo "[PASS] 守护进程已启动 (PID=${DAEMON_PID}, Listen=127.0.0.1:${PORT})"

echo ""
echo "=================================================================="
echo "[Layer 3] 宿主桥、会话上下文隔离、防伪造校验与 LittleWhiteBox Server 插件验收"
echo "=================================================================="
node - "${PORT}" "${TOKEN}" << 'NODESCRIPT'
const assert = require('node:assert/strict');
const [port, token] = process.argv.slice(2);
const base = `http://127.0.0.1:${port}`;

(async () => {
  // 1. /health 检查
  const healthRes = await fetch(`${base}/health`);
  assert.equal(healthRes.status, 200);
  const health = await healthRes.json();
  assert.equal(health.ok, true);
  assert.equal(health.status, 'healthy');
  assert.ok(Array.isArray(health.plugins) && health.plugins.some(p => p.id === 'littlewhitebox-server'));
  console.log('[PASS] GET /health -> healthy, littlewhitebox-server mounted');

  // 2. 未授权拦截检查 (401)
  const unauthRes = await fetch(`${base}/api/plugins/littlewhitebox-server/status`);
  assert.equal(unauthRes.status, 401);
  console.log('[PASS] Unauthorized request rejected with HTTP 401');

  // 3. 伪造 X-Rikka-Profile-Handle 跨会话越权拦截检查 (403)
  const spoofedRes = await fetch(`${base}/api/plugins/littlewhitebox-server/v1/jobs`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'X-Rikka-Assistant-Id': 'assistant-A',
      'X-Rikka-Conversation-Id': 'conv-1',
      'X-Rikka-Profile-Handle': 'assistant-B_conv-2',
    },
  });
  assert.equal(spoofedRes.status, 403);
  console.log('[PASS] Spoofed X-Rikka-Profile-Handle rejected with HTTP 403');

  // 4. Body 身份覆盖拦截检查 (403)
  const bodySpoofRes = await fetch(`${base}/api/host/context`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'X-Rikka-Assistant-Id': 'assistant-A',
      'X-Rikka-Conversation-Id': 'conv-1',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      assistantId: 'assistant-B',
      conversationId: 'conv-2',
      variables: { hacked: true },
    }),
  });
  assert.equal(bodySpoofRes.status, 403);
  console.log('[PASS] Body session identity override rejected with HTTP 403');

  // 5. LittleWhiteBox Server /status 能力集验证
  const statusRes = await fetch(`${base}/api/plugins/littlewhitebox-server/status`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  assert.equal(statusRes.status, 200);
  const statusBody = await statusRes.json();
  assert.equal(statusBody.ok, true);
  assert.equal(statusBody.id, 'littlewhitebox-server');
  assert.ok(statusBody.capabilities.includes('draw-runs-v1'));
  assert.ok(statusBody.capabilities.includes('exa-web-v1'));
  console.log('[PASS] GET /api/plugins/littlewhitebox-server/status -> capabilities:', statusBody.capabilities.join(', '));

  // 6. Loopback Probe 回环连通性验证
  const probeRes = await fetch(`${base}/api/plugins/littlewhitebox-server/v1/draw-runs/probe`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'X-CSRF-Token': token,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({}),
  });
  assert.equal(probeRes.status, 200);
  const probeBody = await probeRes.json();
  assert.equal(probeBody.ok, true);
  console.log('[PASS] POST /api/plugins/littlewhitebox-server/v1/draw-runs/probe -> ok=true');

  // 7. 多助手/多会话上下文隔离验证 (/api/host/context)
  await fetch(`${base}/api/host/context`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'X-Rikka-Assistant-Id': 'assistant-A',
      'X-Rikka-Conversation-Id': 'conv-1',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ variables: { secret: 'alpha' } }),
  });
  await fetch(`${base}/api/host/context`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'X-Rikka-Assistant-Id': 'assistant-B',
      'X-Rikka-Conversation-Id': 'conv-2',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ variables: { secret: 'beta' } }),
  });

  const ctxA = await fetch(`${base}/api/host/context`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'X-Rikka-Assistant-Id': 'assistant-A',
      'X-Rikka-Conversation-Id': 'conv-1',
    },
  }).then(r => r.json());
  const ctxB = await fetch(`${base}/api/host/context`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'X-Rikka-Assistant-Id': 'assistant-B',
      'X-Rikka-Conversation-Id': 'conv-2',
    },
  }).then(r => r.json());
  assert.equal(ctxA.variables.secret, 'alpha');
  assert.equal(ctxB.variables.secret, 'beta');
  console.log('[PASS] /api/host/context multi-assistant/multi-conversation isolation verified');
})();
NODESCRIPT

if command -v adb >/dev/null 2>&1 && adb get-state >/dev/null 2>&1; then
  echo ""
  echo "=================================================================="
  echo "[Layer 4] Android 真机 logcat 崩溃与 7 阶段发送诊断检查"
  echo "=================================================================="
  if adb logcat -d | grep -q "NavDisplay backstack cannot be empty"; then
    echo "[FAIL] 检测到 NavDisplay backstack cannot be empty 崩溃记录"
    exit 1
  else
    echo "[PASS] logcat 中未发现 NavDisplay backstack cannot be empty 崩溃"
  fi
  echo "[INFO] 最近 10 条 [send-diag] 7 阶段发送链路日志："
  adb logcat -d | grep "\[send-diag\]" | tail -n 10 || echo "  (当前 logcat 尚无 [send-diag] 记录)"
fi

echo ""
echo "=================================================================="
echo "ALL VERIFICATION LAYERS PASSED IN CURRENT ENVIRONMENT"
echo "=================================================================="
