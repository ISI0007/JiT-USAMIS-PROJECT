package com.usamis.servlet;

import jakarta.servlet.annotation.WebServlet;

@WebServlet(urlPatterns = {"/api/grades", "/api/grades/*"})
public class GradeServlet extends GradeServletImpl {}
