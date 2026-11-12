from django.db import models
from django.utils import timezone

class UserAccount(models.Model):
    ROLE_CHOICES = (
        ('admin', 'Admin'),
        ('student', 'Student'),
    )

    username = models.CharField(max_length=100, unique=True)
    password = models.CharField(max_length=100)
    role = models.CharField(max_length=20, choices=ROLE_CHOICES, default='student')
    
    
    roll_number = models.CharField(max_length=50, blank=True, default='')
    subject = models.CharField(max_length=100, default='Python & Django Lab')
    marks = models.IntegerField(default=0)
    max_marks = models.IntegerField(default=100)
    remarks = models.CharField(max_length=200, default='Pending review')
    
   
    created_at = models.DateTimeField(auto_now_add=True)
    last_login = models.DateTimeField(null=True, blank=True)

    def calculate_grade(self):
        if self.marks >= 90:
            return 'A+'
        elif self.marks >= 80:
            return 'A'
        elif self.marks >= 70:
            return 'B'
        elif self.marks >= 60:
            return 'C'
        elif self.marks >= 50:
            return 'D'
        else:
            return 'F'

    def __str__(self):
        return f"{self.username} ({self.role}) - {self.marks}/{self.max_marks}"
