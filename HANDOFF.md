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

## MVP-2 で入れたもの（v2.0）
- `SerendipityEngine` … 定義外の組み合わせから確率 35%（シードは実験回数＋素材の並び）で研究候補を開放。
  `PlayerState.researchLeads` に記録し、実験室とプロフィールに一覧表示
- `HintEngine` … 実験成功率とクイズ正答率からヒント Lv を自動調整。手動固定も可（`autoHint`）
- `SkillEngine` … 仕様書28節の科学的思考力7項目（知識量/観察力/仮説形成/実験設計/結果解釈/原因分析/応用力）
- `RoadmapEngine` … 前提技術の深さから段を算出し、技術画面を段ごとの分岐表示に変更。
  各ノードに派生先（`└→`）を表示
- 図鑑に「化合物」タブ … 素材を単体 / 化合物 / 混合物に分類（`materials.json` の `kind`）。
  素材詳細に区分・採取か加工か・その素材を使う実験を追加

## 次にやること（MVP-3）
- 大規模な技術ツリーと多地域探索
- AI 科学助手のオンライン連携（現状はルールベースのローカル推薦のみ）
- クラウド同期とイベント
- `tech_salt` / `tech_lens` の画像差し替え（絵柄が他と揃っていない）
- `elem_au` の刻印「FINE GOLD」がイメージクイズの答えを示してしまう
