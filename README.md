# Expense Tracker AI (Django + DRF)

## Overview
This repository now hosts a **Python/Django** implementation of the Expense Tracker AI application, replacing the original Java Spring Boot code. The project provides:

- **RESTful API** powered by Django REST Framework (DRF) for managing expenses, financial goals, and recurring transactions.
- **HTML/CSS frontend** using vanilla CSS and Google Fonts for a clean, modern UI.
- **User authentication** via Django’s built‑in auth system.
- **Administrative interface** (`/admin/`) for managing data.

## Quick Start
```bash
# Clone the repo (if you haven't already)
git clone https://github.com/Shubhamverma2221/expense-tracker-ai.git
cd expense-tracker-ai

# Create a virtual environment (recommended)
python -m venv .venv
# Activate (PowerShell)
.\.venv\Scripts\Activate.ps1
# or Bash
source .venv/bin/activate

# Install dependencies
pip install -r requirements.txt

# Apply migrations and create a superuser
python manage.py migrate
python manage.py createsuperuser

# Run the development server
python manage.py runserver
```
Open [http://127.0.0.1:8000/](http://127.0.0.1:8000/) in your browser.

## API Endpoints
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/expenses/` | `GET, POST` | List all expenses / create a new expense |
| `/api/expenses/<id>/` | `GET, PUT, PATCH, DELETE` | Retrieve, update or delete a specific expense |
| `/api/goals/` | `GET, POST` | Manage financial goals |
| `/api/recurring/` | `GET, POST` | Manage recurring transactions |

All endpoints require authentication (use the superuser credentials you created). You can obtain a session cookie via the Django admin login or add token authentication later.

## Project Structure
```
expense-tracker-ai-main/
│   manage.py
│   requirements.txt
│   .gitignore
│
├───expense_tracker_proj/      # Django project settings
│       __init__.py
│       settings.py
│       urls.py
│       wsgi.py
│
└───expenses/                  # Core app
        __init__.py
        admin.py
        apps.py
        models.py
        serializers.py
        views.py
        urls.py
        migrations/

static/                         # Static files (CSS, images)
templates/                     # HTML templates (base.html, etc.)
```

## Contributing
Feel free to open issues or submit pull requests. When contributing:
1. Fork the repository.
2. Create a feature branch.
3. Ensure code follows PEP‑8 standards.
4. Run tests (`python manage.py test`) before submitting.

## License
This project is licensed under the MIT License.
