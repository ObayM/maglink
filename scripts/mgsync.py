import json, subprocess, time, urllib.request
import os, signal, bisect

PHONE = "http://192.168.1.3:8080" # This is the url you get from phyphox

MESSAGE = "hello world" # The message we are trying to send our phone :)

WARMUP = 4
SYNC = "11100010010"
HALF = 0.1
SKIP = HALF / 2

def phone (path):
    return json.load(urllib.request.urlopen(PHONE + path))


payload = MESSAGE.encode()
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

phone("/control?cmd=stop")
phone("/control?cmd=clear")
phone("/control?cmd=start")
time_0 = time.time()


msg_start = 2.0

for i, want_busy in enumerate(halves):
    wait_until(msg_start + i * HALF)
    busy(want_busy)

wait_until(msg_start + len(halves) * HALF)

busy(False)
time.sleep(1)

phone("/control?cmd=stop")

for p in procs:
    p.kill()
    p.wait()

data = phone("/get?mag_time=full&magX=full&magY=full&magZ=full")["buffer"]
t = data["mag_time"]["buffer"]
x = data["magX"]["buffer"]
y = data["magY"]["buffer"]
z = data["magZ"]["buffer"]

n = min(len(t), len(x), len(y), len(z))
t = t[:n]


with open(f"sync{time.strftime('%H-%M-%S')}.json", "w") as f:
    json.dump({
        "t": t,
        "magX": x,
        "magY": y,
        "magZ": z,
        "message": MESSAGE,
        "frame": frame,
        "sync": SYNC,
        "half": HALF,
        "msg_start": msg_start,
        "warmup": WARMUP,
    }, f)

s = [a - b + c for a, b, c in zip(x, y, z)]



prefix = [0]

for v in s:
    prefix.append(prefix[-1] + v)

def fast_avg(a, b):
    i = bisect.bisect_left(t, a)
    j = bisect.bisect_left(t, b)
    return (prefix[j] - prefix[i]) / max(j - i, 1)

def bit_margin(start):
    return fast_avg(start + SKIP, start + HALF) - fast_avg(start + HALF + SKIP, start + 2 * HALF)

def sync_score(start):
    agree, strength = 0, 0
    for k, b in enumerate(SYNC):
        m = bit_margin(start + k * 2 * HALF)
        if b == "0":
            m = -m
        strength += m
        if m > 0:
            agree += 1
    return agree, strength


candidates = []
candidate = t[0]

while candidate < t[-1] - len(SYNC) * 2 * HALF:
    candidates.append((sync_score(candidate), candidate))
    candidate += 0.005

candidates.sort(reverse=True)

def read_byte(data_start, k):
    bits = ""
    for i in range(8):
        bits += "1" if bit_margin(data_start + (8 * k + i) * 2 * HALF) > 0 else "0"
    return int(bits, 2)


tried = []
results = []

for score, start in candidates:
    if any(abs(start - other) < HALF for other in tried):
        continue
    tried.append(start)

    data_start = start + len(SYNC) * 2 * HALF
    length = read_byte(data_start, 0)
    got = bytes(read_byte(data_start, 1 + k) for k in range(length))
    got_check = read_byte(data_start, 1 + length)

    ok = length > 0 and got_check == 255 - (length + sum(got)) % 256
    results.append((ok, start, got))

    if ok or len(tried) == 5:
        break

ok, start, got = next((r for r in results if r[0]), results[0])

print("tried", len(tried), "spots, sync at", round(start, 3), "s, sent at", msg_start + WARMUP * 2 * HALF, "s")
print("checksum ok:", ok)
print("text:" if ok else "best guess (broken):", got.decode(errors="replace"))
