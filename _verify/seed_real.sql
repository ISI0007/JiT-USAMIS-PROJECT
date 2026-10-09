-- ═══════════════════════════════════════════════════════════════
-- USAMIS seed data: realistic JIT students, courses, enrollments,
-- grades and fees. Idempotent-ish: clears test junk first.
-- ═══════════════════════════════════════════════════════════════
BEGIN;

-- ---- 1. Remove obvious test junk (courses/enrollments/grades created by tests)
DELETE FROM grades WHERE enrollment_id IN (
  SELECT id FROM enrollments WHERE course_id IN (
    SELECT id FROM courses WHERE name IN ('GradeTest','CRUD Test Course','GTest')
       OR code IN ('CS5537','CS438','CS4877')
  )
);
DELETE FROM enrollments WHERE course_id IN (
  SELECT id FROM courses WHERE name IN ('GradeTest','CRUD Test Course','GTest')
     OR code IN ('CS5537','CS438','CS4877')
);
DELETE FROM courses WHERE name IN ('GradeTest','CRUD Test Course','GTest')
   OR code IN ('CS5537','CS438','CS4877');

-- Fix duplicate/garbled lecturer names
UPDATE users SET last_name='Fang'  WHERE username='lec002' AND last_name='Fang2';

-- ---- 2. Realistic courses (keep existing ones, add the core catalogue)
INSERT INTO courses (code, name, department_id, instructor_id, credits, max_enrollment, semester, status, description)
VALUES
  ('CS301','Database Systems',1,1,3,40,'Semester 1 2024/25','Active','Relational modelling, SQL, transactions and indexing.'),
  ('CS201','Data Structures and Algorithms',1,2,4,50,'Semester 1 2024/25','Active','Lists, trees, graphs, sorting and complexity analysis.'),
  ('CS101','Introduction to Programming',1,1,3,60,'Semester 1 2024/25','Active','Programming fundamentals with Python.'),
  ('CS401','Operating Systems',1,2,4,45,'Semester 1 2024/25','Active','Processes, scheduling, memory and file systems.'),
  ('CS302','Web Application Development',1,2,3,40,'Semester 2 2024/25','Active','Full-stack web development with modern frameworks.'),
  ('AI301','Machine Learning Fundamentals',3,2,3,35,'Semester 1 2024/25','Active','Supervised and unsupervised learning, model evaluation.'),
  ('BUS201','Management Information Systems',7,1,3,50,'Semester 1 2024/25','Active','Information systems in modern organisations.'),
  ('BUS301','Financial Accounting',7,2,3,45,'Semester 2 2024/25','Active','Financial statements, ledgers and reporting.'),
  ('CS501','Computer Networks',1,2,4,40,'Semester 2 2024/25','Active','TCP/IP, routing, and network security basics.'),
  ('CS601','Software Engineering',1,1,3,40,'Semester 2 2024/25','Active','Requirements, design, testing and agile delivery.')
ON CONFLICT (code) DO UPDATE SET
  name = EXCLUDED.name, credits = EXCLUDED.credits,
  semester = EXCLUDED.semester, status = EXCLUDED.status;

-- ---- 3. Realistic student roster (JIT-flavoured names, STU2024xxx)
-- Only insert students that don't already exist by student_id.
INSERT INTO students (student_id, first_name, last_name, date_of_birth, gender, email, phone,
                      department_id, program_id, year_of_study, enrollment_date, status, nationality)
VALUES
  ('STU2024001','Wei','Zhang','2003-05-12','Male','wei.zhang@jit.edu.cn','138-0101',1,1,3,'2024-09-01','Active','China'),
  ('STU2024002','Mei','Liu','2004-02-20','Female','mei.liu@jit.edu.cn','138-0102',1,1,2,'2024-09-01','Active','China'),
  ('STU2024003','Jun','Chen','2002-08-15','Male','jun.chen@jit.edu.cn','138-0103',1,1,3,'2024-09-01','Active','China'),
  ('STU2024004','Yaseen','Al-Rashid','2003-11-01','Male','yaseen@jit.edu.cn','138-0104',1,1,2,'2024-09-01','Active','Pakistan'),
  ('STU2024005','Li','Wang','2003-03-08','Female','li.wang@jit.edu.cn','138-0105',2,2,2,'2024-09-01','Active','China'),
  ('STU2024006','Hao','Zhao','2002-12-19','Male','hao.zhao@jit.edu.cn','138-0106',3,3,3,'2024-09-01','Active','China'),
  ('STU2024007','Xin','Sun','2004-07-23','Female','xin.sun@jit.edu.cn','138-0107',7,7,1,'2024-09-01','Active','China'),
  ('STU2024008','Kai','Zhou','2003-09-30','Male','kai.zhou@jit.edu.cn','138-0108',1,1,2,'2024-09-01','Active','China')
ON CONFLICT (student_id) DO NOTHING;

COMMIT;

-- Report
SELECT 'students' AS what, COUNT(*) FROM students
UNION ALL SELECT 'courses', COUNT(*) FROM courses
UNION ALL SELECT 'enrollments', COUNT(*) FROM enrollments
UNION ALL SELECT 'grades', COUNT(*) FROM grades
UNION ALL SELECT 'fees', COUNT(*) FROM fee_records;
