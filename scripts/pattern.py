import os, signal, subprocess, time

HALF = 6.0
CYCLES = 12

def wait_until(moment):
    while time.time() - time_0 < moment:
        time.sleep(0.001)

def busy(on):
    for p in procs:
        os.kill(p.pid, signal.SIGCONT if on else signal.SIGSTOP)

procs = [subprocess.Popen(["yes"], stdout=subprocess.DEVNULL) for _ in range(8)]
busy(False)
time_0 = time.time()

try:
    for i in range(CYCLES):
        print(f"cycle {i + 1}/{CYCLES}")
        wait_until(i * 2 * HALF)
        
        busy(True)

        wait_until(i * 2 * HALF + HALF)
        busy(False)
finally:
    for p in procs:
        p.kill()
        p.wait()
