"""
Python executor for Rikkahub.
Executes Python code with stdout capture, matplotlib auto-save,
and result file detection.

Available built-in functions (call these from your code):
  query_knowledge_base(query, limit=10)         - Search knowledge base
  add_knowledge_entry(title, content)           - Add entry to knowledge base
  update_knowledge_entry(id, title, content)    - Update knowledge entry
  delete_knowledge_entry(id)                    - Delete knowledge entry
  list_knowledge_entries(limit=20)               - List knowledge base entries
  list_conversations(limit=10)                   - List recent conversations
  get_conversation_messages(conv_id)             - Read conversation messages
  list_assistants()                              - List all assistants & their key settings
  get_assistant_settings(assistant_id)           - Read full assistant settings
  update_assistant_setting(id, key, value)       - Change any assistant setting
  get_setting(key)                               - Read global app setting
  update_setting(key, value)                     - Change global app setting
  get_app_info()                                 - App version & paths

"""

# ── Chaquopy fix: executor replaces random.Random.__init__ with restored_init
# but doesn't inject random._traced_calls. This runs before any imports that touch random/secrets.
import random as _random
if not hasattr(_random, '_traced_calls'):
    _random._traced_calls = []

import sys
import json
import os
import ast
import threading
from io import StringIO
import traceback

# ── 跨调用持久的执行命名空间 ───────────────────────────────────────────
# 旧实现把 exec() 放在 execute() 函数体里, 赋值的变量落在函数局部作用域,
# 函数返回后即销毁。于是「先 import, 下一次调用再使用」必然 NameError。
# 这里放到模块级字典, 让变量在同一次应用进程内跨调用保留。
_NAMESPACE = {
    "__name__": "__main__",
    "__builtins__": __builtins__,
}

# executor 会改写全局 sys.stdout / sys.stderr 并 chdir, 这两者都是进程级状态。
# 即使上层已改为串行执行工具, 这里仍加锁, 防止将来任何并发调用互相污染
# （实测: 两个并发调用时, 后者的输出会丢到真实 stdout, 前者拿到 TextIOWrapper 后崩溃）。
_EXEC_LOCK = threading.RLock()

# Bridge to Android services - set from Kotlin via execute() parameter
_bridge = None


# ============================================================
# Bridge wrapper functions
# ============================================================

def query_knowledge_base(query, limit=10):
    if _bridge:
        try:
            return _bridge.queryKnowledgeBase(query, limit)
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def add_knowledge_entry(title, content, assistant_id=None):
    if _bridge:
        try:
            return _bridge.addKnowledgeEntry(title, content, assistant_id)
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def list_knowledge_entries(limit=20):
    if _bridge:
        try:
            return _bridge.listKnowledgeEntries(limit)
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def list_conversations(limit=10):
    if _bridge:
        try:
            return _bridge.listConversations(limit)
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def get_conversation_messages(conversation_id, limit=50):
    if _bridge:
        try:
            return _bridge.getConversationMessages(conversation_id, limit)
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def get_app_info():
    if _bridge:
        try:
            return _bridge.getAppInfo()
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def list_assistants():
    if _bridge:
        try:
            return _bridge.listAssistants()
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def get_assistant_settings(assistant_id):
    if _bridge:
        try:
            return _bridge.getAssistantSettings(assistant_id)
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def update_assistant_setting(assistant_id, key, value):
    if _bridge:
        try:
            return _bridge.updateAssistantSetting(assistant_id, key, value)
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def update_knowledge_entry(entry_id, title=None, content=None):
    if _bridge:
        try:
            return _bridge.updateKnowledgeEntry(entry_id, title, content)
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def delete_knowledge_entry(entry_id):
    if _bridge:
        try:
            return _bridge.deleteKnowledgeEntry(entry_id)
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def get_setting(key):
    if _bridge:
        try:
            return _bridge.getSetting(key)
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def update_setting(key, value):
    if _bridge:
        try:
            return _bridge.updateSetting(key, value)
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"


# ============================================================
# Main executor
# ============================================================

def execute(code: str, workdir: str, bridge=None) -> str:
    """Execute Python code, return JSON with results."""
    with _EXEC_LOCK:
        return _execute_locked(code, workdir, bridge)


def _execute_locked(code: str, workdir: str, bridge=None) -> str:
    """[execute] 的实际实现; 调用方必须已持有 _EXEC_LOCK。"""
    global _bridge
    _bridge = bridge
    old_stdout = sys.stdout
    old_stderr = sys.stderr
    sys.stdout = StringIO()
    sys.stderr = StringIO()

    # List files before execution
    before = set()
    try:
        before = set(os.listdir(workdir))
    except Exception:
        pass

    result = None
    error = None
    output_files = []

    try:
        os.chdir(workdir)
    except Exception:
        pass

    # Pre-configure matplotlib
    try:
        import matplotlib
        matplotlib.use('Agg')
        import matplotlib.pyplot as plt
        plt.rcParams['figure.facecolor'] = 'white'
        plt.rcParams['axes.facecolor'] = 'white'
        plt.rcParams['savefig.facecolor'] = 'white'
    except ImportError:
        pass

    try:
        # ── 用 AST 分段执行 ────────────────────────────────────────────
        # 旧实现是「先 eval(整段), 失败再 exec(整段)」。这有两个硬伤:
        #   1) eval 只接受单个表达式, 一旦是语句就抛 SyntaxError;
        #   2) exec 不返回任何值, 所以最后一条表达式的值永远丢失
        #      （`mu = 5` 换行 `mu * 2` 期望 10, 实际返回 None）。
        # 改为: 除最后一条语句外全部 exec; 最后一条若是表达式, 用 eval 取值。
        tree = ast.parse(code, filename="<execute_python>", mode="exec")
        body = tree.body
        if body:
            for node in body[:-1]:
                exec(compile(ast.Module(body=[node], type_ignores=[]), "<execute_python>", "exec"), _NAMESPACE)
            last = body[-1]
            if isinstance(last, ast.Expr):
                expr = ast.Expression(body=last.value)
                ast.fix_missing_locations(expr)
                result = eval(compile(expr, "<execute_python>", "eval"), _NAMESPACE)
            else:
                exec(compile(ast.Module(body=[last], type_ignores=[]), "<execute_python>", "exec"), _NAMESPACE)
                result = None

        # Auto-save matplotlib figures
        try:
            import matplotlib.pyplot as plt
            for i, fig_num in enumerate(plt.get_fignums()):
                fig = plt.figure(fig_num)
                fname = "figure_{}.png".format(i+1) if plt.get_fignums() else "figure.png"
                fig.savefig(os.path.join(workdir, fname), dpi=150,
                           bbox_inches='tight', facecolor='white', edgecolor='none')
                output_files.append(fname)
                plt.close(fig)
        except ImportError:
            pass

    except Exception as e:
        error = "{}\n{}".format(e, traceback.format_exc())

    finally:
        stdout = sys.stdout.getvalue()
        stderr = sys.stderr.getvalue()
        sys.stdout = old_stdout
        sys.stderr = old_stderr

        # Find new files
        try:
            after = set(os.listdir(workdir))
            for f in after - before:
                if not f.startswith('.'):
                    fpath = os.path.join(workdir, f)
                    if os.path.isfile(fpath) and os.path.getsize(fpath) > 0:
                        output_files.append(f)
        except Exception:
            pass

    resp = {}
    if error:
        resp["error"] = error
    if stdout:
        resp["stdout"] = stdout
    if stderr:
        resp["stderr"] = stderr
    if result is not None and not error:
        resp["result"] = str(result)
    if output_files:
        resp["files"] = list(set(output_files))
    if not resp:
        resp["result"] = "ok"
    return json.dumps(resp)
