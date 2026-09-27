package com.usamis.servlet;

import jakarta.servlet.annotation.WebServlet;

@WebServlet(urlPatterns = {"/api/dashboard"})
public class DashboardServlet extends DashboardServletImpl {}
