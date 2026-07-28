# Release署名とAAB作成手順

更新日: 2026-07-27

このファイルにはパスワード、APIキー、署名鍵の実体を書かない。`.jks`、`.keystore`、署名用プロパティはリポジトリ外で管理する。

## 方針

- Play ConsoleではPlay App Signingを有効化する。
- 手元ではUpload keyでAABへ署名する。
- 同じパッケージ名`com.byokeylab.speak`は、公開後も同じ署名系統で更新する。
- デバッグ署名や別のUpload keyで上書きすると、既存ユーザーの更新として扱えない。

## こちらで準備済みのこと

- `app/build.gradle.kts`は、次のGradleプロパティまたは環境変数を読み取る。
  - `BYOKEY_RELEASE_STORE_FILE`
  - `BYOKEY_RELEASE_STORE_PASSWORD`
  - `BYOKEY_RELEASE_KEY_ALIAS`
  - `BYOKEY_RELEASE_KEY_PASSWORD`
- 4項目がすべて設定されている場合だけ、`release`署名設定を有効化する。
- `.gitignore`で`*.jks`と`*.keystore`を除外している。
- `targetSdk = 36`、R8、リソース縮小は設定済み。

## 初回だけ行う作業

1. リポジトリ外に署名鍵保管フォルダを作る。

   例:

   ```powershell
   New-Item -ItemType Directory -Force "$env:USERPROFILE\Documents\BYOKey-release-secrets"
   ```

2. Upload keyを作成する。

   ```powershell
   keytool -genkeypair `
     -v `
     -keystore "$env:USERPROFILE\Documents\BYOKey-release-secrets\byokey-speak-upload.jks" `
     -storetype JKS `
     -keyalg RSA `
     -keysize 4096 `
     -validity 10000 `
     -alias byokey-speak-upload
   ```

3. 表示されたパスワード、alias、保管場所を、パスワードマネージャーなどリポジトリ外へ保存する。

4. Play Consoleでアプリ作成後、Play App Signingを有効化し、初回AABをアップロードする。

## 毎回のReleaseビルド

Windowsの日本語パス配下で`testDebugUnitTest`を直接実行すると、JUnitワーカーがテストクラスを解決できず`ClassNotFoundException`になることがある。Java 21でも発生するため、テストとReleaseビルドはASCIIの仮想ドライブ経由で実行する。

PowerShell例:

```powershell
subst B: "D:\ドキュメント\Codex"
cd B:\byokey-speak-android

$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:BYOKEY_RELEASE_STORE_FILE = "$env:USERPROFILE\Documents\BYOKey-release-secrets\byokey-speak-upload.jks"
$env:BYOKEY_RELEASE_KEY_ALIAS = "byokey-speak-upload"
$env:BYOKEY_RELEASE_STORE_PASSWORD = "<password-managerから入力>"
$env:BYOKEY_RELEASE_KEY_PASSWORD = "<password-managerから入力>"
$env:BYOKEY_AI_REPORT_ENDPOINT = "https://<deployed-worker-domain>/v1/ai-reports"

.\gradlew.bat testDebugUnitTest :app:bundleRelease
```

`B:`が使用中の場合は、空いているドライブ文字へ変更する。仮想ドライブを解除する場合は次を実行する。

```powershell
subst B: /D
```

生成物:

```text
app/build/outputs/bundle/release/app-release.aab
```

## 提出前チェック

- `versionCode`が前回提出より大きい。
- `BYOKEY_AI_REPORT_ENDPOINT`が本番Worker URLになっている。
- `app-release.aab`をPlay Consoleの内部テストへアップロードできる。
- Release候補で、APIキー設定、短い会話、Quick Assist、AI返信報告、バックアップ／復元を実機確認する。
- Play ConsoleのApp accessに審査専用APIキーと手順を登録する。

## 紛失・漏えい時

- Upload keyを紛失または漏えいした場合は、Play Consoleの手順に従ってUpload key resetを申請する。
- App signing key自体はPlay App Signing側が保持するため、Upload keyとは別物である。
- 審査用APIキーが漏れた場合は、プロバイダー側で直ちに無効化し、Play ConsoleのApp accessも更新する。
