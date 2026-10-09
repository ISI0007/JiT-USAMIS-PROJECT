package com.usamis.servlet;

import com.google.gson.JsonObject;
import com.usamis.dao.AcademicDAO;
import com.usamis.dao.StudentDAO;
import com.usamis.model.Models;
import com.usamis.model.Models.*;
import com.usamis.util.JsonUtil;
import com.usamis.util.ValidationUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

/* ══════════════════════════════════════════════════════════════
   DASHBOARD SERVLET
   GET /api/dashboard → role-aware stats.

   WHY role-aware: the dashboard used to return the SAME global figures to
   everyone, so a student saw the whole institution's numbers. A student must
   see ONLY their own: their GPA, their enrollments, their fees, their best
   score. Staff see aggregate figures.
══════════════════════════════════════════════════════════════ */
@WebServlet(urlPatterns = {"/api/dashboard"})
public final class DashboardServlet extends HttpServlet {

    private final AcademicDAO dao = new AcademicDAO();
    private final StudentDAO studentDAO = new StudentDAO();

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");

        if (user != null && "student".equalsIgnoreCase(user.roleName)) {
            // Self-scoped payload: resolve the caller's own student row.
            JsonObject out = new JsonObject();
            Optional<Student> meOpt = studentDAO.findByUserId(user.id);
            if (meOpt.isEmpty()) {
                out.addProperty("scope", "student");
                out.addProperty("myGpa", 0.0);
                out.addProperty("myEnrollments", 0);
                out.addProperty("myFeesBilled", 0.0);
                out.addProperty("myFeesPaid", 0.0);
                out.addProperty("myFeesOutstanding", 0.0);
                out.addProperty("myBestScore", 0.0);
                out.addProperty("myBestCourse", "");
                JsonUtil.success(resp, out);
                return;
            }
            Student me = meOpt.get();
            out.addProperty("scope", "student");
            out.addProperty("studentId", me.studentId);
            out.addProperty("studentName", (me.firstName + " " + me.lastName).trim());

            // Own enrollments + own grades (DAO is already student-scoped by student.id)
            var enrollments = dao.findEnrollmentsByStudent(me.id);
            var grades = dao.findGradesByStudent(me.id);
            out.addProperty("myEnrollments", enrollments.size());

            double sumPts = 0; int nPts = 0;
            double best = -1; String bestCourse = "";
            for (Grade g : grades) {
                if (g.gpaPoints != null) { sumPts += g.gpaPoints; nPts++; }
                if (g.score != null && g.score > best) { best = g.score; bestCourse = g.courseCode != null ? g.courseCode : ""; }
            }
            out.addProperty("myGpa", nPts > 0 ? Math.round((sumPts / nPts) * 100.0) / 100.0 : 0.0);
            out.addProperty("myBestScore", best >= 0 ? best : 0.0);
            out.addProperty("myBestCourse", bestCourse);

            // Own fees
            double billed = 0, paid = 0;
            for (FeeRecord f : dao.findFeesByStudent(me.id)) {
                billed += f.totalAmount; paid += f.paidAmount;
            }
            out.addProperty("myFeesBilled", billed);
            out.addProperty("myFeesPaid", paid);
            out.addProperty("myFeesOutstanding", billed - paid);
            JsonUtil.success(resp, out);
            return;
        }

        // Staff: aggregate institution figures.
        JsonObject out = new JsonObject();
        DashboardStats stats = dao.getDashboardStats();
        out.addProperty("scope", "staff");
        out.add("stats", com.google.gson.JsonParser.parseString(gsonOf(stats)));
        JsonUtil.success(resp, out);
    }

    private String gsonOf(DashboardStats s) {
        JsonObject o = new JsonObject();
        o.addProperty("totalStudents", s.totalStudents);
        o.addProperty("activeEnrollments", s.activeEnrollments);
        o.addProperty("averageGpa", s.averageGpa);
        o.addProperty("studentsAtRisk", s.studentsAtRisk);
        o.addProperty("feeCollectionRate", s.feeCollectionRate);
        o.addProperty("totalFeesBilled", s.totalFeesBilled);
        o.addProperty("totalFeesPaid", s.totalFeesPaid);
        o.addProperty("totalFeesOutstanding", s.totalFeesOutstanding);
        o.addProperty("totalCourses", s.totalCourses);
        o.addProperty("totalUsers", s.totalUsers);
        return o.toString();
    }
}
