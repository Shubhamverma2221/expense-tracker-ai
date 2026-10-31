from rest_framework import serializers
from .models import Expense, FinancialGoal, Recurring

class ExpenseSerializer(serializers.ModelSerializer):
    class Meta:
        model = Expense
        fields = '__all__'

class FinancialGoalSerializer(serializers.ModelSerializer):
    class Meta:
        model = FinancialGoal
        fields = '__all__'

class RecurringSerializer(serializers.ModelSerializer):
    class Meta:
        model = Recurring
        fields = '__all__'
