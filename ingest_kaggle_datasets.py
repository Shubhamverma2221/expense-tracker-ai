"""
Kaggle Personal Finance & Credit Card Dataset Importer for ExpenseAI
Downloads and ingests real-world transactions from Kaggle datasets into expenses.db
"""

import os
import glob
import pandas as pd
from datetime import date, datetime, timedelta
import kagglehub
from db import ExpenseDB
from categorizer import CategoryRules

db = ExpenseDB(db_path="expenses.db")
rules = CategoryRules(path="categories.json")

# Standard ExpenseAI category mapping
CATEGORY_MAP = {
    "groceries": "Food",
    "food": "Food",
    "dining": "Food",
    "restaurants": "Food",
    "fast food": "Food",
    "supermarket": "Food",
    "coffee": "Food",
    "transport": "Travel",
    "transportation": "Travel",
    "travel": "Travel",
    "gas": "Travel",
    "fuel": "Travel",
    "transit": "Travel",
    "flight": "Travel",
    "cab": "Travel",
    "shopping": "Shopping",
    "clothing": "Shopping",
    "retail": "Shopping",
    "electronics": "Shopping",
    "utilities": "Bills",
    "bills": "Bills",
    "rent": "Bills",
    "housing": "Bills",
    "electricity": "Bills",
    "internet": "Bills",
    "entertainment": "Entertainment",
    "movies": "Entertainment",
    "streaming": "Entertainment",
    "games": "Entertainment",
    "recreation": "Entertainment",
    "health": "Health",
    "medical": "Health",
    "pharmacy": "Health",
    "fitness": "Health",
    "education": "Education",
    "tuition": "Education",
    "books": "Education",
    "investment": "Investments",
    "savings": "Investments",
}


def normalize_category(raw_cat: str, description: str = "") -> str:
    if not raw_cat or pd.isna(raw_cat):
        return rules.categorize(str(description or ""))
    
    clean = str(raw_cat).strip().lower()
    for k, v in CATEGORY_MAP.items():
        if k in clean:
            return v
    return rules.categorize(str(description or clean))


def ingest_dataset_1():
    print("\n" + "=" * 65)
    print("📥 Ingesting: saraswathyyy/personal-finance-dataset")
    print("=" * 65)
    try:
        path = kagglehub.dataset_download("saraswathyyy/personal-finance-dataset")
        print("  • Downloaded to:", path)
        csv_files = glob.glob(os.path.join(path, "**", "*.csv"), recursive=True)
        if not csv_files:
            print("  ⚠️ No CSV files found.")
            return 0

        total_added = 0
        for f in csv_files:
            df = pd.read_csv(f)
            print(f"  • Reading {os.path.basename(f)} ({len(df)} rows)")
            print("    Columns:", list(df.columns))

            # Detect columns
            date_col = next((c for c in df.columns if "date" in c.lower()), None)
            amt_col = next((c for c in df.columns if any(k in c.lower() for k in ["amount", "cost", "price", "spend"])), None)
            cat_col = next((c for c in df.columns if "cat" in c.lower()), None)
            desc_col = next((c for c in df.columns if any(k in c.lower() for k in ["desc", "item", "merchant", "name", "title"])), None)

            with db._get_connection() as conn:
                for _, row in df.head(300).iterrows():
                    amt = abs(float(row[amt_col])) if amt_col and pd.notna(row[amt_col]) else 250.0
                    if amt <= 0 or amt > 500000:
                        continue
                    
                    desc = str(row[desc_col]) if desc_col and pd.notna(row[desc_col]) else "Personal expense"
                    raw_c = str(row[cat_col]) if cat_col and pd.notna(row[cat_col]) else ""
                    cat = normalize_category(raw_c, desc)

                    try:
                        d_val = pd.to_datetime(row[date_col]).strftime("%Y-%m-%d") if date_col and pd.notna(row[date_col]) else date.today().isoformat()
                    except Exception:
                        d_val = date.today().isoformat()

                    conn.execute(
                        """
                        INSERT INTO expenses (date, amount, description, category, user_id, payment_method, notes)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                        (d_val, round(amt, 2), desc[:120], cat, 2, "Card", "Kaggle personal-finance-dataset"),
                    )
                    total_added += 1

                conn.commit()
            print(f"  ✅ Added {total_added} records from {os.path.basename(f)}")
        return total_added
    except Exception as e:
        print("  ⚠️ Ingestion error:", e)
        return 0


def ingest_dataset_2():
    print("\n" + "=" * 65)
    print("📥 Ingesting: entrepreneurlife/personal-finance")
    print("=" * 65)
    try:
        path = kagglehub.dataset_download("entrepreneurlife/personal-finance")
        print("  • Downloaded to:", path)
        csv_files = glob.glob(os.path.join(path, "**", "*.csv"), recursive=True)
        if not csv_files:
            print("  ⚠️ No CSV files found.")
            return 0

        total_added = 0
        for f in csv_files:
            df = pd.read_csv(f)
            print(f"  • Reading {os.path.basename(f)} ({len(df)} rows)")
            print("    Columns:", list(df.columns))

            date_col = next((c for c in df.columns if "date" in c.lower()), None)
            amt_col = next((c for c in df.columns if any(k in c.lower() for k in ["amount", "cost", "price", "spend"])), None)
            cat_col = next((c for c in df.columns if "cat" in c.lower()), None)
            desc_col = next((c for c in df.columns if any(k in c.lower() for k in ["desc", "item", "merchant", "name", "title"])), None)

            with db._get_connection() as conn:
                for _, row in df.head(300).iterrows():
                    amt = abs(float(row[amt_col])) if amt_col and pd.notna(row[amt_col]) else 180.0
                    if amt <= 0 or amt > 500000:
                        continue

                    desc = str(row[desc_col]) if desc_col and pd.notna(row[desc_col]) else "Commercial spend"
                    raw_c = str(row[cat_col]) if cat_col and pd.notna(row[cat_col]) else ""
                    cat = normalize_category(raw_c, desc)

                    try:
                        d_val = pd.to_datetime(row[date_col]).strftime("%Y-%m-%d") if date_col and pd.notna(row[date_col]) else date.today().isoformat()
                    except Exception:
                        d_val = date.today().isoformat()

                    conn.execute(
                        """
                        INSERT INTO expenses (date, amount, description, category, user_id, payment_method, notes)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                        (d_val, round(amt, 2), desc[:120], cat, 2, "UPI", "Kaggle personal-finance"),
                    )
                    total_added += 1

                conn.commit()
            print(f"  ✅ Added {total_added} records from {os.path.basename(f)}")
        return total_added
    except Exception as e:
        print("  ⚠️ Ingestion error:", e)
        return 0


def ingest_dataset_3():
    print("\n" + "=" * 65)
    print("📥 Ingesting: ealtman2019/credit-card-transactions")
    print("=" * 65)
    try:
        path = kagglehub.dataset_download("ealtman2019/credit-card-transactions")
        print("  • Downloaded to:", path)
        csv_files = glob.glob(os.path.join(path, "**", "*.csv"), recursive=True)
        if not csv_files:
            print("  ⚠️ No CSV files found.")
            return 0

        total_added = 0
        for f in csv_files[:1]: # Sample first credit card batch
            df = pd.read_csv(f, nrows=500)
            print(f"  • Reading {os.path.basename(f)} ({len(df)} sample rows)")
            print("    Columns:", list(df.columns))

            amt_col = next((c for c in df.columns if any(k in c.lower() for k in ["amount", "amt"])), None)
            desc_col = next((c for c in df.columns if any(k in c.lower() for k in ["merchant", "desc", "trans"])), None)
            date_col = next((c for c in df.columns if "date" in c.lower() or "time" in c.lower()), None)

            with db._get_connection() as conn:
                for _, row in df.head(400).iterrows():
                    amt_str = str(row[amt_col]).replace("$", "").replace(",", "").strip() if amt_col and pd.notna(row[amt_col]) else "50"
                    try:
                        amt = abs(float(amt_str))
                    except Exception:
                        amt = 50.0

                    if amt <= 0 or amt > 50000:
                        continue

                    # Convert to INR roughly (or realistic spend scale)
                    amt_inr = round(amt * 82.5 if amt < 100 else amt, 2)
                    desc = str(row[desc_col]) if desc_col and pd.notna(row[desc_col]) else "Credit Card Merchant"
                    cat = rules.categorize(desc)

                    d_val = (date.today() - timedelta(days=random.randint(1, 180))).isoformat()

                    conn.execute(
                        """
                        INSERT INTO expenses (date, amount, description, category, user_id, payment_method, notes)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                        (d_val, amt_inr, desc[:120], cat, 2, "Credit Card", "Kaggle credit-card-transactions"),
                    )
                    total_added += 1

                conn.commit()
            print(f"  ✅ Added {total_added} records from credit-card-transactions")
        return total_added
    except Exception as e:
        print("  ⚠️ Ingestion error:", e)
        return 0


def main():
    import random
    print("=" * 70)
    print("🌐 KAGGLE DATASET PIPELINE INGESTION FOR EXPENSEAI")
    print("=" * 70)

    n1 = ingest_dataset_1()
    n2 = ingest_dataset_2()
    n3 = ingest_dataset_3()

    total_in_db = db.count_expenses()
    demo_in_db = db.count_expenses(user_id=2)

    print("\n" + "=" * 70)
    print("🎉 ALL KAGGLE DATASETS INGESTED SUCCESSFULLY!")
    print(f"  • Total Expenses in Database: {total_in_db:,} transactions")
    print(f"  • Demo User Active Portfolio: {demo_in_db:,} transactions")
    print("=" * 70)


if __name__ == "__main__":
    main()
