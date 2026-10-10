"""Graph-based course recommendations.

Builds a lightweight student/course/prerequisite graph from the existing
relational data (no external graph library) and ranks courses for a student by:

  1. Prerequisite satisfaction  — only recommend courses whose prerequisites the
     student has already passed (hard filter).
  2. Graph proximity            — courses taken by similar students (co-enrollment),
     where similarity is cosine over the co-enrollment vector.
  3. Department / program fit   — prefer courses in the student's own department.

A full GNN would need far more history than seed data provides; this deterministic
graph scorer is the honest, testable core a GNN can later replace behind the same
interface (CourseRecommender.recommend).
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Iterable

import numpy as np


@dataclass
class Course:
    id: int
    code: str
    name: str
    department_id: int
    credits: int = 3
    prerequisites: list = field(default_factory=list)  # list of course ids


@dataclass
class StudentProfile:
    id: int
    department_id: int
    passed_course_ids: set = field(default_factory=set)
    enrolled_course_ids: set = field(default_factory=set)


class CourseRecommender:
    """Deterministic, explainable course recommender over a small graph."""

    def __init__(self, courses: Iterable[Course], enrollments: dict):
        # enrollments: {student_id: set(course_id)}  (historical + current)
        self.courses = {c.id: c for c in courses}
        self.enrollments = {int(k): set(v) for k, v in enrollments.items()}
        self._cosine_cache = {}

    # ── similarity ─────────────────────────────────────────────
    def _co_enrollment_vector(self, student_id: int) -> dict:
        return self.enrollments.get(int(student_id), set())

    def _similarity(self, a: int, b: int) -> float:
        key = (min(a, b), max(a, b))
        if key in self._cosine_cache:
            return self._cosine_cache[key]
        va, vb = self._co_enrollment_vector(a), self._co_enrollment_vector(b)
        if not va or not vb:
            self._cosine_cache[key] = 0.0
            return 0.0
        inter = len(va & vb)
        denom = float(np.sqrt(len(va)) * np.sqrt(len(vb))) or 1.0
        sim = inter / denom
        self._cosine_cache[key] = sim
        return sim

    # ── hard filter ────────────────────────────────────────────
    def _eligible(self, student: StudentProfile) -> list:
        taken = student.passed_course_ids | student.enrolled_course_ids
        out = []
        for c in self.courses.values():
            if c.id in taken:
                continue
            if all(p in student.passed_course_ids for p in c.prerequisites):
                out.append(c)
        return out

    # ── scoring ────────────────────────────────────────────────
    def recommend(self, student: StudentProfile, top_k: int = 5) -> list:
        eligible = self._eligible(student)
        if not eligible:
            return []

        # Peer popularity: how often each course appears among *similar* students.
        peer_scores = {}
        for other_id, other_courses in self.enrollments.items():
            if other_id == student.id:
                continue
            sim = self._similarity(student.id, other_id)
            if sim <= 0:
                continue
            for cid in other_courses:
                peer_scores[cid] = peer_scores.get(cid, 0.0) + sim

        max_peer = max(peer_scores.values()) if peer_scores else 1.0
        ranked = []
        for c in eligible:
            peer = peer_scores.get(c.id, 0.0) / max_peer
            dept_fit = 1.0 if c.department_id == student.department_id else 0.0
            score = 0.6 * peer + 0.3 * dept_fit + 0.1 * (c.credits / 4.0)
            reasons = []
            if peer > 0:
                reasons.append(f"taken by similar students (peer score {peer:.2f})")
            if dept_fit:
                reasons.append("matches your department")
            if c.prerequisites:
                reasons.append("prerequisites satisfied")
            ranked.append({
                "course_id": c.id, "code": c.code, "name": c.name,
                "department_id": c.department_id, "credits": c.credits,
                "score": round(float(score), 4), "reasons": reasons,
            })
        ranked.sort(key=lambda r: r["score"], reverse=True)
        return ranked[:top_k]


def build_from_rows(course_rows: list, enrollment_rows: list) -> CourseRecommender:
    """Adapt raw REST rows into the recommender. Keeps parsing testable + separate."""
    courses = []
    for r in course_rows:
        pre = r.get("prerequisites") or []
        if isinstance(pre, str):
            pre = [int(x) for x in pre.replace(",", " ").split() if x.strip().isdigit()]
        courses.append(Course(
            id=int(r["id"]), code=str(r.get("code", "")), name=str(r.get("name", "")),
            department_id=int(r.get("departmentId", r.get("department_id", 0)) or 0),
            credits=int(r.get("credits", 3) or 3), prerequisites=[int(p) for p in pre],
        ))
    enrollments = {}
    for r in enrollment_rows:
        sid = int(r.get("studentId", r.get("student_id", 0)) or 0)
        cid = int(r.get("courseId", r.get("course_id", 0)) or 0)
        enrollments.setdefault(sid, set()).add(cid)
    return CourseRecommender(courses, enrollments)
