from __future__ import annotations

import math
import re
from datetime import date, datetime, timedelta
from typing import Any, Dict, List, Optional, Tuple

from categorizer import CategoryRules
from db import ExpenseDB
from predictor import Predictor


class FinancialIntelligenceEngine:
    def __init__(self, db: ExpenseDB, rules: Optional[CategoryRules] = None) -> None:
        self.db = db
        self.rules = rules or CategoryRules(path="categories.json")
        self.predictor = Predictor(db)

    # ==================== 1. AI FINANCIAL HEALTH SCORE (0 - 100) ====================

    def calculate_health_score(self, user_id: int) -> Dict[str, Any]:
        """Calculates a comprehensive 0-100 financial health and stability score."""
        user = self.db.get_user_by_id(user_id)
        income = float(user.get("income", 50000.0)) if user else 50000.0
        monthly_budget = float(user.get("monthly_budget", 25000.0)) if user else 25000.0

        summary = self.db.get_summary(period="month", user_id=user_id)
        total_spent = summary["total"]
        by_category = summary["by_category"]

        # Factor 1: Savings Rate (0 - 25 points)
        # Target: Saving >= 20% of income earns full 25 points
        savings_amount = max(0.0, income - total_spent)
        savings_rate = (savings_amount / income) if income > 0 else 0.0
        savings_score = min(25.0, max(0.0, (savings_rate / 0.25) * 25.0))

        # Factor 2: Budget Adherence (0 - 25 points)
        # Based on overall budget and category budgets
        budgets = self.db.get_user_budgets(user_id)
        if budgets:
            adherent_count = sum(1 for b in budgets if not b["is_exceeded"])
            budget_score = (adherent_count / len(budgets)) * 25.0
        else:
            budget_utilization = (total_spent / monthly_budget) if monthly_budget > 0 else 1.0
            if budget_utilization <= 0.8:
                budget_score = 25.0
            elif budget_utilization <= 1.0:
                budget_score = 20.0
            elif budget_utilization <= 1.15:
                budget_score = 10.0
            else:
                budget_score = 4.0

        # Factor 3: Spending Volatility / Consistency (0 - 20 points)
        # Checks standard deviation of daily spending
        daily_totals = list(self.db.get_daily_totals_current_month(user_id).values())
        if len(daily_totals) >= 3:
            avg_daily = sum(daily_totals) / len(daily_totals)
            variance = sum((x - avg_daily) ** 2 for x in daily_totals) / len(daily_totals)
            std_dev = math.sqrt(variance)
            cv = (std_dev / avg_daily) if avg_daily > 0 else 0.0
            # Lower coefficient of variation = more consistent habits
            if cv < 0.6:
                volatility_score = 20.0
            elif cv < 1.0:
                volatility_score = 16.0
            elif cv < 1.5:
                volatility_score = 11.0
            else:
                volatility_score = 6.0
        else:
            volatility_score = 15.0

        # Factor 4: Essential vs Discretionary Ratio (0 - 15 points)
        # 50/30 Rule: Needs (Food, Bills, Health, Education) vs Wants (Shopping, Entertainment, Other)
        essential_cats = {"food", "bills", "health", "education"}
        essential_spend = sum(amt for cat, amt in by_category.items() if cat.lower() in essential_cats)
        discretionary_spend = total_spent - essential_spend
        disc_ratio = (discretionary_spend / total_spent) if total_spent > 0 else 0.3
        if disc_ratio <= 0.35:
            ratio_score = 15.0
        elif disc_ratio <= 0.50:
            ratio_score = 11.0
        elif disc_ratio <= 0.65:
            ratio_score = 7.0
        else:
            ratio_score = 3.0

        # Factor 5: Recurring Bill Burden (0 - 15 points)
        recurring = self.db.list_recurring(user_id)
        total_recurring = sum(r["amount"] for r in recurring if r["is_active"])
        rec_ratio = (total_recurring / income) if income > 0 else 0.2
        if rec_ratio <= 0.25:
            recurring_score = 15.0
        elif rec_ratio <= 0.40:
            recurring_score = 10.0
        else:
            recurring_score = 5.0

        total_health_score = int(round(savings_score + budget_score + volatility_score + ratio_score + recurring_score))
        total_health_score = max(10, min(99, total_health_score))

        if total_health_score >= 80:
            status = "Strong & Stable"
            status_color = "var(--accent)"
            grade = "A"
            diagnosis = "Excellent financial discipline. High savings rate with controlled discretionary outflow."
        elif total_health_score >= 65:
            status = "Healthy"
            status_color = "#38bdf8"
            grade = "B"
            diagnosis = "Good financial posture. Keep an eye on non-essential impulse spending."
        elif total_health_score >= 50:
            status = "Moderate Risk"
            status_color = "var(--warning)"
            grade = "C"
            diagnosis = "Spending is approaching safe thresholds. Review subscriptions and top category limits."
        else:
            status = "High Vulnerability"
            status_color = "var(--danger)"
            grade = "D"
            diagnosis = "Outflow is exceeding planned limits. Implement immediate discretionary spending caps."

        return {
            "score": total_health_score,
            "status": status,
            "status_color": status_color,
            "grade": grade,
            "diagnosis": diagnosis,
            "savings_rate_pct": round(savings_rate * 100, 1),
            "factors": {
                "savings_rate": {"score": round(savings_score, 1), "max": 25, "label": "Savings Accumulation"},
                "budget_adherence": {"score": round(budget_score, 1), "max": 25, "label": "Budget Adherence"},
                "volatility": {"score": round(volatility_score, 1), "max": 20, "label": "Spending Consistency"},
                "needs_vs_wants": {"score": round(ratio_score, 1), "max": 15, "label": "Essential vs Discretionary Ratio"},
                "recurring_burden": {"score": round(recurring_score, 1), "max": 15, "label": "Fixed Obligations Burden"},
            },
        }

    # ==================== 2. "CAN I AFFORD THIS?" REAL-TIME ENGINE ====================

    def check_affordability(self, user_id: int, item_name: str, amount: float, category: Optional[str] = None) -> Dict[str, Any]:
        """Evaluates whether a user can safely afford a proposed purchase."""
        if not category:
            category = self.rules.categorize(item_name)

        user = self.db.get_user_by_id(user_id)
        monthly_budget = float(user.get("monthly_budget", 25000.0)) if user else 25000.0
        income = float(user.get("income", 50000.0)) if user else 50000.0
        curr = user.get("currency", "₹") if user else "₹"

        # Current month metrics
        summary = self.db.get_summary(period="month", user_id=user_id)
        total_spent = summary["total"]
        category_spent = summary["by_category"].get(category, 0.0)

        # Days remaining in month
        today = date.today()
        days_in_month = (date(today.year, today.month + 1, 1) - timedelta(days=1)).day if today.month < 12 else 31
        days_left = max(1, days_in_month - today.day + 1)

        # Upcoming committed bills in remainder of month
        recurring = self.db.list_recurring(user_id)
        upcoming_bills = 0.0
        for r in recurring:
            if r["is_active"]:
                try:
                    due_d = date.fromisoformat(r["next_due_date"])
                    if due_d >= today and due_d.month == today.month:
                        upcoming_bills += float(r["amount"])
                except Exception:
                    pass

        # Discretionary remaining buffer
        safe_pool = max(0.0, monthly_budget - total_spent - upcoming_bills)
        safe_daily_before = safe_pool / days_left
        safe_pool_after = max(0.0, safe_pool - amount)
        safe_daily_after = safe_pool_after / days_left

        # Category Budget Check
        budgets = self.db.get_user_budgets(user_id)
        cat_budget = next((b for b in budgets if b["category"].lower() == category.lower()), None)
        cat_limit = cat_budget["limit_amount"] if cat_budget else (monthly_budget * 0.3)
        cat_spent_after = category_spent + amount
        cat_exceeded = cat_spent_after > cat_limit

        # Affordability Decision Logic
        if amount <= (safe_pool * 0.35) and not cat_exceeded:
            verdict = "Affordable ✅"
            verdict_badge = "success"
            risk_score = 15
            recommendation = f"This purchase fits comfortably within your monthly budget buffer. You will still have {curr}{safe_pool_after:.2f} safe discretionary funds remaining."
        elif amount <= safe_pool and (cat_spent_after <= cat_limit * 1.1):
            verdict = "Caution ⚠️"
            verdict_badge = "warning"
            risk_score = 55
            recommendation = f"This purchase is feasible today, but it consumes {round((amount/safe_pool)*100)}% of your remaining buffer. Your safe daily allowance will reduce from {curr}{safe_daily_before:.0f}/day to {curr}{safe_daily_after:.0f}/day."
        else:
            verdict = "High Financial Risk 🚨"
            verdict_badge = "danger"
            risk_score = 88
            shortfall = amount - safe_pool
            recommendation = f"This purchase would exceed your safe discretionary pool by {curr}{shortfall:.2f} and risk a cash-flow shortage against upcoming bills. Consider delaying or saving for it."

        return {
            "item_name": item_name,
            "amount": amount,
            "category": category,
            "verdict": verdict,
            "verdict_badge": verdict_badge,
            "risk_score": risk_score,
            "safe_pool_before": round(safe_pool, 2),
            "safe_pool_after": round(safe_pool_after, 2),
            "safe_daily_before": round(safe_daily_before, 2),
            "safe_daily_after": round(safe_daily_after, 2),
            "upcoming_bills": round(upcoming_bills, 2),
            "days_left": days_left,
            "recommendation": recommendation,
        }

    # ==================== 3. CASH-FLOW COLLISION & BILL TIMING PREDICTOR ====================

    def detect_cashflow_collisions(self, user_id: int) -> List[Dict[str, Any]]:
        """Projects day-by-day cash balance across the month and flags bill shortfall collisions."""
        user = self.db.get_user_by_id(user_id)
        monthly_budget = float(user.get("monthly_budget", 25000.0)) if user else 25000.0
        income = float(user.get("income", 50000.0)) if user else 50000.0
        curr = user.get("currency", "₹") if user else "₹"

        summary = self.db.get_summary(period="month", user_id=user_id)
        total_spent = summary["total"]
        today = date.today()
        avg_daily_burn = summary["daily_average"] if summary["daily_average"] > 0 else 400.0

        recurring = self.db.list_recurring(user_id)
        active_bills = [r for r in recurring if r["is_active"]]

        collisions: List[Dict[str, Any]] = []

        # Current estimated liquid pool
        current_pool = max(0.0, monthly_budget - total_spent)

        # Iterate forward over next 20 days
        running_pool = current_pool
        for day_offset in range(1, 21):
            future_date = today + timedelta(days=day_offset)
            running_pool -= avg_daily_burn

            # Check bills due on this day
            bills_today = []
            for b in active_bills:
                try:
                    due = date.fromisoformat(b["next_due_date"])
                    if due.day == future_date.day and due.month == future_date.month:
                        bills_today.append(b)
                except Exception:
                    pass

            for bill in bills_today:
                bill_amt = float(bill["amount"])
                if running_pool < bill_amt:
                    shortfall = bill_amt - max(0.0, running_pool)
                    collisions.append({
                        "date": future_date.isoformat(),
                        "day_name": future_date.strftime("%A, %d %B"),
                        "days_until": day_offset,
                        "bill_name": bill["description"],
                        "bill_amount": bill_amt,
                        "projected_pool": round(max(0.0, running_pool), 2),
                        "shortfall": round(shortfall, 2),
                        "severity": "high" if shortfall > 2000 else "medium",
                        "message": f"Projected balance ({curr}{max(0.0, running_pool):.0f}) may not cover {bill['description']} ({curr}{bill_amt:.0f}) on {future_date.strftime('%b %d')}. Estimated shortfall: {curr}{shortfall:.0f}.",
                    })
                running_pool -= bill_amt

        return collisions

    # ==================== 4. MONEY LEAK DETECTOR (< ₹300 MICRO-SPENDS) ====================

    def detect_money_leaks(self, user_id: int, threshold: float = 300.0) -> Dict[str, Any]:
        """Audits micro-expenses that silently drain user wealth."""
        user = self.db.get_user_by_id(user_id)
        curr = user.get("currency", "₹") if user else "₹"

        # Fetch current month expenses
        today = date.today()
        first_day = today.replace(day=1).isoformat()
        expenses = self.db.list_expenses(user_id=user_id, start_date=first_day, limit=500)

        micro_spends = [e for e in expenses if float(e["amount"]) <= threshold]
        total_micro_amount = sum(float(e["amount"]) for e in micro_spends)
        total_month_spend = sum(float(e["amount"]) for e in expenses)

        leak_pct = (total_micro_amount / total_month_spend * 100) if total_month_spend > 0 else 0.0

        # Group by category
        cat_leaks: Dict[str, float] = {}
        for e in micro_spends:
            c = e["category"]
            cat_leaks[c] = cat_leaks.get(c, 0.0) + float(e["amount"])

        sorted_cats = sorted(cat_leaks.items(), key=lambda x: -x[1])
        top_leak_category = sorted_cats[0][0] if sorted_cats else "None"

        # Projected annual micro-spend drain
        annual_projected = total_micro_amount * 12

        return {
            "micro_spend_count": len(micro_spends),
            "total_micro_amount": round(total_micro_amount, 2),
            "leak_pct": round(leak_pct, 1),
            "top_leak_category": top_leak_category,
            "annual_projected": round(annual_projected, 2),
            "leak_items": micro_spends[:10],
            "recommendation": f"You made {len(micro_spends)} small purchases below {curr}{threshold:.0f} this month totaling {curr}{total_micro_amount:.2f} ({round(leak_pct)}% of total spend). Cutting 30% of these small convenience fees could save {curr}{annual_projected * 0.3:.0f}/year.",
        }

    # ==================== 5. SAFE DAILY SPENDING LIMIT ====================

    def calculate_safe_daily_spend(self, user_id: int) -> Dict[str, Any]:
        """Calculates dynamic real-time safe daily allowance for the remainder of the month."""
        user = self.db.get_user_by_id(user_id)
        monthly_budget = float(user.get("monthly_budget", 25000.0)) if user else 25000.0
        curr = user.get("currency", "₹") if user else "₹"

        summary = self.db.get_summary(period="month", user_id=user_id)
        total_spent = summary["total"]

        today = date.today()
        days_in_month = (date(today.year, today.month + 1, 1) - timedelta(days=1)).day if today.month < 12 else 31
        days_left = max(1, days_in_month - today.day + 1)

        recurring = self.db.list_recurring(user_id)
        upcoming_bills = sum(float(r["amount"]) for r in recurring if r["is_active"])

        safe_pool = max(0.0, monthly_budget - total_spent - (upcoming_bills * 0.5))
        safe_daily = safe_pool / days_left

        return {
            "safe_daily_spend": round(safe_daily, 2),
            "safe_pool": round(safe_pool, 2),
            "days_left": days_left,
            "monthly_budget": monthly_budget,
            "total_spent": round(total_spent, 2),
        }

    # ==================== 6. FINANCIAL "WHAT-IF" SIMULATOR ====================

    def simulate_what_if(self, user_id: int, scenario_type: str, params: Dict[str, Any]) -> Dict[str, Any]:
        """Simulates multi-month financial outcomes for potential decisions."""
        user = self.db.get_user_by_id(user_id)
        monthly_budget = float(user.get("monthly_budget", 25000.0)) if user else 25000.0
        income = float(user.get("income", 50000.0)) if user else 50000.0
        curr = user.get("currency", "₹") if user else "₹"

        forecast = self.predictor.predict_next_month(user_id=user_id)
        baseline_monthly_spend = forecast["total_next_month"] or 20000.0
        baseline_monthly_savings = max(0.0, income - baseline_monthly_spend)

        months = [f"Month {i}" for i in range(1, 7)]

        if scenario_type == "major_purchase":
            purchase_cost = float(params.get("amount", 50000.0))
            item_name = params.get("item_name", "Major Purchase")

            baseline_trajectory = [baseline_monthly_savings * i for i in range(1, 7)]
            revised_trajectory = [max(0.0, (baseline_monthly_savings * i) - purchase_cost) for i in range(1, 7)]

            delay_months = round(purchase_cost / baseline_monthly_savings, 1) if baseline_monthly_savings > 0 else 6.0

            return {
                "scenario_title": f"Purchase Impact: {item_name} ({curr}{purchase_cost:,.0f})",
                "months": months,
                "baseline_trajectory": [round(x, 2) for x in baseline_trajectory],
                "revised_trajectory": [round(x, 2) for x in revised_trajectory],
                "savings_delay": f"This purchase will set back your savings accumulation by approximately {delay_months} months.",
                "monthly_impact": f"Month 1 net savings will decrease from {curr}{baseline_monthly_savings:,.0f} to {curr}{max(0.0, baseline_monthly_savings - purchase_cost):,.0f}.",
            }

        elif scenario_type == "habit_change":
            # e.g. reduce orders / save daily
            daily_save = float(params.get("daily_savings", 100.0))
            monthly_gain = daily_save * 30.0
            annual_gain = daily_save * 365.0

            baseline_trajectory = [baseline_monthly_savings * i for i in range(1, 7)]
            revised_trajectory = [(baseline_monthly_savings + monthly_gain) * i for i in range(1, 7)]

            return {
                "scenario_title": f"Habit Change: Save {curr}{daily_save:.0f}/day",
                "months": months,
                "baseline_trajectory": [round(x, 2) for x in baseline_trajectory],
                "revised_trajectory": [round(x, 2) for x in revised_trajectory],
                "savings_delay": f"Saving {curr}{daily_save:.0f}/day yields an extra {curr}{monthly_gain:,.0f}/month and {curr}{annual_gain:,.0f}/year.",
                "monthly_impact": f"Increases your monthly savings rate by {round((monthly_gain/income)*100, 1)}%.",
            }

        else:
            # Fixed cost increase (e.g. rent increase)
            cost_increase = float(params.get("cost_increase", 2000.0))
            revised_monthly_savings = max(0.0, baseline_monthly_savings - cost_increase)

            baseline_trajectory = [baseline_monthly_savings * i for i in range(1, 7)]
            revised_trajectory = [revised_monthly_savings * i for i in range(1, 7)]

            return {
                "scenario_title": f"Cost Increase: +{curr}{cost_increase:.0f}/month",
                "months": months,
                "baseline_trajectory": [round(x, 2) for x in baseline_trajectory],
                "revised_trajectory": [round(x, 2) for x in revised_trajectory],
                "savings_delay": f"Reduces your 6-month wealth creation by {curr}{cost_increase * 6:,.0f}.",
                "monthly_impact": f"Reduces projected monthly savings from {curr}{baseline_monthly_savings:,.0f} to {curr}{revised_monthly_savings:,.0f}.",
            }

    # ==================== 7. SMART RECEIPT TEXT PARSER ====================

    def parse_receipt_text(self, text: str) -> Dict[str, Any]:
        """Extracts merchant, total, date, and suggested category from receipt snippet."""
        clean_text = text.strip()

        # Extract Total Amount: Match patterns like "Total: 1250.00", "Grand Total INR 450", "Amount: 890"
        total = 0.0
        total_patterns = [
            r"(?:total|grand\s+total|amount\s+due|net\s+amount|bill\s+amount)[\s:=₹rs\.]*([\d,]+\.?\d*)",
            r"(?:₹|rs\.?)\s*([\d,]+\.?\d*)",
            r"([\d,]+\.\d{2})",
        ]
        for pat in total_patterns:
            m = re.search(pat, clean_text, re.IGNORECASE)
            if m:
                try:
                    total = float(m.group(1).replace(",", ""))
                    if total > 0:
                        break
                except Exception:
                    pass

        # Extract Date
        trans_date = date.today().isoformat()
        date_m = re.search(r"(\d{4}[-/]\d{2}[-/]\d{2}|\d{2}[-/]\d{2}[-/]\d{4})", clean_text)
        if date_m:
            raw_d = date_m.group(1).replace("/", "-")
            try:
                if len(raw_d.split("-")[0]) == 4:
                    trans_date = raw_d
                else:
                    parts = raw_d.split("-")
                    trans_date = f"{parts[2]}-{parts[1]}-{parts[0]}"
            except Exception:
                pass

        # Extract Merchant / Store (First non-empty line or common store)
        lines = [l.strip() for l in clean_text.splitlines() if l.strip()]
        merchant = lines[0] if lines else "Store Receipt"
        if len(merchant) > 35:
            merchant = merchant[:35]

        category = self.rules.categorize(clean_text)

        return {
            "merchant": merchant,
            "amount": round(total, 2) if total > 0 else 0.0,
            "date": trans_date,
            "category": category,
            "raw_text": clean_text,
        }
