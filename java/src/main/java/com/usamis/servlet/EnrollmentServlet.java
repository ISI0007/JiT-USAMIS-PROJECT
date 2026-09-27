package com.usamis.servlet;

import jakarta.servlet.annotation.WebServlet;

@WebServlet(urlPatterns = {"/api/enrollments", "/api/enrollments/*"})
public class EnrollmentServlet extends EnrollmentServletImpl {}
