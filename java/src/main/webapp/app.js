/* USAMIS application adapter.
 * The view remains in index.html, while all state changes go through the
 * Servlet controllers. No credentials or business data are stored in JS.
 */
'use strict';

// The WAR is deployed under Tomcat's /usamis context. Derive that prefix so
// the same frontend also works when the app is mounted at another context.
const API_BASE = window.location.pathname.replace(/\/$/, '');

const Api = {
  async request(path, options = {}) {
    const response = await fetch(`${API_BASE}${path}`, {
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
      ...options
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok || body.success === false) {
      throw new Error(body.message || `Request failed (${response.status})`);
    }
    return body.data !== undefined ? body.data : body;
  },
  get(path) { return this.request(path); },
  post(path, data) { return this.request(path, { method: 'POST', body: JSON.stringify(data) }); },
  put(path, data) { return this.request(path, { method: 'PUT', body: JSON.stringify(data) }); },
  del(path) { return this.request(path, { method: 'DELETE' }); }
};

function showApiError(error) {
  console.error(error);
  showToast(error.message || 'Server request failed', 'error');
}

function normalizeUser(user) {
  return { ...user, role: user.role || user.roleName,
    name: user.name || `${user.firstName || ''} ${user.lastName || ''}`.trim(),
    title: user.title || user.roleName || user.role };
}

function mapStudent(s) {
  return { id: s.studentId, fname: s.firstName, lname: s.lastName,
    dept: s.departmentName || s.department || '', prog: s.programName || s.program || '',
    year: s.yearOfStudy ? `Year ${s.yearOfStudy}` : '', gpa: Number(s.gpa || 0),
    email: s.email || '', phone: s.phone || '', gender: s.gender || '',
    status: s.status || 'Active', dob: s.dateOfBirth || '', _raw: s };
}
function mapCourse(c) {
  return { id: c.id, code: c.code, name: c.name, dept: c.departmentName || '',
    credits: c.credits, instructor: c.instructorName || '', enrolled: c.enrolledCount || 0,
    max: c.maxEnrollment, semester: c.semester, status: c.status, _raw: c };
}
function mapEnrollment(e) {
  return { id: e.id, student: e.studentNo, studentName: e.studentName,
    course: e.courseCode, courseName: e.courseName, semester: e.semester,
    date: e.enrolledDate, status: e.status, _raw: e };
}
function mapGrade(g) {
  return { id: g.id, studentId: g.studentNo, studentName: g.studentName,
    course: g.courseCode, courseName: g.courseName, score: g.score,
    letter: g.letterGrade, points: g.gpaPoints, semester: g.semester, _raw: g };
}
function mapFee(f) {
  return { id: f.id, receipt: f.receiptNo, student: f.studentNo, studentName: f.studentName,
    type: f.feeType, semester: f.semester, total: f.totalAmount, paid: f.paidAmount,
    date: f.paymentDate || '-', method: f.paymentMethod || '-', status: f.status, _raw: f };
}

async function loadServerState() {
  const [students, courses, enrollments, grades] = await Promise.all([
    Api.get('/api/students'), Api.get('/api/courses'), Api.get('/api/enrollments'), Api.get('/api/grades')
  ]);
  DB.students = students.map(mapStudent);
  DB.courses = courses.map(mapCourse);
  DB.enrollments = enrollments.map(mapEnrollment);
  DB.grades = grades.map(mapGrade);
  if (currentUser.role === 'admin') {
    const [users, audit] = await Promise.all([Api.get('/api/users'), Api.get('/api/audit?limit=50')]);
    DB.users = users.map(u => ({ id: u.id, username: u.username, fname: u.firstName,
      lname: u.lastName, email: u.email, role: u.roleName, status: u.status }));
    DB.audit = audit.map(a => ({ id: a.id, time: a.createdAt, user: a.username,
      role: a.roleName, action: a.action, module: a.module, desc: a.description, ip: a.ipAddress }));
  }
  const fees = await Api.get('/api/fees').catch(() => []);
  DB.fees = fees.map(mapFee);
}

/* Override the demo login with the real Session-backed controller. */
async function doLogin() {
  const username = document.getElementById('loginUser').value.trim();
  const password = document.getElementById('loginPass').value;
  try {
    const result = await Api.post('/api/auth/login', { username, password });
    currentUser = normalizeUser(result.user);
    document.getElementById('loginPage').style.display = 'none';
    document.getElementById('app').style.display = 'flex';
    await initApp();
  } catch (e) { showApiError(e); }
}

async function doLogout() {
  try { await Api.post('/api/auth/logout', {}); } catch (e) { console.warn(e); }
  currentUser = null;
  document.getElementById('loginPage').style.display = 'flex';
  document.getElementById('app').style.display = 'none';
}

async function initApp() {
  try {
    renderSidebarUser(); renderSidebarNav(); setTopbarDate();
    await loadServerState(); populateDropdowns(); navigateTo('dashboard');
  } catch (e) { showApiError(e); }
}

/* Restore an existing server session after a browser refresh. */
document.addEventListener('DOMContentLoaded', async () => {
  try {
    const user = await Api.get('/api/auth/me');
    currentUser = normalizeUser(user);
    document.getElementById('loginPage').style.display = 'none';
    document.getElementById('app').style.display = 'flex';
    await initApp();
  } catch (_) { /* no active session; show login */ }
});

async function addStudent() {
  try {
    const body = { studentId: document.getElementById('s_id').value.trim(),
      firstName: document.getElementById('s_fname').value.trim(), lastName: document.getElementById('s_lname').value.trim(),
      departmentId: Number(document.getElementById('s_dept').value) || 1,
      programId: Number(document.getElementById('s_prog').value) || 1,
      yearOfStudy: Number(document.getElementById('s_year').value) || 1,
      email: document.getElementById('s_email').value.trim(), phone: document.getElementById('s_phone').value.trim(),
      gender: document.getElementById('s_gender').value, dateOfBirth: document.getElementById('s_dob').value };
    await Api.post('/api/students', body); closeModal('modalStudent'); await loadServerState(); renderStudents();
    showToast('Student saved to the database', 'success');
  } catch (e) { showApiError(e); }
}
async function deleteStudent(i) { if (!confirm('Deactivate this student?')) return;
  try { await Api.del(`/api/students/${DB.students[i]._raw.id}`); await loadServerState(); renderStudents(); showToast('Student deactivated', 'success'); }
  catch (e) { showApiError(e); }
}
async function addCourse() { try {
  const body = { code: document.getElementById('c_code').value.trim(), name: document.getElementById('c_name').value.trim(),
    departmentId: Number(document.getElementById('c_dept').value) || 1, credits: Number(document.getElementById('c_credits').value) || 3,
    maxEnrollment: Number(document.getElementById('c_max').value) || 50, semester: document.getElementById('c_sem').value };
  await Api.post('/api/courses', body); closeModal('modalCourse'); await loadServerState(); renderCourses(); showToast('Course saved to the database', 'success');
} catch (e) { showApiError(e); } }
async function deleteCourse(i) { if (!confirm('Cancel this course?')) return;
  try { await Api.del(`/api/courses/${DB.courses[i].id}`); await loadServerState(); renderCourses(); showToast('Course cancelled', 'success'); }
  catch (e) { showApiError(e); }
}
async function deleteEnroll(i) { if (!confirm('Drop this enrollment?')) return;
  try { await Api.del(`/api/enrollments/${DB.enrollments[i]._raw.id}`); await loadServerState(); renderEnrollment(); showToast('Enrollment updated', 'success'); }
  catch (e) { showApiError(e); }
}

function populateDropdowns() {
  const studentOpts = DB.students.map(s => `<option value="${s._raw.id}">${s.fname} ${s.lname} (${s.id})</option>`).join('');
  const courseOpts = DB.courses.map(c => `<option value="${c.id}">${c.name} (${c.code})</option>`).join('');
  ['e_student','g_student','f_student'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.innerHTML = '<option value="">— Select Student —</option>' + studentOpts;
  });
  ['e_course','g_course'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.innerHTML = '<option value="">— Select Course —</option>' + courseOpts;
  });
}

async function addEnrollment() {
  const studentId = Number(document.getElementById('e_student').value);
  const courseId = Number(document.getElementById('e_course').value);
  if (!studentId || !courseId) { showToast('Please select student and course', 'error'); return; }
  try {
    await Api.post('/api/enrollments', { studentId, courseId,
      semester: document.getElementById('e_sem').value });
    closeModal('modalEnroll'); await loadServerState(); populateDropdowns(); renderEnrollment(); renderCourses();
    showToast('Enrollment saved to the database', 'success');
  } catch (e) { showApiError(e); }
}

async function addGrade() {
  const studentId = Number(document.getElementById('g_student').value);
  const courseId = Number(document.getElementById('g_course').value);
  const score = Number(document.getElementById('g_score').value);
  const enrollment = DB.enrollments.find(e => e._raw.studentId === studentId && e._raw.courseId === courseId);
  if (!studentId || !courseId || !Number.isFinite(score) || !enrollment) {
    showToast('Select an enrolled student/course and enter a valid score', 'error'); return;
  }
  try {
    await Api.post('/api/grades', { enrollmentId: enrollment._raw.id, score });
    closeModal('modalGrade'); await loadServerState(); renderGrades();
    showToast('Grade saved to the database', 'success');
  } catch (e) { showApiError(e); }
}

async function addFee() {
  const studentId = Number(document.getElementById('f_student').value);
  const total = Number(document.getElementById('f_total').value);
  const paid = Number(document.getElementById('f_paid').value || 0);
  if (!studentId || !Number.isFinite(total) || !Number.isFinite(paid) || paid > total) {
    showToast('Enter valid fee amounts', 'error'); return;
  }
  try {
    await Api.post('/api/fees', { studentId, totalAmount: total, paidAmount: paid,
      feeType: document.getElementById('f_type').value,
      semester: document.getElementById('f_sem').value,
      paymentMethod: document.getElementById('f_method').value });
    closeModal('modalFee'); await loadServerState(); renderFees();
    showToast('Fee record saved to the database', 'success');
  } catch (e) { showApiError(e); }
}

async function payFee(i) {
  const fee = DB.fees[i];
  const amount = Number(prompt('Payment amount', String(fee.total - fee.paid)));
  if (!Number.isFinite(amount) || amount <= 0) return;
  try { await Api.post(`/api/fees/${fee._raw.id}/pay`, { amount, method: fee.method || 'Cash' });
    await loadServerState(); renderFees(); showToast('Payment saved to the database', 'success');
  } catch (e) { showApiError(e); }
}

async function addUser() {
  const password = document.getElementById('u_pass').value;
  const role = document.getElementById('u_role').value;
  const roleIds = { admin: 1, registrar: 2, lecturer: 3, finance: 4, student: 5 };
  try {
    await Api.post('/api/users', { username: document.getElementById('u_uname').value.trim(), password,
      firstName: document.getElementById('u_fname').value.trim(), lastName: document.getElementById('u_lname').value.trim(),
      email: document.getElementById('u_email').value.trim(), roleId: roleIds[role] || 5 });
    closeModal('modalUser'); await loadServerState(); renderUsers(); showToast('User saved to the database', 'success');
  } catch (e) { showApiError(e); }
}

async function deleteUser(i) {
  const user = DB.users[i]; if (!user || !confirm('Deactivate this user?')) return;
  try { await Api.del(`/api/users/${user.id}`); await loadServerState(); renderUsers(); showToast('User deactivated', 'success'); }
  catch (e) { showApiError(e); }
}
