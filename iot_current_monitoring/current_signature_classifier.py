"""
Current Signature Analysis (MCSA & NILM) Anomaly Classifier
Processes raw AC current samples and extracts harmonic FFT features to detect:
1. Normal Resistive / Inductive Loads
2. Motor Bearing Degradation
3. Electrical Arcing & Fire Risk
"""

import math
from typing import Any, Dict, List, Tuple


class CurrentSignatureEngine:
    def __init__(self, sampling_rate: int = 10000, mains_freq: float = 50.0) -> None:
        self.sampling_rate = sampling_rate
        self.mains_freq = mains_freq

    def calculate_rms_and_peak(self, waveform: List[float]) -> Tuple[float, float, float]:
        """Calculates RMS Current, Peak Current, and Crest Factor."""
        if not waveform:
            return 0.0, 0.0, 1.414

        n = len(waveform)
        sum_sq = sum(x * x for x in waveform)
        rms = math.sqrt(sum_sq / n)
        peak = max(abs(x) for x in waveform)
        crest_factor = (peak / rms) if rms > 0.01 else 1.414
        return round(rms, 3), round(peak, 3), round(crest_factor, 3)

    def extract_harmonic_features(self, waveform: List[float]) -> Dict[str, float]:
        """Extracts Fundamental (50Hz), 3rd (150Hz), 5th (250Hz) harmonics and THD via Discrete Fourier Transform."""
        n = len(waveform)
        if n < 100:
            return {"fundamental": 0.0, "thd": 0.0, "h3_ratio": 0.0, "h5_ratio": 0.0}

        def dft_magnitude(target_freq: float) -> float:
            k = int(round(target_freq * n / self.sampling_rate))
            real_part = sum(waveform[t] * math.cos(2 * math.pi * k * t / n) for t in range(n))
            imag_part = sum(waveform[t] * math.sin(2 * math.pi * k * t / n) for t in range(n))
            return math.sqrt(real_part * real_part + imag_part * imag_part) * (2.0 / n)

        f0 = dft_magnitude(50.0)
        h3 = dft_magnitude(150.0)
        h5 = dft_magnitude(250.0)
        h7 = dft_magnitude(350.0)

        thd = math.sqrt(h3 * h3 + h5 * h5 + h7 * h7) / f0 if f0 > 0.1 else 0.0
        h3_ratio = (h3 / f0) if f0 > 0.1 else 0.0
        h5_ratio = (h5 / f0) if f0 > 0.1 else 0.0

        return {
            "fundamental_50hz": round(f0, 3),
            "harmonic_3rd": round(h3, 3),
            "harmonic_5th": round(h5, 3),
            "harmonic_7th": round(h7, 3),
            "thd": round(thd, 3),
            "h3_ratio": round(h3_ratio, 3),
            "h5_ratio": round(h5_ratio, 3),
        }

    def classify_signature(self, waveform: List[float]) -> Dict[str, Any]:
        """Classifies the electrical current state and detects anomalies."""
        rms, peak, crest_factor = self.calculate_rms_and_peak(waveform)
        harmonics = self.extract_harmonic_features(waveform)

        if rms < 0.1:
            return {
                "state": "IDLE / OFF",
                "severity": "normal",
                "confidence": 99,
                "rms_current_a": rms,
                "peak_current_a": peak,
                "crest_factor": crest_factor,
                "diagnosis": "No electrical load detected on line.",
            }

        # Check 1: Electrical Arcing / High-Frequency Noise
        if crest_factor > 2.8 or harmonics["thd"] > 0.45:
            return {
                "state": "🔥 ARCING FAULT / FIRE RISK",
                "severity": "critical",
                "confidence": 92,
                "rms_current_a": rms,
                "peak_current_a": peak,
                "crest_factor": crest_factor,
                "thd": harmonics["thd"],
                "diagnosis": "Erratic current spikes & severe harmonic distortion detected. Possible micro-arcing or loose connection.",
            }

        # Check 2: Motor Bearing Degradation / Friction Wear
        if harmonics["h3_ratio"] > 0.15 or (crest_factor > 1.6 and harmonics["thd"] > 0.20):
            return {
                "state": "⚙️ MOTOR BEARING DEGRADATION",
                "severity": "warning",
                "confidence": 88,
                "rms_current_a": rms,
                "peak_current_a": peak,
                "crest_factor": crest_factor,
                "h3_ratio": harmonics["h3_ratio"],
                "diagnosis": "Elevated 3rd harmonic sideband and crest factor indicate bearing mechanical friction or rotor eccentricity.",
            }

        # Check 3: Normal Healthy Motor / Inductive Load
        if harmonics["h3_ratio"] > 0.05:
            return {
                "state": "🟢 HEALTHY INDUCTIVE MOTOR",
                "severity": "normal",
                "confidence": 95,
                "rms_current_a": rms,
                "peak_current_a": peak,
                "crest_factor": crest_factor,
                "diagnosis": "Standard sinusoidal waveform with expected mild inductive phase lag.",
            }

        # Check 4: Normal Pure Resistive Load (Kettle, Heater)
        return {
            "state": "🟢 PURE RESISTIVE LOAD (HEATER/KETTLE)",
            "severity": "normal",
            "confidence": 98,
            "rms_current_a": rms,
            "peak_current_a": peak,
            "crest_factor": crest_factor,
            "diagnosis": "Clean sinusoidal 50Hz waveform with near-zero harmonic distortion.",
        }
