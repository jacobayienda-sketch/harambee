# Publishing Harambee Tracker on Google Play

Only the **play** edition goes on Google Play. It has no SMS permissions, so it can be approved without a special exception. Keep giving the **full** edition (automatic M-Pesa capture) to treasurers as an APK.

## 1. Developer account (one time)
1. Sign up at https://play.google.com/console. There is a one-time US$25 fee.
2. Complete identity verification (ID, and a phone and email that Google verifies).
3. New **personal** accounts must run a **closed test with at least 12 testers for 14 days** before production. Organisation accounts (which need a D-U-N-S number) skip this.

## 2. Create your upload key (one time, on your own computer)
```
keytool -genkeypair -v -keystore harambee-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```
Then create `keystore.properties` in the project folder (it is in .gitignore and never committed):
```
storeFile=harambee-upload.jks
storePassword=YOUR_PASSWORD
keyAlias=upload
keyPassword=YOUR_PASSWORD
```
**Back up the .jks file and passwords somewhere safe.** With Play App Signing, Google can reset a lost upload key, but it takes time.

## 3. Build the bundle
```
./gradlew bundlePlayRelease
```
Output: `app/build/outputs/bundle/playRelease/app-play-release.aab`

## 4. Package name
The app id is `com.harambee.tracker`. If Play says it's taken, change `applicationId` in `app/build.gradle.kts` (e.g. `com.yourname.harambeetracker`) **before the first upload**. It can never change afterwards.

## 5. Privacy policy URL
Host `store/privacy-policy.html` publicly. Some easy options:
- **GitHub Pages:** works if the repository is public, or with a paid plan.
- **Google Sites:** paste the text into a new site.
- Any website you already have.

## 6. In Play Console
1. **Create app** → name "Harambee Tracker", language English, App, Free.
2. **App content:** fill in privacy policy URL, ads (no), app access, content rating, target audience, data safety and financial features, using `store/data-safety.md`.
3. **Main store listing:** copy from `store/listing.md`, and upload the icon, feature graphic and screenshots.
4. **Testing → Closed testing:** create a track, upload the .aab, add your 12+ testers' emails, and send them the opt-in link.
5. After 14 days: **Production → Create release**, upload the same or a newer .aab, and send it for review. Reviews usually take a few days.

## 7. Each update
Increase `versionCode` (and `versionName`) in `app/build.gradle.kts`, then either:
- **By hand:** run `./gradlew bundlePlayRelease` and upload the .aab in Play Console, or
- **Automatically:** GitHub → Actions → **Play Store release** → Run workflow, then choose the track (internal / alpha / beta / production). One-time setup:
  1. Play Console → **Setup → API access** → create a service account in Google Cloud, give it **Release manager** permission for this app, and download its JSON key.
  2. GitHub repository → **Settings → Secrets and variables → Actions** → add:
     - `UPLOAD_KEYSTORE_BASE64`: run `base64 -w0 harambee-upload.jks` (macOS: `base64 -i harambee-upload.jks`) and paste the output
     - `UPLOAD_STORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`
     - `PLAY_SERVICE_ACCOUNT_JSON`: the whole JSON file's contents
  3. The very first release must still be uploaded by hand (step 6). The workflow handles every one after that.

## Store graphics (ready in `store/graphics/`)
| File | Use in Play Console |
|---|---|
| `icon-512.png` | App icon (512×512), from `store/brand/icon.svg` |
| `feature-graphic-1024x500.png` | Feature graphic |
| `screenshot-1-home.png` … `screenshot-6-contributors.png` | Phone screenshots (1233×2460) |

The screenshots are the real app (Play edition) with **fictional** sample names. To regenerate them after UI changes, run:
`./gradlew testPlayDebugUnitTest -Pscreenshots --tests '*StoreScreenshots*'`
To rebuild the feature graphic, open `store/feature-graphic.html` in a browser at 1024×500 and screenshot it.

## Logo
`store/brand/` holds the logo: three people standing together with a gold "confirmed" badge (Harambee = *pulling together*; ✓ = every contribution accounted for).
- `icon.svg` / `icon-1024.png`: app icon, square (stores add their own rounding)
- `logo-horizontal(-dark).svg/.png`: icon + "Harambee TRACKER" wordmark, for letterheads, posters and WhatsApp group pictures
- `logo-tagline(-dark).svg/.png`: the same with the Kiswahili tagline *Kila mchango unahesabiwa* ("Every contribution is counted")
- `mark-on-light`, `mark-mono`: the symbol alone, in colour or one colour, on transparent backgrounds
- Colours: green `#0B6E4F`, gold `#F5B83D`, ink `#10241C`

`make_logo.py` generates the SVGs **and** the app's launcher, themed and notification icons from one design: `python3 store/brand/make_logo.py`.
