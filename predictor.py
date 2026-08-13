from __future__ import annotations

import math
from datetime import date
from typing import Any, Dict, List, Optional, Tuple

from db import ExpenseDB


def _linear_regression(x_values: List[float], y_values: List[float]) -> Tuple[float, float, float]:
    """Simple least squares linear regression.

    Returns (slope, intercept, r_squared) for y = slope * x + intercept.
    """
    n = float(len(x_values))
    if n == 0:
        return 0.0, 0.0, 0.0
    x_mean = sum(x_values) / n
    y_mean = sum(y_values) / n
    denom = sum((x - x_mean) ** 2 for x in x_values)
    if denom == 0:
        return 0.0, y_mean, 0.0
    slope = sum((x - x_mean) * (y - y_mean) for x, y in zip(x_values, y_values)) / denom
    intercept = y_mean - slope * x_mean

    # Compute R-squared (coefficient of determination)
    total_variance = sum((y - y_mean) ** 2 for y in y_values)
    if total_variance == 0:
        r_squared = 1.0
    else:
        residual_variance = sum((y - (slope * x + intercept)) ** 2 for x, y in zip(x_values, y_values))
        r_squared = max(0.0, min(1.0, 1.0 - (residual_variance / total_variance)))

    return slope, intercept, r_squared


class Predictor:
    def __init__(self, db: ExpenseDB) -> None:
        self.db = db

    def predict_next_month(self, months_back: int = 6, user_id: Optional[int] = None) -> Dict[str, Any]:
        """Predict the next month's total and per-category spend with confidence & trend metrics."""
        monthly_totals = self.db.monthly_totals(user_id=user_id)
        if not monthly_totals:
            return {
                "total_next_month": 0.0,
                "per_category_next_month": {},
                "historical_months": [],
                "historical_values": [],
                "predicted_month_label": "Next Month",
                "trend": "Stable",
                "trend_pct": 0.0,
                "confidence_score": 50,
            }

        keys_sorted = sorted(monthly_totals.keys())
        lookback = max(2, min(months_back, len(keys_sorted)))
        keys_recent = keys_sorted[-lookback:]
        y_values = [max(0.0, float(monthly_totals[k])) for k in keys_recent]
        x_values = list(range(len(keys_recent)))

        slope, intercept, r_squared = _linear_regression(x_values, y_values)
        next_x = len(x_values)
        regressed_total = slope * next_x + intercept

        recent_avg = sum(y_values) / float(len(y_values)) if y_values else 0.0
        total_next = regressed_total if regressed_total >= 0 else recent_avg
        total_next = max(0.0, float(total_next))

        # Trend analysis
        last_actual = y_values[-1] if y_values else 0.0
        trend_pct = 0.0
        if last_actual > 0:
            trend_pct = round(((total_next - last_actual) / last_actual) * 100, 1)

        if trend_pct > 3.0:
            trend = "Increasing"
        elif trend_pct < -3.0:
            trend = "Decreasing"
        else:
            trend = "Stable"

        confidence_score = int(max(40, min(95, r_squared * 100)))

        # Determine next month label
        last_ym = keys_recent[-1]
        try:
            year, mon = map(int, last_ym.split("-"))
            if mon == 12:
                next_ym = f"{year + 1}-01"
            else:
                next_ym = f"{year}-{mon + 1:02d}"
        except Exception:
            next_ym = "Next Month"

        # Per-category projection
        by_cat = self.db.monthly_totals_by_category(user_id=user_id)
        per_category_next: Dict[str, float] = {}

        if by_cat:
            categories: set[str] = set()
            for k in keys_recent:
                month_map = by_cat.get(k, {})
                categories.update(month_map.keys())

            for category in sorted(categories):
                y_cat = [float(by_cat.get(k, {}).get(category, 0.0)) for k in keys_recent]
                x_cat = x_values
                if sum(y_cat) == 0:
                    continue
                s, b, _ = _linear_regression(x_cat, y_cat)
                cat_pred = s * len(x_cat) + b
                if cat_pred < 0:
                    cat_pred = sum(y_cat) / float(len(y_cat))
                per_category_next[category] = round(max(0.0, float(cat_pred)), 2)

        if not per_category_next:
            last_key = keys_recent[-1]
            last_cats = by_cat.get(last_key, {}) if by_cat else {}
            last_total = sum(last_cats.values()) or 1.0
            for category, cat_total in last_cats.items():
                per_category_next[category] = round(total_next * (float(cat_total) / float(last_total)), 2)

        return {
            "total_next_month": round(total_next, 2),
            "per_category_next_month": per_category_next,
            "historical_months": keys_recent,
            "historical_values": [round(v, 2) for v in y_values],
            "predicted_month_label": next_ym,
            "trend": trend,
            "trend_pct": trend_pct,
            "confidence_score": confidence_score,
            "recent_average": round(recent_avg, 2),
        }

    def simulate_savings_target(self, target_savings_pct: float, user_id: Optional[int] = None) -> Dict[str, Any]:
        """Calculates recommended category budget caps to achieve a target savings percentage."""
        forecast = self.predict_next_month(months_back=6, user_id=user_id)
        total_pred = forecast["total_next_month"]
        target_spend = max(0.0, total_pred * (1.0 - (target_savings_pct / 100.0)))
        savings_amount = total_pred - target_spend

        # Recommend cuts primarily on discretionary categories (Shopping, Entertainment, Food)
        discretionary_weights = {
            "Shopping": 0.40,
            "Entertainment": 0.30,
            "Food": 0.20,
            "Travel": 0.10,
        }

        recommendations: Dict[str, Dict[str, float]] = {}
        for cat, current_pred in forecast["per_category_next_month"].items():
            weight = discretionary_weights.get(cat, 0.05)
            suggested_cut = min(current_pred * 0.4, savings_amount * weight)
            new_target = max(0.0, current_pred - suggested_cut)
            recommendations[cat] = {
                "current_forecast": current_pred,
                "suggested_cap": round(new_target, 2),
                "potential_savings": round(suggested_cut, 2),
            }

        return {
            "current_forecast_total": total_pred,
            "target_spend": round(target_spend, 2),
            "target_savings_amount": round(savings_amount, 2),
            "recommendations": recommendations,
        }
