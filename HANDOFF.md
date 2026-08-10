# ScienceRoomApp 引き継ぎメモ

## これは何か
『科学室アプリ_スマホ版仕様書.md』の MVP-1 実装。
学ぶ → 探索 → 素材発見 → 実験 → 成功/失敗 → 理解 → 技術解禁 のループが一周する状態。

## 構成
- Kotlin + Jetpack Compose（Material 3）、単一 Activity
- 画面遷移は `MainActivity.kt` の `AppRoot` が文字列ルートで管理（Navigation ライブラリ不使用）
- 保存は SharedPreferences に `PlayerState` を JSON で 1 レコード
- コンテンツは `app/src/main/assets/data/*.json`。コードに直接埋め込まない（仕様書の原則1）
- 画像は `app/src/main/res/drawable/` に 58 枚。`imageId` の文字列から `getIdentifier` で解決

## データ
| ファイル | 中身 |
|---|---|
| elements.json | 元素 16 |
| materials.json | 素材 26（自然 16 + 加工 10） |
| locations.json | 地域 5（森・川・山・海岸・洞窟） |
| technologies.json | 技術 12（火起こし → レンズ） |
| reactions.json | 反応 13 |
| temperature.json | 温度挙動 8 物質 |

## エンジン（`engine/Engine.kt`）
- `ExplorationEngine` … 探索回数と地域IDからシードを作る決定的乱択（仕様書の原則4）
- `ExperimentEngine` … 材料一致 → 温度/時間/器具の検証 → S/A/B/C/D 判定＋失敗原因の重み付け
- `LearningEngine` … 4モードの出題、忘却リスク順の優先出題、間隔反復（10分/1日/3日/7日/30日）
- `TechnologyEngine` … 前提技術・素材・知識・反応の充足判定と解禁処理
- `RecommendEngine` … 次の行動 TOP3 をルールで生成（原則2：判定はルール、AIは説明と推薦）

## ビルド
GitHub Actions（`.github/workflows/build.yml`）で `gradle assembleDebug`。
Actions の artifact から debug APK をダウンロードする。Gradle Wrapper は同梱せず、
`gradle/actions/setup-gradle` が Gradle 8.9 を用意する。

## 次にやること（MVP-2）
- 素材の加工段階（未発見→発見→採取→加工→精製）の明示
- セレンディピティ（未定義の組み合わせから新研究候補を出す）
- 科学的思考力の項目別評価
- AI 科学助手のオンライン連携（現状はルールベースのローカル推薦のみ）
- `tech_salt` / `tech_lens` の画像差し替え（絵柄が他と揃っていない）
