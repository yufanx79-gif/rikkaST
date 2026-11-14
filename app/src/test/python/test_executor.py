"""
executor.py 的回归测试（纯 Python，不经 Chaquopy）。

运行方式：
    cd app/src/main/python && python -m pytest ../../test/python/test_executor.py -v
或直接：
    python app/src/test/python/test_executor.py

## 修的是什么

旧实现：
```python
try:
    result = eval(code)      # 只能求值单个表达式
except SyntaxError:
    exec(code)               # 能执行语句，但不返回任何值
    result = None            # ← 结果永远被丢弃
```

三个硬伤：
1. 最后一条表达式的值永远丢失（`mu = 5` 换行 `mu * 2` 期望 10，实际 None）
2. exec 在函数体内执行，变量进局部作用域 → 跨调用 import 的变量消失（NameError 来源）
3. eval 的 SyntaxError 被吞掉，真实错误不可见

修法：ast.parse 分段 + 模块级持久命名空间 + 互斥锁。
"""
import json
import os
import sys
import tempfile
import threading

# 让测试能 import 到 app/src/main/python/executor.py
_HERE = os.path.dirname(os.path.abspath(__file__))
_PY_DIR = os.path.abspath(os.path.join(_HERE, "..", "..", "main", "python"))
sys.path.insert(0, _PY_DIR)

import executor  # noqa: E402


def run(code, workdir):
    return json.loads(executor.execute(code, workdir))


def test_multiline_last_expression_returns_value(tmp_path):
    """多行赋值 + 最后一条表达式 → 必须返回表达式的值。"""
    r = run("mu = 5\nsigma = 2\nmu + sigma", str(tmp_path))
    assert r.get("result") == "7", r


def test_import_then_use_in_same_call(tmp_path):
    r = run("import statistics\nstatistics.mean([1,2,3])", str(tmp_path))
    assert r.get("result") == "2", r


def test_function_definition_and_call(tmp_path):
    r = run("def f(x):\n    return x * 2\nf(21)", str(tmp_path))
    assert r.get("result") == "42", r


def test_print_is_captured(tmp_path):
    r = run("print('hello')\n7 * 6", str(tmp_path))
    assert r.get("stdout") == "hello\n", r
    assert r.get("result") == "42", r


def test_pure_expression(tmp_path):
    assert run("2 + 2", str(tmp_path)).get("result") == "4"


def test_statement_only_returns_ok(tmp_path):
    r = run("x = 1", str(tmp_path))
    assert r.get("result") == "ok", r


def test_empty_code_does_not_crash(tmp_path):
    r = run("", str(tmp_path))
    assert r.get("result") == "ok", r


def test_variables_persist_across_calls(tmp_path):
    """跨调用变量必须保留 —— 旧实现会 NameError。"""
    run("import math\n_persisted = math.pi", str(tmp_path))
    r = run("_persisted * 2", str(tmp_path))
    assert r.get("result") is not None, r
    assert abs(float(r["result"]) - 6.283185307179586) < 1e-9, r


def test_syntax_error_reported(tmp_path):
    r = run("def broken(:", str(tmp_path))
    assert "error" in r, r
    assert "invalid syntax" in r["error"], r


def test_runtime_error_reported(tmp_path):
    r = run("1 / 0", str(tmp_path))
    assert "error" in r, r
    assert "division" in r["error"].lower() or "ZeroDivision" in r["error"], r


def test_concurrent_calls_do_not_pollute_each_other(tmp_path):
    """并发调用时 sys.stdout / os.chdir 是进程级全局状态，必须被锁隔离。

    修复前实测：后者的输出丢到真实 stdout，前者拿到 TextIOWrapper 后崩溃。
    """
    results = {}

    def worker(name, delay):
        code = (
            f"import time\n"
            f"time.sleep({delay})\n"
            f"print('out-{name}')\n"
            f"'{name}'"
        )
        results[name] = run(code, str(tmp_path))

    threads = [
        threading.Thread(target=worker, args=(n, d))
        for n, d in [("A", 0.05), ("B", 0.02), ("C", 0.03)]
    ]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    for name in ("A", "B", "C"):
        r = results[name]
        assert r.get("stdout") == f"out-{name}\n", f"{name}: {r}"
        assert r.get("result") == name, f"{name}: {r}"


if __name__ == "__main__":
    import pathlib
    import shutil

    # executor 会 os.chdir 到 workdir，Windows 下进程 cwd 仍在目录里会导致无法删除。
    # 每个用例结束后先切回原目录再清理。
    original_cwd = os.getcwd()
    failures = 0
    for name, fn in sorted(globals().items()):
        if not name.startswith("test_") or not callable(fn):
            continue
        td = tempfile.mkdtemp()
        try:
            fn(pathlib.Path(td))
            print(f"  PASS  {name}")
        except AssertionError as e:
            failures += 1
            print(f"  FAIL  {name}: {e}")
        except Exception as e:  # noqa: BLE001
            failures += 1
            print(f"  ERROR {name}: {type(e).__name__}: {e}")
        finally:
            os.chdir(original_cwd)
            shutil.rmtree(td, ignore_errors=True)
    print()
    print("全部通过" if failures == 0 else f"{failures} 个失败")
    sys.exit(1 if failures else 0)
