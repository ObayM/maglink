# CPU Magnetic link

Sending text (information) from my laptop to my phone through the magnetic field of the CPU's power circuitry, without any Wi-Fi, Bluetooth, sound, or a cable.

## How it works

Basically the way this works is when the CPU jumps from idle to 100% (or near to it), its power draw goes from about 6W to 23W and the current rushing through the voltage regulator makes a tiny magnetic field.

Using the fact that the phone's compass sensor can feel these "jumps", the laptop can send bits by freezing/unfreezing 8 copies of `yes` (busy then idle is a 1, idle then busy is a 0).

There is an Android app that reads the magnetometer as fast as it can (about 122 readings a second on a Galaxy A16) and keeps the last 60 seconds. Once a second it slides along that buffer looking for a spot where all 11 sync bits match, then reads the length, the bytes and the checksum. A message only shows up if the checksum passes :)

## Results

| speed | half-bit | result |
| --- | --- | --- |
| 5 bits/s | 0.1 s | works |
| 10 bits/s | 0.05 s | works (live version) |
| 12.5 bits/s | 0.04 s | fails |
| 16.7 bits/s | 0.03 s | fails |

MAGNETO (Guri et al., 2018) reported up to 5 bits/s from a computer to a phone over a short distance. Anyway my phone needs to be very close to the laptop, so it's not the same setup, but it's the closest comparison I know of.

## Try it

You need a Linux laptop (macOS might work too, untested. Windows won't, it has no SIGSTOP) with Python 3, and an Android phone (Android 10+).

1. Build and install the app:
```
cd android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

2. Find a good spot to put the phone in, set `HALF = 0.5` in `scripts/pattern.py`, you can run it and slide the phone around the laptop while watching the wave in the app, when it turns into a clean square wave, that's the right place!

3. Fix the axis mix, `Decoder.mix()` combines the three axes as `x + y - z` which is right for me but it could be different for you :(

4. You can send by running `python3 scripts/transmit.py`. Make sure `HALF` in the script matches the one in `Decoder.kt`.

The app also serves its raw readings on your local network, which makes debugging much easier:

```
curl http://<phone-ip>:8081/ > readings.json
```

