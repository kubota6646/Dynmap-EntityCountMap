# Dynmap-EntityCountMap

Minecraft 1.21.x（Spigot / Paper）向け Dynmap 拡張プラグインです。  
ロードされている各チャンクのエンティティ数に応じて色が変わるヒートマップレイヤーを Dynmap に追加します。

## カラーグラデーション

| エンティティ数 | 色 |
|---|---|
| 0 (空) | マーカーなし（透明） |
| 少ない | 🔵 青 |
| 中程度 | 🟢 緑 → 🟡 黄 |
| 多い (max-entities 以上) | 🔴 赤 |

`max-entities`（デフォルト 50）を境に青→赤のグラデーションで補間されます。

## 動作環境

| 項目 | 要件 |
|---|---|
| Minecraft | 1.21.x（1.21.8 含む） |
| サーバーソフト | **Spigot 1.21.x** または **Paper 1.21.x** |
| Java | 21 以上 |
| Dynmap | 3.x |

## インストール

1. [Dynmap](https://www.spigotmc.org/resources/dynmap.274/) を先にインストールする
2. 本プラグインの JAR を `plugins/` フォルダに配置する
3. サーバーを起動すると `plugins/Dynmap-EntityCountMap/config.yml` が生成される

## 設定 (`config.yml`)

```yaml
# マーカー更新間隔 (tick, 20 tick = 1 秒)
update-interval: 200

# この値以上のエンティティ数を「赤」と見なす閾値
max-entities: 50

# プレイヤーをカウントに含めるか
include-players: true

# アイテムドロップをカウントに含めるか
include-item-drops: false

# チャンク矩形の塗りつぶし不透明度 (0.0〜1.0)
fill-opacity: 0.45

# Dynmap レイヤーパネルでの表示優先度
layer-priority: 10

# 対象ワールドのホワイトリスト (空 = 全ワールド)
worlds: []
```

## コマンド

| コマンド | エイリアス | 説明 |
|---|---|---|
| `/entitycountmap reload` | `/ecm reload` | `config.yml` を再読み込みし、Dynmap レイヤーを再起動します |

### リロードの動作

1. 現在の更新タスクをキャンセルし、全マーカーと Dynmap マーカーセットを削除
2. `plugins/Dynmap-EntityCountMap/config.yml` をディスクから再読み込み
3. 新しい設定で Dynmap レイヤーを再初期化

## パーミッション

| パーミッションノード | デフォルト | 説明 |
|---|---|---|
| `entitycountmap.reload` | OP のみ | `/entitycountmap reload` の実行を許可します |

## ビルド方法

### Gradle（推奨）

```bash
# Linux / macOS
./gradlew build

# Windows
gradlew.bat build
```

生成された JAR は `build/libs/dynmap-entitycountmap-1.0.0.jar` に出力されます。

> **注意**: ビルド時に SpigotMC Nexus・Dynmap Maven リポジトリへのインターネット接続が必要です。  
> オフラインビルドの場合は、[BuildTools](https://www.spigotmc.org/wiki/buildtools/) で生成した  
> `spigot-api` JAR を `~/.m2` にインストールした上で `./gradlew build --offline` を実行してください。

### Maven

```bash
mvn package
```

生成された JAR は `target/dynmap-entitycountmap-1.0.0.jar` に出力されます。

## ライセンス

[MIT License](LICENSE)
