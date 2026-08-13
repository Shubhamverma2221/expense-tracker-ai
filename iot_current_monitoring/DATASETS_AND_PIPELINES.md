# 📊 Public Datasets & Implementation Pipelines (NILM & Motor MCSA)

This guide breaks down the benchmark datasets, toolkits, and algorithmic pipelines for **Eldercare Routine Monitoring** and **Workshop Motor Maintenance**.

---

## 🏆 Summary Comparison

| Metric | 👴 Eldercare Routine Anomaly (NILM) | ⚙️ Workshop Motor Maintenance (MCSA) |
| :--- | :--- | :--- |
| **Benchmark Datasets** | **UK-DALE** & **REDD** | **Paderborn University Bearing Dataset** |
| **Data Format** | Whole-house & sub-metered Power/Current (1Hz & 16kHz) | Synchronous Stator Current + Vibration at 64kHz |
| **Open-Source Toolkit** | `NILMTK` (Non-Intrusive Load Monitoring Toolkit) | `scipy.signal` / `librosa` / `scikit-learn` |
| **Core ML Task** | Energy Disaggregation ➔ Temporal Routine Anomaly Detection | Motor Current Signature Analysis (FFT) ➔ Bearing Fault Classification |
| **Data Availability** | ⭐⭐⭐⭐⭐ (Public, Free, standard benchmark) | ⭐⭐⭐⭐⭐ (Free academic access, Kaggle mirror) |
| **Story / Presentation** | *"Privacy-first eldercare: zero cameras, 1 breaker clamp"* | *"Democratizing industrial predictive maintenance for small workshops"* |

---

## 1. 👴 Option A: Silent Eldercare Routine Anomaly Detector

### Datasets:
1. **UK-DALE (UK Domestic Appliance-Level Electricity)**:
   - 5 houses, recorded up to 655 days at 1-second (whole house) and 6-second (individual appliances) intervals.
   - Ground truth labels for: Kettle, Microwave, Toaster, Washing Machine, Geyser, Fridge, TV.
2. **REDD (Reference Energy Disaggregation Dataset)**:
   - High-frequency (15kHz) raw current/voltage waveforms + 1Hz aggregate power for 6 US homes.

### Algorithmic 2-Stage Pipeline:
```
[Whole-House Current / Power]
             │
             ▼ (Stage 1: Energy Disaggregation via NILMTK / 1D-CNN)
[Appliance State Time Series: Kettle=ON @ 7:42 AM, Microwave=ON @ 8:15 AM]
             │
             ▼ (Stage 2: Temporal Markov / Daily Gaussian Mixture Model)
[Routine Likelihood Calculation: P(Activity | Time_of_Day)]
             │
             ▼
[Alert Trigger: If Morning Routine Confidence < 15% by 9:30 AM ➔ Family Alert]
```

---

## 2. ⚙️ Option B: Small Workshop Motor Predictive Maintenance

### Dataset:
* **Paderborn University Bearing Dataset**:
  - Contains synchronous measurements of **Motor Stator Current** (2 phases) and vibration signals under constant load.
  - **32 Bearing Conditions**:
    - 6 Healthy baseline bearings
    - 12 Artificially damaged bearings
    - 14 Real run-to-failure fatigue/wear damaged bearings

### Motor Current Signature Analysis (MCSA) Mathematical Principles:
When mechanical bearing faults develop, load torque variations modulate the air-gap magnetic flux, producing specific sideband harmonics in the stator phase current at characteristic fault frequencies:

$$f_{\text{fault}} = |f_{\text{mains}} \pm k \cdot f_{\text{characteristic}}|$$

Where:
- $f_{\text{BPFO}}$ = Ball Pass Frequency Outer Race
- $f_{\text{BPFI}}$ = Ball Pass Frequency Inner Race
- $f_{\text{BSF}}$ = Ball Spin Frequency
- $f_{\text{FTF}}$ = Fundamental Train Frequency

---

## 🚀 Recommended Choice

- If you want a **consumer/healthtech AI story**: Choose **Eldercare Routine Monitoring (UK-DALE + NILMTK)**.
- If you want an **industrial/hardware IoT AI story**: Choose **Motor Predictive Maintenance (Paderborn Dataset)**.
