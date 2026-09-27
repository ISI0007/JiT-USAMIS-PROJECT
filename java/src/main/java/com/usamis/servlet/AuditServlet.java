package com.usamis.servlet;

import jakarta.servlet.annotation.WebServlet;

@WebServlet(urlPatterns = {"/api/audit", "/api/audit/*"})
public class AuditServlet extends AuditServletImpl {}
