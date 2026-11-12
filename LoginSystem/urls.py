"""
URL configuration for LoginSystem.
Supports separate Admin Panel and Student Panel.
"""
from django.contrib import admin
from django.urls import path
from home import views

urlpatterns = [
    path('admin/', admin.site.urls),
    path('', views.login_view, name='login'),
    path('login/', views.login_view, name='login_page'),
    path('logout/', views.logout_view, name='logout'),
    
    # Separate Role-Based Panels
    path('admin-panel/', views.admin_panel, name='admin_panel'),
    path('admin-panel/update-marks/<int:student_id>/', views.update_marks, name='update_marks'),
    path('student-panel/', views.student_panel, name='student_panel'),
]
