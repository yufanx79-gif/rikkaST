<div align="center">

<img src="docs/icon.png" alt="rikkaST Icon" width="128" height="128" />

# rikkaST

**Android 向けネイティブ AI チャットクライアント · SillyTavern 1.19.0 互換**

*独立パッケージ共存 · キャラカード／ワールドブック／プリセットの公式準拠インポート · サードパーティ拡張＆デュアルエンジン常駐サーバー · 長会話向け HTML パネルプール*

<p>
  <a href="README.md">简体中文</a> ·
  <a href="README_EN.md">English</a> ·
  <b>日本語</b>
</p>

<p>
  <a href="https://github.com/yufanx79-gif/rikkaST/releases"><img src="https://img.shields.io/github/v/release/yufanx79-gif/rikkaST?style=flat-square&label=Release&color=111111" alt="Release" /></a>
  <a href="https://github.com/yufanx79-gif/rikkaST/stargazers"><img src="https://img.shields.io/github/stars/yufanx79-gif/rikkaST?style=flat-square&color=f59e0b" alt="Stars" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-AGPL--3.0-2563eb?style=flat-square" alt="License" /></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?style=flat-square" alt="Android" />
  <img src="https://img.shields.io/badge/ABI-arm64--v8a-555555?style=flat-square" alt="ABI" />
</p>

<p>
  <a href="https://github.com/yufanx79-gif/rikkaST/releases"><b>最新 APK をダウンロード</b></a> ·
  <a href="https://github.com/yufanx79-gif/rikkaST/issues">不具合報告</a> ·
  <a href="THIRD-PARTY-NOTICES.md">サードパーティ表記</a> ·
  <a href="docs/PRIVACY-NETWORK.md">プライバシーと通信について</a>
</p>

</div>

---

**rikkaST** は [RikkaHub](https://github.com/rikkahub/rikkahub) をベースに Kotlin + Jetpack Compose でネイティブ実装された独立オープンソースフォークです。

- **使いやすい Android AI クライアントを探している方へ**：API キーを設定すればすぐ会話できます。マルチプロバイダー、ストリーミング、音声対話、Web 検索、プロアクティブ記憶、ナレッジベース、MCP / Skills、内蔵 Python 3.12、PRoot Linux ワークスペースをそのまま利用でき、SillyTavern 機能は普段使いの邪魔をしません。
- **スマホで SillyTavern をネイティブに遊びたい方へ**：キャラクターカード、ワールドブック（Lorebook）、プリセット／SPreset、正規表現スクリプト、マクロ、スラッシュコマンド、HTML ステータス画面、サードパーティ拡張をアプリ内でネイティブ動作させます。Termux でブラウザ版を開く手間やバックグラウンド終了に悩まされる必要はありません。

> [!TIP]
> 独立したパッケージ名 `me.rerere.rikkahub.st` を採用しているため、公式 RikkaHub と同じ端末に共存でき、データを上書きしません。RikkaHub のバックアップ（SSH リモートワークスペースを除く）や SillyTavern のバックアップ ZIP の直接インポートにも対応しています。

<!-- ここに実機スクリーンショットを 3〜4 枚追加するのがおすすめです（チャット＆HTMLパネル／キャラカード＆ワールドブック／拡張管理／テーマ画面） -->

---

## ✨ 主な特徴

| 分野 | 実装内容 |
| :--- | :--- |
| 🍺 **SillyTavern 1.19.0 互換** | 上流 SillyTavern 1.19.0 の公開モジュール 205 件に準拠。カード・ワールドブック・プリセット・正規表現・マクロ・スラッシュコマンド・Quick Replies を一貫して処理 |
| 🧩 **サードパーティ拡張ホスト** | ローカル ZIP / Git URL から拡張を導入可能。`/scripts/extensions/third-party/` を同一オリジン HTTPS で配信し、MVU や主要フロントエンドライブラリをオフライン同梱 |
| ⚙️ **デュアルエンジン常駐サービス** | `127.0.0.1:18181` で設定不要の内蔵 JVM サーバーが即起動。PRoot Node.js v20.18.1 を導入すると完全なバックエンドデーモンへシームレスに移行 |
| 🖼️ **長会話向け HTML パネルプール** | 5 種類の自動分類、優先度付き LRU WebView プール、過去ログのスナップショット凍結＆タップ復帰、ネストスクロール制御、クラッシュ自動再構築を実装 |
| 🤖 **マルチモデル・マルチプロトコル** | OpenAI（Chat Completions / Responses API）、Anthropic Claude、Google Gemini（Vertex AI / Interactions API）、xAI Grok、ChatGPT ログイン、複数 API キーローテーションに対応 |
| 🧠 **記憶とナレッジベース** | ファイルベースの記憶管理、プロアクティブ記憶リマインダー（カスタムプロンプト対応）、定期統合、ローカル文書解析（PDF / DOCX / XLSX） |
| 🛠️ **エージェント＆ローカル環境** | MCP・Skills 対応、内蔵 Python 3.12（Chaquopy）、PRoot Linux ワークスペース＆PTY ターミナル、JS サンドボックス、19 種以上の Web 検索エンジン |
| 🔊 **音声・グループチャット・表現力** | 複数キャラのグループチャット発話制御、立ち絵・表情（Expressions）、オーディオブリッジ、音声モード（各種 ASR / TTS）、画像・動画生成 |
| 🖥️ **マルチデバイス＆同期** | NSD 自動検出対応の LAN Web UI、WebDAV / S3（AWS SigV4）クラウド同期、Chatbox / CherryStudio 設定インポート |
| 🛡️ **プライバシーと品質保証** | Firebase / Crashlytics を除去し、SPreset の `LogUpload` 外部送信も明示的に遮断。**175 個の単体テストファイル**で回帰を検証 |

---

## 🚀 クイックスタート

1. [Releases](https://github.com/yufanx79-gif/rikkaST/releases) から最新の `arm64-v8a` APK をダウンロードしてインストールします（Android 8.0 / API 26 以上）。
2. **設定 → プロバイダー** でモデル API とデフォルトモデルを設定すると、すぐチャットを始められます。
3. 既存データの移行：
   - **キャラカード**：アシスタント画面から `.png` / `.json` / `.charx` / `.byaf` をワンタップでインポート（Android ファイルマネージャーの MIME 誤判定を防ぐ拡張子優先判定）。
   - **SillyTavern 一括移行**：SillyTavern のデータディレクトリまたはバックアップ ZIP をそのままインポート。`characters/`、`worlds/`、`OpenAI Settings/`、`QuickReplies/`、`settings.json` 内のグローバル正規表現を自動認識します。
   - **RikkaHub などの移行**：RikkaHub バックアップ（SSH リモートワークスペースを除く）の復元や、Chatbox / CherryStudio からのインポートにも対応しています。

---

## 🍺 SillyTavern 互換（1.19.0 準拠）

`st-runtime` を内蔵し、単なるシステムプロンプト結合ではなく SillyTavern 1.19.0 本来の挙動に沿ったパイプラインを備えています。

| アセット／機能 | ソースコードで実装済みの対応内容 |
| :--- | :--- |
| **キャラクターカード** | `.png` / `.json` / `.charx` / `.byaf` のインポート・編集に対応。キャラ設定・内蔵ワールドブック・拡張フィールドを保持 |
| **ワールドブック（Lorebook）** | 常時／キーワード発火、再帰スキャン、Sticky / Cooldown / Delay 状態管理、デコレーター（`@@activate` / `@@dont_activate` 等）、Outlet 注入、ローカルベクトル意味検索 |
| **プリセット＆SPreset** | Chat Completion プリセットと SPreset 拡張キー（`ChatSquash` / `RegexBinding` / `MacroNest` 等）を QuickJS 安全フォールバック付きで処理。外部送信を伴う `LogUpload` は意図的にスキップ |
| **マクロ＆正規表現スクリプト** | 独立マクロエンジン（変数・乱数・日時・状態・ネスト対応）。グローバル／カード／プリセットの正規表現を重複排除し段階適用 |
| **スラッシュコマンド＆QR** | クロージャと変数スコープを備えたスラッシュコマンドパーサー＆実行器、Quick Replies のインポートと実行 |
| **コンテキスト＆演出** | Author's Note、Instruct Mode、Persona、グループチャット発話スケジューリング、ローカル TikToken 計測（`cl100k_base` / `o200k_base`）、立ち絵表情表示、オーディオブリッジ |

<details>
<summary><b>📖 技術詳細を開く：サードパーティ拡張・デュアルエンジン常駐サービス・HTML パネルプール</b></summary>

### サードパーティ拡張ホスト

- **ネイティブ資産エンドポイント**：ローカル ZIP（64 MB 上限・展開 256 MB 上限の zip ボム対策およびパストラバーサル検証付き）や Git URL から拡張を導入でき、資産インターセプター経由で `/scripts/extensions/third-party/<folder>/` を同一オリジン HTTPS で WebView に配信します。
- **オフライン同梱ライブラリ**：MVU バンドルに加え、EJS / KaTeX / Mermaid / jQuery / Lodash / Showdown / DOMPurify / Zod / YAML などをローカル配信し、不安定なモバイル回線での白紙化を防ぎます。

### デュアルエンジンローカル常駐サービス（`127.0.0.1:18181`）

サーバープラグインを必要とする拡張（`littlewhitebox-server` など）向けに 2 段階構成を採用しています：

1. **設定不要の内蔵 JVM コンパニオン（`BuiltinNodeCompanionServer`）**：追加ダウンロードなしでプロセス内起動し、`/health`、アシスタント／会話ごとの分離コンテキスト、`/api/backends/chat-completions/{status,generate}`、プラグインルートを処理します。
2. **PRoot Linux + Node.js v20.18.1 デーモン（`NodeRuntimeManager`）**：共有 Linux Rootfs と Node.js を任意インストールすると、`127.0.0.1:18181` を自動的に引き継いで `server-bootstrap.cjs` を起動し、Express 互換ルーターでサーバープラグインを動かします。

いずれも `127.0.0.1` ループバック限定で待ち受け、セッションごとのランダムトークン認証（Bearer / X-CSRF-Token）、リクエストサイズ上限、ログ内の API キー／トークン自動マスクを適用しています。

### 長会話向け HTML / JS パネルエンジン（`MessageHtmlBlock.kt`）

ステータスバーや動的カードを含む長いロールプレイでも快適にスクロールできるよう、専用ライフサイクル管理を実装しています：

- **5 クラス自動分類**：`STATIC` / `ANIMATED` / `LIGHT` / `SEND` / `BIG` を静的判定し、用途に応じた保持戦略を選択。
- **優先度付きアクティブプール**：同時稼働するパネル WebView 数を厳格に制限（標準 2〜3 個、設定上限 16 個）し、優先度ベースで LRU 破棄。
- **スナップショット凍結＆タップ復帰**：過去メッセージはスナップショットを保存して WebView を解放し、タップ時に再マウント。
- **スクロール協調＆クラッシュ耐性**：JS 側から内部スクロール領域を通知して外側リストとジェスチャーを調停。縦長パネルのソフトウェアレイヤー切替やレンダラー異常終了時の自動再構築（最大 5 回）に対応。

</details>

---

## 🤖 ネイティブ AI クライアント機能

- **マルチプロバイダー＆プロトコル**：OpenAI（Chat Completions & Responses API）、Anthropic Claude、Google Gemini（Vertex AI & Interactions API）、xAI Grok、ChatGPT アカウントログイン、複数 API キーローテーション・クォータ照会。
- **記憶とナレッジベース**：ファイルベース記憶管理、プロアクティブ記憶リマインダー（カスタムプロンプト対応）、バックグラウンド定期統合、PDF（MuPDF / PDFBox）・DOCX・XLSX のローカル解析。
- **エージェントツール＆ローカル環境**：
  - MCP（Model Context Protocol）および Skills システム。
  - **Python 3.12** 内蔵（Chaquopy：`requests`、`beautifulsoup4`、`pypdf`、`openpyxl`、`markdownify`、`tabulate` 等を同梱）。
  - **PRoot Linux ワークスペース＆PTY ターミナル**、ファイル操作、JS サンドボックス、電卓、カレンダー、クリップボード、チャート描画ツール。
  - 19 種以上の Web 検索・取得エンジン（Bing、Brave、Tavily、Exa、Perplexity、Firecrawl、Jina、SearXNG、Serper、Bocha、Metaso、Zhipu 等）。
- **音声・メディア・同期**：
  - 複数 ASR（OpenAI Realtime、DashScope、Volcengine、Step、MiMo）と TTS（ElevenLabs、Fish Audio、OpenAI、Gemini、MiniMax、Qwen、Volcengine、Groq、xAI、システム TTS 等）による音声対話。
  - 会話分岐、チェックポイント、メッセージキュー、画像・動画生成。
  - **LAN Web UI**（Ktor + NSD 自動検出）、**WebDAV** / **S3（AWS SigV4）** 同期。
- **テーマ＆多言語**：Minimal / Claude / Ocean / Sakura / Spring / Autumn / Black 等のプリセットテーマ、カスタム JSON テーマ、フロストグラス風吹き出し。UI は簡体字中国語・繁体字中国語・English・日本語・한국어・Русский・العربية に対応。

---

## 🛡️ プライバシーと品質

- **テレメトリ非搭載**：ビルド設定から Firebase / Crashlytics プラグインを除外しています。
- **安全なデフォルト設計**：SPreset の `LogUpload` を明示的にスキップし、拡張 ZIP の容量・パストラバーサル検証、ローカルサービスの `127.0.0.1` 限定バインドとログ機密マスクを実施。
- **175 個の単体テストファイル**：実キャラカード／プリセットの回帰、ワールドブックのデコレーター＆ベクトルフォールバック、マクロ／正規表現／スラッシュコマンド契約、HTML パネルプール優先度、Compose 性能ガード、DB マイグレーション等をカバー。

---

## 🛠️ ビルド方法

> [!NOTE]
> ビルドには **JDK 21**、**Android SDK**（`compileSdk 37` / `minorApiLevel 2`、`minSdk 26`）、**Python 3.12**（Chaquopy 用）が必要です。現在のツールチェーンは Gradle 9.6.0 / AGP 9.4.0 / Kotlin 2.4.21 です。

<details>
<summary><b>📦 モジュール構成と技術スタック</b></summary>

| モジュール／パス | 役割 |
| :--- | :--- |
| `:app` | メインアプリ、Jetpack Compose UI、Room / DataStore、SillyTavern 互換パイプライン（`data/st/`、`data/ai/transformers/`）、HTML パネルプール、LAN Web UI |
| `app/src/main/assets/st-runtime` | SillyTavern 1.19.0 互換 JS ランタイム、Shims、MVU バンドル、オフライン依存ライブラリ、`node-host` 起動スクリプト |
| `:ai` | マルチプロバイダー抽象化、SSE デコーダー、エージェントループ、キーローテーション、クォータ照会 |
| `:workspace` | PRoot Linux Rootfs 導入・パッチ、ネイティブ C++ PTY ターミナル、共有 Node.js デーモン管理 |
| `:search` / `:speech` / `:document` | Web 検索、ASR/TTS エンジン、PDF/Office 文書パーサー |
| `:mediagen` / `:videogen` / `:oauth` | 画像・動画生成および認証モジュール |
| `:highlight` / `:material3` / `:ui` / `:common` / `:web` | コードハイライト、Material 3 コンポーネント、共通ライブラリ、Web 静的資産 |

主な技術スタック：Kotlin · Jetpack Compose · Material 3 Expressive · Navigation 3 · Koin · Room · DataStore · OkHttp · Ktor · Coil 3 · Chaquopy · QuickJS

</details>

```bash
git clone https://github.com/yufanx79-gif/rikkaST.git
cd rikkaST
cp local.properties.example local.properties
# local.properties に sdk.dir と chaquopy.python を設定（Release 署名情報はローカルのみに記載しコミットしないでください）

# 単体テストを実行
./gradlew :app:testDebugUnitTest --console=plain

# Debug APK をビルド
./gradlew :app:assembleDebug --console=plain

# Release APK をビルド（署名設定が必要）
./gradlew :app:assembleRelease --console=plain
```

Windows 環境では `./gradlew` を `gradlew.bat` に置き換えて実行してください。

---

## 🙏 謝辞とライセンス

| プロジェクト | 関係 | ライセンス |
| :--- | :--- | :--- |
| [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) | 上流プロジェクト（ネイティブ Android マルチ LLM クライアント基盤および UI） | AGPL-3.0 |
| [SillyTavern/SillyTavern](https://github.com/SillyTavern/SillyTavern) | 互換対象（1.19.0 のコア仕様および公開モジュールに準拠） | AGPL-3.0 |
| MVU / Chaquopy / PRoot / Termux 等 | ランタイム・サンドボックス・ツールチェーン各要素（詳細は [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)） | 表記ファイル参照 |

本プロジェクトは **[GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE)** の下で公開されています。

---

## ⭐ Star History

rikkaST が役に立ったら、ぜひ Star で応援してください ⭐

<a href="https://www.star-history.com/#yufanx79-gif/rikkaST&Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=yufanx79-gif/rikkaST&type=Date&theme=dark" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=yufanx79-gif/rikkaST&type=Date" />
    <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=yufanx79-gif/rikkaST&type=Date" />
  </picture>
</a>
