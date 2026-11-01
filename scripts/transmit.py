
import subprocess, time
import os, signal

MESSAGE = "!flash" # The message we are trying to send our phone :)

WARMUP = 4
SYNC = "11100010010"
HALF = 0.05

ART = """
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

def picture(art):
    rows = art.strip().splitlines()
    bits = "".join("1" if c == "#" else "0" for row in rows for c in row)

    bits += "0" * (-len(bits) % 8)
    pixels = bytes(int(bits[i:i + 8], 2) for i in range(0, len(bits), 8))
    
    return bytes([1, len(rows[0]), len(rows)]) + pixels


payload =  picture(ART) # MESSAGE.encode() for messages
body = bytes([len(payload)]) + payload
check = 255 - sum(body) % 256 # flipped so a silent stretch (all zeros) can't pass

frame = SYNC + "".join(format(b, "08b") for b in body + bytes([check]))

print(frame)

halves = [True, False] * WARMUP

for bit in frame:
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