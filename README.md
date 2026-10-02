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
- **Export** to CSV for a spreadsheet.
- Works offline. Nothing leaves the phone.

## Build

Requirements: JDK 17+ and the Android SDK (platform 36).

```
./gradlew testDebugUnitTest   # parser, list and message tests
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

Stack: Kotlin, Jetpack Compose (Material 3), Room, minSdk 26.

## Google Play note

Google Play only allows `READ_SMS` / `RECEIVE_SMS` for approved use cases. Installing the APK directly (sideloading) works as-is. To publish on Play you would need to apply for the SMS permission exception, or ship a build without SMS permissions that relies on paste/share import.
