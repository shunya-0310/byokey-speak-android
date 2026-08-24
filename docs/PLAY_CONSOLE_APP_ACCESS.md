# Play Console App access登録文

更新日: 2026-07-27

APIキーはこのファイルやGitへ記載しない。審査専用キーはPlay ConsoleのApp access欄へ
直接入力する。

## English instructions

```text
BYOKey Speak is a paid BYOK English conversation app. Its core conversation
features require a user-provided LLM API key.

Review access:
1. Launch the app and complete the onboarding screens.
2. On the final onboarding screen, check the consent box for sending conversation
   data to Google Gemini.
3. Open Settings > System settings.
4. Enter the reusable review API key provided below.
5. Select the recommended Gemini model shown in the app.
6. Tap "Test connection", then tap "Save settings".
7. Open Chats > New Chat and send a short English or Japanese message.
8. The sparkle icon opens Quick Assist. The globe icon enables a current-topic
   conversation. The flag icon on each AI reply opens the in-app content report flow.
9. Settings > Backup / Restore can export an encrypted backup. API keys are never
    included in backup files.

Review API key:
[PASTE THE DEDICATED REVIEW KEY IN PLAY CONSOLE ONLY]

The key is intended only for Google Play review, remains valid for repeated review,
and has a restricted budget and monitoring.
```

## 審査キー運用

- 開発、ニュース生成、個人利用とは別のGoogle Cloudプロジェクトで発行する。
- Gemini API以外の権限を付けない。
- 低い予算通知と日次監視を設定する。
- 審査期間中は常時有効にする。
- キーを交換したら、次回提出前にPlay ConsoleのApp accessも更新する。
- アプリ、ソース、README、Issue、スクリーンショットへキーを含めない。
