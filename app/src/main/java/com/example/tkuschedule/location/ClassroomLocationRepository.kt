package com.example.tkuschedule.location

data class ClassroomDestination(
    val originalClassroom: String,
    val buildingCode: String,
    val buildingName: String,
    val roomNumber: String,
    val searchQuery: String
) {
    val displayName: String
        get() {
            return if (roomNumber.isBlank()) {
                buildingName
            } else {
                "$buildingName $roomNumber"
            }
        }
}

object ClassroomLocationRepository {

    /*
     * 先收錄目前確定的大樓代碼。
     * 後續可以依照實際課表繼續加入。
     */
    private val buildingNames = mapOf(
        "B" to "商管大樓",
        "E" to "工學大樓"
    )

    fun findDestination(
        classroom: String
    ): ClassroomDestination? {

        val normalized =
            classroom
                .trim()
                .uppercase()
                .replace(" ", "")
                .replace("-", "")

        if (
            normalized.isBlank() ||
            normalized == "未公告"
        ) {
            return null
        }

        findByChineseBuildingName(
            originalClassroom = classroom,
            normalized = normalized
        )?.let {
            return it
        }

        return findByBuildingCode(
            originalClassroom = classroom,
            normalized = normalized
        )
    }

    private fun findByChineseBuildingName(
        originalClassroom: String,
        normalized: String
    ): ClassroomDestination? {

        val matchedBuilding =
            buildingNames.entries
                .firstOrNull {
                    normalized.contains(
                        it.value
                    )
                }
                ?: return null

        val roomNumber =
            normalized
                .replace(
                    matchedBuilding.value,
                    ""
                )
                .trim()

        return createDestination(
            originalClassroom =
                originalClassroom,
            buildingCode =
                matchedBuilding.key,
            buildingName =
                matchedBuilding.value,
            roomNumber =
                roomNumber
        )
    }

    private fun findByBuildingCode(
        originalClassroom: String,
        normalized: String
    ): ClassroomDestination? {

        val buildingCode =
            normalized
                .firstOrNull()
                ?.toString()
                ?: return null

        val buildingName =
            buildingNames[buildingCode]
                ?: return null

        val roomNumber =
            normalized
                .drop(1)
                .trim()

        return createDestination(
            originalClassroom =
                originalClassroom,
            buildingCode =
                buildingCode,
            buildingName =
                buildingName,
            roomNumber =
                roomNumber
        )
    }

    private fun createDestination(
        originalClassroom: String,
        buildingCode: String,
        buildingName: String,
        roomNumber: String
    ): ClassroomDestination {

        return ClassroomDestination(
            originalClassroom =
                originalClassroom,
            buildingCode =
                buildingCode,
            buildingName =
                buildingName,
            roomNumber =
                roomNumber,
            searchQuery =
                "淡江大學淡水校園 $buildingName"
        )
    }
}