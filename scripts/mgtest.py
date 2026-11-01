import json, subprocess, time, urllib.request


PHONE = "http://192.168.1.3:8080" # This is the url you get from phyphox
CYCLES = 8
HALF = 2.0
SKIP = 0.3

def phone (path):
    return json.load(urllib.request.urlopen(PHONE + path))

def get_avg(t, values, start, end):
    picked = []

    for ti, v in zip(t, values):
        if start <= ti < end:
            picked.append(v)
    
    return sum(picked) / len(picked)

    
phone("/control?cmd=stop")
phone("/control?cmd=clear")
phone("/control?cmd=start")

time_0 = time.time()
time.sleep(2)

busy = []

for i in range(CYCLES):
    a = time.time() - time_0

    procs = [subprocess.Popen(["yes"], stdout=subprocess.DEVNULL) for _ in range(8)]

    time.sleep(HALF)
    for p in procs:
        p.kill()

    b = time.time() - time_0
    busy.append((a,b))

    print(f"cycle {i+1}/{CYCLES}")
    time.sleep(HALF)

phone("/control?cmd=stop")

data = phone("/get?mag_time=full&magX=full&magY=full&magZ=full")["buffer"]

t = data["mag_time"]["buffer"]

for axis in ["magX", "magY", "magZ"]:
    v = data[axis]["buffer"]
    diffs = [get_avg(t, v, a + SKIP, b) - get_avg(t, v, b + SKIP, b + HALF) for a, b in busy]
    
    print(axis, "average", round(sum(diffs) / len(diffs), 3), "per cycle", [round(d, 2) for d in diffs])

with open(f"test{time.strftime("%H-%M-%S")}.json", "w") as f:
    json.dump({
        "t": t,
        "magX": data["magX"]["buffer"],
        "magY": data["magY"]["buffer"],
        "magZ": data["magZ"]["buffer"],
        "busy": busy,
    }, f)

## RESULTS:


# magX average 0.689 per cycle [0.54, 0.48, 1.14, 0.34, 1.1, 0.44, 0.44, 1.04]
# magY average -0.766 per cycle [-0.72, -0.45, -1.2, -0.3, -1.31, -0.53, -0.39, -1.23]
# magZ average 0.404 per cycle [0.17, 0.21, 0.76, 0.25, 0.74, 0.24, 0.17, 0.69]