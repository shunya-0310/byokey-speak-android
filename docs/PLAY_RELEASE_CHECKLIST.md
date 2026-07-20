# Google Play公開チェックリスト

更新日: 2026-07-20

## アプリ

- [x] 固有パッケージ名を`com.byokeylab.speak`にする
- [x] APIキーを暗号化保存する
- [x] APIキーをURLとログへ含めない
- [x] アプリデータのバックアップを無効にする
- [x] HTTP平文通信を禁止する
- [x] LLM接続テストを用意する
- [ ] 会話、メモ、APIキーの削除導線を揃える
- [ ] 初回に外部LLMへの送信内容を説明する
- [ ] オフライン、429、残高不足、無効モデルを分けて表示する
- [ ] 実機でGemini、OpenAI、Claudeを各1回以上確認する
- [ ] ReleaseビルドでR8を有効にする
- [ ] リリース署名鍵を作成し、安全な場所へ保管する
- [ ] Android App Bundleを生成する

## Google Play

- [ ] Play Consoleでアプリを作成する
- [ ] Google Play Billingで買い切り商品または有料アプリ価格を設定する
- [ ] 内部テストを実施する
- [ ] クローズドテスト要件をアカウント種別に応じて確認する
- [ ] Data safetyを公開時コードに合わせて申告する
- [ ] コンテンツレーティングを回答する
- [ ] 広告なしを申告する
- [ ] 対象年齢を確認する
- [ ] プライバシーポリシーURLを登録する
- [ ] サポートURLと連絡先を登録する

2026年8月31日以降、新規アプリと更新はAndroid 16、API level 36以上が必要になる。
現在の`targetSdk 35`は2026年7月20日時点では提出可能だが、公開が期限以降になる場合は36へ更新する。

## ストア素材

- [ ] アプリアイコン 512 x 512
- [ ] Feature graphic 1024 x 500
- [ ] スマートフォンのスクリーンショット
- [ ] 短い説明
- [ ] 詳細説明
- [ ] BYOK、API料金、対応プロバイダーを明記する
- [ ] 「月額なし」と「LLM利用料は別」を隣接して表示する

## サイト

- [ ] `https://byokey-lab.com/` 親サイト
- [ ] `https://byokey-lab.com/apps/speak` 製品ページ
- [ ] `https://byokey-lab.com/privacy` プライバシーポリシー
- [ ] `https://byokey-lab.com/terms` 利用規約
- [ ] `https://byokey-lab.com/support` サポートとFAQ
- [ ] APIキー作成手順
- [ ] データ削除手順

## 公開直前の一次情報

- Google Play Target API policy: https://support.google.com/googleplay/android-developer/answer/11926878
- Google Play Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Android Keystore: https://developer.android.com/privacy-and-security/keystore
- Android Auto Backup: https://developer.android.com/identity/data/autobackup
- Gemini API: https://ai.google.dev/api
- OpenAI Responses API: https://developers.openai.com/api/reference/resources/responses/methods/create
- Anthropic Messages API: https://platform.claude.com/docs/en/api/messages
