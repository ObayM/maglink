import json, subprocess, time, urllib.request


PHONE = "http://192.168.1.3:8080" # This is the url you get from phyphox
CYCLES = 8

MESSAGE = "hi" # The message we are trying to send our phone :)

HALF = 0.5
SKIP = HALF / 2

def phone (path):
    return json.load(urllib.request.urlopen(PHONE + path))

def get_avg(t, values, start, end):
    picked = []

    for ti, v in zip(t, values):
        if start <= ti < end:
            picked.append(v)
    
    return sum(picked) / len(picked)


bits = "".join(format(b, "08b") for b in MESSAGE.encode()) # converting to 0s/1s

print(bits) # 0110100001101001

halves = []

for bit in bits:
    if bit == "1":
        halves += [True, False]
    else:
        halves += [False, True]


def wait_until(moment):
    while time.time() - time_0 < moment:
        time.sleep(0.001)

def stop(procs):
    for p in procs:
        p.kill()
    
    for p in procs:
        p.wait()

phone("/control?cmd=stop")
phone("/control?cmd=clear")
phone("/control?cmd=start")
time_0 = time.time()


msg_start = 2.0
procs = []

for i, want_busy in enumerate(halves):
    wait_until(msg_start + i * HALF)

    if want_busy and not procs:
        procs = [subprocess.Popen(["yes"], stdout=subprocess.DEVNULL) for _ in range(8)]
    if not want_busy and procs:
        stop(procs)
        procs = []

wait_until(msg_start + len(halves) * HALF)

stop(procs)
time.sleep(1)

phone("/control?cmd=stop")

data = phone("/get?mag_time=full&magX=full&magY=full&magZ=full")["buffer"]
t = data["mag_time"]["buffer"]
x = data["magX"]["buffer"]
y = data["magY"]["buffer"]
z = data["magZ"]["buffer"]


with open(f"send{time.strftime('%H-%M-%S')}.json", "w") as f:
    json.dump({
        "t": t,
        "magX": x,
        "magY": y,
        "magZ": z,
        "message": MESSAGE,
        "bits": bits,
        "half": HALF,
        "msg_start": msg_start,
    }, f)

s = [a - b + c for a, b, c in zip(x, y, z)]

received = ""

for i in range(len(bits)):
    start = msg_start + i * 2 * HALF
    
    first = get_avg(t, s, start + SKIP, start + HALF)
    second = get_avg(t, s, start + HALF + SKIP, start + 2 * HALF)
    
    received += "1" if first > second else "0"
    print(i, round(first - second, 2))


text = bytes(int(received[i:i +8], 2) for i in range(0, len(received), 8))

print("sent:", bits)
print("got: ", received)
print("text:", text.decode(errors="replace"))