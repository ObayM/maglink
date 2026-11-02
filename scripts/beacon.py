
import subprocess, time
import os, signal

HALF = 0.5

def wait_until(moment):
    while time.time() - time_0 < moment:
        time.sleep(0.001)

def busy(on):
    for p in procs:
        os.kill(p.pid, signal.SIGCONT if on else signal.SIGSTOP)

procs = [subprocess.Popen(["yes"], stdout=subprocess.DEVNULL) for _ in range(8)]
busy(False)

time_0 = time.time()
print("beacon on, ctrl+c to stop")

try:
    i = 0
    while True:
        wait_until(i * HALF)
        busy(i % 2 == 0)
        i += 1
except KeyboardInterrupt:
    print("stopped")

finally:
    for p in procs:
        p.kill()
        p.wait()
