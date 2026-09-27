package com.usamis.servlet;

import jakarta.servlet.annotation.WebServlet;

@WebServlet(urlPatterns = {"/api/courses", "/api/courses/*"})
public class CourseServlet extends CourseServletImpl {}
