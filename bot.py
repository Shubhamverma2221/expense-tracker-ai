import os
import random
import re
from datetime import date, datetime, timedelta
from typing import Any, Dict, List, Optional

from categorizer import CategoryRules
from db import ExpenseDB
from financial_engine import FinancialIntelligenceEngine
from predictor import Predictor

try:
    from google import genai
    GENAI_AVAILABLE = True
except ImportError:
    GENAI_AVAILABLE = False


class ChatBot:
    def __init__(self, db: ExpenseDB, rules: Optional[CategoryRules] = None, user_id: int = 1, currency: str = "₹") -> None:
        self.db = db
        self.rules = rules or CategoryRules(path="categories.json")
        self.predictor = Predictor(db)
        self.engine = FinancialIntelligenceEngine(db, self.rules)
        self.user_id = user_id
        self.currency = currency

        # Initialize Gemini Client if API key is available
        self.gemini_client = None
        gemini_api_key = os.environ.get("GEMINI_API_KEY", "").strip()
        if GENAI_AVAILABLE and gemini_api_key:
            try:
                self.gemini_client = genai.Client(api_key=gemini_api_key)
            except Exception as e:
                print("Gemini client initialization notice:", e)

    # ==================== 1. AFFORDABILITY & "CAN I AFFORD THIS?" ====================

    def _parse_affordability_intent(self, text: str) -> Optional[str]:
        """Detects queries like 'Can I afford shoes for 3500?', 'Should I buy iPhone for 75000?'"""
        lowered = text.lower()
        if not any(k in lowered for k in ["can i afford", "should i buy", "can i buy", "afford to buy", "can i get", "is it safe to buy"]):
            return None

        amt_match = re.search(r"(?:₹|rs\.?|\$|€|£)?\s*(\d+(?:,\d{3})*(?:\.\d{1,2})?)", lowered)
        if not amt_match:
            return "💡 Please include the item price (e.g. *'Can I afford shoes for ₹3,500?'*)."

        amount = float(amt_match.group(1).replace(",", ""))

        item_name = "Purchase Item"
        item_match = re.search(r"(?:buy|afford|get)\s+(?:a|an|the)?\s*(.+?)\s+(?:for|costing|worth|at|priced)\s+(?:₹|rs\.?|\$|€|£)?\s*\d+", text, re.IGNORECASE)
        if item_match:
            item_name = item_match.group(1).strip()
        else:
            item_name = re.sub(r"(?:can i afford|should i buy|can i buy|for|₹|rs|\d+)", "", text, flags=re.IGNORECASE).strip() or "Item"

        res = self.engine.check_affordability(user_id=self.user_id, item_name=item_name, amount=amount)

        out = [
            f"### 🔍 Affordability Decision: **{res['item_name']}** ({self.currency}{amount:,.2f})",
            f"• **Verdict:** **{res['verdict']}** (Risk Score: {res['risk_score']}/100)",
            f"• **Category:** {res['category']}",
            f"• **Discretionary Buffer Before:** {self.currency}{res['safe_pool_before']:,.2f}",
            f"• **Discretionary Buffer After:** {self.currency}{res['safe_pool_after']:,.2f}",
            f"• **Safe Daily Allowance Impact:** {self.currency}{res['safe_daily_before']:,.0f}/day ➔ **{self.currency}{res['safe_daily_after']:,.0f}/day**",
            f"\n💡 **Recommendation:**\n{res['recommendation']}",
        ]
        return "\n".join(out)

    # ==================== 2. FINANCIAL HEALTH SCORE ====================

    def _parse_health_score_intent(self, text: str) -> Optional[str]:
        lowered = text.lower()
        if not any(k in lowered for k in ["health score", "financial health", "my score", "financial stability", "financial rating"]):
            return None

        h = self.engine.calculate_health_score(self.user_id)
        out = [
            f"### 🧠 AI Financial Health Rating: **{h['score']}/100** ({h['status']} - Grade {h['grade']})",
            f"• **Savings Rate:** {h['savings_rate_pct']}%",
            f"• **Diagnosis:** {h['diagnosis']}",
            f"\n**Factor Breakdown:**",
        ]
        for _, f in h["factors"].items():
            out.append(f"• {f['label']}: **{f['score']}/{f['max']} pts**")

        return "\n".join(out)

    # ==================== 3. MONEY LEAK DETECTOR ====================

    def _parse_money_leaks_intent(self, text: str) -> Optional[str]:
        lowered = text.lower()
        if not any(k in lowered for k in ["money leak", "leaking money", "small expenses", "micro spend", "hidden leak", "micro-spend"]):
            return None

        leaks = self.engine.detect_money_leaks(self.user_id)
        out = [
            f"### 🕵️ Money Leak Audit (< {self.currency}300 Micro-Spends)",
            f"• **Micro-Transactions Logged:** {leaks['micro_spend_count']} purchases",
            f"• **Total Micro-Spend This Month:** {self.currency}{leaks['total_micro_amount']:,.2f} ({leaks['leak_pct']}% of total)",
            f"• **Top Leak Category:** 🏷️ {leaks['top_leak_category']}",
            f"• **Projected Annual Drain:** {self.currency}{leaks['annual_projected']:,.2f}/year",
            f"\n💡 **Optimization Strategy:**\n{leaks['recommendation']}",
        ]
        return "\n".join(out)

    # ==================== 4. SAFE DAILY SPENDING LIMIT ====================

    def _parse_safe_daily_intent(self, text: str) -> Optional[str]:
        lowered = text.lower()
        if not any(k in lowered for k in ["safe daily", "spend today", "daily limit", "daily allowance", "how much can i spend"]):
            return None

        s = self.engine.calculate_safe_daily_spend(self.user_id)
        return (
            f"### 💰 Today's Safe-to-Spend Allowance: **{self.currency}{s['safe_daily_spend']:,.2f} / day**\n"
            f"• **Billing Cycle Remaining:** {s['days_left']} days\n"
            f"• **Discretionary Liquid Buffer:** {self.currency}{s['safe_pool']:,.2f}\n"
            f"• **Committed Upcoming Bills:** {self.currency}{s['upcoming_bills']:,.2f}\n"
            f"\n🛡️ *Spending at or below this run-rate ensures you comfortably meet upcoming recurring bills.*"
        )

    # ==================== 5. CASH-FLOW COLLISION ====================

    def _parse_cashflow_collision_intent(self, text: str) -> Optional[str]:
        lowered = text.lower()
        if not any(k in lowered for k in ["collision", "upcoming bill", "bill risk", "shortfall", "upcoming expense"]):
            return None

        alerts = self.engine.detect_cashflow_collisions(self.user_id)
        if not alerts:
            return "✅ **No Cash-Flow Collisions Detected!** Your liquid buffer is projected to comfortably cover all upcoming scheduled bills."

        a = alerts[0]
        return (
            f"### 🚨 Cash-Flow Collision Warning on **{a['day_name']}**\n"
            f"• **Scheduled Bill:** {a['bill_name']} ({self.currency}{a['bill_amount']:,.2f})\n"
            f"• **Projected Shortfall:** **{self.currency}{a['shortfall']:,.2f}**\n"
            f"• **Days Remaining:** {a['days_remaining']} days\n"
            f"\n💡 *Recommendation:* Implement a discretionary spending freeze until this bill is settled."
        )

    # ==================== 6. WHAT-IF SCENARIOS ====================

    def _parse_what_if_intent(self, text: str) -> Optional[str]:
        lowered = text.lower()
        if not lowered.startswith("what if"):
            return None

        amt_match = re.search(r"(?:₹|rs\.?|\$)?\s*(\d+(?:,\d{3})*)", lowered)
        amount = float(amt_match.group(1).replace(",", "")) if amt_match else 50000

        if any(k in lowered for k in ["save", "saving", "daily"]):
            daily = amount if amount < 1000 else 100
            res = self.engine.simulate_what_if(self.user_id, "habit_change", {"daily_savings": daily})
        elif any(k in lowered for k in ["rent", "cost", "increase"]):
            res = self.engine.simulate_what_if(self.user_id, "fixed_cost", {"cost_increase": amount})
        else:
            res = self.engine.simulate_what_if(self.user_id, "major_purchase", {"amount": amount, "item_name": "Major Purchase"})

        return (
            f"### 🧪 Simulation Result: {res['scenario_title']}\n"
            f"• **Savings Impact:** {res['savings_delay']}\n"
            f"• **Monthly Shift:** {res['monthly_impact']}\n"
            f"\n📊 *View full 6-month comparative trajectory in the **Decision Studio** tab.*"
        )

    # ==================== 7. TRANSACTION LOGGING & BUDGETS ====================

    def _parse_add_intent(self, text: str) -> Optional[str]:
        amt_match = re.search(r"(?:₹|rs\.?|\$)?\s*(\d+(?:\.\d{1,2})?)", text)
        if not amt_match:
            return None

        lowered = text.lower()
        if not any(k in lowered for k in ["spent", "paid", "bought", "add expense", "log"]):
            return None

        amount = float(amt_match.group(1))
        desc = re.sub(r"(?:spent|paid|bought|add expense|log|for|on|₹|rs|\d+(?:\.\d{1,2})?)", "", text, flags=re.IGNORECASE).strip() or "Expense"
        category = self.rules.categorize(desc)

        expense_id = self.db.add_expense(
            date_iso=date.today().isoformat(),
            amount=amount,
            description=desc,
            category=category,
            user_id=self.user_id,
        )
        return f"✅ **Logged Expense #{expense_id}:** {self.currency}{amount:,.2f} under **{category}** (*{desc}*)."

    def _parse_budget_intent(self, text: str) -> Optional[str]:
        lowered = text.lower()
        if not any(k in lowered for k in ["budget", "how much spent", "monthly limit"]):
            return None

        summary = self.db.get_summary("month", user_id=self.user_id)
        user = self.db.get_user_by_id(self.user_id)
        limit = float(user.get("monthly_budget", 25000.0)) if user else 25000.0
        remaining = max(0.0, limit - summary["total"])

        return (
            f"### 🎯 Monthly Budget Status\n"
            f"• **Total Spent:** {self.currency}{summary['total']:,.2f}\n"
            f"• **Monthly Budget Limit:** {self.currency}{limit:,.2f}\n"
            f"• **Remaining Safe Pool:** {self.currency}{remaining:,.2f} ({((summary['total']/limit)*100):.1f}% utilized)"
        )

    def _parse_predict_intent(self, text: str) -> Optional[str]:
        lowered = text.lower()
        if not any(k in lowered for k in ["forecast", "predict", "next month", "prediction"]):
            return None

        forecast = self.predictor.predict_next_month(user_id=self.user_id)
        return (
            f"🔮 **AI Forecast for {forecast['predicted_month_label']}**\n"
            f"• **Projected Total:** {self.currency}{forecast['total_next_month']:,.2f}\n"
            f"• **Trend:** {forecast['trend']} ({forecast['trend_pct']:+0.1f}%)\n"
            f"• **Confidence Score:** {forecast['confidence_score']}%"
        )

    # ==================== 8. GOOGLE GEMINI AI LLM RESPONSE ENGINE ====================

    def _generate_gemini_response(self, prompt: str) -> Optional[str]:
        """Queries Google Gemini LLM with grounded user financial portfolio context."""
        if not self.gemini_client:
            return None

        try:
            # Build Grounded User Context
            user = self.db.get_user_by_id(self.user_id)
            user_name = user.get("name", "User") if user else "User"
            income = float(user.get("income", 50000.0)) if user else 50000.0
            budget = float(user.get("monthly_budget", 25000.0)) if user else 25000.0
            savings_goal = float(user.get("savings_goal", 100000.0)) if user else 100000.0

            summary = self.db.get_summary("month", user_id=self.user_id)
            health = self.engine.calculate_health_score(self.user_id)
            safe_spend = self.engine.calculate_safe_daily_spend(self.user_id)
            leaks = self.engine.detect_money_leaks(self.user_id)

            system_context = f"""You are 'ExpenseAI Copilot', an empathetic, sharp, and highly intelligent AI personal financial advisor.
Current User Profile:
- Name: {user_name}
- Currency: {self.currency}
- Monthly Income: {self.currency}{income:,.2f}
- Monthly Budget Target: {self.currency}{budget:,.2f}
- Savings Goal: {self.currency}{savings_goal:,.2f}
- Current Month Total Spend: {self.currency}{summary['total']:,.2f} (Count: {summary['count']} transactions)
- AI Financial Health Score: {health['score']}/100 (Grade {health['grade']}, Status: {health['status']})
- Safe Daily Spending Allowance: {self.currency}{safe_spend['safe_daily_spend']:,.2f}/day ({safe_spend['days_left']} days left in month)
- Identified Micro-Expense Leaks (< {self.currency}300): {leaks['micro_spend_count']} transactions totaling {self.currency}{leaks['total_micro_amount']:,.2f}
- Category Breakdown: {summary['by_category']}

Instructions:
1. Answer the user's question directly with clear, concise, actionable financial advice.
2. Ground your advice using their real financial numbers when relevant.
3. Keep answers punchy (2-4 concise paragraphs or bullet points). Use Markdown formatting with relevant emojis.
"""

            response = self.gemini_client.models.generate_content(
                model="gemini-2.5-flash",
                contents=[
                    {"role": "user", "parts": [{"text": f"{system_context}\n\nUser Question: {prompt}"}]}
                ],
            )
            if response and response.text:
                return response.text.strip()
        except Exception as e:
            print("Gemini API call error:", e)

        return None

    # ==================== 9. MAIN DISPATCHER ====================

    def respond(self, text: str) -> str:
        if not text or not text.strip():
            return "Please enter a question or command. Type 'help' for examples."

        clean_text = text.strip()

        # Deterministic Decision Intents (Instant calculation)
        handlers = [
            self._parse_affordability_intent,
            self._parse_health_score_intent,
            self._parse_money_leaks_intent,
            self._parse_safe_daily_intent,
            self._parse_cashflow_collision_intent,
            self._parse_what_if_intent,
            self._parse_add_intent,
            self._parse_budget_intent,
            self._parse_predict_intent,
        ]

        for handler in handlers:
            result = handler(clean_text)
            if result:
                return result

        # Grounded Google Gemini LLM Response
        gemini_reply = self._generate_gemini_response(clean_text)
        if gemini_reply:
            return gemini_reply

        # Fallback if offline
        return (
            "🤖 **AI Copilot Ready:**\n"
            "• *'Can I afford headphones for ₹4,500?'*\n"
            "• *'What is my health score?'*\n"
            "• *'Where am I leaking money?'*\n"
            "• *'Safe daily limit'* \n"
            "• *'How can I save ₹20,000 in 3 months?'*\n"
            "• *'What if I save ₹150 every day?'*"
        )
