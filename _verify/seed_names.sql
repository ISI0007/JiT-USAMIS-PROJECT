-- Force correct names for the whole roster (covers pre-existing rows).
BEGIN;
UPDATE students SET first_name='Wei',    last_name='Zhang',     gender='Male',   date_of_birth='2003-05-12', email='wei.zhang@jit.edu.cn',  phone='138-0101', nationality='China',    year_of_study=3, department_id=1, program_id=1 WHERE student_id='STU2024001';
UPDATE students SET first_name='Mei',    last_name='Liu',       gender='Female', date_of_birth='2004-02-20', email='mei.liu@jit.edu.cn',    phone='138-0102', nationality='China',    year_of_study=2, department_id=1, program_id=1 WHERE student_id='STU2024002';
UPDATE students SET first_name='Jun',    last_name='Chen',      gender='Male',   date_of_birth='2002-08-15', email='jun.chen@jit.edu.cn',   phone='138-0103', nationality='China',    year_of_study=3, department_id=1, program_id=1 WHERE student_id='STU2024003';
UPDATE students SET first_name='Yaseen', last_name='Al-Rashid', gender='Male',   date_of_birth='2003-11-01', email='yaseen@jit.edu.cn',     phone='138-0104', nationality='Pakistan', year_of_study=2, department_id=1, program_id=1 WHERE student_id='STU2024004';
UPDATE students SET first_name='Li',     last_name='Wang',      gender='Female', date_of_birth='2003-03-08', email='li.wang@jit.edu.cn',    phone='138-0105', nationality='China',    year_of_study=2, department_id=2, program_id=3 WHERE student_id='STU2024005';
UPDATE students SET first_name='Hao',    last_name='Zhao',      gender='Male',   date_of_birth='2002-12-19', email='hao.zhao@jit.edu.cn',   phone='138-0106', nationality='China',    year_of_study=3, department_id=3, program_id=4 WHERE student_id='STU2024006';
UPDATE students SET first_name='Xin',    last_name='Sun',       gender='Female', date_of_birth='2004-07-23', email='xin.sun@jit.edu.cn',    phone='138-0107', nationality='China',    year_of_study=1, department_id=7, program_id=9 WHERE student_id='STU2024007';
UPDATE students SET first_name='Kai',    last_name='Zhou',      gender='Male',   date_of_birth='2003-09-30', email='kai.zhou@jit.edu.cn',   phone='138-0108', nationality='China',    year_of_study=2, department_id=1, program_id=2 WHERE student_id='STU2024008';
COMMIT;

SELECT student_id, first_name || ' ' || last_name AS name, email, year_of_study
FROM students WHERE student_id BETWEEN 'STU2024001' AND 'STU2024008' ORDER BY student_id;
