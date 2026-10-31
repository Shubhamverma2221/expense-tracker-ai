from django.contrib import admin
from .models import Expense, FinancialGoal, Recurring

admin.site.register(Expense)
admin.site.register(FinancialGoal)
admin.site.register(Recurring)
