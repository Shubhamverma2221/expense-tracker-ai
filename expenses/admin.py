from django.contrib import admin
from .models import Expense, FinancialGoal

@admin.register(Expense)
class ExpenseAdmin(admin.ModelAdmin):
    list_display = ('user', 'date', 'amount', 'category', 'payment_method')
    list_filter = ('user', 'date', 'category')
    search_fields = ('description', 'notes')

@admin.register(FinancialGoal)
class FinancialGoalAdmin(admin.ModelAdmin):
    list_display = ('title', 'user', 'target_amount', 'current_amount', 'target_date', 'category')
    list_filter = ('user', 'target_date', 'category')
    search_fields = ('title',)
