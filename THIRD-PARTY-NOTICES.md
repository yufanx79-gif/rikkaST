# Third-Party Notices / 第三方组件声明

本产品（rikkaST）包含下列第三方组件。各组件版权归其各自所有者，并依其许可条款分发。
This product includes the third-party components listed below. Each is distributed under the terms
of its respective license; all rights remain with the respective copyright holders.

> 本项目整体以 **AGPL-3.0** 发布（见 [LICENSE](LICENSE)）。
> 若你分发本项目的二进制，请一并提供本文件。

---

## 1. 与本项目同许可（AGPL-3.0）的组件

### SillyTavern
- Copyright (C) SillyTavern contributors
- License: **GNU Affero General Public License v3.0**
- Source: https://github.com/SillyTavern/SillyTavern
- Files: `app/src/main/assets/st-runtime/vendor/st/events.js`, `.../vendor/st/eventemitter.js`
- Note: 仅作为**事件契约**参考实现使用；本项目对 SillyTavern 的兼容层（`st-compat/**`）为自行实现。

### MuPDF
- Copyright (C) 2004-2025 Artifex Software, Inc.
- License: **GNU Affero General Public License v3.0**（Artifex 双授权，本项目使用 AGPL 分支）
- Source: https://github.com/ArtifexSoftware/mupdf
- Files: `document/src/main/jniLibs/arm64-v8a/libmupdf_java.so`, `.../x86_64/libmupdf_java.so`
- Note: 本产品**未修改** MuPDF 源码，仅通过其官方 JNI 绑定调用以解析 PDF。
  若需 MuPDF 的 Corresponding Source，请见上述官方仓库（版本对应其官方 release）。
- **Artifex 与 MuPDF 名称/商标归 Artifex Software, Inc. 所有，本项目与 Artifex 无隶属或背书关系。**

### 上游 RikkaHub
- Copyright (C) RikkaHub contributors
- License: **GNU Affero General Public License v3.0**
- Source: https://github.com/rikkahub/rikkahub
- Note: 本项目是其定制分支；逐文件差异见 [DIVERGENCE.md](DIVERGENCE.md)。

---

## 2. SQLite FTS5 中文分词器

### simple（wangfenjin/simple）
- Copyright (c) wangfenjin and contributors
- License: **MIT**
- Source: https://github.com/wangfenjin/simple
- Files: `app/src/main/jniLibs/{arm64-v8a,x86_64}/libsimple.so`
- 词典数据：`app/src/main/assets/simple_dict/**`（jieba 词典，随上述项目分发）

### SQLite
- Public Domain（SQLite 官方声明）
- Source: https://sqlite.org/

---

## 3. 数学渲染

### KaTeX 0.16.45（含 mhchem 扩展与 60 个字体文件）
- Copyright (C) 2013-2024 Khan Academy and other contributors
- License: **MIT**（全文见 `app/src/main/assets/st-runtime/vendor/katex/LICENSE`）
- Source: https://github.com/KaTeX/KaTeX
- Files: `app/src/main/assets/st-runtime/vendor/katex/**`

---

### Mermaid
- Copyright (c) 2014-2024 Knut Sveidqvist and contributors
- License: **MIT**
- Source: https://github.com/mermaid-js/mermaid
- Files: `app/src/main/assets/html/mermaid.min.js`
- License text: `app/src/main/assets/licenses/Mermaid-LICENSE.txt`

## 4. 前端运行时库（`app/src/main/assets/st-runtime/vendor/lib/**`）

| 组件 | 版权 | 许可 |
|---|---|---|
| jQuery 3.5.1 | OpenJS Foundation and other contributors | MIT |
| lodash | OpenJS Foundation and other contributors | MIT |
| DOMPurify | Cure53 and other contributors | Apache-2.0 OR MPL-2.0 |
| EJS | Matthew Eernisse and contributors | Apache-2.0 |
| highlight.js | highlight.js contributors | BSD-3-Clause |
| toastr | Hans Fjällemark / John Papa | MIT |
| Select2 | Kevin Brown, Igor Vaynberg and contributors | MIT |
| Bowser | Dustin Diaz and contributors | MIT |
| Vue 3 / vue-router | Evan You and contributors | MIT |
| yaml (eemeli/yaml) | Eemeli Aro and contributors | ISC |
| zod | Colin McDonnell and contributors | MIT |

> 各库的完整版权行与许可全文，见其官方仓库；本文件仅作汇总声明。

---

## 5. npm 包（`app/src/main/assets/st-runtime/vendor/npm/**`）

以下包被以 ESM 形式 vendored 进运行时（用于卡片脚本 / MVU / 扩展生态）：

| 包 | 许可 |
|---|---|
| `zod` | MIT |
| `openai` | Apache-2.0 |
| `@anthropic-ai/sdk` | MIT |
| `@google/genai` | Apache-2.0 |
| `mathjs` / `complex.js` / `decimal.js` / `fraction.js` / `escape-latex` / `typed-function` / `javascript-natural-sort` / `seedrandom` | Apache-2.0（mathjs 系） / MIT |
| `typebox` | MIT |
| `jsonrepair` | ISC |
| `klona` | MIT |
| `json5` | MIT |
| `p-retry` / `retry` | MIT |
| `partial-json` | MIT |
| `standardwebhooks` | MIT |
| `tiny-emitter` | MIT |
| `fast-sha256` | Unlicense |
| `compare-versions` | MIT |
| `@babel/runtime` | MIT |
| `@intlify/*` | MIT |
| `@stablelib/base64` | MIT |
| `@earendil-works/pi-ai` | MIT（见其官方仓库） |

> 上述包的完整版权行与许可全文见各自 npm 页面 / 官方仓库。

---

## 6. 分词与编码数据

### tiktoken 编码表
- Copyright (C) OpenAI
- License: **MIT**
- Source: https://github.com/openai/tiktoken
- Files: `app/src/main/assets/st-runtime/vendor/data/cl100k_base.tiktoken`, `o200k_base.tiktoken`

---

## 7. MVU 变量框架（vendored）

### MVU Standalone（shim）
- Copyright (C) 2024 MVU Extension Team
- License: **MIT**
- Source: https://github.com/uuuiiiiooo/mvu-sillytavern-extension
- Files: `app/src/main/assets/st-runtime/shims/**`

### MagicalAstrogy/MagVarUpdate（MVU bundle）
- Copyright (c) 2025 MagicalAstrogy & StageDog
- License: **MIT**
- Source: https://github.com/MagicalAstrogy/MagVarUpdate
- Files: `app/src/main/assets/st-runtime/mvu/bundle.js`
- License text: `app/src/main/assets/st-runtime/mvu/bundle.js.LICENSE.txt`（包含 bundle 内置依赖的许可证信息）

---

## 8. Gradle / 构建期依赖

- `gradle/wrapper/gradle-wrapper.jar` — Gradle, Inc.，Apache-2.0
- `gradle/vineflower.jar` — Vineflower（FernFlower 后继），Apache-2.0
- `material3/material-color-utilities`（git submodule）— Copyright 2021 Google LLC，Apache-2.0

其余构建期依赖（AndroidX、Kotlin、Compose、Coil、Room、Koin 等）均为 Apache-2.0，
其声明见各自 Maven artifact 的 `META-INF/LICENSE`。

---

## 9. 商标声明

- **SillyTavern** 是其各自所有者的商标。本项目**兼容**其数据格式与扩展契约，
  但**不隶属于、也不受其背书**。
- **MuPDF** / **Artifex** 是 Artifex Software, Inc. 的商标。
- **OpenAI** / **Anthropic** / **Google** / **DeepSeek** 等为各自公司商标。
- 本项目与上述任何公司均无隶属或背书关系。

---

## 10. App 内嵌 Python 运行时依赖

Chaquopy 在构建时从 PyPI 安装以下通用依赖（用于 `execute_python` 工具的常规数据处理；不包含任何命理/占星引擎）：

| 包 | 许可证 |
|---|---|
| `requests` | Apache-2.0 |
| `beautifulsoup4` | MIT |
| `markdown` | BSD-3-Clause |
| `pypdf` | BSD-3-Clause |
| `openpyxl` | MIT |
| `markdownify` | MIT |
| `tabulate` | MIT |
| `python-dateutil` | Apache-2.0 / BSD-3-Clause |
| `pytz` | MIT |
| `setuptools` | MIT |

> 这些包不随源码仓库 vendored；构建时由 Chaquopy/pip 解析并保留各自元数据。

---

## 11. 其它数据资产

| 资产 | 说明 |
|---|---|
| `app/src/main/assets/simple_dict/**` | jieba 中文分词词典（随 `wangfenjin/simple` 分发） |
| `app/src/main/assets/st-runtime/vendor/data/*.tiktoken` | OpenAI tiktoken 编码表（MIT） |
| `app/src/main/assets/st-runtime/vendor/katex/fonts/**` | KaTeX 字体（MIT） |
| `app/src/main/assets/icons/**` | 各 AI 提供商图标（商标归各自所有者，仅作指示性使用） |

---

## 12. 字体

| 字体 | 许可证 | 文件 |
|---|---|---|
| Google Sans Flex | SIL Open Font License 1.1 | `app/src/main/res/font/google_sans_flex.ttf` |
| JetBrains Mono | SIL Open Font License 1.1 | `app/src/main/res/font/jetbrains_mono.ttf` |

> OFL-1.1 全文见 `app/src/main/assets/licenses/OFL-1.1.txt`。字体名称与版权归各自作者所有。

---

## 13. Web UI 依赖

`web-ui/` 是一个独立的 pnpm 项目；其 npm 依赖由 `package.json` / `pnpm-lock.yaml` 声明，构建时安装，**不随本仓库 vendored**。每个依赖保留其自身许可证。

*本文件由项目维护者汇总。若发现遗漏或错误，欢迎提 Issue 更正。*
