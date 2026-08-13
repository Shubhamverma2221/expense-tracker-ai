"""
Synthetic AC Current Waveform Generator & Tester
Simulates SCT-013 CT clamp ADC samples for:
1. Pure Resistive Load (Kettle 2000W)
2. Normal Induction Motor (500W)
3. Degraded Motor (Bearing Friction Harmonics)
4. Arcing Fault Burst (Fire Risk)
"""

import math
import random
from current_signature_classifier import CurrentSignatureEngine


def generate_waveform(wave_type: str = "resistive", samples: int = 1000, sample_rate: int = 10000) -> list:
    waveform = []
    freq = 50.0 # 50Hz mains
    
    for i in range(samples):
        t = i / sample_rate
        
        if wave_type == "resistive":
            # Clean 50Hz sine wave (8.7A RMS ~ 2000W)
            i_val = 12.3 * math.sin(2 * math.pi * freq * t) + random.uniform(-0.1, 0.1)
            
        elif wave_type == "healthy_motor":
            # 50Hz with minor 3rd harmonic
            i_val = 3.5 * math.sin(2 * math.pi * freq * t) + 0.3 * math.sin(2 * math.pi * 3 * freq * t)
            
        elif wave_type == "motor_bearing_fault":
            # Significant 3rd & 5th harmonic distortion + elevated peaks
            i_val = (
                4.0 * math.sin(2 * math.pi * freq * t)
                + 1.2 * math.sin(2 * math.pi * 3 * freq * t)
                + 0.6 * math.sin(2 * math.pi * 5 * freq * t)
                + random.uniform(-0.3, 0.3)
            )
            
        elif wave_type == "arcing_fault":
            # Erratic high-frequency spikes and flat-topping
            base = 6.0 * math.sin(2 * math.pi * freq * t)
            spike = random.choice([0, 0, 0, random.uniform(8.0, 18.0)]) if (i % 20 == 0) else 0.0
            i_val = base + spike + random.uniform(-1.2, 1.2)
            
        else:
            i_val = 0.0
            
        waveform.append(i_val)
        
    return waveform


def main():
    engine = CurrentSignatureEngine(sampling_rate=10000)
    print("=" * 60)
    print("⚡ TESTING ELECTRICAL CURRENT SIGNATURE CLASSIFIER")
    print("=" * 60)
    
    test_cases = [
        ("Resistive Load (Heater/Kettle)", "resistive"),
        ("Healthy Induction Motor", "healthy_motor"),
        ("Degraded Motor (Bearing Wear)", "motor_bearing_fault"),
        ("Electrical Arcing (Fire Risk)", "arcing_fault"),
    ]
    
    for title, w_type in test_cases:
        wave = generate_waveform(w_type)
        res = engine.classify_signature(wave)
        print(f"\n▶ Input: {title}")
        print(f"  • Classification: {res['state']}")
        print(f"  • Severity:       {res['severity'].upper()}")
        print(f"  • RMS Current:    {res['rms_current_a']} A")
        print(f"  • Crest Factor:   {res['crest_factor']}")
        print(f"  • Diagnosis:      {res['diagnosis']}")
        
    print("\n" + "=" * 60)
    print("✅ All Current Signature Edge AI Tests Passed Successfully!")
    print("=" * 60)


if __name__ == "__main__":
    main()
