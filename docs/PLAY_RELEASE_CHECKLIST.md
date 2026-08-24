# Google Play公開チェックリスト

更新日: 2026-08-23

詳細な設計判断は[Google Play審査・データ移行 実装方針](PLAY_REVIEW_AND_DATA_PORTABILITY_PLAN.md)を参照する。
Release署名の具体的な手順は[Release署名とAAB作成手順](RELEASE_SIGNING_RUNBOOK.md)を参照する。

## アプリ

- [x] 固有パッケージ名を`com.byokeylab.speak`にする
- [x] APIキーを暗号化保存する
- [x] APIキーをURLとログへ含めない
- [x] アプリデータのバックアップを無効にする
- [x] HTTP平文通信を禁止する
- [x] LLM接続テストを用意する
- [x] 会話、メモ、APIキーの削除導線を揃える
- [x] 初回に外部LLMへの送信内容を説明する
- [x] オフライン、429、残高不足、無効または利用不可のモデルIDを分けて表示する
- [x] Settingsに「バックアップ／復元」を追加する
- [x] APIキーを含まない暗号化バックアップとトランザクション復元を実装する
- [x] AI返信ごとのアプリ内報告機能を実装する
- [x] 報告専用Worker、D1スキーマ、レート制限、90日削除をローカル実装する
- [x] 報告専用Workerをデプロイし、releaseビルドへURLを設定する
- [x] 外部LLM送信を明示的に同意する操作を追加する
- [x] 生成AI向けの共通安全プロンプトを追加する
- [ ] Gemini APIの利用条件と安全設定を実通信で確認する
- [x] AboutにAI回答の注意事項を追加する
- [x] Aboutに利用規約リンクを追加する
- [ ] 実機でGemini会話、Gemini TTS、端末音声を各1回以上確認する
- [ ] GeminiのクライアントアプリでのAPIキー利用に関する注意事項とBYOK方式の整合を確認する
- [x] `compileSdk`を36.1、`targetSdk`を36へ更新する
- [x] ReleaseビルドでR8を有効にする
- [x] リリース署名鍵を作成し、安全な場所へ保管する
- [x] Android App Bundleを生成する（署名済みRelease候補）
- [x] `testDebugUnitTest`をASCII仮想ドライブ経由で実行し、全Unit Testの成功を確認する
- [ ] Release候補で起動、Room、PDF、音声、通信、復元を確認する

## Google Play

- [ ] Play Consoleでアプリを作成する
- [ ] 最初から有料アプリとして作成し、価格500円を設定する
- [ ] Payments profileと販売地域を設定する
- [ ] Play App Signingを有効化する
- [ ] 審査専用Geminiキーと英語のApp access手順を登録する
- [ ] 内部テストを実施する
- [ ] 該当アカウントでは12人が14日間連続参加するクローズドテストを実施する
- [ ] Data safetyを公開時コードに合わせて申告する
- [ ] コンテンツレーティングを回答する
- [ ] 広告なしを申告する
- [ ] Play Consoleの対象年齢を「18歳以上のみ」にし、未成年者のアクセス制限を有効化する
- [ ] プライバシーポリシーURLを登録する
- [ ] サポートURLと問い合わせフォームを登録する
- [ ] 2026年9月30日までにパッケージ名と署名鍵の登録状態を確認する
- [ ] 有料アプリの問い合わせへ3営業日以内に応答する運用を決める

2026年8月31日以降、新規アプリと更新はAndroid 16、API level 36以上が必要になる。
2026年7月27日に`compileSdk 36.1`、`targetSdk 36`へ更新済みである。

## ストア素材

- [ ] アプリアイコン 512 x 512
- [ ] Feature graphic 1024 x 500
- [ ] スマートフォンのスクリーンショット
- [ ] 短い説明
- [ ] 詳細説明
- [ ] BYOK、Gemini API料金、Gemini専用であることを明記する
- [ ] 「月額なし」と「LLM利用料は別」を隣接して表示する
- [ ] 「Gemini APIキー必須」「会話内容はGeminiへ送信」を明記する
- [ ] 「AIの回答には誤りが含まれる場合がある」を明記する

## サイト

- [ ] `https://byokey-lab.com/` 親サイト
- [x] `https://byokey-lab.com/speak/english/` 製品ページ
- [x] `https://byokey-lab.com/privacy` 更新版プライバシーポリシーをデプロイする
- [x] `https://byokey-lab.com/terms` 利用規約をデプロイする
- [x] `https://byokey-lab.com/support` サポートとFAQ
- [ ] APIキー作成手順
- [ ] モデルID、課金設定、利用可能モデルの確認手順
- [ ] データ削除手順
- [ ] バックアップ／復元と機種変更手順
- [ ] AI生成内容の報告データ、保持期間、削除窓口
- [ ] 返金方針とGoogle Playでの注文番号確認手順

## 公開直前の一次情報

- Google Play Target API policy: https://support.google.com/googleplay/android-developer/answer/11926878
- Google Play Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Google Play AI-Generated Content: https://support.google.com/googleplay/android-developer/answer/13985936
- Google Play App access: https://support.google.com/googleplay/android-developer/answer/15748846
- Google Play closed testing: https://support.google.com/googleplay/android-developer/answer/14151465
- Android Keystore: https://developer.android.com/privacy-and-security/keystore
- Android Auto Backup: https://developer.android.com/identity/data/autobackup
- Android backup security: https://developer.android.com/privacy-and-security/risks/backup-best-practices
- Android Storage Access Framework: https://developer.android.com/training/data-storage/shared/documents-files
- Gemini API: https://ai.google.dev/api
- Gemini API terms: https://ai.google.dev/gemini-api/terms?hl=ja
- Gemini speech generation: https://ai.google.dev/gemini-api/docs/speech-generation
