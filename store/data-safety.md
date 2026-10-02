# Play Console answers (Play edition)

## Data safety (App content → Data safety)
- **Does your app collect or share any of the required user data types?** No.
  - All records (names, phone numbers, amounts) are stored only on the device.
  - The app has no server and no analytics or advertising SDKs, and makes no network requests.
  - Sharing (WhatsApp, files, backups) happens only when the user chooses to share, through Android's share sheet. Under Play's definitions, user-initiated transfers are not "collection".
- **Is all data encrypted in transit?** Not applicable (no data leaves the device by the app).
- **Can users request data deletion?** Yes: Settings → Backup & restore → Delete all, or uninstall the app.

## Permissions used (Play edition)
- `POST_NOTIFICATIONS`: reminders to share updated totals.
- `USE_BIOMETRIC`: optional app lock.
- **No SMS permissions.** The `full` edition (with automatic SMS capture) is distributed outside Play.

## Other declarations
- **Ads:** No ads.
- **App access:** All features work without an account. No login needed for review.
- **Target audience:** 18+ (treasurers and committee members).
- **Content rating questionnaire:** Utility/productivity app, no violence, sexual content, gambling or user-generated public content. Expected rating: Everyone / PEGI 3.
- **Financial features declaration:** The app does not provide financial services, loans, payments or money transfers. It only keeps records the user enters.
- **Government app:** No. **News app:** No. **Health app:** No.
