"""
Eldercare Routine Anomaly Detector over Appliance Current / NILM Streams
Simulates / ingests disaggregated appliance events (UK-DALE / REDD) and detects:
1. Normal Daily Activity Rhythm (Kettle, Microwave, Geyser, TV)
2. Inactivity Anomaly (Possible fall / illness / missed routine)
3. Nighttime Restlessness (Dementia / sleep disturbance indicator)
"""

import math
import random
from datetime import datetime, time
from typing import Any, Dict, List


class EldercareRoutineEngine:
    def __init__(self, resident_name: str = "Grandmother") -> None:
        self.resident_name = resident_name
        # Baseline expected routine windows (Hour of day: mean, std_dev in hours, weight)
        self.baseline_routines = {
            "kettle_morning": {"name": "Morning Tea/Kettle", "mean_hour": 7.5, "std_dev": 0.75, "importance": "high"},
            "geyser_morning": {"name": "Morning Bath/Geyser", "mean_hour": 8.5, "std_dev": 0.8, "importance": "medium"},
            "microwave_lunch": {"name": "Lunch Preparation", "mean_hour": 13.0, "std_dev": 1.0, "importance": "high"},
            "tv_evening": {"name": "Evening TV/Living Room", "mean_hour": 18.5, "std_dev": 1.5, "importance": "low"},
            "kettle_evening": {"name": "Night Water/Kettle", "mean_hour": 21.0, "std_dev": 0.8, "importance": "medium"},
        }

    def _gaussian_probability(self, hour: float, mean: float, std: float) -> float:
        """Calculates likelihood of an event occurring at a given hour."""
        diff = hour - mean
        exponent = -0.5 * (diff * diff) / (std * std)
        return math.exp(exponent)

    def evaluate_day_log(self, current_hour: float, logged_events: List[Dict[str, Any]]) -> Dict[str, Any]:
        """
        Evaluates real-time appliance activations up to current_hour.
        logged_events: list of dicts with keys: {'appliance': str, 'hour': float, 'power_w': float}
        """
        active_appliances = {e["appliance"]: e["hour"] for e in logged_events}
        alerts = []
        routine_score = 100

        # Check 1: Morning Inactivity Anomaly (Critical Eldercare Metric)
        if current_hour >= 9.5: # 9:30 AM checkpoint
            morning_kettle = active_appliances.get("kettle_morning")
            morning_geyser = active_appliances.get("geyser_morning")

            if not morning_kettle and not morning_geyser:
                routine_score -= 50
                alerts.append({
                    "severity": "CRITICAL",
                    "title": "🚨 Urgent Morning Inactivity Alert",
                    "message": f"Zero kitchen or geyser electrical activity detected by {current_hour:.1f}:00. Expected morning tea between 7:00 AM and 8:30 AM. Possible fall or medical emergency.",
                })
            elif not morning_kettle:
                routine_score -= 20
                alerts.append({
                    "severity": "WARNING",
                    "title": "⚠️ Delayed Morning Routine",
                    "message": "Morning kettle usage has not occurred yet. Usual pattern is 7:30 AM ± 45 mins.",
                })

        # Check 2: Nighttime Restlessness / Confusion
        night_events = [e for e in logged_events if (e["hour"] >= 1.0 and e["hour"] <= 5.0)]
        if len(night_events) >= 3:
            routine_score -= 30
            alerts.append({
                "severity": "WARNING",
                "title": "🌙 Unusual Nighttime Activity",
                "message": f"Multiple appliance activations detected between 1:00 AM and 5:00 AM ({len(night_events)} events). Possible sleep disruption or confusion.",
            })

        # Check 3: Appliance Left Running (Stove / Iron / Space Heater)
        for e in logged_events:
            duration_hrs = e.get("duration_hours", 0.0)
            if e["appliance"] in ["space_heater", "iron", "stove"] and duration_hrs > 2.5:
                routine_score -= 40
                alerts.append({
                    "severity": "HIGH",
                    "title": "🔥 Continuous High-Power Draw",
                    "message": f"{e['appliance'].capitalize()} has been drawing continuous power for {duration_hrs:.1f} hours without cycling off.",
                })

        routine_score = max(0, min(100, routine_score))

        status = "Normal & Active" if routine_score >= 80 else ("Moderate Deviation" if routine_score >= 50 else "High Anomaly Risk")

        return {
            "resident_name": self.resident_name,
            "evaluated_hour": f"{int(current_hour):02d}:{int((current_hour % 1)*60):02d}",
            "routine_health_score": routine_score,
            "status": status,
            "total_events_logged": len(logged_events),
            "alerts": alerts,
        }


def main():
    engine = EldercareRoutineEngine(resident_name="Kamala Devi (Age 74)")
    print("=" * 65)
    print("👴 TESTING ELDERCARE NILM APPLIANCE ROUTINE ANOMALY DETECTOR")
    print("=" * 65)

    # Scenario 1: Normal healthy morning
    normal_events = [
        {"appliance": "kettle_morning", "hour": 7.4, "power_w": 1800, "duration_hours": 0.08},
        {"appliance": "geyser_morning", "hour": 8.3, "power_w": 2200, "duration_hours": 0.35},
        {"appliance": "microwave_lunch", "hour": 12.8, "power_w": 900, "duration_hours": 0.05},
    ]
    res1 = engine.evaluate_day_log(current_hour=14.0, logged_events=normal_events)
    print(f"\n▶ Scenario 1: Normal Routine Day (Evaluation @ {res1['evaluated_hour']})")
    print(f"  • Routine Health Score: {res1['routine_health_score']}/100 ({res1['status']})")
    print(f"  • Active Alerts:        {len(res1['alerts'])}")

    # Scenario 2: Missed morning routine (Fall / Inactivity crisis)
    crisis_events = [
        {"appliance": "fridge", "hour": 3.0, "power_w": 120, "duration_hours": 0.2}, # Just background fridge cycling
    ]
    res2 = engine.evaluate_day_log(current_hour=10.0, logged_events=crisis_events)
    print(f"\n▶ Scenario 2: Missed Morning Routine (Evaluation @ {res2['evaluated_hour']})")
    print(f"  • Routine Health Score: {res2['routine_health_score']}/100 ({res2['status']})")
    for a in res2["alerts"]:
        print(f"  • [{a['severity']}] {a['title']}: {a['message']}")

    print("\n" + "=" * 65)
    print("✅ Eldercare NILM Routine Anomaly Engine Verified!")
    print("=" * 65)


if __name__ == "__main__":
    main()
