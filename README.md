# BYOKey Speak for Android

BYOKey Speakは、自分のLLM APIキーを使う、買い切り型の語学会話コーチです。
運営サーバーやユーザー登録を前提とせず、会話履歴、復習メモ、設定は端末内に保存します。

## 現在の機能

- 初回起動時のOnboarding(BYOK方式、API料金、外部送信の説明)
- Gemini、OpenAI、ClaudeからLLMプロバイダーを選択
- APIキーをAndroid Keystoreで保護した鍵によりAES-GCM暗号化して保存
- APIキーとAPI呼び出しモデルIDの接続テスト(失敗理由を7分類で表示)
- CEFR A1からC2までの会話レベル設定
- `Coach Personalities & Skills`の自由記述
- 英語と日本語が混ざった会話
- Coach / Chats / Review / Progress / Settings の5タブ構成
- LINE風の複数チャット(New Chat、ピン留め、名前変更、削除、自動タイトル)
- CoachとChatsの両方に常駐するQuick Assist(文脈考慮、候補1〜3個)
- Quick Assist候補を既存入力へ追記し、入力内容を上書きしない
- Android標準の音声入力と読み上げ
- 会話履歴、学習メモ、進捗のRoomによる端末内保存
- 復習状態(未復習/復習済み)つきの学習メモ
- ストリーク、累計学習日、ターン数、Quick Assist利用数などの進捗表示
- APIキー、会話、メモ、進捗、全ローカルデータの個別削除
- 毎朝のトピック通知

## 対応プロバイダー

| Provider | Default model | API |
| --- | --- | --- |
| Gemini | `gemini-3.1-flash-lite` | Generate Content API |
| OpenAI | `gpt-5.4-mini` | Responses API |
| Claude | `claude-haiku-4-5-20251001` | Messages API |

設定画面には各プロバイダーの代表的なモデル候補(推奨マーク・特徴つき)を表示し、タップで選択、アイコンでモデルIDをコピーできます。

アプリではAPI呼び出しに使うモデルIDを指定できます。ただし、各プロバイダー側の課金設定、利用可能モデル、有効化状態はアプリから変更できません。APIキー、モデルID、課金設定は各プロバイダーの画面でユーザー自身が確認します。
LLM利用料金はアプリ代金に含まれません。

> [!IMPORTANT]
> 接続実装と、各プロバイダーがモバイルアプリでのAPIキー利用を認めるかは別問題です。
> OpenAIはAPIキーをモバイルアプリなどのクライアント環境へ配置しないよう公式に案内しています。
> OpenAI対応は、利用条件と安全面を再確認するまで公開対象として確定しません。

## セキュリティとプライバシー

- APIキーの暗号鍵はAndroid Keystoreから取り出せません。
- APIキー本体はAES-GCMで暗号化して端末内へ保存します。
- APIキーをURL、ログ、ソースコードへ含めません。
- アプリデータのクラウドバックアップを無効にしています。
- 会話内容は選択したLLMプロバイダーへ直接送信されます。
- BYOKey Labが運営するサーバーへAPIキーや会話内容を送信しません。

詳細は[プライバシーデータマップ](docs/PRIVACY_DATA_MAP.md)を参照してください。

## ビルド

必要環境はAndroid Studio、JDK 17、Android SDK 35です。

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest :app:assembleDebug
```

APKは`app/build/outputs/apk/debug/app-debug.apk`へ生成されます。

## 公開準備

- パッケージ名: `com.byokeylab.speak`
- 表示名: `BYOKey Speak`
- Playストア候補名: `BYOKey Speak: English Coach`
- 公開方式: 有料アプリ買い切り
- 価格: 500円
- 公式サイト候補: `https://byokey-lab.com/speak/english/`
- 問い合わせ先: `byokey-lab@gmail.com`

公開前の残作業は[Play公開チェックリスト](docs/PLAY_RELEASE_CHECKLIST.md)を参照してください。
開発者へ渡す上位仕様は[要件定義書](docs/REQUIREMENTS.md)、機能範囲と優先順位は[プロダクト仕様](docs/PRODUCT_SPEC.md)にまとめています。
