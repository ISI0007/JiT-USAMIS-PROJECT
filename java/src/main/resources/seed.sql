-- ═══════════════════════════════════════════════
--  USAMIS — Seed Data
--  Run AFTER schema.sql
-- ═══════════════════════════════════════════════

-- ─── USERS (bcrypt hashes — password = 'admin123', 'reg123', etc.)
-- Real BCrypt cost-12 hashes generated with org.mindrot.jbcrypt, matching
-- PasswordUtil.COST = 12. Verified with BCrypt.checkpw() before committing.
INSERT INTO users (username, password_hash, first_name, last_name, email, role_id, status) VALUES
  ('admin001', '$2a$12$keCYnGe2.x3mPOdDGVL.oeONybF1JvmAzfCMi4B.f8cYc827n0/OS', 'System',  'Administrator', 'admin@jit.edu.cn',   1, 'active'),
  ('reg001',   '$2a$12$qaU1E4cw/vhzpUbaM9yKHeIvCI/cNDfmmErpoxAt4Qua2SMSetgsK', 'Wang',    'Fang',          'reg@jit.edu.cn',     2, 'active'),
  ('lec001',   '$2a$12$AyjDaQfL3WRS5MjhJVM/n.zVOpTbaiT5PqKZ7/BzF3Za9JF07JLjK', 'Li',      'Gang',          'lec001@jit.edu.cn',  3, 'active'),
  ('lec002',   '$2a$12$L1tNS3JdcejbrToBoANfYeZvxrBWiQqicPpm.vXSimgUHL0q8sVge', 'Wang',    'Fang2',         'lec002@jit.edu.cn',  3, 'active'),
  ('fin001',   '$2a$12$GwaqH1cvzdTESVdIbJNHq.VIFp4QwpjuDK6Awk8SgrECITkqeO77O', 'Sun',     'Hong',          'fin@jit.edu.cn',     4, 'active'),
  ('stu001',   '$2a$12$PurxBU43yxQ5ur1u4.pVduk039HN6Aqgt2hRKL6SVS4omvotKmN2O', 'Yaseen',  'Al-Rashid',     'yaseen@jit.edu.cn',  5, 'active'),
  ('stu002',   '$2a$12$l.7nNt/AP0yUxFZ8bL764.XPetbxMtwVQ8N3mo7fqmVENYbmRk/JW', 'Wei',     'Zhang',         'wei@jit.edu.cn',     5, 'active'),
  ('stu003',   '$2a$12$RlORsSQg7zIAModZWbESUOw4zmBeO7M.0iEgVzNGGRAa5HmJOAOru', 'Mei',     'Liu',           'mei@jit.edu.cn',     5, 'active');

-- ─── INSTRUCTORS
INSERT INTO instructors (user_id, department_id, title, specialization) VALUES
  (3, 1, 'Prof.', 'Database Systems and Software Engineering'),
  (4, 2, 'Prof.', 'Algorithms and Data Structures');

-- ─── STUDENTS
INSERT INTO students (student_id, user_id, first_name, last_name, date_of_birth, gender, email, phone, department_id, program_id, year_of_study, nationality) VALUES
  ('STU2024001', 7, 'Wei',    'Zhang',     '2003-05-12', 'Male',   'wei.zhang@jit.edu.cn',  '138-0001', 1, 1, 2, 'Chinese'),
  ('STU2024002', 8, 'Mei',    'Liu',       '2004-02-20', 'Female', 'mei.liu@jit.edu.cn',    '138-0002', 2, 3, 1, 'Chinese'),
  ('STU2024003', NULL, 'Jun', 'Chen',      '2002-08-15', 'Male',   'jun.chen@jit.edu.cn',   '138-0003', 7, 9, 3, 'Chinese'),
  ('STU2024004', 6, 'Yaseen', 'Al-Rashid', '2003-11-01', 'Male',   'yaseen@jit.edu.cn',     '138-0004', 1, 1, 2, 'International'),
  ('STU2024005', NULL, 'Xin', 'Wang',      '2003-07-22', 'Female', 'xin.wang@jit.edu.cn',   '138-0005', 3, 4, 2, 'Chinese'),
  ('STU2024006', NULL, 'Hao', 'Li',        '2001-03-30', 'Male',   'hao.li@jit.edu.cn',     '138-0006', 5, 7, 4, 'Chinese'),
  ('STU2024007', NULL, 'Fang','Zhou',      '2004-09-14', 'Female', 'fang.zhou@jit.edu.cn',  '138-0007', 4, 6, 1, 'Chinese'),
  ('STU2024008', NULL, 'Song','Zhenhua',   '2003-12-05', 'Male',   'song.zhenhua@jit.edu.cn','138-0008', 1, 1, 2, 'Chinese');

-- ─── COURSES
INSERT INTO courses (code, name, department_id, instructor_id, credits, max_enrollment, semester, status) VALUES
  ('CS301', 'Database Systems',                1, 1, 3, 50, 'Semester 1 2024/25', 'Active'),
  ('CS201', 'Data Structures and Algorithms', 1, 2, 4, 40, 'Semester 1 2024/25', 'Active'),
  ('SE401', 'Software Engineering Principles', 2, 1, 3, 35, 'Semester 1 2024/25', 'Active'),
  ('AI301', 'Machine Learning Fundamentals',   3, 2, 4, 30, 'Semester 1 2024/25', 'Active'),
  ('CS101', 'Introduction to Programming',     1, 1, 3, 50, 'Semester 1 2024/25', 'Full'),
  ('NET201','Network Security Basics',          5, 1, 3, 30, 'Semester 2 2024/25', 'Active'),
  ('BUS201','Management Information Systems',  7, 2, 3, 40, 'Semester 1 2024/25', 'Active');

-- ─── ENROLLMENTS (student.id × course.id)
INSERT INTO enrollments (student_id, course_id, semester, enrolled_date) VALUES
  (1, 1, 'Semester 1 2024/25', '2024-09-01'),
  (2, 2, 'Semester 1 2024/25', '2024-09-01'),
  (3, 7, 'Semester 1 2024/25', '2024-09-01'),
  (4, 1, 'Semester 1 2024/25', '2024-09-01'),
  (5, 4, 'Semester 1 2024/25', '2024-09-02'),
  (8, 1, 'Semester 1 2024/25', '2024-09-02'),
  (1, 2, 'Semester 1 2024/25', '2024-09-03'),
  (4, 2, 'Semester 1 2024/25', '2024-09-03');

-- ─── GRADES (enrollment_id references above)
INSERT INTO grades (enrollment_id, score, letter_grade, gpa_points, entered_by) VALUES
  (1, 92.0, 'A',  4.0, 3),
  (2, 85.0, 'B+', 3.5, 4),
  (3, 62.0, 'C',  2.0, 4),
  (4, 97.0, 'A+', 4.0, 3),
  (5, 70.0, 'B-', 2.7, 4),
  (6, 88.0, 'B+', 3.5, 3),
  (7, 90.0, 'A',  4.0, 4),
  (8, 95.0, 'A+', 4.0, 3);

-- ─── FEE RECORDS
INSERT INTO fee_records (receipt_no, student_id, fee_type, semester, total_amount, paid_amount, payment_method, payment_date, status, created_by) VALUES
  ('RCP2024001', 1, 'Tuition Fee',   'Semester 1 2024/25', 12000.00, 12000.00, 'Alipay',        '2024-08-28', 'Paid',    5),
  ('RCP2024002', 2, 'Tuition Fee',   'Semester 1 2024/25', 12000.00,  8000.00, 'WeChat Pay',    '2024-08-30', 'Partial', 5),
  ('RCP2024003', 3, 'Tuition Fee',   'Semester 1 2024/25', 12000.00,     0.00, NULL,            NULL,         'Unpaid',  5),
  ('RCP2024004', 4, 'Tuition Fee',   'Semester 1 2024/25', 18000.00, 18000.00, 'Bank Transfer', '2024-08-25', 'Paid',    5),
  ('RCP2024005', 5, 'Accommodation', 'Semester 1 2024/25',  5000.00,  5000.00, 'WeChat Pay',    '2024-09-01', 'Paid',    5),
  ('RCP2024006', 8, 'Tuition Fee',   'Semester 1 2024/25', 12000.00,  6000.00, 'Alipay',        '2024-09-05', 'Partial', 5);

-- ─── AUDIT LOG (bootstrap entries)
INSERT INTO audit_log (user_id, username, role_name, action, module, description, ip_address) VALUES
  (1, 'admin001', 'Admin',    'LOGIN',  'Auth',      'System initialized',                             '127.0.0.1'),
  (1, 'admin001', 'Admin',    'CREATE', 'Students',  'Bulk import: 8 students loaded from seed data', '127.0.0.1'),
  (3, 'lec001',   'Lecturer', 'CREATE', 'Grades',    'Grades entered for CS301 — 3 students',         '192.168.1.102'),
  (5, 'fin001',   'Finance',  'CREATE', 'Fees',      'Fee records created for Semester 1 2024/25',    '192.168.1.103');
