# Harambee Tracker

Android app for Kenyan Harambee treasurers who collect contributions on their **personal M-Pesa number**: family fundraisers, church and welfare groups, burial committees.

It reads M-Pesa "You have received…" confirmations, keeps an exact running total with no duplicates, and writes the WhatsApp contribution-list update for you.

## How it works

1. **Money arrives.** When an SMS from the `MPESA` sender ID arrives, the app reads the code, amount, name, phone and time. Messages from any other sender are ignored, so fake "Confirmed" texts from ordinary numbers are never counted.
2. **No duplicates.** Every M-Pesa transaction code is stored once (unique in the database). Seeing the same message again, from the inbox scan, a forward or a paste, does nothing.
3. **You decide.** A notification pops up: *"KES 1,000 from Sandra Sirma. Add to <Harambee>?"*. It has **Add ✅** and **Not a contribution** buttons, plus a hint: *new contributor*, *already gave KES 1,000*, or *matches "CO Peter chesos" on the list*. Tap it to review in the app (change the Harambee or the name shown on the list).
4. **The list updates.** If the payer has a pledge (a line without ✅) the line is ticked. Otherwise a new numbered line is added.
5. **Share.** A second notification shows the new total. Tap it for the WhatsApp update, then **Share to WhatsApp** to pick the group.

Switch off **Ask before adding** on the home screen to add every payment to the active Harambee automatically. Only do this if the number receives nothing else.

## The WhatsApp update

It matches the format groups already use:

```
<appeal message>

Send your contribution to *Eliud Murkomen 0723 934 660*

     *Contribution List*
1. Eliud Murkomen 1,000 ✅
2. Hillary Chebii 1,000 ✅
3. John cheruyot 1,000
4. 

*Total received: KES 2,000*
Pledges pending: KES 1,000

Thanks for your generous contribution 🙏
```

You can turn on or off: the totals and target, pledges, the blank next number, an "Updated" timestamp, and sorting by amount.

## Other features

- **Several Harambees** at once. Each has its own appeal text, send-to number, target, start date (earlier M-Pesa money is ignored) and open/closed state.
- **Import an existing WhatsApp list.** Paste the post going round the group. Names with ✅ count as paid and names without are pledges. Later M-Pesa payments tick the matching names instead of adding them twice.
- **Scan the inbox** for M-Pesa payments received since the Harambee started. They go to Review.
- **Paste or share M-Pesa messages** into the app, e.g. ones forwarded from another committee member's phone.
- **Manual entries:** cash, bank, M-Pesa to another number (with the code, to block duplicates), and pledges.
- **Rename** a contributor for the list ("CO Peter chesos"), for one payment or for every payment from that number.
- **Reversals** from Safaricom are taken out of the total automatically.
- **One line per person:** repeat payments combine ("Oliver Kimutai 1,000 ✅"). This is an option on the update screen.
- **Long lists:** choose the whole list, the latest 20 names (numbers stay correct), or totals only. Share the full list as a PDF.
- **Closing report:** total, contributors, breakdown by method and by collector, unpaid pledges and members who contributed, plus the full list. Share it on WhatsApp, as a PDF, or close the Harambee.
- **Several collectors:** add other committee members' numbers. They appear in the update, and forwarded or pasted messages can be marked "received by" them, with a total per person.
- **Thank-you messages:** a "Say thanks" button on the payment notification, or "Send thank-you message" on any contribution. It opens WhatsApp (or SMS) with the message typed in. When M-Pesa hides part of the number, you pick the contact.
- **Pledge reminders:** share the pending-pledges list to the group, or remind each person privately.
- **Members groups** (welfare / church / workplace): keep a members list (typed, pasted, or built from a past Harambee's contributors) and link it to a Harambee. You see who has and hasn't contributed, the expected amount each, a "yet to contribute" list for the group, and one-tap reminders.
- **Message wording:** edit the thank-you and reminder templates in Settings.
- **Export** to CSV for a spreadsheet.
- Works offline. Nothing leaves the phone.

## Your records are safe

- **Deleting SMS doesn't matter.** Each payment, including the full original M-Pesa message, is stored in the app's own database the moment it arrives. Clearing the inbox afterwards changes nothing.
- **Missed messages are caught up.** If the phone was off or the app was stopped when money arrived, the inbox is checked the next time the app opens. Already-recorded codes are skipped. (A message deleted *before* the app ever saw it can still be pasted or typed in with its code.)
- **Backups:**
  - Save a backup file to Google Drive, Downloads or WhatsApp.
  - Restore it on any phone. Damaged files and files from other apps are refused.
  - The app also keeps automatic daily copies (the last 7), plus a copy before every restore or delete, so a mistake can be undone.
  - Android's own Google backup and phone-to-phone transfer include the records too.
- **All-or-nothing writes.** Every change is a single database transaction, so a crash can't leave a half-updated list.
- **Activity history.** Every addition, tick, edit (old → new amount or name), exclusion, reversal and deletion is logged with the time. Useful when the committee asks questions.
- **Safe upgrades.** Database versions are recorded in `app/schemas`, and every upgrade keeps existing data.

## Settings

- **Incoming payments:** ask before adding, catch up on missed messages (with "Check now"), notification sound and style.
- **Data & backup:** backup and restore, automatic copies, activity history, delete everything (asks you to type DELETE, and is undoable).
- **Security:** app lock with fingerprint, face or phone PIN, re-locked after a minute away. It also hides the app in the recent-apps view and blocks screenshots.
- **Appearance:** light, dark or follow the phone; wallpaper colours on Android 12+; themed icon on Android 13+.
- **Message wording:** thank-you, pledge reminder and member reminder.
- **Remembered choices:** the WhatsApp update options are kept between updates.

## Editions

| | `full` | `play` |
|---|---|---|
| Automatic M-Pesa SMS capture | ✅ | — |
| Inbox scan and catch-up | ✅ | — |
| Paste, share or type payments | ✅ | ✅ |
| Everything else | ✅ | ✅ |
| SMS permissions | Yes | **None**, so it can go on Google Play |

## Build

Requirements: JDK 17+ and the Android SDK (platform 36).

```
./gradlew testFullDebugUnitTest            # parser, list, message and backup tests
./gradlew assembleFullDebug                # app/build/outputs/apk/full/debug/
./gradlew assemblePlayDebug                # app/build/outputs/apk/play/debug/
```

GitHub Actions (`.github/workflows/android.yml`) runs the tests and lint and builds both editions on every push.

Stack: Kotlin, Jetpack Compose (Material 3), Room, kotlinx.serialization, AndroidX Biometric, minSdk 26.

## Google Play note

Google Play only allows `READ_SMS` / `RECEIVE_SMS` for approved use cases. Install the `full` edition APK directly (sideloading). Publish the `play` edition, which has no SMS permissions, on Google Play.
