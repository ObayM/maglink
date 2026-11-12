# CPU Magnetic link

Sending text (information) from my laptop to my phone through the magnetic field of the CPU's power circuitry, without any Wi-Fi, Bluetooth, sound, or a cable.


https://github.com/user-attachments/assets/318336ba-6685-4490-99c6-a5bc1f37b4df


## How it works

Basically the way this works is when the CPU jumps from idle to 100% (or near to it), its power draw goes from about 6W to 23W and the current rushing through the voltage regulator makes a tiny magnetic field.

Using the fact that the phone's compass sensor can feel these "jumps", the laptop can send bits by freezing/unfreezing 8 copies of `yes` (busy then idle is a 1, idle then busy is a 0).

There is an Android app that reads the magnetometer as fast as it can (about 125 readings a second on a Galaxy A16) and keeps the last 60 seconds. Twice a second it slides along that buffer looking for a spot where all 11 sync bits match, then reads the length, the bytes and the checksum. A message only shows up if the checksum passes :)
If it doesn't, the app flips the bits it was least surea bout (1 or 2 at a time) and checks again which saves a lot of "almost-right" messages.

## What it can do

**Text**: You can send any text and it will be streamed
**16x16 Pictures**: Send pictures (welll, small ones for now)
**Commands**: send `!flash`, `!buzz` or `!open https://...` and the phone toggles the flashlight, vibrates or opens the link.
Finally, tracking when your laptop is idle/on high load or any laptop actually


## Results

| mode | coding | speed | worked (at a good spot) |
| --- | --- | --- | --- |
| normal | Manchester, 50 ms halves | 10 bits/s | 4/4 |
| fast | 4b5b + NRZI, 40 ms pieces | 20 bits/s | 3/6 |
| turbo | 4b5b + NRZI, 16 ms pieces | 50 bits/s | 2/8 (experimental) |


MAGNETO (Guri et al., 2018) reported up to 5 bits/s from a computer to a phone, at up to 12.5 cm. But here the phone needs to be right on the laptop, so it's not the same setup, but it's the closest comparison I know of.

## Try it

You need a Linux laptop (macOS might work too, untested. Windows won't, it has no SIGSTOP) with Python 3, and an Android phone (Android 10+).

1. Build and install the app:
```
cd android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```
2. Find a good spot: run `python3 scripts/beacon.py` and slide the phone around the laptop. When the app shows "Signal ... µT", that's the strength there. 15+ means fast should work, 8+ means use normal, lower means keep looking.

3. Set `MESSAGE` and `MODE` in `scripts/transmit.py` and run `python3 scripts/transmit.py`. For a picture, use `payload = picture(CAT)` instead of the message.

The app also serves its raw readings on your local network, which makes debugging much easier:

```
curl http://<phone-ip>:8081/ > readings.json
```

