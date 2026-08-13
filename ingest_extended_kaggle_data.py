"""
Extended Kaggle Dataset Ingestion
Ingests 8,000 extended records from saraswathyyy/personal-finance-dataset
and entrepreneurlife/personal-finance Excel file into ExpenseAI database.
"""

import os
import glob
import pandas as pd
import random
from datetime import date, datetime, timedelta
from db import ExpenseDB
from categorizer import CategoryRules

db = ExpenseDB(db_path="expenses.db")
rules = CategoryRules(path="categories.json")


def clean_cat(cat_raw, desc_raw=""):
    c_str = str(cat_raw or "").strip().lower()
    mapping = {
        "grocer": "Food",
        "food": "Food",
        "dine": "Food",
        "restaur": "Food",
        "cafe": "Food",
        "coffee": "Food",
        "snack": "Food",
        "transport": "Travel",
        "transit": "Travel",
        "gas": "Travel",
        "fuel": "Travel",
        "flight": "Travel",
        "cab": "Travel",
        "uber": "Travel",
        "auto": "Travel",
        "shop": "Shopping",
        "cloth": "Shopping",
        "apparel": "Shopping",
        "retail": "Shopping",
        "utilit": "Bills",
        "bill": "Bills",
        "rent": "Bills",
        "electr": "Bills",
        "water": "Bills",
        "gas bill": "Bills",
        "entertain": "Entertainment",
        "movie": "Entertainment",
        "stream": "Entertainment",
        "game": "Entertainment",
        "health": "Health",
        "medic": "Health",
        "pharm": "Health",
        "fit": "Health",
        "gym": "Health",
        "doctor": "Health",
        "educat": "Education",
        "course": "Education",
        "book": "Education",
        "invest": "Investments",
        "sip": "Investments",
        "stock": "Investments",
    }
    for k, v in mapping.items():
        if k in c_str:
            return v
    return rules.categorize(str(desc_raw or cat_raw or "Other"))


def ingest_extended_csv():
    csv_path = r"C:\Users\priya\.cache\kagglehub\datasets\saraswathyyy\personal-finance-dataset\versions\1\personal_finance_dataset_8000_extended.csv"
    if not os.path.exists(csv_path):
        print(f"⚠️ {csv_path} not found.")
        return 0

    df = pd.read_csv(csv_path)
    print(f"\n📂 Reading {os.path.basename(csv_path)} ({len(df):,} rows)")
    print("   Columns:", list(df.columns))

    added = 0
    with db._get_connection() as conn:
        for idx, row in df.iterrows():
            amt = abs(float(row.get("Amount", 250.0)))
            if amt <= 0 or amt > 200000:
                continue

            raw_cat = row.get("Category", "General")
            desc = f"{raw_cat} expense #{int(row.get('Transaction_ID', idx))}"
            cat = clean_cat(raw_cat, desc)

            # Distribute dates across last 2 years (2024 to 2026)
            days_ago = random.randint(1, 720)
            d_val = (date.today() - timedelta(days=days_ago)).isoformat()

            pay = random.choice(["UPI", "Credit Card", "Debit Card", "NetBanking", "Cash"])
            uid = random.choices([2, 3, 4], weights=[0.65, 0.20, 0.15], k=1)[0]

            conn.execute(
                """
                INSERT INTO expenses (date, amount, description, category, user_id, payment_method, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (d_val, round(amt, 2), desc[:120], cat, uid, pay, "Kaggle 8k Extended Dataset"),
            )
            added += 1

            if added % 1500 == 0:
                print(f"   - Ingested {added:,}/{len(df):,} records...")

        conn.commit()

    print(f"✅ Ingested {added:,} transactions from {os.path.basename(csv_path)}")
    return added


def ingest_excel_file():
    excel_path = r"C:\Users\priya\.cache\kagglehub\datasets\entrepreneurlife\personal-finance\versions\2\personal_transactions_dashboard_ready (2).xlsx"
    if not os.path.exists(excel_path):
        print(f"⚠️ {excel_path} not found.")
        return 0

    df = pd.read_excel(excel_path)
    print(f"\n📂 Reading {os.path.basename(excel_path)} ({len(df):,} rows)")
    print("   Columns:", list(df.columns))

    date_col = next((c for c in df.columns if "date" in c.lower()), None)
    amt_col = next((c for c in df.columns if any(k in c.lower() for k in ["amount", "cost", "price", "spend"])), None)
    cat_col = next((c for c in df.columns if "cat" in c.lower()), None)
    desc_col = next((c for c in df.columns if any(k in c.lower() for k in ["desc", "merchant", "item", "title", "name"])), None)

    added = 0
    with db._get_connection() as conn:
        for idx, row in df.iterrows():
            amt = abs(float(row[amt_col])) if amt_col and pd.notna(row[amt_col]) else 150.0
            if amt <= 0 or amt > 200000:
                continue

            desc = str(row[desc_col]) if desc_col and pd.notna(row[desc_col]) else "Personal Purchase"
            raw_c = str(row[cat_col]) if cat_col and pd.notna(row[cat_col]) else ""
            cat = clean_cat(raw_c, desc)

            try:
                d_val = pd.to_datetime(row[date_col]).strftime("%Y-%m-%d") if date_col and pd.notna(row[date_col]) else date.today().isoformat()
            except Exception:
                d_val = (date.today() - timedelta(days=random.randint(1, 365))).isoformat()

            pay = random.choice(["UPI", "Credit Card", "Debit Card", "NetBanking"])
            uid = random.choices([2, 3, 4], weights=[0.65, 0.20, 0.15], k=1)[0]

            conn.execute(
                """
                INSERT INTO expenses (date, amount, description, category, user_id, payment_method, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (d_val, round(amt, 2), desc[:120], cat, uid, pay, "Kaggle personal-finance xlsx"),
            )
            added += 1

        conn.commit()

    print(f"✅ Ingested {added:,} transactions from {os.path.basename(excel_path)}")
    return added


def main():
    print("=" * 70)
    print("🚀 EXTENDED REAL-WORLD KAGGLE DATASET INGESTION")
    print("=" * 70)

    n1 = ingest_extended_csv()
    n2 = ingest_excel_file()

    total_in_db = db.count_expenses()
    demo_in_db = db.count_expenses(user_id=2)
    student_in_db = db.count_expenses(user_id=3)
    pro_in_db = db.count_expenses(user_id=4)

    print("\n" + "=" * 70)
    print(f"🎉 FINAL HUGE DATABASE STATS:")
    print(f"  • Total Expenses in Database: {total_in_db:,} transactions")
    print(f"  • Priya Sharma (Main Demo):   {demo_in_db:,} transactions")
    print(f"  • Aarav Mehta (Student Mode): {student_in_db:,} transactions")
    print(f"  • Vikram Malhotra (Pro):      {pro_in_db:,} transactions")
    print("=" * 70)


if __name__ == "__main__":
    main()
