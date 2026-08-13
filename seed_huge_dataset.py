"""
High-Volume Realistic Data Seeder for ExpenseAI
Populates 1,500+ comprehensive, multi-year expense records across diverse categories,
payment methods, recurring bills, budgets, and user personas from 2024 to 2026.
"""

import os
import random
from datetime import date, datetime, timedelta
from categorizer import CategoryRules
from db import ExpenseDB

# Category-specific realistic merchant/description blueprints with realistic price distributions
EXPENSE_BLUEPRINTS = {
    "Food": [
        ("Swiggy Biryani Bowl", 280, 550, "UPI"),
        ("Zomato Pizza Order", 350, 750, "Credit Card"),
        ("Blinkit Quick Groceries", 180, 650, "UPI"),
        ("Zepto Daily Milk & Bread", 120, 320, "UPI"),
        ("Chai Point Masala Tea & Samosa", 60, 160, "UPI"),
        ("Starbucks Cappuccino & Muffin", 320, 580, "Credit Card"),
        ("BigBasket Monthly Provisions", 1800, 4500, "Debit Card"),
        ("Nature's Basket Organic Fruits", 450, 1200, "Credit Card"),
        ("Subway Footlong Sub", 220, 420, "UPI"),
        ("Haldiram's Family Dinner", 850, 2200, "UPI"),
        ("McDonald's Meal Combo", 199, 499, "UPI"),
        ("Office Cafeteria Lunch", 80, 150, "Cash"),
        ("Local Bakery Cake & Pastries", 150, 450, "UPI"),
        ("Street Food Pav Bhaji & Chaat", 70, 180, "Cash"),
    ],
    "Travel": [
        ("Uber Auto to Metro Station", 65, 140, "UPI"),
        ("Uber Premier to Airport", 650, 1450, "Credit Card"),
        ("Ola Cab to Office", 180, 380, "UPI"),
        ("Metro Smart Card Auto-Recharge", 200, 500, "UPI"),
        ("Shell Petrol Full Tank", 1200, 3500, "Credit Card"),
        ("HPCL Fuel Station", 500, 2000, "UPI"),
        ("Fastag Highway Toll Deduction", 85, 240, "NetBanking"),
        ("Airport Parking Fee", 150, 400, "Cash"),
        ("Intercity Bus Ticket", 450, 1200, "UPI"),
        ("IRCTC Train Ticket (AC 2-Tier)", 850, 2400, "NetBanking"),
        ("IndiGo Flight Ticket to Mumbai", 3800, 7500, "Credit Card"),
    ],
    "Shopping": [
        ("Amazon India Electronics & Cables", 350, 2200, "Credit Card"),
        ("Flipkart Big Billion Day Purchase", 800, 6500, "Credit Card"),
        ("Myntra Casual Clothing & Shoes", 1200, 4500, "Credit Card"),
        ("Zara Denim Jacket", 2500, 5990, "Credit Card"),
        ("Decathlon Sports Gear & Shoes", 650, 3200, "UPI"),
        ("Uniqlo Supima Cotton T-Shirts", 1490, 3990, "Credit Card"),
        ("Nykaa Skincare & Grooming", 450, 1850, "UPI"),
        ("IKEA Home Decor & Organizers", 890, 4800, "Credit Card"),
        ("Local Bookshop Paperback Novels", 299, 850, "UPI"),
        ("Weekend Mall Retail Shopping", 1500, 6000, "Credit Card"),
    ],
    "Bills": [
        ("Airtel Xstream Fiber Broadband", 999, 1499, "NetBanking"),
        ("Tata Power / Electricity Discom", 1400, 3800, "NetBanking"),
        ("Jio 5G Postpaid Family Plan", 599, 999, "UPI"),
        ("IGL Piped Cooking Gas Bill", 450, 950, "UPI"),
        ("Apartment Monthly Maintenance", 2500, 4000, "NetBanking"),
        ("House Maid & Cook Salary", 4000, 7000, "UPI"),
        ("Water Tanker Supply Charges", 350, 800, "Cash"),
    ],
    "Entertainment": [
        ("Netflix 4K Ultra HD Subscription", 649, 649, "Credit Card"),
        ("Spotify Premium Family Plan", 179, 179, "UPI"),
        ("YouTube Premium Family", 189, 189, "Credit Card"),
        ("PVR IMAX Movie Tickets & Popcorn", 650, 1600, "Credit Card"),
        ("BookMyShow Standup Comedy Show", 799, 2499, "Credit Card"),
        ("Sony PS5 PlayStation Plus Pass", 499, 850, "Credit Card"),
        ("Steam PC Game Summer Sale", 450, 2800, "Credit Card"),
        ("Weekend Bowling & Gaming Arcade", 600, 1500, "UPI"),
    ],
    "Health": [
        ("Cult.fit Gym & Yoga Annual Pass", 1200, 2400, "Credit Card"),
        ("Apollo Pharmacy Prescription Meds", 180, 950, "UPI"),
        ("1mg Healthcare & Multivitamins", 350, 1400, "UPI"),
        ("Dr. Lal PathLabs Full Body Profile", 1200, 2800, "Credit Card"),
        ("Dental Cleaning & Checkup", 800, 1800, "UPI"),
        ("Opticals & Eye Test Spectacles", 1500, 4500, "Credit Card"),
        ("Dermatologist Consultation", 700, 1500, "UPI"),
    ],
    "Education": [
        ("Coursera Professional Certificate", 1499, 2999, "Credit Card"),
        ("Udemy Python & AI Masterclass", 389, 799, "UPI"),
        ("O'Reilly Technical Books Online", 1200, 2500, "Credit Card"),
        ("Medium & Substack Subscriptions", 250, 600, "Credit Card"),
        ("University Exam & Lab Fee", 800, 3500, "NetBanking"),
    ],
    "Investments": [
        ("Zerodha Nifty 50 Index Fund SIP", 5000, 15000, "NetBanking"),
        ("Groww Parag Parikh Flexi Cap SIP", 3000, 10000, "NetBanking"),
        ("Public Provident Fund (PPF) Deposit", 2500, 12500, "NetBanking"),
        ("Sovereign Gold Bond (SGB) Unit", 4500, 9000, "NetBanking"),
    ],
    "Other": [
        ("Amazon Delivery Tip & Courier Fee", 30, 80, "UPI"),
        ("Dry Cleaning & Ironing Clothes", 120, 450, "Cash"),
        ("Office Stationery & Printouts", 40, 180, "Cash"),
        ("Car Wash & Detailing Service", 350, 900, "UPI"),
        ("House Key Duplication & Lock Repair", 150, 350, "Cash"),
        ("Charity & Temple Donation", 100, 1000, "UPI"),
    ],
}


def seed_huge_dataset(target_total: int = 1500):
    print("=" * 70)
    print(f"🚀 SEEDING HUGE REALISTIC DATASET (~{target_total}+ TRANSACTIONS)")
    print("=" * 70)

    db = ExpenseDB(db_path="expenses.db")
    rules = CategoryRules(path="categories.json")

    # 1. Setup / update users
    users = [
        {
            "name": "Priya Sharma",
            "email": "guest@expensetracker.ai",
            "password": "guest123",
            "currency": "₹",
            "monthly_budget": 35000.0,
            "income": 75000.0,
            "savings_goal": 300000.0,
            "is_student_mode": 0,
            "monthly_allowance": 0.0,
            "role": "user",
            "weight": 0.65,  # 65% of generated records go to main demo user
        },
        {
            "name": "Aarav Mehta",
            "email": "student@expensetracker.ai",
            "password": "student123",
            "currency": "₹",
            "monthly_budget": 14000.0,
            "income": 15000.0,
            "savings_goal": 25000.0,
            "is_student_mode": 1,
            "monthly_allowance": 14000.0,
            "role": "user",
            "weight": 0.20,  # 20% of records for student mode
        },
        {
            "name": "Vikram Malhotra",
            "email": "pro@expensetracker.ai",
            "password": "pro123",
            "currency": "₹",
            "monthly_budget": 75000.0,
            "income": 180000.0,
            "savings_goal": 1200000.0,
            "is_student_mode": 0,
            "monthly_allowance": 0.0,
            "role": "user",
            "weight": 0.15,  # 15% of records for high-income investor persona
        },
    ]

    user_ids = {}
    for u in users:
        existing = db.get_user_by_email(u["email"])
        if existing:
            uid = existing["id"]
            db.update_user_profile(
                user_id=uid,
                name=u["name"],
                currency=u["currency"],
                monthly_budget=u["monthly_budget"],
                income=u["income"],
                savings_goal=u["savings_goal"],
                is_student_mode=u["is_student_mode"],
                monthly_allowance=u["monthly_allowance"],
                theme="dark",
            )
            user_ids[u["email"]] = uid
            print(f"  • Updated user {u['name']} (ID: {uid})")
        else:
            uid = db.create_user(
                name=u["name"],
                email=u["email"],
                password=u["password"],
                currency=u["currency"],
                role=u["role"],
            )
            db.update_user_profile(
                user_id=uid,
                name=u["name"],
                currency=u["currency"],
                monthly_budget=u["monthly_budget"],
                income=u["income"],
                savings_goal=u["savings_goal"],
                is_student_mode=u["is_student_mode"],
                monthly_allowance=u["monthly_allowance"],
                theme="dark",
            )
            user_ids[u["email"]] = uid
            print(f"  • Created user {u['name']} (ID: {uid})")

    # 2. Populate Budgets and Recurring Bills for all users
    curr_month = date.today().strftime("%Y-%m")
    for u in users:
        uid = user_ids[u["email"]]
        mb = u["monthly_budget"]

        # Category budgets
        db.set_budget(uid, "Food", round(mb * 0.25, -2), curr_month)
        db.set_budget(uid, "Travel", round(mb * 0.15, -2), curr_month)
        db.set_budget(uid, "Shopping", round(mb * 0.18, -2), curr_month)
        db.set_budget(uid, "Bills", round(mb * 0.22, -2), curr_month)
        db.set_budget(uid, "Entertainment", round(mb * 0.10, -2), curr_month)
        db.set_budget(uid, "Health", round(mb * 0.10, -2), curr_month)

        # Clear old recurring and re-seed
        with db._get_connection() as conn:
            conn.execute("DELETE FROM recurring WHERE user_id = ?", (uid,))

        if not u["is_student_mode"]:
            db.add_recurring(uid, "Apartment House Rent", 12000.0, "Bills", "Monthly", date(date.today().year, date.today().month, 15).isoformat())
            db.add_recurring(uid, "HDFC Personal Loan / EMI", 4500.0, "Bills", "Monthly", date(date.today().year, date.today().month, 18).isoformat())
            db.add_recurring(uid, "Airtel Fiber Broadband", 999.0, "Bills", "Monthly", date(date.today().year, date.today().month, 22).isoformat())
            db.add_recurring(uid, "Netflix 4K Ultra HD", 649.0, "Entertainment", "Monthly", date(date.today().year, date.today().month, 25).isoformat())
        else:
            db.add_recurring(uid, "Hostel & Mess Charges", 6500.0, "Bills", "Monthly", date(date.today().year, date.today().month, 10).isoformat())
            db.add_recurring(uid, "Mobile 5G Data Pack", 399.0, "Bills", "Monthly", date(date.today().year, date.today().month, 20).isoformat())
            db.add_recurring(uid, "Spotify Student Plan", 66.0, "Entertainment", "Monthly", date(date.today().year, date.today().month, 28).isoformat())

    print("\n  • Configured category spending budgets and scheduled recurring bills.")

    # 3. Generate 1500+ Historical Expense Records across 730 days (24 months)
    today = date.today()
    start_date = today - timedelta(days=730)

    categories_list = list(EXPENSE_BLUEPRINTS.keys())
    # Category distribution weights (Food, Travel, Shopping have higher transaction frequencies)
    category_weights = [0.32, 0.20, 0.15, 0.08, 0.10, 0.06, 0.03, 0.03, 0.03]

    print(f"\n  • Generating transactions between {start_date.isoformat()} and {today.isoformat()}...")

    added_count = 0
    with db._get_connection() as conn:
        for i in range(target_total):
            # Select user by persona weight
            rand_val = random.random()
            if rand_val < 0.65:
                u_email = "guest@expensetracker.ai"
            elif rand_val < 0.85:
                u_email = "student@expensetracker.ai"
            else:
                u_email = "pro@expensetracker.ai"

            target_uid = user_ids[u_email]

            # Generate realistic timestamp (higher density in recent 3 months)
            if random.random() < 0.45:
                days_ago = random.randint(0, 90) # Recent 3 months
            elif random.random() < 0.75:
                days_ago = random.randint(91, 365) # Last 1 year
            else:
                days_ago = random.randint(366, 730) # Year 2

            tx_date = today - timedelta(days=days_ago)

            # Choose category
            cat = random.choices(categories_list, weights=category_weights, k=1)[0]
            blueprint = random.choice(EXPENSE_BLUEPRINTS[cat])
            desc, min_p, max_p, default_pay = blueprint

            # Scale student prices slightly lower, pro prices higher
            scale = 0.65 if u_email == "student@expensetracker.ai" else (1.4 if u_email == "pro@expensetracker.ai" else 1.0)
            amount = round(random.uniform(min_p * scale, max_p * scale), 2)

            # Realistic payment method variation
            pay_method = default_pay
            if random.random() < 0.25:
                pay_method = random.choice(["UPI", "Credit Card", "Debit Card", "Cash", "NetBanking"])

            # Occasional notes
            notes = ""
            if random.random() < 0.15:
                notes = random.choice(["Family dinner", "Work expense", "Weekend outing", "Impulse purchase", "Discretionary", "Split with friends"])

            conn.execute(
                """
                INSERT INTO expenses (date, amount, description, category, user_id, payment_method, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (tx_date.isoformat(), amount, desc, cat, target_uid, pay_method, notes),
            )
            added_count += 1

            if added_count % 300 == 0:
                print(f"    - Seeded {added_count}/{target_total} transactions...")

        # Add some intentional micro-transactions in the current month for the Money Leak Detector
        micro_spends = [
            ("Cutting Chai & Rusk", 25.0, "Food", "Cash"),
            ("Swiggy Delivery Fee & Tip", 49.0, "Food", "UPI"),
            ("Zomato Platform Surcharge", 10.0, "Food", "UPI"),
            ("Zepto Packaging Handling Fee", 15.0, "Food", "UPI"),
            ("Blinkit Surge Charge", 35.0, "Food", "UPI"),
            ("Metro Station Two-Wheeler Parking", 30.0, "Travel", "Cash"),
            ("Highway Fastag Service Fee", 12.0, "Travel", "NetBanking"),
            ("Xerox Copy & Document Printout", 45.0, "Other", "Cash"),
            ("Chilled Water Bottle & Snack", 40.0, "Food", "UPI"),
            ("Filter Coffee from Corner Stall", 30.0, "Food", "UPI"),
            ("Late Night Ice Cream Cone", 75.0, "Food", "UPI"),
            ("Grocery Plastic Bag & Carry Charge", 14.0, "Other", "UPI"),
            ("App Store 100GB Cloud Addon", 130.0, "Other", "Credit Card"),
            ("YouTube 4K Movie Rental", 120.0, "Entertainment", "UPI"),
        ]

        for desc, amt, cat, pay in micro_spends:
            for day_offset in range(1, 12):
                if random.random() < 0.4:
                    tx_d = today - timedelta(days=day_offset)
                    conn.execute(
                        """
                        INSERT INTO expenses (date, amount, description, category, user_id, payment_method, notes)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                        (tx_d.isoformat(), amt, desc, cat, user_ids["guest@expensetracker.ai"], pay, "Micro-transaction"),
                    )
                    added_count += 1

        # Commit all transaction inserts
        conn.commit()

    # 4. Total count verification
    total_in_db = db.count_expenses()
    demo_count = db.count_expenses(user_id=user_ids["guest@expensetracker.ai"])
    student_count = db.count_expenses(user_id=user_ids["student@expensetracker.ai"])
    pro_count = db.count_expenses(user_id=user_ids["pro@expensetracker.ai"])

    print("\n" + "=" * 70)
    print(f"✅ DATASET SEEDING COMPLETE!")
    print(f"  • Total Expenses in Database: {total_in_db:,} transactions")
    print(f"  • Priya Sharma (Main Demo):   {demo_count:,} transactions")
    print(f"  • Aarav Mehta (Student Mode): {student_count:,} transactions")
    print(f"  • Vikram Malhotra (Pro):      {pro_count:,} transactions")
    print(f"  • Historical Coverage:        August 2024 to August 2026 (24 Months)")
    print("=" * 70)


if __name__ == "__main__":
    seed_huge_dataset(1500)
