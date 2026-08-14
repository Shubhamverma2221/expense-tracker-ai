import functools
import os
from datetime import date, datetime, timedelta
from typing import Any, Dict, Optional

from flask import (
    Flask,
    flash,
    jsonify,
    redirect,
    render_template,
    request,
    send_file,
    session,
    url_for,
)

from bot import ChatBot
from categorizer import CATEGORY_METADATA, CategoryRules
from db import ExpenseDB
from financial_engine import FinancialIntelligenceEngine
from predictor import Predictor

app = Flask(__name__)
app.secret_key = os.environ.get("SECRET_KEY", "expensetracker-secret-key-prod-2026")

db = ExpenseDB(db_path=os.environ.get("DATABASE_PATH", "expenses.db"))
rules = CategoryRules(path="categories.json")
predictor = Predictor(db)
engine = FinancialIntelligenceEngine(db, rules)

# Auto-seed database if empty on fresh Render/Cloud deployment
try:
    if db.count_expenses() == 0:
        import seed_huge_dataset
        seed_huge_dataset.seed_huge_dataset(1500)
except Exception as e:
    print("Auto-seed notice on startup:", e)


# ==================== AUTH DECORATORS & HELPERS ====================

def login_required(view):
    @functools.wraps(view)
    def wrapped_view(**kwargs):
        if "user_id" not in session:
            flash("Please log in or try the instant demo to access this feature.", "warning")
            return redirect(url_for("login", next=request.path))
        return view(**kwargs)
    return wrapped_view


def admin_required(view):
    @functools.wraps(view)
    def wrapped_view(**kwargs):
        if "user_id" not in session or session.get("user_role") != "admin":
            flash("Administrator access required for this area.", "danger")
            return redirect(url_for("dashboard"))
        return view(**kwargs)
    return wrapped_view


@app.context_processor
def inject_global_context():
    unread_count = 0
    curr_symbol = session.get("user_currency", "₹")
    if "user_id" in session:
        unread_count = db.count_unread_notifications(session["user_id"])
    return {
        "unread_notifs_count": unread_count,
        "today_date": date.today().isoformat(),
        "cat_meta": CATEGORY_METADATA,
        "currency": curr_symbol,
    }


# ==================== PUBLIC & AUTH ROUTES ====================

@app.route("/")
def index():
    if "user_id" in session:
        return redirect(url_for("dashboard"))
    return render_template("landing.html", title="AI Expense Tracker & Financial Intelligence")


@app.route("/landing")
def landing():
    return render_template("landing.html", title="AI Expense Tracker - Predictive Finance")


@app.route("/login", methods=["GET", "POST"])
def login():
    if "user_id" in session:
        return redirect(url_for("dashboard"))

    if request.method == "POST":
        email = request.form.get("email", "").strip().lower()
        password = request.form.get("password", "")
        remember = request.form.get("remember")
        next_url = request.form.get("next") or request.args.get("next")

        if not email or not password:
            flash("Please enter both your email address and password.", "warning")
            return render_template("auth/login.html", email=email)

        user = db.authenticate_user(email, password)
        if user:
            session["user_id"] = user["id"]
            session["user_name"] = user["name"]
            session["user_email"] = user["email"]
            session["user_role"] = user["role"]
            session["user_currency"] = user.get("currency", "₹")

            if remember:
                session.permanent = True
                app.permanent_session_lifetime = timedelta(days=30)

            db.log_audit("User Login", user_id=user["id"], user_email=user["email"], ip_address=request.remote_addr or "127.0.0.1")
            flash(f"Welcome back, {user['name'].split(' ')[0]}!", "success")

            # Safe redirect: internal paths only
            if next_url and next_url.startswith("/") and not next_url.startswith("//"):
                return redirect(next_url)
            return redirect(url_for("dashboard"))
        else:
            flash("Invalid email address or password. Please verify your credentials.", "danger")
            return render_template("auth/login.html", email=email)

    return render_template("auth/login.html", title="Sign In - AI Expense Tracker")


@app.route("/guest-login")
def guest_login():
    """1-Click instant login to demo account with sample pre-seeded data."""
    demo_user = db.get_user_by_email("guest@expensetracker.ai")
    if not demo_user:
        db._seed_default_users_and_data()
        demo_user = db.get_user_by_email("guest@expensetracker.ai")

    if demo_user:
        session["user_id"] = demo_user["id"]
        session["user_name"] = demo_user["name"]
        session["user_email"] = demo_user["email"]
        session["user_role"] = demo_user["role"]
        session["user_currency"] = demo_user.get("currency", "₹")
        db.log_audit("Demo Login", user_id=demo_user["id"], user_email=demo_user["email"], ip_address=request.remote_addr or "127.0.0.1")
        flash("Logged into Demo Account! Feel free to explore all features.", "success")
        return redirect(url_for("dashboard"))

    flash("Could not initialize guest demo session.", "danger")
    return redirect(url_for("login"))


@app.route("/signup", methods=["GET", "POST"])
def signup():
    if "user_id" in session:
        return redirect(url_for("dashboard"))

    if request.method == "POST":
        name = request.form.get("name", "").strip()
        email = request.form.get("email", "").strip().lower()
        password = request.form.get("password", "")
        confirm_password = request.form.get("confirm_password", "")
        currency = request.form.get("currency", "₹")

        if len(name) < 2:
            flash("Please enter a valid full name.", "danger")
            return render_template("auth/signup.html", name=name, email=email)

        if len(password) < 6:
            flash("Password must be at least 6 characters long.", "danger")
            return render_template("auth/signup.html", name=name, email=email)

        if password != confirm_password:
            flash("Passwords do not match.", "danger")
            return render_template("auth/signup.html", name=name, email=email)

        existing = db.get_user_by_email(email)
        if existing:
            flash("An account with this email address already exists.", "warning")
            return render_template("auth/signup.html", name=name, email=email)

        user_id = db.create_user(name=name, email=email, password=password, currency=currency)
        if user_id:
            session["user_id"] = user_id
            session["user_name"] = name
            session["user_email"] = email
            session["user_role"] = "user"
            session["user_currency"] = currency

            # Seed default budgets for new user
            curr_month = date.today().strftime("%Y-%m")
            db.set_budget(user_id, "Food", 6000.0, curr_month)
            db.set_budget(user_id, "Bills", 8000.0, curr_month)
            db.set_budget(user_id, "Shopping", 5000.0, curr_month)

            db.add_notification(user_id, "🎉 Welcome to ExpenseAI!", "Your account has been created. Start logging your daily expenses to unlock smart AI insights.", "success")
            db.log_audit("User Registration", user_id=user_id, user_email=email, ip_address=request.remote_addr or "127.0.0.1")

            flash(f"Welcome to ExpenseAI, {name}! Your workspace is ready.", "success")
            return redirect(url_for("dashboard"))
        else:
            flash("An error occurred during account creation. Please try again.", "danger")

    return render_template("auth/signup.html", title="Create Account - AI Expense Tracker")


@app.route("/forgot-password", methods=["GET", "POST"])
def forgot_password():
    if request.method == "POST":
        email = request.form.get("email", "").strip()
        flash(f"If an account exists for {email}, a secure password reset link has been dispatched.", "info")
        return redirect(url_for("login"))
    return render_template("auth/forgot_password.html", title="Reset Password")


@app.route("/logout")
def logout():
    uid = session.get("user_id")
    uemail = session.get("user_email")
    if uid:
        db.log_audit("User Logout", user_id=uid, user_email=uemail, ip_address=request.remote_addr or "127.0.0.1")
    session.clear()
    flash("You have been signed out safely.", "info")
    return redirect(url_for("landing"))


# ==================== AUTHENTICATED CORE PAGES ====================

@app.route("/dashboard")
@login_required
def dashboard():
    uid = session["user_id"]
    user = db.get_user_by_id(uid)
    curr = user.get("currency", "₹") if user else "₹"
    session["user_currency"] = curr

    summary_month = db.get_summary("month", user_id=uid)
    recent_expenses = db.list_expenses(user_id=uid, limit=6)
    forecast = predictor.predict_next_month(user_id=uid)
    budgets = db.get_user_budgets(uid)
    upcoming_bills = db.list_recurring(uid)

    # Decision Intelligence Outputs
    health_score = engine.calculate_health_score(uid)
    safe_daily_spend = engine.calculate_safe_daily_spend(uid)
    cashflow_alerts = engine.detect_cashflow_collisions(uid)

    user_monthly_budget = float(user.get("monthly_budget", 25000.0)) if user else 25000.0
    budget_remaining = max(0.0, user_monthly_budget - summary_month["total"])
    budget_used_pct = round((summary_month["total"] / user_monthly_budget * 100), 1) if user_monthly_budget > 0 else 0

    monthly_map = db.monthly_totals(user_id=uid)
    monthly_trend_labels = list(monthly_map.keys())[-6:] if monthly_map else [date.today().strftime("%Y-%m")]
    monthly_trend_values = [monthly_map[k] for k in monthly_trend_labels] if monthly_map else [0.0]

    by_cat = summary_month.get("by_category", {})
    category_chart_labels = list(by_cat.keys())
    category_chart_values = list(by_cat.values())
    category_chart_colors = [CATEGORY_METADATA.get(c, {}).get("color", "#6366f1") for c in category_chart_labels]

    return render_template(
        "index.html",
        title="Dashboard - AI Expense Tracker",
        user=user,
        summary=summary_month,
        recent_expenses=recent_expenses,
        forecast=forecast,
        budgets=budgets,
        upcoming_bills=upcoming_bills,
        health_score=health_score,
        safe_daily_spend=safe_daily_spend,
        cashflow_alerts=cashflow_alerts,
        user_monthly_budget=user_monthly_budget,
        budget_remaining=budget_remaining,
        budget_used_pct=budget_used_pct,
        day_of_month=date.today().day,
        current_month_name=date.today().strftime("%B %Y"),
        monthly_trend_labels=monthly_trend_labels,
        monthly_trend_values=monthly_trend_values,
        category_chart_labels=category_chart_labels,
        category_chart_values=category_chart_values,
        category_chart_colors=category_chart_colors,
    )


@app.route("/decision-studio")
@login_required
def decision_studio_page():
    uid = session["user_id"]
    user = db.get_user_by_id(uid)
    health_score = engine.calculate_health_score(uid)
    safe_daily_spend = engine.calculate_safe_daily_spend(uid)
    cashflow_collisions = engine.detect_cashflow_collisions(uid)
    money_leaks = engine.detect_money_leaks(uid)

    return render_template(
        "decision_studio.html",
        title="Decision & Risk Studio - ExpenseAI",
        user=user,
        health_score=health_score,
        safe_daily_spend=safe_daily_spend,
        cashflow_collisions=cashflow_collisions,
        money_leaks=money_leaks,
    )


@app.route("/add", methods=["GET", "POST"])
@login_required
def add():
    uid = session["user_id"]
    if request.method == "POST":
        amount = float(request.form.get("amount", "0") or 0)
        description = request.form.get("description", "").strip()
        date_iso = request.form.get("date", date.today().isoformat()) or date.today().isoformat()
        category = request.form.get("category", "").strip()
        payment_method = request.form.get("payment_method", "UPI")
        notes = request.form.get("notes", "").strip()

        if not category:
            category = rules.categorize(description)

        if amount <= 0 or not description:
            flash("Please specify a valid transaction amount and description.", "danger")
            return redirect(url_for("add"))

        expense_id = db.add_expense(
            date_iso=date_iso,
            amount=amount,
            description=description,
            category=category,
            user_id=uid,
            payment_method=payment_method,
            notes=notes,
        )

        db.log_audit(f"Logged Expense #{expense_id} ({category})", user_id=uid, user_email=session.get("user_email"))
        flash(f"Expense of {session.get('user_currency', '₹')}{amount:.2f} logged under {category}!", "success")
        return redirect(url_for("dashboard"))

    return render_template("add.html", title="Add Expense", today=date.today().isoformat())


@app.route("/expenses")
@app.route("/list")
@login_required
def list_expenses():
    uid = session["user_id"]
    category = request.args.get("category")
    payment_method = request.args.get("payment_method")
    start = request.args.get("start")
    end = request.args.get("end")
    query = request.args.get("q")
    sort_by = request.args.get("sort", "date_desc")

    page = int(request.args.get("page", "1"))
    limit = 20
    offset = (page - 1) * limit

    total_count = db.count_expenses(
        user_id=uid,
        start_date=start or None,
        end_date=end or None,
        category=category or None,
        search_query=query or None,
        payment_method=payment_method or None,
    )
    expenses = db.list_expenses(
        user_id=uid,
        start_date=start or None,
        end_date=end or None,
        category=category or None,
        search_query=query or None,
        payment_method=payment_method or None,
        sort_by=sort_by,
        limit=limit,
        offset=offset,
    )

    total_pages = max(1, (total_count + limit - 1) // limit)

    return render_template(
        "list.html",
        title="Expense Directory",
        expenses=expenses,
        page=page,
        current_page=page,
        limit=limit,
        total_pages=total_pages,
        total_count=total_count,
        query=query or "",
        selected_category=category or "all",
        selected_payment=payment_method or "all",
        search_query=query or "",
        start_date=start or "",
        end_date=end or "",
        sort_by=sort_by,
    )


@app.route("/expenses/delete/<int:expense_id>", methods=["POST"])
@login_required
def delete_expense_route(expense_id: int):
    uid = session["user_id"]
    db.delete_expense(expense_id=expense_id, user_id=uid)
    db.log_audit(f"Deleted Expense #{expense_id}", user_id=uid, user_email=session.get("user_email"))
    flash("Expense transaction deleted.", "info")
    return redirect(request.referrer or url_for("list_expenses"))


@app.route("/budgets", methods=["GET", "POST"])
@login_required
def budgets_page():
    uid = session["user_id"]
    curr_month = date.today().strftime("%Y-%m")

    if request.method == "POST":
        action = request.form.get("action", "set_budget")
        if action == "set_budget":
            category = request.form.get("category", "General").strip()
            limit_amt = float(request.form.get("limit_amount", "0") or 0)
            if limit_amt > 0 and category:
                db.set_budget(uid, category, limit_amt, month=curr_month)
                flash(f"Monthly budget for {category} set to {session.get('user_currency', '₹')}{limit_amt:.2f}.", "success")
        elif action == "add_recurring":
            desc = request.form.get("description", "").strip()
            amt = float(request.form.get("amount", "0") or 0)
            cat = request.form.get("category", "Bills").strip()
            freq = request.form.get("frequency", "Monthly")
            due = request.form.get("next_due_date", date.today().isoformat())
            if amt > 0 and desc:
                db.add_recurring(uid, desc, amt, cat, freq, due)
                flash(f"Recurring subscription for '{desc}' added!", "success")
        return redirect(url_for("budgets_page"))

    budgets = db.get_user_budgets(uid, month=curr_month)
    recurring = db.list_recurring(uid)

    total_allocated_budget = sum(b["limit_amount"] for b in budgets)
    total_budget_spent = sum(b["spent"] for b in budgets)
    total_budget_remaining = max(0.0, total_allocated_budget - total_budget_spent)
    budget_utilization_pct = round((total_budget_spent / total_allocated_budget * 100), 1) if total_allocated_budget > 0 else 0

    return render_template(
        "budgets.html",
        title="Budgets & Recurring",
        budgets=budgets,
        recurring=recurring,
        current_month=curr_month,
        current_month_str=date.today().strftime("%B %Y"),
        total_allocated_budget=total_allocated_budget,
        total_budget_spent=total_budget_spent,
        total_budget_remaining=total_budget_remaining,
        budget_utilization_pct=budget_utilization_pct,
    )


@app.route("/budgets/delete/<int:budget_id>", methods=["POST"])
@login_required
def delete_budget_route(budget_id: int):
    uid = session["user_id"]
    db.delete_budget(budget_id, uid)
    flash("Category budget removed.", "info")
    return redirect(url_for("budgets_page"))


@app.route("/recurring/delete/<int:recurring_id>", methods=["POST"])
@login_required
def delete_recurring_route(recurring_id: int):
    uid = session["user_id"]
    db.delete_recurring(recurring_id, uid)
    flash("Recurring payment removed.", "info")
    return redirect(url_for("budgets_page"))


@app.route("/analytics")
@app.route("/summary")
@login_required
def analytics_page():
    uid = session["user_id"]
    period = request.args.get("period", "month")
    summary = db.get_summary(period, user_id=uid)
    weekday_dist = db.get_weekday_spending_distribution(user_id=uid)

    by_cat = summary.get("by_category", {})
    cat_labels = list(by_cat.keys())
    cat_values = list(by_cat.values())
    cat_colors = [CATEGORY_METADATA.get(c, {}).get("color", "#6366f1") for c in cat_labels]

    by_pay = summary.get("by_payment", {})
    pay_labels = list(by_pay.keys())
    pay_values = list(by_pay.values())

    weekday_labels = list(weekday_dist.keys())
    weekday_values = list(weekday_dist.values())

    return render_template(
        "analytics.html",
        title="Spending Analytics & Trends",
        summary=summary,
        period=period,
        weekday_dist=weekday_dist,
        cat_labels=cat_labels,
        cat_values=cat_values,
        cat_colors=cat_colors,
        pay_labels=pay_labels,
        pay_values=pay_values,
        weekday_labels=weekday_labels,
        weekday_values=weekday_values,
    )


@app.route("/predict")
@login_required
def predict_page():
    uid = session["user_id"]
    months = int(request.args.get("months", "6"))
    forecast = predictor.predict_next_month(months_back=months, user_id=uid)

    return render_template(
        "predict.html",
        title="AI Forecast & Predictions",
        months=months,
        forecast=forecast,
    )


@app.route("/chat", methods=["GET", "POST"])
@login_required
def chat_page():
    uid = session["user_id"]
    curr = session.get("user_currency", "₹")
    bot = ChatBot(db=db, rules=rules, user_id=uid, currency=curr)

    response: Optional[str] = None
    user_text = ""
    if request.method == "POST":
        user_text = request.form.get("message", "").strip()
        if user_text:
            response = bot.respond(user_text)

    return render_template("chat.html", title="AI Financial Copilot", user_text=user_text, response=response)


@app.route("/notifications")
@login_required
def notifications_page():
    uid = session["user_id"]
    notifs = db.get_notifications(user_id=uid, limit=30)
    return render_template("notifications.html", title="Notifications", notifications=notifs)


@app.route("/notifications/read-all", methods=["POST"])
@login_required
def mark_notifications_read():
    uid = session["user_id"]
    db.mark_all_notifications_read(uid)
    flash("All notifications marked as read.", "info")
    return redirect(url_for("notifications_page"))


@app.route("/notifications/clear", methods=["POST"])
@login_required
def clear_notifications_route():
    uid = session["user_id"]
    db.clear_all_notifications(uid)
    flash("Notification inbox cleared.", "info")
    return redirect(url_for("notifications_page"))


@app.route("/profile", methods=["GET", "POST"])
@login_required
def profile_page():
    uid = session["user_id"]
    if request.method == "POST":
        name = request.form.get("name", "").strip()
        currency = request.form.get("currency", "₹")
        monthly_budget = float(request.form.get("monthly_budget", "25000") or 25000)
        income = float(request.form.get("income", "50000") or 50000)
        savings_goal = float(request.form.get("savings_goal", "100000") or 100000)
        is_student_mode = 1 if request.form.get("is_student_mode") == "1" else 0
        monthly_allowance = float(request.form.get("monthly_allowance", "15000") or 15000)
        theme = request.form.get("theme", "light")

        db.update_user_profile(
            user_id=uid,
            name=name,
            currency=currency,
            monthly_budget=monthly_budget,
            income=income,
            savings_goal=savings_goal,
            is_student_mode=is_student_mode,
            monthly_allowance=monthly_allowance,
            theme=theme,
        )
        session["user_name"] = name
        session["user_currency"] = currency
        flash("Profile & Financial Baselines updated successfully!", "success")
        return redirect(url_for("profile_page"))

    user = db.get_user_by_id(uid)
    return render_template("profile.html", title="User Profile", user=user)


@app.route("/settings")
@login_required
def settings_page():
    return render_template("settings.html", title="Account Settings")


@app.route("/settings/change-password", methods=["POST"])
@login_required
def change_password_route():
    uid = session["user_id"]
    new_pwd = request.form.get("new_password", "")
    confirm_pwd = request.form.get("confirm_password", "")

    if len(new_pwd) < 6 or new_pwd != confirm_pwd:
        flash("Passwords must match and have at least 6 characters.", "danger")
        return redirect(url_for("settings_page"))

    db.update_user_password(uid, new_pwd)
    flash("Password updated successfully!", "success")
    return redirect(url_for("settings_page"))


@app.route("/settings/delete-account", methods=["POST"])
@login_required
def delete_account_route():
    uid = session["user_id"]
    db.delete_user(uid)
    session.clear()
    flash("Your account and all records have been deleted.", "info")
    return redirect(url_for("landing"))


@app.route("/export/csv")
@login_required
def export_expenses_csv():
    uid = session["user_id"]
    csv_filename = "expenses_export.csv"
    export_path = db.export_csv(path=csv_filename, user_id=uid)
    return send_file(export_path, as_attachment=True, download_name="my_expenses.csv")


@app.route("/export/json")
@login_required
def export_expenses_json():
    uid = session["user_id"]
    json_data = db.export_json(user_id=uid)
    return (
        json_data,
        200,
        {
            "Content-Type": "application/json",
            "Content-Disposition": "attachment; filename=my_expenses.json",
        },
    )


# ==================== ADMIN PANEL ====================

@app.route("/admin")
@admin_required
def admin_page():
    metrics = db.get_admin_metrics()
    return render_template("admin.html", title="Admin Dashboard", admin_metrics=metrics)


# ==================== PUBLIC CONTENT PAGES ====================

@app.route("/about")
def about_page():
    return render_template("about.html", title="About & Portfolio")


@app.route("/features")
def features_page():
    return render_template("features.html", title="Features Tour")


@app.route("/contact", methods=["GET", "POST"])
def contact_page():
    if request.method == "POST":
        name = request.form.get("name", "").strip()
        email = request.form.get("email", "").strip()
        subject = request.form.get("subject", "").strip()
        message = request.form.get("message", "").strip()

        if name and email and message:
            db.save_contact_message(name, email, subject, message)
            flash("Thank you for reaching out! Your message has been received.", "success")
            return redirect(url_for("contact_page"))
        else:
            flash("Please complete all required contact fields.", "danger")

    return render_template("contact.html", title="Contact & Support")


@app.route("/privacy")
def privacy_page():
    return render_template("privacy.html", title="Privacy Policy")


@app.route("/terms")
def terms_page():
    return render_template("terms.html", title="Terms & Conditions")


# ==================== REST API ENDPOINTS ====================

@app.route("/api/chat", methods=["POST"])
def api_chat():
    data = request.get_json() or {}
    text = data.get("message", "").strip()
    uid = session.get("user_id", 1)
    curr = session.get("user_currency", "₹")

    bot = ChatBot(db=db, rules=rules, user_id=uid, currency=curr)
    bot_response = bot.respond(text)
    return jsonify({"response": bot_response})


@app.route("/api/affordability", methods=["POST"])
def api_affordability():
    data = request.get_json() or {}
    item_name = data.get("item_name", "Item")
    amount = float(data.get("amount", 0.0) or 0.0)
    category = data.get("category")
    uid = session.get("user_id", 1)

    result = engine.check_affordability(user_id=uid, item_name=item_name, amount=amount, category=category)
    return jsonify(result)


@app.route("/api/health-score")
def api_health_score():
    uid = session.get("user_id", 1)
    score_data = engine.calculate_health_score(uid)
    return jsonify(score_data)


@app.route("/api/what-if", methods=["POST"])
def api_what_if():
    data = request.get_json() or {}
    scenario_type = data.get("scenario_type", "major_purchase")
    params = data.get("params", {})
    uid = session.get("user_id", 1)

    result = engine.simulate_what_if(user_id=uid, scenario_type=scenario_type, params=params)
    return jsonify(result)


@app.route("/api/money-leaks")
def api_money_leaks():
    uid = session.get("user_id", 1)
    leaks = engine.detect_money_leaks(uid)
    return jsonify(leaks)


@app.route("/api/cashflow-collision")
def api_cashflow_collision():
    uid = session.get("user_id", 1)
    collisions = engine.detect_cashflow_collisions(uid)
    return jsonify(collisions)


@app.route("/api/parse-receipt", methods=["POST"])
def api_parse_receipt():
    data = request.get_json() or {}
    text = data.get("text", "")
    parsed = engine.parse_receipt_text(text)
    return jsonify(parsed)


@app.route("/api/summary")
def api_summary():
    uid = session.get("user_id")
    period = request.args.get("period", "month")
    summary_data = db.get_summary(period=period, user_id=uid)
    return jsonify(summary_data)


@app.route("/api/predict")
def api_predict():
    uid = session.get("user_id")
    months = int(request.args.get("months", "6"))
    forecast = predictor.predict_next_month(months_back=months, user_id=uid)
    return jsonify(forecast)


# ==================== CUSTOM ERROR HANDLERS ====================

@app.errorhandler(404)
def page_not_found(e):
    return render_template("404.html", title="Page Not Found"), 404


@app.errorhandler(500)
def server_error(e):
    return render_template("500.html", title="Server Error"), 500


@app.errorhandler(403)
def forbidden(e):
    flash("You do not have permission to view that resource.", "danger")
    return redirect(url_for("dashboard"))


# ==================== APPLICATION RUNNER ====================

if __name__ == "__main__":
    port_str = os.environ.get("PORT", "5000")
    try:
        port = int(port_str)
    except ValueError:
        port = 5000
    print(f"=== Starting ExpenseAI on http://127.0.0.1:{port} (or http://localhost:{port}) ===")
    app.run(host="0.0.0.0", port=port, debug=True)
