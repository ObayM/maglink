import json, os, signal, statistics, subprocess, sys, tempfile, threading, time, urllib.request

PHONE = "http://192.168.1.4:8081/"
VIDEO = os.path.expanduser("~/Downloads/demo-1080p.mp4")
LENGTH = 90
REST = 10
OUT = sys.argv[1] if len(sys.argv) > 1 else "activity.json"

work = tempfile.mkdtemp()
code = os.path.join(work, "big.c")
with open(code, "w") as f:
    for i in range(3000):
        f.write(f"int f{i}(int x) {{ for (int k = 0; k < {i % 50 + 5}; k++) x = x * 31 + k; return x; }}\n")

loop = lambda cmd: ["bash", "-c", f"while true; do {cmd}; done"]

ACTIVITIES = [
    ("idle", []),
    ("video", [["mpv", "--mute=yes", "--loop=inf", "--really-quiet", VIDEO]]),
    ("one core", [["python3", "-c", "while True: pass"]]),
    ("build", [loop(f"gcc -O2 -c {code} -o /dev/null")] * 8),
    ("export", [["ffmpeg", "-loglevel", "error", "-stream_loop", "-1", "-i", VIDEO, "-an", "-c:v", "libx264", "-preset", "fast", "-f", "null", "-"]]),
    ("idle", []),
]

readings = {}
clock = []
done = False

def record():
    while not done:
        try:
            snap = json.load(urllib.request.urlopen(PHONE, timeout=10))
            clock.append(time.time() - snap["t"][-1])

            for i, t in enumerate(snap["t"]):
                readings[t] = (snap["x"][i], snap["y"][i], snap["z"][i])
            
        except Exception as e:
            print("phone:", e, flush=True)

        time.sleep(15)

threading.Thread(target=record, daemon=True).start()
segments = []

try:
    for i, (name, cmds) in enumerate(ACTIVITIES):
        if i > 0:
            time.sleep(REST)

        print(f"{name} for {LENGTH} s", flush=True)
        start = time.time()
        
        procs = [subprocess.Popen(c, stdout=subprocess.DEVNULL, start_new_session=True) for c in cmds]
        
        time.sleep(LENGTH)

        if any(p.poll() is not None for p in procs):
            print(f"  {name} stopped early!", flush=True)

        for p in procs:
            os.killpg(p.pid, signal.SIGKILL)
            p.wait()
        segments.append([name, start, time.time()])

    time.sleep(16)
finally:
    
    done = True
    subprocess.run(["rm", "-rf", work])

times = sorted(readings)
offset = statistics.median(clock)
with open(OUT, "w") as f:
    json.dump({
        "segments": [[n, a - offset, b - offset] for n, a, b in segments],
        "t": times,
        "x": [readings[t][0] for t in times],
        "y": [readings[t][1] for t in times],
        "z": [readings[t][2] for t in times],
    }, f)

print(f"saved {len(times)} readings to {OUT}")
