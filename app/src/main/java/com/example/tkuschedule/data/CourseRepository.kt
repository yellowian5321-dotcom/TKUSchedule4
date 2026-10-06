package com.example.tkuschedule.data

import com.example.tkuschedule.network.CourseSearchResult
import com.example.tkuschedule.network.TkuCourseParser
import kotlinx.coroutines.CancellationException

class CourseRepository(
    private val parser: TkuCourseParser =
        TkuCourseParser()
) {
    suspend fun loadDepartments():
            Result<List<Department>> {
        return cancellableResult {
            parser.fetchDepartments()
        }
    }

    suspend fun loadCourses(
        departmentCode: String,
        grade: Int? = null,
        className: String? = null
    ): Result<List<Course>> {
        return cancellableResult {
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
        return cancellableResult {
            parser.searchCourses(query)
        }
    }

    suspend fun searchCoursesDetailed(
        query: String
    ): Result<CourseSearchResult> {
        return cancellableResult {
            parser.searchCoursesDetailed(query)
        }
    }
}

private suspend fun <T> cancellableResult(
    block: suspend () -> T
): Result<T> {
    return try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}