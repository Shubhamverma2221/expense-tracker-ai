from rest_framework import viewsets, permissions
from .models import Expense, FinancialGoal
from .serializers import ExpenseSerializer, FinancialGoalSerializer

class ExpenseViewSet(viewsets.ModelViewSet):
    """API endpoint for CRUD operations on expenses."""
    queryset = Expense.objects.all()
    serializer_class = ExpenseSerializer
    permission_classes = [permissions.IsAuthenticatedOrReadOnly]

    def perform_create(self, serializer):
        # Associate the logged‑in user automatically
        serializer.save(user=self.request.user)

class FinancialGoalViewSet(viewsets.ModelViewSet):
    """API endpoint for CRUD operations on financial goals."""
    queryset = FinancialGoal.objects.all()
    serializer_class = FinancialGoalSerializer
    permission_classes = [permissions.IsAuthenticatedOrReadOnly]

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)
