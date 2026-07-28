# Google Play審査・データ移行 実装方針

更新日: 2026-07-27

## 結論

BYOK方式そのものを禁止するGoogle Playポリシーは確認できない。Google Playには、ユーザー自身のAPIキー、Android Keystore、端末内会話保存を明記したBYOKアプリの公開実績が複数ある。

一方、GoogleとOpenAIは、本番のモバイルアプリへAPIキーを置く方式をセキュリティ上推奨していない。これはGoogle Playの公開禁止とは別の論点である。BYOKey Speakでは、ユーザー自身が入力したキーをKeystore由来の鍵で暗号化し、キーの取り出し・共有・予算上限について繰り返し説明する。ただし、端末が侵害された場合まで安全と断定しない。

初回公開前のP0は次の5項目とする。

1. Settingsに「バックアップ／復元」を追加する。
2. AI返信ごとのアプリ内報告機能を追加する。
3. 外部LLM送信を明示的に同意する操作を追加する。
4. Google Play審査用の再利用可能なアクセス手順を用意する。
5. targetSdk 36、Release署名、R8、AABを整備する。

## 確認済みの事実

| 論点 | 確認結果 | BYOKey Speakへの判断 |
| --- | --- | --- |
| BYOKアプリの公開実績 | Google PlayにYourLLM、ChatticaAI、AnyLLM、UnboundChat等が公開されている | BYOK自体は直ちに却下理由とは考えにくい |
| 生成AIポリシー | AIチャットには、アプリを離れずに不適切な生成内容を報告・フラグする機能が必要 | 各AI返信に報告アイコンを付ける |
| 審査アクセス | 制限された機能を審査するため、常時有効で再利用可能なアクセス情報が必要 | 専用の審査キーと英語手順をPlay Consoleへ登録する |
| Data safety | 端末外へ送信したデータは、開発者サーバーを経由しなくても原則「収集」の検討対象 | 会話本文等のLLM送信を正直に申告する |
| プライバシーポリシー | 全アプリで、公開URLとアプリ内導線が必要。データ保持・削除も記載する | Aboutのリンクを維持し、報告・バックアップを追記する |
| target API | 2026年8月31日以降、新規アプリと更新はAPI 36以上が必要 | 初回公開前に36へ上げる |
| 新規個人アカウント | 2023年11月13日以降作成の場合、12人が14日間連続参加するクローズドテストが必要 | アカウント該当時は公開日程へ最低14日を組み込む |
| 有料設定 | 一度無料提供したアプリは同じパッケージで有料へ戻せない | 最初からPaidで作成する |

## 現行コードとの差分

| 項目 | 現状 | 方針 | 優先度 |
| --- | --- | --- | --- |
| バックアップ／復元 | `allowBackup=false`。ユーザー操作の移行機能なし | 暗号化ファイルによる手動バックアップを追加 | P0 |
| AI返信の報告 | 未実装 | 各AI返信に報告アイコンと理由選択を追加 | P0 |
| 外部送信同意 | 「続行すると同意」と表示するのみ | 明示チェックまたは同意ボタンを追加 | P0 |
| 審査用アクセス | 未整備 | 独立したGemini審査キーと英語手順を用意 | P0 |
| 生成AI安全策 | 会話プロンプトにPlay向け安全規則がない | 共通安全規則とプロバイダー安全設定を追加 | P0 |
| targetSdk | 35 | compileSdk/targetSdkを36へ更新 | P0 |
| Release最適化 | R8無効、署名設定未整備 | R8有効化、Upload key、AABを整備 | P0 |
| データ削除 | APIキー、会話、メモ、進捗、全データの導線あり | 実機で削除後の状態を確認 | P0検証 |
| プライバシー導線 | Aboutに公開URLへのリンクあり | 公開ページの内容とURLを確定 | P0 |
| 利用規約 | アプリ内リンクなし | Aboutへ利用規約を追加 | P0 |
| エラー表示 | APIキー、モデル、課金、429、通信等を分類済み | 3社実機で文言を確認 | P0検証 |
| Play内更新案内 | 未実装 | 初回公開には不要。公開後にFlexible updateを検討 | P2 |

## バックアップ／復元

### 採用方式

Settingsに独立した「バックアップ／復元」セクションを追加する。Android Auto Backupは初回版では有効にせず、ユーザーが保存先を選ぶ暗号化ファイル方式を採用する。

理由は次のとおりである。

- 機種変更、再インストール、端末故障への備えをユーザー自身が管理できる。
- BYOKey Labのサーバーへ学習データを送らずに済む。
- APIキーをバックアップ対象から確実に除外できる。
- Android Keystoreの鍵を別端末へ復元して復号不能になる事故を避けられる。

### 画面

Settingsの上位区分を次の順にする。

1. システム設定
2. レベル、コーチ設定
3. バックアップ／復元
4. データ削除
5. ヘルプ
6. About

説明文は次の内容とする。

> 機種変更や再インストールに備えて、会話履歴、Vocabulary List、分析結果、学習設定をバックアップできます。APIキーは安全のため含まれません。新しい端末ではAPIキーを再度設定してください。

操作は「バックアップファイルを作成」「バックアップから復元」の2つとする。ファイル選択にはStorage Access Frameworkを使い、広範なストレージ権限は要求しない。

### 対象データ

含める:

- ChatsとMessages
- Vocabulary List
- 学習メモ
- Daily stats
- 会話分析履歴
- CEFR、コーチスキル、モデル、テーマ、音声、通知、効果音等の非秘密設定

含めない:

- APIキー
- Android Keystoreの鍵
- Secure SharedPreferences
- デイリーニュースのキャッシュ
- 一時的な入力欄、処理中状態
- チュートリアル表示済み等の端末固有UI状態

### ファイルと復元

- 拡張子は`.byokbackup`とする。
- `formatVersion`、作成日時、アプリ版、データ本体を持つ。
- データ本体はユーザーが設定したパスフレーズから導出した鍵でAES-GCM暗号化する。
- パスフレーズは端末にも運営側にも保存しない。
- 読み込み後に形式、バージョン、必須項目、整合性をすべて検証してからDBへ書く。
- 復元はRoomの単一トランザクションで行い、途中失敗時は既存データを維持する。
- 初回版は「現在のデータを置き換える」のみとし、重複判定が難しいマージ復元は行わない。
- 復元確認画面に、対象件数、作成日時、APIキーが含まれないことを表示する。

### 受け入れ条件

- 復号後のバックアップペイロードにAPIキーが存在しない。
- 空DB、新端末相当、旧formatVersion、不正ファイル、誤パスフレーズをテストする。
- 復元失敗時に既存の会話履歴が消えない。
- 復元後にChats、Vocabulary List、Progressの件数が一致する。
- 新端末ではAPIキー再入力の案内が出る。

## AI生成コンテンツの報告

### UI

- 各AI返信の操作列に旗アイコンを追加する。
- タップ後、理由を「不適切」「危険・有害」「事実と異なる」「その他」から選ぶ。
- 送信前に、報告対象の返信本文がBYOKey Labへ送られることを明示する。
- APIキーは絶対に含めない。
- 送信完了、通信失敗、再試行をアプリ内で完結させる。

### 送信データ

- 報告対象のAI返信
- ユーザーが明示的に同意した場合のみ、直前のユーザー発話1件
- 理由と任意コメント
- プロバイダー、モデル、アプリ版
- ランダム生成した報告ID

氏名、メール、端末ID、広告ID、APIキーは送らない。保存期間は90日を上限とし、プライバシーポリシーへ目的、送信先、保持期間、削除窓口を記載する。

### 受信基盤

Google Formをブラウザーで開くだけでは「アプリを離れずに報告」の条件を満たしにくい。報告専用の軽量HTTPSエンドポイントを用意する。第一候補はCloudflare WorkerとD1である。

エンドポイント側では、本文サイズ制限、レート制限、入力検証、保存期間超過データの自動削除を行う。汎用問い合わせフォームとは分離する。

## 明示同意と安全策

### 外部送信同意

Onboarding最終画面の「同意したものとみなす」は、明示操作へ変更する。

- 「会話内容、会話履歴の一部、CEFR、コーチ設定が、選択したLLMプロバイダーへ送信されることを確認しました」のチェックを設ける。
- 未チェックでは会話開始へ進めない。
- プライバシーポリシーと利用規約を同じ画面から開ける。
- 同意バージョンと日時だけを端末内へ保存する。
- ポリシーの重要変更時は再同意を求める。

### 生成内容の安全策

- 共通システムプロンプトに、児童搾取、性的搾取、自傷促進、詐欺、違法行為、危険行為、差別・嫌がらせ、悪意あるコードへの協力を拒否する規則を追加する。
- プロバイダーが提供する安全設定を無効化しない。
- 英会話学習に必要な安全な言い換えや一般的説明へ誘導する。
- デイリーニュースは出典URLと日付を維持し、政治・選挙記事の要約で断定や投票誘導を行わない。
- Aboutまたは会話画面に「AIの回答には誤りが含まれる場合があります」を簡潔に表示する。

## 審査用アクセス

初回は専用のGemini審査キーを使う。通常運用、ニュース生成、開発用キーとプロジェクトを分ける。

- Gemini APIだけを利用可能にする。
- 低い日次上限、予算通知、異常利用監視を設定する。
- Play ConsoleのApp accessへ、英語で設定手順、キー、推奨モデル、会話開始手順を登録する。
- キーは常時有効かつ再利用可能に保つ。
- キーを交換した場合は、次の審査提出前にApp accessも更新する。
- 審査キーでAPIキー削除、短い会話、Quick Assist、会話分析まで確認できるようにする。

専用キーの漏えいリスクを許容できない場合は、次案として審査専用のレート制限付きプロキシを用意する。ローカルの固定文だけを返すデモモードは、実際の中心機能を確認できないため採用しない。

## 対応プロバイダー

初回公開では「実機で正常系と主要エラーを確認できたプロバイダーだけ」を表示する。

- Gemini、OpenAI、Claudeを各1回以上、Release相当ビルドで確認する。
- APIキー無効、モデル無効、429、課金未設定、通信断を確認する。
- 公開直前に各社のAPI利用条件とモバイルキーの注意事項を再確認する。
- 1社でも未確認なら、その社を初回公開版の選択肢から外す。初回版をGeminiのみとしても、BYOKの本質は変わらない。

## ReleaseとPlay Console

### アプリ

- compileSdk/targetSdkを36へ上げる。
- ReleaseでR8を有効化し、縮小後の起動、Room、PDF、音声、HTTP通信を確認する。
- Upload keyを作成し、`.jks`とパスワードをリポジトリ外へ保管する。
- Play App Signingを有効化する。
- `versionCode`を提出ごとに増やし、AABを生成する。
- 2026年9月30日までにパッケージ名と署名鍵の登録状態をPlay Consoleで確認する。

### Play Consoleと公開物

- 最初からPaidとして作成する。
- Data safetyは、会話本文等の外部LLM送信とAI報告データを含めて回答する。
- 対象年齢は初回版では18歳以上を基本案とし、各プロバイダー規約とIARC回答で確定する。
- プライバシーポリシー、利用規約、サポート、データ削除、返金方針を公開URLで用意する。
- ストア説明の近接した位置に「買い切り」「月額なし」「APIキー必須」「API料金別」を記載する。
- AI生成内容には誤りがあり得ること、会話内容が選択したLLMへ送信されることを記載する。
- 有料アプリの問い合わせには3営業日以内に応答する運用を作る。

## 実装順

1. バックアップ／復元と復元テスト
2. AI返信の報告UI、報告API、保持期限
3. 明示同意、安全プロンプト、利用規約リンク
4. targetSdk 36、R8、署名、AAB
5. 3社の実機検証と初回公開プロバイダー確定
6. Play Console申告、ストア素材、内部テスト
7. 該当する場合、12人・14日間のクローズドテスト

## 2026-07-27 実装状況

実装済み:

- Settingsの「バックアップ／復元」
- APIキーを除外した`.byokbackup`形式
- PBKDF2-HMAC-SHA256とAES-256-GCMによる暗号化
- 復元前の形式・参照・設定検証とRoom単一トランザクション置換
- AI返信ごとの旗アイコン、理由、任意コメント、直前発話の個別同意
- 通報用Cloudflare WorkerとD1スキーマ
- Onboardingの明示チェックと既存ユーザーへの再同意
- 会話・Quick Assistの共通安全規則
- AboutのAI回答注意事項
- `compileSdk 36.1`、`targetSdk 36`
- ReleaseのR8・リソース縮小
- Release AABの生成確認

公開前に必要な外部作業:

- Cloudflare D1作成、Workerデプロイ、`BYOKEY_AI_REPORT_ENDPOINT`設定
- 更新版プライバシーポリシーのサイトデプロイ
- 利用規約ページの作成とAboutリンク
- Upload key作成とrelease署名情報の安全な設定
- 審査専用GeminiキーのPlay Console登録
- Release候補の実機検証

専用通報エンドポイントが未設定の場合、アプリは報告内容をクリップボードへコピーして
お問い合わせフォームを開く。これは開発中のフォールバックであり、Play審査提出版では
WorkerのURLを必ず設定する。

## 失敗しやすい点と対策

- バックアップへAPIキーが混入する: 保存モデルを秘密情報と非秘密情報で明確に分け、APIキー不在を自動テストする。
- 復元途中で既存データが消える: 全検証後に単一トランザクションで置き換える。
- 報告機能が問い合わせフォームへの外部リンクだけになる: アプリ内からHTTPS送信を完了させる。
- Data safetyと実際の通信が食い違う: Release候補の通信先と送信項目を最終版データマップへ反映してから回答する。
- 審査キーが失効する: 独立プロジェクト、監視、定期疎通、App access更新を公開手順へ含める。

## 一次情報

確認日: 2026-07-27

- Google Play AI-Generated Content: https://support.google.com/googleplay/android-developer/answer/13985936
- Google Play Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Google Play User Data: https://support.google.com/googleplay/android-developer/answer/10144311
- Google Play App access: https://support.google.com/googleplay/android-developer/answer/15748846
- Google Play Target API: https://support.google.com/googleplay/android-developer/answer/11926878
- Google Play closed testing: https://support.google.com/googleplay/android-developer/answer/14151465
- Google Play pricing: https://support.google.com/googleplay/android-developer/answer/6334373
- Google Play support requirements: https://support.google.com/googleplay/android-developer/answer/113477
- Android Storage Access Framework: https://developer.android.com/training/data-storage/shared/documents-files
- Android backup security: https://developer.android.com/privacy-and-security/risks/backup-best-practices
- Gemini API key security: https://ai.google.dev/gemini-api/docs/api-key
- OpenAI API key safety: https://help.openai.com/en/articles/5112595-best-practices-for-api-key-safety

## BYOK公開例

公開例は審査通過を保証するものではない。BYOK方式がGoogle Play上で実運用されていることの確認にのみ用いる。

- YourLLM: https://play.google.com/store/apps/details?id=io.yourgptapp
- ChatticaAI: https://play.google.com/store/apps/details?id=com.aminteractivesoftware.chatticaai
- AnyLLM: https://play.google.com/store/apps/details?id=com.dotwellabs.anyllm
- UnboundChat: https://play.google.com/store/apps/details?id=com.marko.unboundchat
