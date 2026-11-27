---
layout: default
title: Privacy Policy
---

# Privacy Policy for MorseRinger

**Last Updated:** 2026-11-27

MorseRinger ("the App") is an Android application designed to translate incoming caller names into Morse code audio. 

## Data Collection and Use

MorseRinger requires access to your Contacts and Phone State, and must be set as the Call Screening app, to function. **All data processing occurs locally on your device.** 

The App uses these permissions strictly for the following purposes:
1. **Call Screening:** To detect when an incoming call arrives and determine the caller's phone number.
2. **Contacts Access:** To look up the incoming phone number in your device's address book and retrieve the corresponding display name.

Once the name is retrieved, it is converted into Morse code audio and played through your device's speaker. 

### Why the App needs the Contacts permission

The App's core function is to tell you who is calling. To do that it must match the number of *any* incoming call against your address book at the moment the phone rings, without any interaction from you. The Android Contact Picker cannot provide this: it only returns contacts you select manually while the App is open, so it cannot identify an arbitrary caller.

The App keeps its use of Contacts as narrow as possible:
- It reads contacts only while an incoming call is ringing, and only for that call's number.
- It reads only the display name of the matching contact. No other contact fields (phone numbers, e-mail addresses, photos, notes, etc.) are read.
- It never lists, copies, or browses your address book.
- The name is held in memory only for the duration of the call and is never saved, logged, or transmitted.

## Data Sharing and Storage

**MorseRinger does not share, transmit, or store your personal data.** 
- No contact information is ever sent to external servers.
- No contact names, phone numbers or call logs are saved or uploaded.
- The app operates entirely offline and does not contain any tracking, analytics, or advertisement SDKs.

## Your Consent

By using the App, you consent to the local processing of your contacts and call states as described above. You may revoke these permissions at any time via your Android device's settings, though this will prevent the App from functioning correctly.

## Contact

If you have any questions about this privacy policy or the App's data handling practices, please contact the developer via our GitHub repository.

---
[Return Home](index.html)
