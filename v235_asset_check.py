import os
import zipfile

APK = r"app\build\outputs\apk\release\app-universal-release.apk"
ASSETS = r"app\src\main\assets"

# 资产逐字节：v234 清单 + v235 新增（KaTeX 离线资产 / math-render / 改动过的运行时文件）
rel_files = [
    "st-runtime/st-compat/tokenizers.js",
    "st-runtime/st-compat/macros.js",
    "st-runtime/vendor/data/cl100k_base.tiktoken",
    "st-runtime/vendor/data/o200k_base.tiktoken",
    # v235 新增
    "st-runtime/shims/math-render.js",
    "st-runtime/runtime.js",
    "st-runtime/index.html",
    "st-runtime/panel.html",
    "html/mark.html",
    "st-runtime/vendor/katex/katex.min.js",
    "st-runtime/vendor/katex/katex.mjs",
    "st-runtime/vendor/katex/katex.min.css",
    "st-runtime/vendor/katex/mhchem.mjs",
    "st-runtime/vendor/katex/mhchem.min.js",
    "st-runtime/vendor/katex/LICENSE",
]

# KaTeX 字体全量（60 个）
fonts_dir = os.path.join(ASSETS, "st-runtime", "vendor", "katex", "fonts")
for name in sorted(os.listdir(fonts_dir)):
    rel_files.append("st-runtime/vendor/katex/fonts/" + name)

z = zipfile.ZipFile(APK)
ok = True
missing = []
for rel in rel_files:
    apk_path = "assets/" + rel
    disk = os.path.join(ASSETS, rel.replace("/", os.sep))
    if apk_path not in z.namelist():
        missing.append(apk_path)
        ok = False
        print("MISSING", apk_path)
        continue
    a = z.read(apk_path)
    b = open(disk, "rb").read()
    if a != b:
        ok = False
        print("FAIL", apk_path, len(b), "B")
print("checked", len(rel_files), "assets; missing", len(missing))
print("ASSET BYTE-PARITY:", "PASS" if ok else "FAIL")
