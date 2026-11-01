window.onerror = (msg) => {
    document.getElementById("staus").textContent = "Error:" + msg
}

const HALF = 2
const SECONDS = 60

const latest = {}

let recording = null

function record(name, value) {
  latest[name] = value
  
  if (recording) {
    if (!recording[name]) recording[name] = { t: [], v: [] }
    
    recording[name].t.push(performance.now() / 1000)
    recording[name].v.push(value)
  }
}

function yaw(q) {
  const [x, y, z, w] = q
  
  return Math.atan2(2 * (w * z + x * y), 1 - 2 * (y * y + z * z)) * 180 / Math.PI
}



function unwrap(v) {
  const out = [v[0]]
  
  for (let i = 1; i < v.length; i++) {
    let d = v[i] - v[i - 1]
    if (d > 180) d -= 360
    if (d < -180) d += 360
    out.push(out[i - 1] + d)
  }
  
  return out
}

function detrend(t, v) {
  const n = t.length
  
  const mt = t.reduce((a, b) => a + b, 0) / n
  const mv = v.reduce((a, b) => a + b, 0) / n
  let top = 0, bottom = 0
  
  for (let i = 0; i < n; i++) {
    top += (t[i] - mt) * (v[i] - mv)
    bottom += (t[i] - mt) ** 2
  }
  
  const slope = top / bottom
  return v.map((x, i) => x - mv - slope * (t[i] - mt))
}

function strength(t, v, f) {
  let re = 0, im = 0
  
  for (let i = 0; i < t.length; i++) {
    re += v[i] * Math.cos(2 * Math.PI * f * t[i])
    im += v[i] * Math.sin(2 * Math.PI * f * t[i])
  }
  
  return Math.hypot(re, im) / t.length
}

function analyze(t, v) {
  const r = detrend(t, v)
  const T = t[t.length - 1] - t[0]
  const f = 1 / (2 * HALF)
  const signal = strength(t, r, f)

  let noise = 0, count = 0

  for (let k = 2; k <= 8; k++) {
    noise += strength(t, r, f + k / T) ** 2
    count++
    
    if (f - k / T > 1 / T) {
      noise += strength(t, r, f - k / T) ** 2
      count++
    }
  }

  return { snr: signal / Math.sqrt(noise / count), size: Math.PI * signal }
}


setInterval(() => {
    document.getElementById('live').textContent = JSON.stringify(latest, null, 2)
}, 200)

window.addEventListener("deviceorientationabsolute", (e) => {
  if (e.alpha !== null) {
    record("eventHeading", e.alpha)
  }
})

if ("AbsoluteOrientationSensor" in window) {
  const compass = new AbsoluteOrientationSensor({ frequency: 60 })
  compass.addEventListener("reading", () => record("sensorHeading", yaw(compass.quaternion)))
  compass.start()
}


if ("RelativeOrientationSensor" in window) {
  const gyro = new RelativeOrientationSensor({ frequency: 60 })
  gyro.addEventListener("reading", () => record("controlHeading", yaw(gyro.quaternion)))
  gyro.start()
}

if ("Magnetometer" in window) {
  const mag = new Magnetometer({ frequency: 60 })
  mag.addEventListener("reading", () => {
    record("magX", mag.x)
    record("magY", mag.y)
    record("magZ", mag.z)
  })
  mag.start()
}


const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

document.getElementById("start").onclick = async () => {
  try {
    await navigator.wakeLock.request("screen")
  } catch (e) {}

  const status = document.getElementById("status")
  for (let s = 10; s > 0; s--) {
    status.textContent = `Lay the phone down, recording in ${s}`
    await sleep(1000)
  }

  recording = {}
  status.textContent = "Recording, don't touch!"
  await sleep(SECONDS * 1000)

  const data = recording
  recording = null
  status.textContent = "Done"

  if (navigator.vibrate) navigator.vibrate(200)
  showResults(data)
}

function showResults(data) {
  let text = ""
  for (const [name, d] of Object.entries(data)) {
    if (d.t.length < 30) continue
    
    const t = d.t.map((x) => x - d.t[0])
    const v = name.endsWith("Heading") ? unwrap(d.v) : d.v
    const { snr, size } = analyze(t, v)
    const verdict = snr >= 5 ? "SIGNAL" : snr >= 3 ? "maybe" : "nothing"
    
    text += `${name}: strength ${snr.toFixed(1)} (${verdict}), size ${size.toFixed(3)}, ${d.t.length} readings\n`
  }
  document.getElementById("results").textContent = text
}