from rest_framework import serializers
from .models import Expense, FinancialGoal

class ExpenseSerializer(serializers.ModelSerializer):
    class Meta:
        model = Expense
        fields = ['id', 'user', 'date', 'amount', 'description', 'category', 'payment_method', 'notes', 'receipt', 'created_at']
        read_only_fields = ['id', 'user', 'created_at']

class FinancialGoalSerializer(serializers.ModelSerializer):
    progress_percent = serializers.ReadOnlyField()

    class Meta:
        model = FinancialGoal
        fields = ['id', 'user', 'title', 'target_amount', 'current_amount', 'target_date', 'category', 'created_at', 'progress_percent']
        read_only_fields = ['id', 'user', 'created_at', 'progress_percent']
