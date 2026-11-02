
import subprocess, time
import os, signal

MESSAGE = "hi" # The message we are trying to send our phone :)

WARMUP = 4
SYNC = "11100010010"

MODE = "normal" # normal, fast or turbo

REPEAT = True
GAP = 0.5
WAKE = 3.0



HALF, NRZI = {"slow": (0.1, False), "normal": (0.05, False), "fast": (0.04, True), "turbo": (0.016, True)}[MODE]

CODES = ["11110", "01001", "10100", "10101", "01010", "01011", "01110", "01111",
         "10010", "10011", "10110", "10111", "11010", "11011", "11100", "11101"]

HEART = """
................
................
...###....###...
..#####..#####..
.##############.
.##############.
.##############.
..############..
...##########...
....########....
.....######.....
......####......
.......##.......
................
................
................
"""

CAT = """
................
..#..........#..
..##........##..
..###......###..
..############..
.##############.
.###..####..###.
.###..####..###.
.##############.
.######..######.
.#####.##.#####.
..############..
...##########...
.....######.....
................
................
"""

def picture(art):
    rows = art.strip().splitlines()
    bits = "".join("1" if c == "#" else "0" for row in rows for c in row)

    bits += "0" * (-len(bits) % 8)
    pixels = bytes(int(bits[i:i + 8], 2) for i in range(0, len(bits), 8))
    
    return bytes([1, len(rows[0]), len(rows)]) + pixels


def crc16(data):
    crc = 0xFFFF

    for b in data:
        crc ^= b << 8
        
        for _ in range(8):
            if crc & 0x8000:
                crc = (crc << 1) ^ 0x1021
            else:
                crc = crc << 1
            crc &= 0xFFFF
    return crc


payload =  MESSAGE.encode() #picture(CAT)

body = bytes([len(payload)]) + payload
crc = crc16(body)
check = bytes([crc >> 8, crc & 0xFF])


halves = [True, False] * WARMUP

for bit in SYNC:
    if bit == "1":
        halves += [True, False]
    else:
        halves += [False, True]

level = halves[-1]

if NRZI:
    code = "".join(CODES[b >> 4] + CODES[b & 15] for b in body + check)
    level = halves[-1]
    
    for bit in code:
        if bit == "1":
            level = not level
        
        halves.append(level)
else:
    for b in body + check:
       
        for bit in format(b, "08b"):
            if bit == "1":
                halves += [True, False]
            else:
                halves += [False, True]

def wait_until(moment):
    while time.time() - time_0 < moment:
        time.sleep(0.001)

def busy(on):
    for p in procs:
        os.kill(p.pid, signal.SIGCONT if on else signal.SIGSTOP)

procs = [subprocess.Popen(["yes"], stdout=subprocess.DEVNULL) for _ in range(8)]

time_0 = time.time()

msg_start = WAKE
round_time = len(halves) * HALF + GAP

if REPEAT:
    print(f"sending every {round_time:.1f} s, ctrl+c to stop")

try:
    r = 0
    while True:
        start = msg_start + r * round_time

        for i, want_busy in enumerate(halves):
            wait_until(start + i * HALF)
            busy(want_busy)

        wait_until(start + len(halves) * HALF)

        r += 1

        if not REPEAT:
            break

        busy(True)

except KeyboardInterrupt:
    print("stopped")

finally:
    for p in procs:
        p.kill()
        p.wait()