from django.urls import path, include
from rest_framework.routers import DefaultRouter
from .views import ExpenseViewSet, FinancialGoalViewSet
from django.views.generic import TemplateView

router = DefaultRouter()
router.register(r'expenses', ExpenseViewSet, basename='expense')
router.register(r'goals', FinancialGoalViewSet, basename='goal')

urlpatterns = [
    path('api/', include(router.urls)),
    path('', TemplateView.as_view(template_name='index.html'), name='home'),
]
