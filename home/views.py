from django.shortcuts import render, redirect, get_object_or_404
from django.utils import timezone
from django.contrib import messages
from .models import UserAccount

def login_view(request):
    \
    session_role = request.session.get('role')
    if session_role == 'admin':
        return redirect('admin_panel')
    elif session_role == 'student':
        return redirect('student_panel')

    context = {
        "total_users": UserAccount.objects.count()
    }
    
    if request.method == "POST":
        username = request.POST.get("username", "").strip()
        password = request.POST.get("password", "").strip()
        
        
        user = UserAccount.objects.filter(username=username).first()
        
      
        if user and user.password == password:
            user.last_login = timezone.now()
            user.save()
            
            
            request.session['user_id'] = user.id
            request.session['username'] = user.username
            request.session['role'] = user.role
            
            print(f">>> Logged in successfully: {user.role.upper()} '{username}' authenticated!")
            
            if user.role == 'admin':
                return redirect('admin_panel')
            else:
                return redirect('student_panel')
                
        elif (username == "admin" and password == "admin123"):
           
            admin_user, _ = UserAccount.objects.get_or_create(
                username="admin",
                defaults={"password": "admin123", "role": "admin"}
            )
            admin_user.last_login = timezone.now()
            admin_user.save()
            request.session['user_id'] = admin_user.id
            request.session['username'] = "admin"
            request.session['role'] = "admin"
            return redirect('admin_panel')
        else:
            print(f">>> Login failed for username '{username}'")
            context["error"] = "Invalid username or password. Please try again."
            context["typed_username"] = username
            
    return render(request, "login.html", context)


def admin_panel(request):
    
    if request.session.get('role') != 'admin':
        return redirect('login')

    admin_user = UserAccount.objects.filter(id=request.session.get('user_id')).first()
    students = UserAccount.objects.filter(role='student').order_by('-created_at')
    
   
    total_students = students.count()
    total_marks = sum(s.marks for s in students)
    avg_marks = round(total_marks / total_students, 1) if total_students > 0 else 0
    highest_marks = max((s.marks for s in students), default=0)

    if request.method == "POST" and "add_student" in request.POST:
        u_name = request.POST.get("username", "").strip()
        pwd = request.POST.get("password", "").strip()
        roll = request.POST.get("roll_number", "").strip()
        subj = request.POST.get("subject", "Python & Django Lab").strip()
        try:
            m = int(request.POST.get("marks", 0))
        except ValueError:
            m = 0
        rem = request.POST.get("remarks", "Enrolled").strip()

        if UserAccount.objects.filter(username=u_name).exists():
            messages.error(request, f"Username '{u_name}' already exists!")
        elif not u_name or not pwd:
            messages.error(request, "Username and password are required!")
        else:
            UserAccount.objects.create(
                username=u_name,
                password=pwd,
                role='student',
                roll_number=roll,
                subject=subj,
                marks=m,
                remarks=rem
            )
            messages.success(request, f"Student '{u_name}' added successfully with {m} marks!")
            return redirect('admin_panel')

    context = {
        "admin_user": admin_user,
        "students": students,
        "total_students": total_students,
        "avg_marks": avg_marks,
        "highest_marks": highest_marks,
        "server_time": timezone.now(),
    }
    return render(request, "admin_panel.html", context)


def update_marks(request, student_id):
    
    if request.session.get('role') != 'admin':
        return redirect('login')

    student = get_object_or_404(UserAccount, id=student_id, role='student')

    if request.method == "POST":
        try:
            student.marks = int(request.POST.get("marks", student.marks))
        except ValueError:
            pass
        student.remarks = request.POST.get("remarks", student.remarks).strip()
        student.roll_number = request.POST.get("roll_number", student.roll_number).strip()
        student.subject = request.POST.get("subject", student.subject).strip()
        student.save()
        messages.success(request, f"Updated marks for student '{student.username}' to {student.marks}!")

    return redirect('admin_panel')


def student_panel(request):
   
    student_id = request.session.get('user_id')
    if not student_id or request.session.get('role') != 'student':
        return redirect('login')

    student = get_object_or_404(UserAccount, id=student_id)
    grade = student.calculate_grade()
    percentage = round((student.marks / student.max_marks) * 100, 1) if student.max_marks > 0 else 0

    context = {
        "student": student,
        "grade": grade,
        "percentage": percentage,
    }
    return render(request, "student_panel.html", context)



def logout_view(request):
    username = request.session.get('username', 'User')
    request.session.flush()
    print(f">>> {username} logged out.")
    return redirect('login')
