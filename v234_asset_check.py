import zipfile
z = zipfile.ZipFile(r"app\build\outputs\apk\release\app-universal-release.apk")
# 资产逐字节：新文件全部校验
pairs = [
    ("assets/st-runtime/st-compat/tokenizers.js", r"app\src\main\assets\st-runtime\st-compat\tokenizers.js"),
    ("assets/st-runtime/st-compat/macros.js", r"app\src\main\assets\st-runtime\st-compat\macros.js"),
    ("assets/st-runtime/vendor/data/cl100k_base.tiktoken", r"app\src\main\assets\st-runtime\vendor\data\cl100k_base.tiktoken"),
    ("assets/st-runtime/vendor/data/o200k_base.tiktoken", r"app\src\main\assets\st-runtime\vendor\data\o200k_base.tiktoken"),
]
ok = True
for apk_path, disk in pairs:
    a = z.read(apk_path)
    b = open(disk, "rb").read()
    status = "PASS" if a == b else "FAIL"
    if a != b: ok = False
    print(status, apk_path, len(b), "B")
print("ASSET BYTE-PARITY:", "PASS" if ok else "FAIL")
