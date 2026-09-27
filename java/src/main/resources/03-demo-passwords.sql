-- Demo passwords: admin123, reg123, lec123, fin123, stu123.
UPDATE users SET password_hash = '$2a$10$eyP.RxIKzNgIaZ.jkWW.nOgQPuqhnQaeF/W0l3XpxNziMZlTyTk8O' WHERE username = 'admin001';
UPDATE users SET password_hash = '$2a$10$Gmcwci7ayCpP.wryk6LfUO3Mt4eHSQmZSBPktX2vc4b8xqz4h2bK6' WHERE username = 'reg001';
UPDATE users SET password_hash = '$2a$10$GZur1CRkDWMf4BCO1gllau14j/8ttwqasVDOcBDLpstXMCd1hodl.' WHERE username IN ('lec001','lec002');
UPDATE users SET password_hash = '$2a$10$fpfFJ5aVusUo5NgDKqROBOZRNz3klXwoORIg/vC7uBBY1q0hUnvgi' WHERE username = 'fin001';
UPDATE users SET password_hash = '$2a$10$H.ito.aErzqgdp1QiwgUSuXwDu7qhWfR2ixR/omHf.ndjWkgFdutu' WHERE username IN ('stu001','stu002','stu003');
