# ⚡ Non-Intrusive Electrical Current Signature (NILM) AI Blueprint

> **Concept:** Single-Point Amperage Monitoring + Edge AI for High-Impact Underserved Niches

---

## 🏛️ System Architecture

```
  ┌────────────────────────────────────────────────────────┐
  │                 AC Mains Line (Single Core)            │
  └───────────────────────────┬────────────────────────────┘
                              │
                    ┌─────────▼─────────┐
                    │  SCT-013 CT Clamp │ (Non-invasive split-core current transformer)
                    └─────────┬─────────┘
                              │ 0–1V / 0–50mA AC Analog Output
                    ┌─────────▼─────────┐
                    │ Burden Resistor & │
                    │ DC Biasing Circuit│ (Centers AC wave at 1.65V for 3.3V ADC)
                    └─────────┬─────────┘
                              │
                    ┌─────────▼─────────┐
                    │   ESP32 / MCU     │ (1 kHz – 5 kHz high-speed ADC sampling)
                    │  Firmware (C++)   │
                    └─────────┬─────────┘
                              │ Feature Vector: [I_RMS, I_Peak, Crest_Factor, FFT_3rd, FFT_5th, THD]
                              │ MQTT / JSON over Wi-Fi / Serial
                    ┌─────────▼─────────┐
                    │ Edge AI Engine    │ (Random Forest / 1D-CNN / Isolation Forest)
                    └─────────┬─────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
  🔥 Fire & Arc Fault  👴 Eldercare Routine   ⚙️ Workshop Motor
    Overload Alert       Anomaly Alert        Bearing Wear Drift
```

---

## 🎯 The 3 High-Impact Use Cases

### 1. 🔥 Fire Risk & Micro-Arcing Detector (Informal & Legacy Housing)
- **Problem:** Arc-Fault Circuit Interrupters (AFCIs) cost thousands and cannot be retrofitted into old wiring.
- **AI Intervention:** Micro-arcing produces high-frequency erratic current bursts and flat-top sinusoidal distortion.
- **Features:** Total Harmonic Distortion (THD), High-frequency noise energy ($> 500\text{ Hz}$), Rate of current change ($\frac{dI}{dt}$).

### 2. 👴 Silent Eldercare Routine Monitoring (Privacy-First)
- **Problem:** Cameras and wearable panic buttons face huge user resistance.
- **AI Intervention:** Maps household appliance current signatures (Kettle: 1500W at 7:30 AM, Geyser: 2000W at 8:00 AM, Stove: 1000W at 1:00 PM).
- **Features:** Appliance Disaggregation (NILM), State Transition timestamps, Daily Routine Confidence Interval. If morning routine signature is missing by 9:30 AM ➔ family notification.

### 3. ⚙️ Workshop Motor Bearing Degradation Predictor
- **Problem:** Industrial vibration & SCADA systems are unaffordable for small workshops.
- **AI Intervention:** When motor bearings wear out or stator windings heat up, the motor draws asymmetrical phase currents and produces characteristic sideband harmonics around 50 Hz.
- **Features:** Harmonic Power Ratio, Crest Factor ($\frac{I_{peak}}{I_{RMS}}$), Stator Current Signature Analysis (MCSA).

---

## 📦 Hardware Bill of Materials (Total: ~₹400 / $5 USD)

| Component | Purpose | Estimated Cost |
| :--- | :--- | :--- |
| **SCT-013-000** | 100A/50mA Non-invasive Split Core Current Transformer Clamp | ~₹150 |
| **ESP32 DevKit V1** | Dual-core 240MHz microcontroller with Wi-Fi & 12-bit ADC | ~₹220 |
| **Resistors & Capacitor** | 2x 10kΩ (biasing voltage divider), 1x 33Ω (burden resistor), 1x 10µF (filtering cap) | ~₹20 |
| **3.5mm Audio Jack** | Connector for SCT-013 plug | ~₹10 |
