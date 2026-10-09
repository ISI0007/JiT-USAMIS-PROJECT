package com.usamis.servlet;

import com.google.gson.JsonObject;
import com.usamis.dao.AcademicDAO;
import com.usamis.model.Models;
import com.usamis.model.Models.*;
import com.usamis.util.JsonUtil;
import com.usamis.util.ValidationUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/* ══════════════════════════════════════════════════════════════
   FEE SERVLET
   GET  /api/fees                  → all records (admin, finance)
   GET  /api/fees?student={id}     → by student
   GET  /api/fees/defaulters       → unpaid/partial only
   POST /api/fees                  → create record
   POST /api/fees/{id}/pay         → record a payment
══════════════════════════════════════════════════════════════ */
@WebServlet(urlPatterns = {"/api/fees", "/api/fees/*"})
public final class FeeServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(FeeServlet.class);
    private final AcademicDAO dao = new AcademicDAO();
    private final com.usamis.dao.StudentDAO studentDAO = new com.usamis.dao.StudentDAO();

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        String pathInfo   = req.getPathInfo();
        String studentParam = req.getParameter("student");

        // GET /api/fees/defaulters
        if ("/defaulters".equals(pathInfo)) {
            if (!canManageFees(user)) { JsonUtil.forbidden(resp); return; }
            JsonUtil.success(resp, dao.findFeeDefaulters());
            return;
        }

        // GET /api/fees?student=id
        // SECURITY: a student may only ever read THEIR OWN fees. The old code
        // let any student pass ?student=<other> and read another student's
        // billing (IDOR), and its self-branch passed user.id (a users-table id)
        // where a students.id was expected, so a student's own list was empty.
        if ("student".equalsIgnoreCase(user.roleName)) {
            com.usamis.model.Models.Student me = studentDAO.findByUserId(user.id).orElse(null);
            if (me == null) { JsonUtil.success(resp, java.util.List.of()); return; }
            JsonUtil.success(resp, dao.findFeesByStudent(me.id));
            return;
        }

        if (studentParam != null) {
            int sid = ValidationUtil.parseInt(studentParam, -1);
            if (sid < 0) { JsonUtil.badRequest(resp, "Invalid student ID"); return; }
            JsonUtil.success(resp, dao.findFeesByStudent(sid));
            return;
        }

        if (!canManageFees(user)) { JsonUtil.forbidden(resp); return; }
        JsonUtil.success(resp, dao.findAllFees());
    }

    @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        String pathInfo = req.getPathInfo();

        // POST /api/fees/{id}/pay — record additional payment
        if (pathInfo != null && pathInfo.matches("/\\d+/pay")) {
            handlePayment(req, resp, user);
            return;
        }

        if (!canManageFees(user)) { JsonUtil.forbidden(resp); return; }

        String body = req.getReader().lines().reduce("", String::concat);
        JsonObject j = JsonUtil.fromJson(body, JsonObject.class);
        if (j == null) { JsonUtil.badRequest(resp, "Invalid JSON"); return; }

        if (!j.has("studentId") || !j.has("feeType") || !j.has("totalAmount")) {
            JsonUtil.badRequest(resp, "studentId, feeType, totalAmount are required."); return;
        }

        FeeRecord f = new FeeRecord();
        f.receiptNo    = "RCP" + System.currentTimeMillis();
        f.studentId    = j.get("studentId").getAsInt();
        f.feeType      = j.get("feeType").getAsString();
        f.semester     = j.has("semester") ? j.get("semester").getAsString() : "Semester 1";
        f.totalAmount  = j.get("totalAmount").getAsDouble();
        f.paidAmount   = j.has("paidAmount") ? j.get("paidAmount").getAsDouble() : 0;
        f.paymentMethod= j.has("paymentMethod") ? j.get("paymentMethod").getAsString() : null;
        f.createdBy    = user.id;
        if (f.paidAmount > 0)
            try { f.paymentDate = java.time.LocalDate.now(); } catch (Exception ignored) {}

        try {
            int newId = dao.createFeeRecord(f);
            dao.log(user.id, user.username, user.roleName, "CREATE", "Fees",
                "Created fee record " + f.receiptNo + " for student #" + f.studentId +
                " amount ¥" + f.totalAmount, getIp(req), null);
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("id", newId);
            result.addProperty("receiptNo", f.receiptNo);
            result.addProperty("message", "Fee record created.");
            JsonUtil.ok(resp, result);
        } catch (Exception e) {
            log.error("Create fee", e);
            JsonUtil.serverError(resp, e.getMessage());
        }
    }

    private void handlePayment(HttpServletRequest req, HttpServletResponse resp, UserDTO user)
            throws IOException {
        if (!canManageFees(user)) { JsonUtil.forbidden(resp); return; }

        // Extract fee ID from path /api/fees/{id}/pay
        String pi  = req.getPathInfo();                       // "/{id}/pay"
        String idStr = pi.replaceAll("[^\\d]", "");
        int feeId  = ValidationUtil.parseInt(idStr, -1);
        if (feeId < 0) { JsonUtil.badRequest(resp, "Invalid fee ID"); return; }

        String body = req.getReader().lines().reduce("", String::concat);
        JsonObject j = JsonUtil.fromJson(body, JsonObject.class);
        if (j == null || !j.has("amount")) { JsonUtil.badRequest(resp, "amount required"); return; }

        double amount = j.get("amount").getAsDouble();
        if (amount <= 0) { JsonUtil.badRequest(resp, "Payment amount must be positive."); return; }
        String method = j.has("method") ? j.get("method").getAsString() : "Cash";

        boolean ok = dao.recordPayment(feeId, amount, method);
        if (ok) {
            dao.log(user.id, user.username, user.roleName, "UPDATE", "Fees",
                "Recorded payment ¥" + amount + " for fee #" + feeId, getIp(req), null);
            JsonUtil.success(resp, "Payment of ¥" + amount + " recorded.");
        } else { JsonUtil.serverError(resp, "Payment recording failed."); }
    }

    private boolean canManageFees(UserDTO u) {
        return "admin".equals(u.roleName) || "finance".equals(u.roleName);
    }
    private String getIp(HttpServletRequest r) {
        String xff = r.getHeader("X-Forwarded-For"); return xff != null ? xff.split(",")[0].trim() : r.getRemoteAddr();
    }
}
