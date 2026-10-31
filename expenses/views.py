from rest_framework import viewsets
from .models import Expense, FinancialGoal, Recurring
from .serializers import ExpenseSerializer, FinancialGoalSerializer, RecurringSerializer

class ExpenseViewSet(viewsets.ModelViewSet):
    queryset = Expense.objects.all()
    serializer_class = ExpenseSerializer

class FinancialGoalViewSet(viewsets.ModelViewSet):
    queryset = FinancialGoal.objects.all()
    serializer_class = FinancialGoalSerializer

class RecurringViewSet(viewsets.ModelViewSet):
    queryset = Recurring.objects.all()
    serializer_class = RecurringSerializer
