
import subprocess, time
import os, signal

MESSAGE = "hello" # The message we are trying to send our phone :)

WARMUP = 4
SYNC = "11100010010"
HALF = 0.04

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


payload =  picture(CAT)  #MESSAGE.encode()

body = bytes([len(payload)]) + payload
check = 255 - sum(body) % 256 # flipped so a silent stretch (all zeros) can't pass

code = "".join(CODES[b >> 4] + CODES[b & 15] for b in body + bytes([check]))

halves = [True, False] * WARMUP

for bit in SYNC:
    if bit == "1":
        halves += [True, False]
    else:
        halves += [False, True]

level = halves[-1]

for bit in code:
    if bit == "1":
        level = not level
    halves.append(level)

def wait_until(moment):
    while time.time() - time_0 < moment:
        time.sleep(0.001)

def busy(on):
    for p in procs:
        os.kill(p.pid, signal.SIGCONT if on else signal.SIGSTOP)

procs = [subprocess.Popen(["yes"], stdout=subprocess.DEVNULL) for _ in range(8)]
busy(False)

time_0 = time.time()

msg_start = 2.0

for i, want_busy in enumerate(halves):
    wait_until(msg_start + i * HALF)
    busy(want_busy)

wait_until(msg_start + len(halves) * HALF)

busy(False)

for p in procs:
    p.kill()
    p.wait()