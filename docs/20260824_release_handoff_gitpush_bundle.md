# 2026-08-24 BYOKey Speak Git push / AAB作成 引継ぎメモ

## 目的

別スレッドで、BYOKey Speak Androidの未コミット変更を確認し、署名済みRelease AABを作成し、必要なファイルだけGitHubへpushする。

## 対象リポジトリ

- 作業場所: `D:\ドキュメント\Codex\byokey-speak-android`
- Remote: `origin https://github.com/shunya-0310/byokey-speak-android.git`
- Branch: `main`
- パッケージ名: `com.byokeylab.speak`
- 現在のRelease version: `versionCode = 9` / `versionName = "0.2.0"`

## 現在の作業状態

2026-08-24時点で、手元変更がある。主な内容は次の通り。

- Gemini専用化に関するREADME、仕様、プライバシー文書の更新。
- Gemini TTS/音声プレビュー関連の実装追加。
- Gemini voice sampleと思われる `app/src/main/res/raw/voice_*.wav` の追加。
- `app/build.gradle.kts` はRelease署名用プロパティと `BYOKEY_AI_REPORT_ENDPOINT` を参照する。
- `byokey_debug_data_backup.tar` は未追跡だが、バックアップファイルなのでGitへ入れない。

作業開始直後に必ず確認する。

```powershell
cd 'D:\ドキュメント\Codex\byokey-speak-android'
git status --short --branch
git remote -v
Select-String -Path '.\app\build.gradle.kts' -Pattern 'versionCode|versionName|applicationId' -Context 0,0
```

## 重要な注意

- APIキー、署名鍵、パスワード、Webhook URL、審査用認証情報は出力しない。
- `.jks`、`.keystore`、`.env`、バックアップ、AAB/APK、build outputs、temporary logsはGitへ入れない。
- Release AABを作るには、署名4項目と本番AI report endpointが必要。
- 環境変数が「前スレッドのプロセス内だけ」に設定されている場合、別スレッドへ引き継がれない可能性がある。次スレッドでは値を表示せず、存在だけ確認する。

## 署名設定の存在確認

値は表示しない。`True` / `False` だけ確認する。

```powershell
cd 'D:\ドキュメント\Codex\byokey-speak-android'

$required = @(
  'BYOKEY_RELEASE_STORE_FILE',
  'BYOKEY_RELEASE_STORE_PASSWORD',
  'BYOKEY_RELEASE_KEY_ALIAS',
  'BYOKEY_RELEASE_KEY_PASSWORD',
  'BYOKEY_AI_REPORT_ENDPOINT'
)

foreach ($name in $required) {
  $value = [Environment]::GetEnvironmentVariable($name, 'Process')
  if (-not $value) { $value = [Environment]::GetEnvironmentVariable($name, 'User') }
  if (-not $value) { $value = [Environment]::GetEnvironmentVariable($name, 'Machine') }
  Write-Output "$name=$([bool]$value)"
}
```

1つでも `False` の場合は、利用者にPowerShellで再設定してもらう。秘密値をチャットへ貼らせない。

## AAB作成手順

日本語パス配下でJUnit/KSPが不安定になることがあるため、ASCII仮想ドライブ経由で実行する。`B:` が使用中なら別の空きドライブ文字に変更する。

```powershell
subst B: "D:\ドキュメント\Codex"
cd B:\byokey-speak-android

$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
& "$env:JAVA_HOME\bin\java.exe" -version
.\gradlew.bat --version

.\gradlew.bat --no-daemon testDebugUnitTest :app:bundleRelease

subst B: /D
```

生成物:

```text
D:\ドキュメント\Codex\byokey-speak-android\app\build\outputs\bundle\release\app-release.aab
```

## AAB確認手順

```powershell
cd 'D:\ドキュメント\Codex\byokey-speak-android'

Get-ChildItem -LiteralPath '.\app\build\outputs\bundle\release\app-release.aab' |
  Select-Object FullName,Length,LastWriteTime

Get-FileHash -LiteralPath '.\app\build\outputs\bundle\release\app-release.aab' -Algorithm SHA256

& 'C:\Program Files\Android\Android Studio\jbr\bin\jarsigner.exe' -verify -verbose -certs `
  '.\app\build\outputs\bundle\release\app-release.aab'

Select-String -Path '.\app\build\intermediates\merged_manifests\release\processReleaseManifest\AndroidManifest.xml' `
  -Pattern 'versionCode|versionName'
```

確認観点:

- Unit Testが全件成功している。
- `:app:bundleRelease` が `BUILD SUCCESSFUL` で終わる。
- `jarsigner` で署名済みとして検証できる。
- manifest上の `versionCode` が `9`、`versionName` が `0.2.0`。
- AABのSHA-256、サイズ、作成日時を最終報告に残す。

## Git push手順

push前に差分と空白エラーを確認する。

```powershell
cd 'D:\ドキュメント\Codex\byokey-speak-android'

git status --short --branch
git diff --check
git diff --stat
```

ステージ対象の基本方針:

- `README.md`
- `app/build.gradle.kts`
- `app/src/main/java/com/byokeylab/speak/**/*.kt`
- `app/src/main/res/raw/voice_*.wav`
- `docs/*.md`

除外:

- `byokey_debug_data_backup.tar`
- `app/build/`
- `build/`
- `.gradle/`
- `.idea/`
- `.jks` / `.keystore`
- AAB/APK

PowerShell例:

```powershell
cd 'D:\ドキュメント\Codex\byokey-speak-android'

git add README.md
git add app/build.gradle.kts
git add app/src/main/java/com/byokeylab/speak
git add app/src/main/res/raw/voice_*.wav
git add docs/*.md

git status --short
git diff --cached --stat

git commit -m "Update BYOKey Speak Gemini voice release"
git push origin main
```

push後確認:

```powershell
git status --short --branch
git log --oneline -1
```

`main...origin/main` が同期し、working treeに意図しない未コミット差分が残っていないことを確認する。`byokey_debug_data_backup.tar` は未追跡のままでもよいが、必要なら後でローカル整理する。

## 最終報告に含める内容

- AABのフルパス。
- AABのサイズ、SHA-256、作成日時。
- `testDebugUnitTest` の結果。
- 署名検証結果。
- `versionCode` / `versionName`。
- commit hashとpush先。
- Gitに入れなかったファイルがあれば、その理由。

## 未検証・リスク

- このメモ作成時点では、実際の再ビルドとpushはまだ実行していない。
- 環境変数が別スレッドで読めるかは未確認。読めない場合は、利用者のPowerShell側で再設定が必要。
- Play Console投入前には、実機でAPIキー設定、短い会話、Gemini TTS、Quick Assist、バックアップ/復元、AI返信報告を確認する。
