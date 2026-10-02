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
Host `store/privacy-policy.html` publicly (replace YOUR_EMAIL first). Some easy options:
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
Increase `versionCode` (and `versionName`) in `app/build.gradle.kts`, run `./gradlew bundlePlayRelease`, then upload in Play Console.
