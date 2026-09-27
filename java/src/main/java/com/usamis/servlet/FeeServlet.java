package com.usamis.servlet;

import jakarta.servlet.annotation.WebServlet;

@WebServlet(urlPatterns = {"/api/fees", "/api/fees/*"})
public class FeeServlet extends FeeServletImpl {}
