import json, os, random, signal, subprocess, time, urllib.request, bisect

PHONE = "http://192.168.1.4:8081"

MODE = "raw" # or "raw" for plain NRZI
HALF = 0.05
NBYTES = 16

WARMUP = 4
SYNC = "11100010010"
SKIP = HALF / 2

CODES = ["11110", "01001", "10100", "10101", "01010", "01011", "01110", "01111",
         "10010", "10011", "10110", "10111", "11010", "11011", "11100", "11101"]

data = bytes(random.randrange(256) for _ in range(NBYTES))

bits = "".join(format(b, "08b") for b in data)

if MODE == "4b5b":
    code = "".join(CODES[int(bits[i:i + 4], 2)] for i in range(0, len(bits), 4))
else:
    code = bits

halves = [True, False] * WARMUP

for b in SYNC:
    halves += [True, False] if b == "1" else [False, True]


level = halves[-1]

for b in code:
    if b == "1":
        level = not level
    halves.append(level)
    
halves.append(False)


def wait_until(moment):
    while time.time() - time_0 < moment:
        time.sleep(0.001)

def busy(on):
    for p in procs:
        os.kill(p.pid, signal.SIGCONT if on else signal.SIGSTOP)

procs = [subprocess.Popen(["yes"], stdout=subprocess.DEVNULL) for _ in range(8)]
busy(False)

time_0 = time.time()

for i, want_busy in enumerate(halves):
    wait_until(1.0 + i * HALF)
    busy(want_busy)

wait_until(1.0 + len(halves) * HALF)
busy(False)

for p in procs:
    p.kill()
    p.wait()


time.sleep(1.5)

d = json.load(urllib.request.urlopen(PHONE))
t = d["t"]
s = [a + b - c for a, b, c in zip(d["x"], d["y"], d["z"])]

with open(f"nrzi{time.strftime('%H-%M-%S')}.json", "w") as f:
    json.dump({**d, "mode": MODE, "half": HALF, "data": data.hex(), "code": code}, f)

prefix = [0]
for v in s:
    prefix.append(prefix[-1] + v)

def avg(a, b):
    i = bisect.bisect_left(t, a)
    
    j = bisect.bisect_left(t, b)
    
    return (prefix[j] - prefix[i]) / max(j - i, 1)

def level_at(start):
    return avg(start + SKIP, start + HALF)

def sync_score(start):
    agree, strength = 0, 0
    
    for k, b in enumerate(SYNC):
        m = level_at(start + k * 2 * HALF) - level_at(start + k * 2 * HALF + HALF)
        
        if b == "0":
            m = -m
        strength += m
        
        if m > 0:
            agree += 1
    return agree, strength

best = None
start = max(t[0], t[-1] - len(halves) * HALF - 4)

while start < t[-1] - len(SYNC) * 2 * HALF:
    agree, strength = sync_score(start)
    
    if agree == len(SYNC) and (best is None or strength > best[0]):
        best = (strength, start)
    start += 0.005

if best is None:
    print("no sync found")
    exit()

strength, sync_start = best
amp = strength / len(SYNC)
data_start = sync_start + len(SYNC) * 2 * HALF

got = ""
high = True
before = level_at(data_start - HALF)

for k in range(len(code)):
    now = level_at(data_start + k * HALF)
    diff = now - before
    flip = diff < -amp / 2 if high else diff > amp / 2

    if flip:
        high = not high
    got += "1" if flip else "0"
    before = now

errors = sum(a != b for a, b in zip(got, code))

print(MODE, "half", HALF, "->", round(len(bits) / (len(code) * HALF), 1), "bits/s")
print("symbol errors:", errors, "/", len(code))

if MODE == "4b5b":
    back = ""
    for i in range(0, len(got), 5):
        chunk = got[i:i + 5]
        back += format(CODES.index(chunk), "04b") if chunk in CODES else "????"
else:
    back = got

right = sum(back[8 * i:8 * i + 8] == format(b, "08b") for i, b in enumerate(data))
print("bytes right:", right, "/", len(data))