# 🧠 ExpenseAI — Personal Financial Decision & Risk Intelligence Assistant

[![Python](https://img.shields.io/badge/Python-3.10%2B-blue.svg)](https://www.python.org/)
[![Framework](https://img.shields.io/badge/Framework-Flask%203.x-green.svg)](https://flask.palletsprojects.com/)
[![Database](https://img.shields.io/badge/Database-SQLite-lightgrey.svg)](https://www.sqlite.org/)
[![UI Design](https://img.shields.io/badge/UI-Modern%20Glassmorphism-purple.svg)]()
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)]()

> **"Most expense trackers tell users where their money went. ExpenseAI tells users where their money is going, why it is happening, what is likely to happen next, and what they can do before swiping."**

---

## 🎯 The Problem & Underserved Niche

Traditional personal finance apps (Mint, Splitwise, Excel sheets) suffer from the **"Post-Mortem Trap"**: they record transactions *after* the money is gone and show retrospective pie charts.

### The Real-World Friction:
* **The Student & Young Professional Runway Crisis**: A user receives ₹12,000 allowance or ₹30,000 salary on the 1st, but runs out of money by the 18th due to unmonitored ₹80–₹250 delivery fees and unaligned bill due dates.
* **Pre-Purchase Uncertainty**: Users frequently ask *"Can I afford this ₹3,500 jacket today without compromising my rent on the 15th?"* — with zero tools providing instant, real-time friction before swiping.
* **Cash-Flow Timing Collisions**: Users may be solvent over a 30-day period, but suffer temporary cash deficits when fixed obligations (Rent, EMIs, Broadband) clash with daily burn rates.

**ExpenseAI** solves this by shifting personal finance from **passive accounting** to **active, real-time decision support**.

---

## 🚀 Key Features & Decision Engines

```
                       ┌────────────────────────────────────────────────┐
                       │          Financial Intelligence Loop           │
                       └────────────────────────────────────────────────┘
                                                │
       ┌──────────────────────┬─────────────────┴─────────────────┬──────────────────────┐
       ▼                      ▼                                   ▼                      ▼
🔴 Purchase Impact    🧠 Health Score                     ⏰ Cash-Flow Collision 🕵️ Money Leak Detector
"Can I Afford This?"  (0–100 Multi-Factor)                (20-Day Forward)       (< ₹300 Micro-Spends)
• Discretionary Pool  • Savings Accumulation (25 pts)     • Scheduled Bills      • Cumulative Leak Drain
• Safe Daily Shift    • Budget Adherence (25 pts)         • Daily Burn Rate      • Annual Drain Forecast
• 3-Tier Verdict      • Spending Volatility (20 pts)      • Shortfall Warnings   • Optimization Advice
                      • Essential/Discretionary (15 pts)
                      • Recurring Bill Burden (15 pts)
```

### 1. 🔴 Real-Time "Can I Afford This?" Decision Engine
- Evaluates planned purchases before execution.
- Checks liquid discretionary pool, billing cycle countdown, category budget utilization, and upcoming scheduled bills.
- Returns a 3-tier verdict (**Affordable ✅**, **Caution ⚠️**, **High Risk 🚨**) with exact before/after safe daily spending shifts.

### 2. 🧠 Multi-Factor AI Financial Health Score (0–100)
- Mathematical composite rating based on 5 weighted pillars:
  1. **Savings Rate (25 pts)**: Ratio of monthly savings against total income.
  2. **Budget Adherence (25 pts)**: Category-level spending discipline.
  3. **Spending Volatility (20 pts)**: Day-to-day spending variance and spike minimization.
  4. **Essential vs Discretionary Ratio (15 pts)**: 50/30 needs vs wants adherence.
  5. **Fixed Obligation Burden (15 pts)**: Recurring bills as a percentage of income.
- Delivers actionable diagnostics and historical grade tracking (Grade A/B/C/D).

### 3. ⏰ Cash-Flow Collision & Bill Timing Predictor
- Forward-looking 20-day projection modeling daily burn rate against scheduled recurring commitments (Rent, EMIs, Utilities).
- Flags imminent shortfall dates with exact deficit amounts before due dates arrive.

### 4. 🕵️ Micro-Expense Money Leak Auditor (< ₹300)
- Isolates high-frequency small expenses (coffee, delivery fees, convenience charges).
- Calculates cumulative drain, percentage of monthly outflow, and projected annual wealth loss if uncurbed.

### 5. 💰 Safe-to-Spend Daily Run-Rate Allowance
- Dynamic calculation adjusting every morning:
  $$\text{Safe Daily Spend} = \frac{\text{Monthly Budget} - \text{Total Spent to Date} - \text{Upcoming Committed Bills}}{\text{Days Remaining in Month}}$$

### 6. 🧪 Financial "What-If" Simulation Lab
- Multi-month trajectory modeling:
  - **Major Purchase**: Simulates 6-month savings delay from buying a ₹60,000 laptop.
  - **Habit Optimization**: Projects 1-year and 3-year compound gains from saving ₹100/day (+₹36,500/yr).
  - **Fixed Cost Shift**: Computes runway impact of a ₹2,000 rent increase.

### 7. 🧾 Smart Receipt Scanner & NLP Auto-Parser
- Regex & NLP extraction for receipt snippets and SMS alerts.
- Auto-extracts Merchant Name, Transaction Date, Total Amount, and Category with 1-click database persistence.

### 8. 💬 Conversational Decision Copilot
- Supports natural language commands:
  - *"Can I afford shoes for ₹3,500?"*
  - *"What is my health score?"*
  - *"Where am I leaking money?"*
  - *"Spent 350 on fuel today"*

---

## 📐 Mathematical Formulations

### 1. Financial Health Score
$$\text{Health Score} = S_{\text{savings}} + S_{\text{adherence}} + S_{\text{volatility}} + S_{\text{ratio}} + S_{\text{recurring}}$$
$$\text{Where } S_{\text{savings}} = \min\left(25, \frac{\text{Savings Rate}}{0.25} \times 25\right)$$

### 2. Least-Squares Linear Trend Prediction
$$\text{Slope } (m) = \frac{n \sum (xy) - \sum x \sum y}{n \sum (x^2) - (\sum x)^2}, \quad \text{Intercept } (c) = \frac{\sum y - m \sum x}{n}$$
$$\text{Goodness-of-Fit Confidence } (R^2) = 1 - \frac{\sum (y_i - \hat{y}_i)^2}{\sum (y_i - \bar{y})^2}$$

---

## 🏛️ System Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                          Client Layer (Browser)                        │
│   • Modern Glassmorphism CSS (Dark/Light Mode)                         │
│   • Chart.js Interactive Visualizations & 5-State Feedback (Empty/Load)│
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ HTTP / REST API / JSON
┌───────────────────────────────────▼────────────────────────────────────┐
│                       Application Layer (Flask 3.x)                    │
│   • Session Authentication & Role-Based Access Control (Admin/User)    │
│   • REST Controllers (/api/affordability, /api/what-if, /api/chat)     │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │                                │
┌───────────────────▼─────────────────┐   ┌──────────▼───────────────────┐
│     Decision Intelligence Engines   │   │        Data Persistence      │
│   • FinancialIntelligenceEngine     │   │   • SQLite3 (WAL Mode)       │
│   • Least-Squares Linear Predictor  │   │   • Multi-User Isolated      │
│   • NLP Categorizer & Rule Matcher  │   │   • Audit Logging & Backups  │
└─────────────────────────────────────┘   └──────────────────────────────┘
```

---

## ⚡ Quick Start & Local Setup

### 1. Clone & Setup Environment
```bash
git clone https://github.com/your-username/expense-tracker-ai.git
cd expense-tracker-ai

# Create virtual environment
python -m venv .venv
source .venv/bin/activate  # On Windows: .venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt
```

### 2. Run the Application
```bash
python app.py
```
Open [http://127.0.0.1:5000](http://127.0.0.1:5000) in your browser.

---

## 🐳 Docker Deployment

Run with Docker Compose:
```bash
docker-compose up --build -d
```
The app will be live at `http://localhost:5000`.

---

## 🌐 1-Click Cloud Deployment (Render / Railway)

This repository includes a `render.yaml` and `Procfile`:
1. Push this repository to GitHub.
2. Link the repository on [Render](https://render.com) or [Railway](https://railway.app).
3. Set build command: `pip install -r requirements.txt`
4. Set start command: `gunicorn --config gunicorn_config.py app:app`

---

## 🔑 Pre-Seeded Test Credentials

| Account Role | Email | Password | Features |
| :--- | :--- | :--- | :--- |
| **⚡ Instant Demo** | Click *"1-Click Demo Login"* | *(None required)* | 200+ historical expenses, active budgets, recurring bills. |
| **👤 Demo User** | `guest@expensetracker.ai` | `guest123` | Pre-seeded financial health score & What-If data. |
| **🛡️ Administrator** | `admin@expensetracker.ai` | `admin123` | Platform analytics, user directory, system audit logs. |

---

## 📄 License
Distributed under the MIT License. See `LICENSE` for more information.
