-- ═══════════════════════════════════════════════════════════════
-- USAMIS seed part 2: enrollments, grades, fees for the roster.
-- Links students that have a users row by email/student_id; for
-- students without a login we still create enrollments (registrar view).
-- ═══════════════════════════════════════════════════════════════
BEGIN;

-- Clean prior generated enrollments for our roster so this is re-runnable
DELETE FROM grades WHERE enrollment_id IN (
  SELECT e.id FROM enrollments e JOIN students s ON s.id=e.student_id
  WHERE s.student_id BETWEEN 'STU2024001' AND 'STU2024008');
DELETE FROM enrollments WHERE student_id IN (
  SELECT id FROM students WHERE student_id BETWEEN 'STU2024001' AND 'STU2024008');

-- Enroll the roster in courses (2-4 each), Semester 1 2024/25
INSERT INTO enrollments (student_id, course_id, semester, status, enrolled_date)
SELECT s.id, c.id, 'Semester 1 2024/25', 'Active', '2024-09-05'
FROM students s
JOIN courses c ON (
  (s.student_id='STU2024001' AND c.code IN ('CS301','CS201','CS101','AI301')) OR
  (s.student_id='STU2024002' AND c.code IN ('CS201','CS101','BUS201')) OR
  (s.student_id='STU2024003' AND c.code IN ('BUS201','CS101')) OR
  (s.student_id='STU2024004' AND c.code IN ('CS301','CS201')) OR
  (s.student_id='STU2024005' AND c.code IN ('CS301','CS401','CS501')) OR
  (s.student_id='STU2024006' AND c.code IN ('AI301','CS201','CS301')) OR
  (s.student_id='STU2024007' AND c.code IN ('BUS201','BUS301')) OR
  (s.student_id='STU2024008' AND c.code IN ('CS101','CS302','CS601'))
)
AND c.code IN ('CS301','CS201','CS101','CS401','CS302','AI301','BUS201','BUS301','CS501','CS601');

-- Grades (varied: some strong, some at-risk <60, some mid)
INSERT INTO grades (enrollment_id, score, letter_grade, gpa_points, entered_by)
SELECT e.id, g.score, g.letter, g.points, 3
FROM enrollments e
JOIN students s ON s.id=e.student_id
JOIN courses c ON c.id=e.course_id
JOIN (VALUES
  ('STU2024001','CS301',95.0,'A+',4.0),('STU2024001','CS201',88.0,'B+',3.5),
  ('STU2024001','CS101',92.0,'A',4.0),('STU2024001','AI301',84.0,'B',3.0),
  ('STU2024002','CS201',78.0,'B-',2.7),('STU2024002','CS101',72.0,'C+',2.3),
  ('STU2024002','BUS201',85.0,'B+',3.5),
  ('STU2024003','BUS201',55.0,'D',1.0),('STU2024003','CS101',58.0,'D',1.0),
  ('STU2024004','CS301',97.0,'A+',4.0),('STU2024004','CS201',91.0,'A',4.0),
  ('STU2024005','CS301',82.0,'B',3.0),('STU2024005','CS401',76.0,'B-',2.7),
  ('STU2024005','CS501',88.0,'B+',3.5),
  ('STU2024006','AI301',93.0,'A',4.0),('STU2024006','CS201',89.0,'B+',3.5),
  ('STU2024006','CS301',90.0,'A',4.0),
  ('STU2024007','BUS201',48.0,'F',0.0),('STU2024007','BUS301',52.0,'D',1.0),
  ('STU2024008','CS101',80.0,'B',3.0),('STU2024008','CS302',86.0,'B+',3.5),
  ('STU2024008','CS601',74.0,'C+',2.3)
) AS g(sno, code, score, letter, points)
  ON g.sno=s.student_id AND g.code=c.code;

-- Fees: most paid, a few partial/outstanding so finance has real work.
-- Remove any prior generated fee rows for this roster, then insert fresh.
DELETE FROM fee_records WHERE student_id IN (
  SELECT id FROM students WHERE student_id BETWEEN 'STU2024001' AND 'STU2024008');

INSERT INTO fee_records (receipt_no, student_id, fee_type, semester, total_amount, paid_amount, payment_method, payment_date, status)
SELECT
  'RCP24' || LPAD(ROW_NUMBER() OVER (ORDER BY s.id)::text, 4, '0'),
  s.id, 'Tuition Fee', 'Semester 1 2024/25',
  f.total, f.paid, f.method, f.pdate::date, f.status
FROM students s
JOIN (VALUES
  ('STU2024001',12000.0,12000.0,'Bank Transfer','2024-09-10','Paid'),
  ('STU2024002',12000.0, 8000.0,'Card','2024-09-12','Partial'),
  ('STU2024003',12000.0,    0.0,'-',NULL,'Unpaid'),
  ('STU2024004',18000.0,18000.0,'Bank Transfer','2024-09-08','Paid'),
  ('STU2024005',12000.0,12000.0,'Cash','2024-09-15','Paid'),
  ('STU2024006',12000.0, 6000.0,'Card','2024-09-20','Partial'),
  ('STU2024007',10000.0,10000.0,'Bank Transfer','2024-09-11','Paid'),
  ('STU2024008',12000.0,    0.0,'-',NULL,'Unpaid')
) AS f(sno, total, paid, method, pdate, status)
  ON f.sno=s.student_id
WHERE NOT EXISTS (
  SELECT 1 FROM fee_records fr WHERE fr.student_id=s.id AND fr.semester='Semester 1 2024/25');

COMMIT;

SELECT 'students' AS what, COUNT(*) FROM students
UNION ALL SELECT 'courses', COUNT(*) FROM courses
UNION ALL SELECT 'enrollments', COUNT(*) FROM enrollments
UNION ALL SELECT 'grades', COUNT(*) FROM grades
UNION ALL SELECT 'fees', COUNT(*) FROM fee_records;
