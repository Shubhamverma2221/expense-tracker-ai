import csv
import json
import os
import sqlite3
from datetime import date, datetime, timedelta
from typing import Any, Dict, List, Optional, Tuple
from werkzeug.security import check_password_hash, generate_password_hash


class ExpenseDB:
    def __init__(self, db_path: str = "expenses.db") -> None:
        self.db_path = db_path
        self._ensure_db()
        self._seed_default_users_and_data()

    def _get_connection(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def _ensure_db(self) -> None:
        with self._get_connection() as conn:
            # Users table
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    email TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    role TEXT DEFAULT 'user',
                    currency TEXT DEFAULT '₹',
                    monthly_budget REAL DEFAULT 25000.0,
                    income REAL DEFAULT 50000.0,
                    savings_goal REAL DEFAULT 100000.0,
                    is_student_mode INTEGER DEFAULT 0,
                    monthly_allowance REAL DEFAULT 15000.0,
                    theme TEXT DEFAULT 'light',
                    created_at TEXT NOT NULL
                );
                """
            )

            # Migrate user columns if needed
            cursor = conn.execute("PRAGMA table_info(users)")
            user_cols = [row["name"] for row in cursor.fetchall()]
            if "income" not in user_cols:
                conn.execute("ALTER TABLE users ADD COLUMN income REAL DEFAULT 50000.0")
            if "savings_goal" not in user_cols:
                conn.execute("ALTER TABLE users ADD COLUMN savings_goal REAL DEFAULT 100000.0")
            if "is_student_mode" not in user_cols:
                conn.execute("ALTER TABLE users ADD COLUMN is_student_mode INTEGER DEFAULT 0")
            if "monthly_allowance" not in user_cols:
                conn.execute("ALTER TABLE users ADD COLUMN monthly_allowance REAL DEFAULT 15000.0")

            # Expenses table
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS expenses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL DEFAULT 1,
                    date TEXT NOT NULL,
                    amount REAL NOT NULL,
                    description TEXT NOT NULL,
                    category TEXT NOT NULL,
                    payment_method TEXT DEFAULT 'UPI',
                    notes TEXT DEFAULT '',
                    receipt_path TEXT DEFAULT '',
                    created_at TEXT NOT NULL,
                    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
                );
                """
            )

            cursor = conn.execute("PRAGMA table_info(expenses)")
            columns = [row["name"] for row in cursor.fetchall()]
            if "user_id" not in columns:
                conn.execute("ALTER TABLE expenses ADD COLUMN user_id INTEGER DEFAULT 1")
            if "payment_method" not in columns:
                conn.execute("ALTER TABLE expenses ADD COLUMN payment_method TEXT DEFAULT 'UPI'")
            if "notes" not in columns:
                conn.execute("ALTER TABLE expenses ADD COLUMN notes TEXT DEFAULT ''")
            if "receipt_path" not in columns:
                conn.execute("ALTER TABLE expenses ADD COLUMN receipt_path TEXT DEFAULT ''")
            if "created_at" not in columns:
                conn.execute("ALTER TABLE expenses ADD COLUMN created_at TEXT DEFAULT ''")

            # Budgets table
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS budgets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    category TEXT NOT NULL,
                    limit_amount REAL NOT NULL,
                    month TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
                );
                """
            )

            # Recurring expenses table
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS recurring (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    description TEXT NOT NULL,
                    amount REAL NOT NULL,
                    category TEXT NOT NULL,
                    frequency TEXT NOT NULL,
                    next_due_date TEXT NOT NULL,
                    is_active INTEGER DEFAULT 1,
                    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
                );
                """
            )

            # Financial Goals table
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS financial_goals (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    target_amount REAL NOT NULL,
                    current_amount REAL DEFAULT 0.0,
                    target_date TEXT,
                    category TEXT DEFAULT 'General',
                    created_at TEXT NOT NULL,
                    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
                );
                """
            )

            # Notifications table
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS notifications (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    message TEXT NOT NULL,
                    type TEXT DEFAULT 'info',
                    is_read INTEGER DEFAULT 0,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
                );
                """
            )

            # Contact & Support Messages
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS contact_messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    email TEXT NOT NULL,
                    subject TEXT NOT NULL,
                    message TEXT NOT NULL,
                    status TEXT DEFAULT 'unread',
                    created_at TEXT NOT NULL
                );
                """
            )

            # Audit Logs
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS audit_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER,
                    user_email TEXT,
                    action TEXT NOT NULL,
                    ip_address TEXT DEFAULT '127.0.0.1',
                    created_at TEXT NOT NULL
                );
                """
            )

            conn.commit()

    def _seed_default_users_and_data(self) -> None:
        with self._get_connection() as conn:
            now_iso = datetime.now().isoformat()

            # Seed Admin User
            admin_user = conn.execute("SELECT id, password_hash FROM users WHERE LOWER(email) = ?", ("admin@expensetracker.ai",)).fetchone()
            if not admin_user:
                conn.execute(
                    """
                    INSERT INTO users (name, email, password_hash, role, currency, monthly_budget, income, savings_goal, is_student_mode, monthly_allowance, theme, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 15000.0, 'light', ?)
                    """,
                    (
                        "Admin User",
                        "admin@expensetracker.ai",
                        generate_password_hash("admin123"),
                        "admin",
                        "₹",
                        50000.0,
                        95000.0,
                        250000.0,
                        now_iso,
                    ),
                )
            elif not check_password_hash(admin_user["password_hash"], "admin123"):
                conn.execute("UPDATE users SET password_hash = ?, theme = 'light' WHERE id = ?", (generate_password_hash("admin123"), admin_user["id"]))

            # Seed Demo / Guest User
            demo_user = conn.execute("SELECT id, password_hash FROM users WHERE LOWER(email) = ?", ("guest@expensetracker.ai",)).fetchone()
            if not demo_user:
                cursor = conn.execute(
                    """
                    INSERT INTO users (name, email, password_hash, role, currency, monthly_budget, income, savings_goal, is_student_mode, monthly_allowance, theme, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 15000.0, 'light', ?)
                    """,
                    (
                        "Priya Sharma (Demo)",
                        "guest@expensetracker.ai",
                        generate_password_hash("guest123"),
                        "user",
                        "₹",
                        30000.0,
                        65000.0,
                        150000.0,
                        now_iso,
                    ),
                )
                guest_id = cursor.lastrowid
            else:
                guest_id = demo_user["id"]
                if not check_password_hash(demo_user["password_hash"], "guest123"):
                    conn.execute("UPDATE users SET password_hash = ?, theme = 'light' WHERE id = ?", (generate_password_hash("guest123"), guest_id))

            # Ensure all orphaned expenses map to guest_id
            conn.execute("UPDATE expenses SET user_id = ? WHERE user_id IS NULL OR user_id = 0", (guest_id,))

            # Seed default budgets for guest user if not present
            budget_count = conn.execute("SELECT COUNT(*) as c FROM budgets WHERE user_id = ?", (guest_id,)).fetchone()["c"]
            current_month = date.today().strftime("%Y-%m")
            if budget_count == 0:
                sample_budgets = [
                    ("Food", 6000.0, current_month),
                    ("Travel", 4000.0, current_month),
                    ("Bills", 8000.0, current_month),
                    ("Entertainment", 3500.0, current_month),
                    ("Shopping", 5000.0, current_month),
                    ("Health", 3000.0, current_month),
                ]
                for cat, lim, mon in sample_budgets:
                    conn.execute(
                        "INSERT INTO budgets (user_id, category, limit_amount, month, created_at) VALUES (?, ?, ?, ?, ?)",
                        (guest_id, cat, lim, mon, now_iso),
                    )

            # Seed sample recurring bills with scheduled dates
            rec_count = conn.execute("SELECT COUNT(*) as c FROM recurring WHERE user_id = ?", (guest_id,)).fetchone()["c"]
            if rec_count == 0:
                curr_y, curr_m = date.today().year, date.today().month
                # Schedule bills across the current and upcoming weeks
                bill_1 = f"{curr_y}-{curr_m:02d}-15"
                bill_2 = f"{curr_y}-{curr_m:02d}-18"
                bill_3 = f"{curr_y}-{curr_m:02d}-22"
                bill_4 = f"{curr_y}-{curr_m:02d}-25"

                recurring_samples = [
                    ("Apartment Rent", 12000.0, "Bills", "Monthly", bill_1),
                    ("Personal Loan EMI", 4500.0, "Bills", "Monthly", bill_2),
                    ("Fiber Internet Broadband", 999.0, "Bills", "Monthly", bill_3),
                    ("Netflix 4K Subscription", 649.0, "Entertainment", "Monthly", bill_4),
                ]
                for desc, amt, cat, freq, due in recurring_samples:
                    conn.execute(
                        "INSERT INTO recurring (user_id, description, amount, category, frequency, next_due_date, is_active) VALUES (?, ?, ?, ?, ?, ?, 1)",
                        (guest_id, desc, amt, cat, freq, due),
                    )

            # Seed Financial Goal
            goal_count = conn.execute("SELECT COUNT(*) as c FROM financial_goals WHERE user_id = ?", (guest_id,)).fetchone()["c"]
            if goal_count == 0:
                conn.execute(
                    """
                    INSERT INTO financial_goals (user_id, title, target_amount, current_amount, target_date, category, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        guest_id,
                        "Emergency Fund & Safety Cushion",
                        50000.0,
                        36000.0,
                        f"{date.today().year}-12-31",
                        "Savings",
                        now_iso,
                    ),
                )

            # Seed initial notifications
            notif_count = conn.execute("SELECT COUNT(*) as c FROM notifications WHERE user_id = ?", (guest_id,)).fetchone()["c"]
            if notif_count == 0:
                sample_notifs = [
                    ("🎉 Welcome to ExpenseAI Copilot!", "Your financial decision engine is live. Ask 'Can I afford shoes for ₹3,500?' or check your health score.", "success", 0),
                    ("💡 AI Money Leak Detected", "You have made 34 micro-purchases under ₹250 this month. Review your money leaks in Decision Studio.", "info", 0),
                    ("⚠️ Cash-Flow Timing Alert", "Apartment Rent (₹12,000) is scheduled for the 15th. Ensure available balance meets upcoming obligations.", "warning", 0),
                ]
                for tit, msg, n_type, is_r in sample_notifs:
                    conn.execute(
                        "INSERT INTO notifications (user_id, title, message, type, is_read, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                        (guest_id, tit, msg, n_type, is_r, now_iso),
                    )

            conn.commit()

    # ==================== USER MANAGEMENT ====================

    def create_user(self, name: str, email: str, password: str, role: str = "user", currency: str = "₹") -> Optional[int]:
        email_clean = email.strip().lower()
        pwd_hash = generate_password_hash(password)
        now_iso = datetime.now().isoformat()
        with self._get_connection() as conn:
            try:
                cursor = conn.execute(
                    """
                    INSERT INTO users (name, email, password_hash, role, currency, monthly_budget, income, savings_goal, is_student_mode, monthly_allowance, theme, created_at)
                    VALUES (?, ?, ?, ?, ?, 25000.0, 50000.0, 100000.0, 0, 15000.0, 'light', ?)
                    """,
                    (name.strip(), email_clean, pwd_hash, role, currency, now_iso),
                )
                conn.commit()
                return int(cursor.lastrowid)
            except sqlite3.IntegrityError:
                return None

    def get_user_by_email(self, email: str) -> Optional[Dict]:
        if not email:
            return None
        with self._get_connection() as conn:
            row = conn.execute("SELECT * FROM users WHERE LOWER(email) = LOWER(?)", (email.strip(),)).fetchone()
            return dict(row) if row else None

    def get_user_by_id(self, user_id: int) -> Optional[Dict]:
        with self._get_connection() as conn:
            row = conn.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()
            return dict(row) if row else None

    def authenticate_user(self, email: str, password: str) -> Optional[Dict]:
        if not email or not password:
            return None
        user = self.get_user_by_email(email.strip().lower())
        if user and check_password_hash(user["password_hash"], password):
            return user
        return None

    def update_user_profile(
        self,
        user_id: int,
        name: str,
        currency: str,
        monthly_budget: float,
        income: float = 50000.0,
        savings_goal: float = 100000.0,
        is_student_mode: int = 0,
        monthly_allowance: float = 15000.0,
        theme: str = "dark",
    ) -> bool:
        with self._get_connection() as conn:
            conn.execute(
                """
                UPDATE users
                SET name = ?, currency = ?, monthly_budget = ?, income = ?, savings_goal = ?, is_student_mode = ?, monthly_allowance = ?, theme = ?
                WHERE id = ?
                """,
                (name.strip(), currency, monthly_budget, income, savings_goal, is_student_mode, monthly_allowance, theme, user_id),
            )
            conn.commit()
            return True

    def update_user_password(self, user_id: int, new_password: str) -> bool:
        with self._get_connection() as conn:
            conn.execute(
                "UPDATE users SET password_hash = ? WHERE id = ?",
                (generate_password_hash(new_password), user_id),
            )
            conn.commit()
            return True

    def list_all_users(self) -> List[Dict]:
        with self._get_connection() as conn:
            rows = conn.execute(
                """
                SELECT u.id, u.name, u.email, u.role, u.currency, u.monthly_budget, u.income, u.created_at,
                       COUNT(e.id) as expense_count,
                       COALESCE(SUM(e.amount), 0) as total_spent
                FROM users u
                LEFT JOIN expenses e ON u.id = e.user_id
                GROUP BY u.id
                ORDER BY u.id ASC
                """
            ).fetchall()
            return [dict(row) for row in rows]

    def delete_user(self, user_id: int) -> bool:
        with self._get_connection() as conn:
            conn.execute("DELETE FROM expenses WHERE user_id = ?", (user_id,))
            conn.execute("DELETE FROM budgets WHERE user_id = ?", (user_id,))
            conn.execute("DELETE FROM recurring WHERE user_id = ?", (user_id,))
            conn.execute("DELETE FROM financial_goals WHERE user_id = ?", (user_id,))
            conn.execute("DELETE FROM notifications WHERE user_id = ?", (user_id,))
            conn.execute("DELETE FROM users WHERE id = ?", (user_id,))
            conn.commit()
            return True

    # ==================== EXPENSES CRUD ====================

    def add_expense(
        self,
        date_iso: str,
        amount: float,
        description: str,
        category: str,
        user_id: int = 1,
        payment_method: str = "UPI",
        notes: str = "",
        receipt_path: str = "",
    ) -> int:
        now_iso = datetime.now().isoformat()
        with self._get_connection() as conn:
            cursor = conn.execute(
                """
                INSERT INTO expenses(user_id, date, amount, description, category, payment_method, notes, receipt_path, created_at)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (user_id, date_iso, amount, description.strip(), category.strip(), payment_method, notes.strip(), receipt_path, now_iso),
            )
            conn.commit()
            expense_id = int(cursor.lastrowid)

        # Check if this expense pushed category over budget
        self._check_budget_threshold(user_id, category, date_iso[:7])
        return expense_id

    def get_expense(self, expense_id: int, user_id: Optional[int] = None) -> Optional[Dict]:
        with self._get_connection() as conn:
            if user_id:
                row = conn.execute("SELECT * FROM expenses WHERE id = ? AND user_id = ?", (expense_id, user_id)).fetchone()
            else:
                row = conn.execute("SELECT * FROM expenses WHERE id = ?", (expense_id,)).fetchone()
            return dict(row) if row else None

    def update_expense(
        self,
        expense_id: int,
        user_id: int,
        date_iso: str,
        amount: float,
        description: str,
        category: str,
        payment_method: str = "UPI",
        notes: str = "",
        receipt_path: Optional[str] = None,
    ) -> bool:
        with self._get_connection() as conn:
            if receipt_path is not None:
                conn.execute(
                    """
                    UPDATE expenses
                    SET date = ?, amount = ?, description = ?, category = ?, payment_method = ?, notes = ?, receipt_path = ?
                    WHERE id = ? AND user_id = ?
                    """,
                    (date_iso, amount, description.strip(), category.strip(), payment_method, notes.strip(), receipt_path, expense_id, user_id),
                )
            else:
                conn.execute(
                    """
                    UPDATE expenses
                    SET date = ?, amount = ?, description = ?, category = ?, payment_method = ?, notes = ?
                    WHERE id = ? AND user_id = ?
                    """,
                    (date_iso, amount, description.strip(), category.strip(), payment_method, notes.strip(), expense_id, user_id),
                )
            conn.commit()
            return True

    def delete_expense(self, expense_id: int, user_id: Optional[int] = None) -> bool:
        with self._get_connection() as conn:
            if user_id:
                conn.execute("DELETE FROM expenses WHERE id = ? AND user_id = ?", (expense_id, user_id))
            else:
                conn.execute("DELETE FROM expenses WHERE id = ?", (expense_id,))
            conn.commit()
            return True

    def list_expenses(
        self,
        user_id: Optional[int] = None,
        start_date: Optional[str] = None,
        end_date: Optional[str] = None,
        category: Optional[str] = None,
        search_query: Optional[str] = None,
        min_amount: Optional[float] = None,
        max_amount: Optional[float] = None,
        payment_method: Optional[str] = None,
        sort_by: str = "date_desc",
        limit: int = 50,
        offset: int = 0,
    ) -> List[Dict]:
        query = "SELECT * FROM expenses"
        clauses: List[str] = []
        params: List[Any] = []

        if user_id is not None:
            clauses.append("user_id = ?")
            params.append(user_id)
        if start_date:
            clauses.append("date >= ?")
            params.append(start_date)
        if end_date:
            clauses.append("date <= ?")
            params.append(end_date)
        if category and category.lower() != "all":
            clauses.append("LOWER(category) = LOWER(?)")
            params.append(category)
        if payment_method and payment_method.lower() != "all":
            clauses.append("LOWER(payment_method) = LOWER(?)")
            params.append(payment_method)
        if min_amount is not None:
            clauses.append("amount >= ?")
            params.append(min_amount)
        if max_amount is not None:
            clauses.append("amount <= ?")
            params.append(max_amount)
        if search_query:
            clauses.append("(description LIKE ? OR notes LIKE ? OR category LIKE ?)")
            term = f"%{search_query.strip()}%"
            params.extend([term, term, term])

        if clauses:
            query += " WHERE " + " AND ".join(clauses)

        sort_map = {
            "date_desc": "date DESC, id DESC",
            "date_asc": "date ASC, id ASC",
            "amount_desc": "amount DESC, date DESC",
            "amount_asc": "amount ASC, date DESC",
        }
        order_clause = sort_map.get(sort_by, "date DESC, id DESC")
        query += f" ORDER BY {order_clause} LIMIT ? OFFSET ?"
        params.extend([limit, offset])

        with self._get_connection() as conn:
            rows = conn.execute(query, params).fetchall()
            return [dict(row) for row in rows]

    def count_expenses(
        self,
        user_id: Optional[int] = None,
        start_date: Optional[str] = None,
        end_date: Optional[str] = None,
        category: Optional[str] = None,
        search_query: Optional[str] = None,
        min_amount: Optional[float] = None,
        max_amount: Optional[float] = None,
        payment_method: Optional[str] = None,
    ) -> int:
        query = "SELECT COUNT(*) as cnt FROM expenses"
        clauses: List[str] = []
        params: List[Any] = []

        if user_id is not None:
            clauses.append("user_id = ?")
            params.append(user_id)
        if start_date:
            clauses.append("date >= ?")
            params.append(start_date)
        if end_date:
            clauses.append("date <= ?")
            params.append(end_date)
        if category and category.lower() != "all":
            clauses.append("LOWER(category) = LOWER(?)")
            params.append(category)
        if payment_method and payment_method.lower() != "all":
            clauses.append("LOWER(payment_method) = LOWER(?)")
            params.append(payment_method)
        if min_amount is not None:
            clauses.append("amount >= ?")
            params.append(min_amount)
        if max_amount is not None:
            clauses.append("amount <= ?")
            params.append(max_amount)
        if search_query:
            clauses.append("(description LIKE ? OR notes LIKE ? OR category LIKE ?)")
            term = f"%{search_query.strip()}%"
            params.extend([term, term, term])

        if clauses:
            query += " WHERE " + " AND ".join(clauses)

        with self._get_connection() as conn:
            row = conn.execute(query, params).fetchone()
            return int(row["cnt"]) if row else 0

    # ==================== SUMMARY & ANALYTICS ====================

    def _date_range_for_period(self, period: str) -> Tuple[str, str]:
        today = date.today()
        if period == "day":
            start = today
        elif period == "week":
            start = today - timedelta(days=today.weekday())
        elif period == "month":
            start = today.replace(day=1)
        elif period == "year":
            start = today.replace(month=1, day=1)
        elif period == "all":
            return ("0001-01-01", "9999-12-31")
        else:
            start = today.replace(day=1)
        return (start.isoformat(), today.isoformat())

    def get_summary(self, period: str = "month", user_id: Optional[int] = None) -> Dict:
        start_date, end_date = self._date_range_for_period(period)
        params_total: List[Any] = [start_date, end_date]
        user_clause = ""
        if user_id is not None:
            user_clause = " AND user_id = ?"
            params_total.append(user_id)

        with self._get_connection() as conn:
            total_row = conn.execute(
                f"SELECT COALESCE(SUM(amount), 0) AS total, COUNT(*) as count FROM expenses WHERE date BETWEEN ? AND ?{user_clause}",
                params_total,
            ).fetchone()

            by_category_rows = conn.execute(
                f"""
                SELECT category, COALESCE(SUM(amount), 0) AS total, COUNT(*) as count
                FROM expenses
                WHERE date BETWEEN ? AND ?{user_clause}
                GROUP BY category
                ORDER BY total DESC
                """,
                params_total,
            ).fetchall()

            by_payment_rows = conn.execute(
                f"""
                SELECT payment_method, COALESCE(SUM(amount), 0) AS total, COUNT(*) as count
                FROM expenses
                WHERE date BETWEEN ? AND ?{user_clause}
                GROUP BY payment_method
                ORDER BY total DESC
                """,
                params_total,
            ).fetchall()

        by_category = {row["category"]: float(row["total"]) for row in by_category_rows}
        by_category_counts = {row["category"]: int(row["count"]) for row in by_category_rows}
        by_payment = {row["payment_method"] or "Other": float(row["total"]) for row in by_payment_rows}

        total_amount = float(total_row["total"]) if total_row else 0.0
        total_count = int(total_row["count"]) if total_row else 0

        days_count = max(1, (date.fromisoformat(end_date) - date.fromisoformat(start_date)).days + 1) if period != "all" else 30
        daily_avg = total_amount / days_count if days_count > 0 else total_amount

        return {
            "total": total_amount,
            "count": total_count,
            "daily_average": daily_avg,
            "by_category": by_category,
            "by_category_counts": by_category_counts,
            "by_payment": by_payment,
            "start_date": start_date,
            "end_date": end_date,
            "period": period,
        }

    def monthly_totals(self, user_id: Optional[int] = None) -> Dict[str, float]:
        query = "SELECT substr(date, 1, 7) AS ym, SUM(amount) AS total FROM expenses"
        params: List[Any] = []
        if user_id is not None:
            query += " WHERE user_id = ?"
            params.append(user_id)
        query += " GROUP BY ym ORDER BY ym ASC"

        with self._get_connection() as conn:
            rows = conn.execute(query, params).fetchall()
            return {row["ym"]: float(row["total"]) for row in rows}

    def monthly_totals_by_category(self, user_id: Optional[int] = None) -> Dict[str, Dict[str, float]]:
        query = "SELECT substr(date, 1, 7) AS ym, category, SUM(amount) AS total FROM expenses"
        params: List[Any] = []
        if user_id is not None:
            query += " WHERE user_id = ?"
            params.append(user_id)
        query += " GROUP BY ym, category ORDER BY ym ASC, category ASC"

        with self._get_connection() as conn:
            rows = conn.execute(query, params).fetchall()
            result: Dict[str, Dict[str, float]] = {}
            for row in rows:
                ym = row["ym"]
                cat = row["category"]
                tot = float(row["total"])
                if ym not in result:
                    result[ym] = {}
                result[ym][cat] = tot
            return result

    def get_daily_totals_current_month(self, user_id: Optional[int] = None) -> Dict[str, float]:
        first_day = date.today().replace(day=1).isoformat()
        today_iso = date.today().isoformat()
        query = "SELECT date, SUM(amount) AS total FROM expenses WHERE date BETWEEN ? AND ?"
        params: List[Any] = [first_day, today_iso]
        if user_id is not None:
            query += " AND user_id = ?"
            params.append(user_id)
        query += " GROUP BY date ORDER BY date ASC"

        with self._get_connection() as conn:
            rows = conn.execute(query, params).fetchall()
            return {row["date"]: float(row["total"]) for row in rows}

    def get_weekday_spending_distribution(self, user_id: Optional[int] = None) -> Dict[str, float]:
        query = "SELECT date, amount FROM expenses"
        params: List[Any] = []
        if user_id is not None:
            query += " WHERE user_id = ?"
            params.append(user_id)

        weekdays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
        dist = {w: 0.0 for w in weekdays}

        with self._get_connection() as conn:
            rows = conn.execute(query, params).fetchall()
            for r in rows:
                try:
                    d = date.fromisoformat(r["date"])
                    w_name = weekdays[d.weekday()]
                    dist[w_name] += float(r["amount"])
                except Exception:
                    pass
        return dist

    # ==================== BUDGET MANAGEMENT ====================

    def get_user_budgets(self, user_id: int, month: Optional[str] = None) -> List[Dict]:
        if not month:
            month = date.today().strftime("%Y-%m")
        with self._get_connection() as conn:
            budgets = conn.execute(
                "SELECT * FROM budgets WHERE user_id = ? AND (month = ? OR month = 'all')",
                (user_id, month),
            ).fetchall()

            first_day = f"{month}-01"
            last_day = f"{month}-31"
            results = []
            for b in budgets:
                cat = b["category"]
                spend_row = conn.execute(
                    "SELECT COALESCE(SUM(amount), 0) AS spent FROM expenses WHERE user_id = ? AND category = ? AND date BETWEEN ? AND ?",
                    (user_id, cat, first_day, last_day),
                ).fetchone()
                spent = float(spend_row["spent"]) if spend_row else 0.0
                limit_amt = float(b["limit_amount"])
                pct = round((spent / limit_amt * 100), 1) if limit_amt > 0 else 0
                results.append({
                    "id": b["id"],
                    "category": cat,
                    "limit_amount": limit_amt,
                    "spent": spent,
                    "remaining": max(0.0, limit_amt - spent),
                    "percentage": min(100.0, pct),
                    "is_exceeded": spent > limit_amt,
                    "month": b["month"],
                })
            return results

    def set_budget(self, user_id: int, category: str, limit_amount: float, month: Optional[str] = None) -> int:
        if not month:
            month = date.today().strftime("%Y-%m")
        now_iso = datetime.now().isoformat()
        with self._get_connection() as conn:
            existing = conn.execute(
                "SELECT id FROM budgets WHERE user_id = ? AND category = ? AND month = ?",
                (user_id, category.strip(), month),
            ).fetchone()
            if existing:
                conn.execute(
                    "UPDATE budgets SET limit_amount = ? WHERE id = ?",
                    (limit_amount, existing["id"]),
                )
                conn.commit()
                return existing["id"]
            else:
                cursor = conn.execute(
                    "INSERT INTO budgets (user_id, category, limit_amount, month, created_at) VALUES (?, ?, ?, ?, ?)",
                    (user_id, category.strip(), limit_amount, month, now_iso),
                )
                conn.commit()
                return cursor.lastrowid

    def delete_budget(self, budget_id: int, user_id: int) -> bool:
        with self._get_connection() as conn:
            conn.execute("DELETE FROM budgets WHERE id = ? AND user_id = ?", (budget_id, user_id))
            conn.commit()
            return True

    def _check_budget_threshold(self, user_id: int, category: str, month: str) -> None:
        with self._get_connection() as conn:
            b = conn.execute(
                "SELECT * FROM budgets WHERE user_id = ? AND category = ? AND (month = ? OR month = 'all')",
                (user_id, category, month),
            ).fetchone()
            if not b:
                return
            first_day = f"{month}-01"
            last_day = f"{month}-31"
            spend_row = conn.execute(
                "SELECT COALESCE(SUM(amount), 0) as total FROM expenses WHERE user_id = ? AND category = ? AND date BETWEEN ? AND ?",
                (user_id, category, first_day, last_day),
            ).fetchone()
            spent = float(spend_row["total"])
            limit_amt = float(b["limit_amount"])

            if spent >= limit_amt:
                self.add_notification(
                    user_id=user_id,
                    title=f"🚨 Budget Exceeded: {category}",
                    message=f"You have spent {spent:.2f} of your {limit_amt:.2f} budget for {category} this month!",
                    type="danger",
                )
            elif spent >= 0.85 * limit_amt:
                self.add_notification(
                    user_id=user_id,
                    title=f"⚠️ Budget Warning: {category}",
                    message=f"You have used {round(spent/limit_amt*100)}% of your {category} monthly budget.",
                    type="warning",
                )

    # ==================== RECURRING BILLS ====================

    def list_recurring(self, user_id: int) -> List[Dict]:
        with self._get_connection() as conn:
            rows = conn.execute("SELECT * FROM recurring WHERE user_id = ? ORDER BY next_due_date ASC", (user_id,)).fetchall()
            return [dict(r) for r in rows]

    def add_recurring(self, user_id: int, description: str, amount: float, category: str, frequency: str, next_due_date: str) -> int:
        with self._get_connection() as conn:
            cursor = conn.execute(
                "INSERT INTO recurring (user_id, description, amount, category, frequency, next_due_date, is_active) VALUES (?, ?, ?, ?, ?, ?, 1)",
                (user_id, description.strip(), amount, category.strip(), frequency, next_due_date),
            )
            conn.commit()
            return cursor.lastrowid

    def delete_recurring(self, recurring_id: int, user_id: int) -> bool:
        with self._get_connection() as conn:
            conn.execute("DELETE FROM recurring WHERE id = ? AND user_id = ?", (recurring_id, user_id))
            conn.commit()
            return True

    # ==================== FINANCIAL GOALS ====================

    def list_goals(self, user_id: int) -> List[Dict]:
        with self._get_connection() as conn:
            rows = conn.execute("SELECT * FROM financial_goals WHERE user_id = ? ORDER BY id ASC", (user_id,)).fetchall()
            results = []
            for r in rows:
                d = dict(r)
                pct = round((d["current_amount"] / d["target_amount"] * 100), 1) if d["target_amount"] > 0 else 0
                d["percentage"] = min(100.0, pct)
                results.append(d)
            return results

    def add_goal(self, user_id: int, title: str, target_amount: float, current_amount: float = 0.0, target_date: Optional[str] = None, category: str = "Savings") -> int:
        now_iso = datetime.now().isoformat()
        with self._get_connection() as conn:
            cursor = conn.execute(
                """
                INSERT INTO financial_goals (user_id, title, target_amount, current_amount, target_date, category, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (user_id, title.strip(), target_amount, current_amount, target_date, category, now_iso),
            )
            conn.commit()
            return cursor.lastrowid

    # ==================== NOTIFICATIONS ====================

    def add_notification(self, user_id: int, title: str, message: str, type: str = "info") -> int:
        now_iso = datetime.now().isoformat()
        with self._get_connection() as conn:
            cursor = conn.execute(
                "INSERT INTO notifications (user_id, title, message, type, is_read, created_at) VALUES (?, ?, ?, ?, 0, ?)",
                (user_id, title, message, type, now_iso),
            )
            conn.commit()
            return cursor.lastrowid

    def get_notifications(self, user_id: int, unread_only: bool = False, limit: int = 20) -> List[Dict]:
        query = "SELECT * FROM notifications WHERE user_id = ?"
        params: List[Any] = [user_id]
        if unread_only:
            query += " AND is_read = 0"
        query += " ORDER BY id DESC LIMIT ?"
        params.append(limit)
        with self._get_connection() as conn:
            rows = conn.execute(query, params).fetchall()
            return [dict(r) for r in rows]

    def count_unread_notifications(self, user_id: int) -> int:
        with self._get_connection() as conn:
            row = conn.execute("SELECT COUNT(*) as c FROM notifications WHERE user_id = ? AND is_read = 0", (user_id,)).fetchone()
            return int(row["c"]) if row else 0

    def mark_all_notifications_read(self, user_id: int) -> bool:
        with self._get_connection() as conn:
            conn.execute("UPDATE notifications SET is_read = 1 WHERE user_id = ?", (user_id,))
            conn.commit()
            return True

    def clear_all_notifications(self, user_id: int) -> bool:
        with self._get_connection() as conn:
            conn.execute("DELETE FROM notifications WHERE user_id = ?", (user_id,))
            conn.commit()
            return True

    # ==================== CONTACT MESSAGES ====================

    def save_contact_message(self, name: str, email: str, subject: str, message: str) -> int:
        now_iso = datetime.now().isoformat()
        with self._get_connection() as conn:
            cursor = conn.execute(
                "INSERT INTO contact_messages (name, email, subject, message, status, created_at) VALUES (?, ?, ?, ?, 'unread', ?)",
                (name.strip(), email.strip(), subject.strip(), message.strip(), now_iso),
            )
            conn.commit()
            return cursor.lastrowid

    def list_contact_messages(self, limit: int = 50) -> List[Dict]:
        with self._get_connection() as conn:
            rows = conn.execute("SELECT * FROM contact_messages ORDER BY id DESC LIMIT ?", (limit,)).fetchall()
            return [dict(r) for r in rows]

    # ==================== AUDIT & ADMIN ====================

    def log_audit(self, action: str, user_id: Optional[int] = None, user_email: Optional[str] = None, ip_address: str = "127.0.0.1") -> None:
        now_iso = datetime.now().isoformat()
        with self._get_connection() as conn:
            conn.execute(
                "INSERT INTO audit_logs (user_id, user_email, action, ip_address, created_at) VALUES (?, ?, ?, ?, ?)",
                (user_id, user_email, action, ip_address, now_iso),
            )
            conn.commit()

    def get_admin_metrics(self) -> Dict[str, Any]:
        with self._get_connection() as conn:
            total_users = conn.execute("SELECT COUNT(*) as c FROM users").fetchone()["c"]
            total_expenses = conn.execute("SELECT COUNT(*) as c, COALESCE(SUM(amount), 0) as s FROM expenses").fetchone()
            total_budgets = conn.execute("SELECT COUNT(*) as c FROM budgets").fetchone()["c"]
            total_messages = conn.execute("SELECT COUNT(*) as c FROM contact_messages").fetchone()["c"]
            recent_logs = conn.execute("SELECT * FROM audit_logs ORDER BY id DESC LIMIT 15").fetchall()
            users = self.list_all_users()
            messages = self.list_contact_messages(limit=10)

            return {
                "total_users": total_users,
                "total_expenses_count": total_expenses["c"],
                "total_platform_spend": float(total_expenses["s"]),
                "total_budgets": total_budgets,
                "total_messages": total_messages,
                "recent_logs": [dict(l) for l in recent_logs],
                "users": users,
                "messages": messages,
            }

    # ==================== EXPORT & DATA BACKUP ====================

    def export_csv(self, path: str, user_id: Optional[int] = None) -> str:
        query = "SELECT id, date, amount, description, category, payment_method, notes FROM expenses"
        params: List[Any] = []
        if user_id is not None:
            query += " WHERE user_id = ?"
            params.append(user_id)
        query += " ORDER BY date DESC, id DESC"

        with self._get_connection() as conn:
            rows = conn.execute(query, params).fetchall()

        with open(path, "w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow(["ID", "Date", "Amount", "Description", "Category", "Payment Method", "Notes"])
            for row in rows:
                writer.writerow([row["id"], row["date"], row["amount"], row["description"], row["category"], row["payment_method"], row["notes"]])
        return os.path.abspath(path)

    def export_json(self, user_id: Optional[int] = None) -> str:
        query = "SELECT * FROM expenses"
        params: List[Any] = []
        if user_id is not None:
            query += " WHERE user_id = ?"
            params.append(user_id)
        query += " ORDER BY date DESC, id DESC"

        with self._get_connection() as conn:
            rows = conn.execute(query, params).fetchall()
            return json.dumps([dict(row) for row in rows], indent=2)
