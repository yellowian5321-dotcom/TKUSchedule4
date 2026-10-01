package com.example.tkuschedule.data

import com.example.tkuschedule.network.TkuCourseParser

class CourseRepository(
    private val parser: TkuCourseParser =
        TkuCourseParser()
) {

    suspend fun loadDepartments():
            Result<List<Department>> {

        return runCatching {
            parser.fetchDepartments()
        }
    }

    suspend fun loadCourses(
        departmentCode: String,
        grade: Int? = null,
        className: String? = null
    ): Result<List<Course>> {

        return runCatching {
            parser.fetchCourses(
                departmentCode = departmentCode,
                grade = grade,
                className = className
            )
        }
    }

    suspend fun searchCourses(
        query: String
    ): Result<List<Course>> {

        return runCatching {
            parser.searchCourses(query)
        }
    }
}