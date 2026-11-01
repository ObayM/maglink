import os, sys, time, glob

os.environ["OPENBLAS_NUM_THREADS"] = "1"; os.environ["OMP_NUM_THREADS"] = "1"

import multiprocessing as mp
import numpy as np

P = (glob.glob("/sys/class/hwmon/hwmon4/power1_input") + glob.glob("/sys/class/hwmon/hwmon4/power1_average"))[0]

def py(stop):
    while not stop.value:
        for _ in range(20000): pass

def mm(stop):
    a = np.random.rand(256, 256)
    while not stop.value:
        a = a @ a; a /= np.abs(a).max()

def watts(sec):
    xs = []
    end = time.time() + sec
    while time.time() < end:
        xs.append(int(open(P).read()) / 1e6); time.sleep(0.1)
    return sum(xs[len(xs)//3:]) / len(xs[len(xs)//3:])

if __name__ == "__main__":
    print("sensor:", P)
    print(f"idle: {watts(3):.1f} W")

    for name, fn in [("python loop", py), ("numpy matmul", mm)]:
        stop = mp.RawValue("b", 0)
        ws = [mp.Process(target=fn, args=(stop,), daemon=True) for _ in range(os.cpu_count())]
        
        for w in ws: w.start()
        
        print(f"{name}: {watts(4):.1f} W")
        stop.value = 1
        
        for w in ws: w.join()
        
        time.sleep(2)
    print(f"idle again: {watts(3):.1f} W")

# Results on this laptop were:
# idle: 12.3 W
# python loop: 22.4 W
# numpy matmul: 22.6 W
# idle again: 4.2 W