from django.urls import path, include
from rest_framework import routers
from .views import ExpenseViewSet, FinancialGoalViewSet, RecurringViewSet

router = routers.DefaultRouter()
router.register(r'expenses', ExpenseViewSet)
router.register(r'goals', FinancialGoalViewSet)
router.register(r'recurring', RecurringViewSet)

urlpatterns = [
    path('', include(router.urls)),
]
