import json
import matplotlib.pyplot as plt


data = json.load(open('data/test.json'))

t = data["t"]

def smooth(v, n):
    out = []
    for i in range(len(v)):
        window = v[max(0, i - n // 2): i + n // 2 + 1]
        out.append(sum(window) / len(window))

    return out

fig, rows = plt.subplots(3, 1, sharex=True, figsize=(12,7))

rate = len(t) / (t[-1] - t[0])
half = data["busy"][0][1] - data["busy"][0][0]
cycle = int(2 * half * rate)


for row, axis in zip(rows, ["magX","magY", "magZ"]):

    for a, b in data["busy"]:
        row.axvspan(a, b, color="lightblue")
    v = data[axis]

    drift = smooth(v, cycle)
    clean = smooth([x - dr for x, dr in zip(v, drift)], 9)
    row.plot(t, clean, linewidth=0.8) 
    row.set_ylabel(axis + '(uT)')

rows[-1].set_xlabel("time (s)")
plt.savefig('data/test.png', dpi=120)
plt.show()