package com.example.tkuschedule.location

import java.text.Normalizer
import java.util.Locale

data class ClassroomDestination(
    val originalClassroom: String,
    val buildingCode: String,
    val buildingName: String,
    val roomNumber: String,
    val searchQuery: String,
    val campusName: String = "淡水校園",
    val canLocateBuilding: Boolean = true,
    val locationNote: String? = null
) {
    val displayName: String
        get() = if (roomNumber.isBlank()) buildingName else "$buildingName $roomNumber"
}

object ClassroomLocationRepository {
    private data class Building(
        val code: String,
        val name: String,
        val aliases: List<String> = emptyList(),
        val campus: String = "淡水校園",
        val note: String? = null,
        val canLocate: Boolean = true
    )

    // 官方來源：
    // https://classic.tku.edu.tw/acad/TCN1003.pdf
    // https://classic.tku.edu.tw/doc/CampusMap202103.pdf
    // 總務處「全校平面配置圖暨各大樓平面圖申請表」（2018.01）。
    // 已收錄的大樓使用下方公開地圖定位點；其他大樓才查詢地址服務。
    private val buildings = listOf(
        Building("A", "行政大樓"),
        Building("B", "商管大樓", listOf("商館", "商學大樓")),
        Building("C", "鍾靈化學館", listOf("鐘靈化學館", "化學館")),
        Building("CH", "覺軒", listOf("覺軒會館", "覺軒花園")),
        Building("CL", "建邦教學大樓", listOf("蘭陽校園"), campus = "蘭陽校園", note = "蘭陽校園教室不適用淡水校園的大樓步行估算。", canLocate = false),
        Building("D", "台北校園", campus = "台北校園", note = "台北校園教室不適用淡水校園的大樓步行估算。", canLocate = false),
        Building("DR", "白樓"),
        Building("E", "工學大樓", listOf("新工館", "新工學大樓")),
        Building("ED", "教育館", listOf("教育大樓", "教育教學大樓")),
        Building("F", "會文館"),
        Building("FF", "學人宿舍剛棟（舊稱）", listOf("剛棟宿舍", "剛棟學人宿舍", "學人宿舍剛棟"), note = "舊剛棟已改為松濤四館，使用四館的大樓定位點。"),
        Building("FL", "外國語文大樓", listOf("外語大樓", "外語館")),
        Building("FP", "學人宿舍毅棟（舊稱）", listOf("毅棟宿舍", "毅棟學人宿舍", "學人宿舍毅棟"), note = "舊毅棟已改為松濤五館，使用五館的大樓定位點。"),
        Building("FS", "樸棟學人宿舍", listOf("樸棟宿舍")),
        Building("FT", "學人宿舍實棟", listOf("實棟宿舍", "實棟學人宿舍")),
        Building("G", "工學館", listOf("舊工館", "舊工學館")),
        Building("GA", "大門管制站", listOf("正門警衛室")),
        Building("GB", "藍白小鎮"),
        Building("GC", "松濤館旁警衛室", listOf("松濤警衛室")),
        Building("GE", "大忠管制站", listOf("大忠街警衛室")),
        Building("GO", "勤務監控管制站", listOf("勤務監控管制中心", "勤務監控中心")),
        Building("GS", "水源街警衛室"),
        Building("H", "宮燈教室", note = "宮燈教室分布於多棟，距離採區域定位點粗估。"),
        Building("HC", "守謙國際會議中心"),
        Building("I", "覺生綜合大樓", listOf("覺生大樓")),
        Building("J", "麗澤國際學舍", listOf("麗澤學舍")),
        Building("K", "建築系館", listOf("建築系", "建築館")),
        Building("L", "文學館", listOf("文館")),
        Building("M", "海事博物館", listOf("黑天鵝展示廳")),
        Building("N", "紹謨紀念游泳館", listOf("游泳館")),
        // O、Q 的中文名稱相同，但不可合併為同一個位置。
        Building("O", "傳播館 O 棟", listOf("傳播館O棟", "傳播館O", "資訊傳播學系")),
        Building("P", "司令臺", listOf("司令台", "水資源中心", "達文西樂創基地")),
        Building("Q", "傳播館 Q 棟", listOf("傳播館Q棟", "傳播館Q", "大眾傳播學系")),
        Building("R", "學生活動中心", listOf("活動中心")),
        Building("RT", "網球場"),
        Building("S", "騮先紀念科學館", listOf("騮先科學館", "科學館")),
        Building("SA", "紹謨紀念活動中心", campus = "蘭陽校園", note = "這是蘭陽校園設施，不適用淡水校園的步行估算。", canLocate = false),
        Building("SG", "紹謨紀念體育館", listOf("體育館")),
        Building("SS", "溜冰場"),
        Building("T", "驚聲紀念大樓", listOf("驚聲大樓", "文錙音樂廳", "驚聲國際會議廳")),
        Building("TH", "校史館暨張建邦創辦人紀念館", listOf("校史館", "張建邦創辦人紀念館")),
        Building("U", "覺生紀念圖書館", listOf("覺生圖書館")),
        Building("V", "視聽教育館", listOf("視教館")),
        Building("W", "風洞實驗館區", listOf("風洞實驗室", "風洞實驗館", "風工程中心"), note = "W 有多個實驗空間，距離採風洞館區定位點粗估。"),
        Building("X", "五虎崗機車停車場", listOf("五虎崗停車場"), note = "X 依官方 2021 校園圖代表機車停車場；舊版水工實驗室教室不能直接套用。"),
        Building("XA", "五虎崗社團空間24號", note = "這個校外社團空間代碼來自舊版配置圖，需確認最新完整地址。", canLocate = false),
        Building("XB", "五虎崗社團空間26號", note = "這個校外社團空間代碼來自舊版配置圖，需確認最新完整地址。", canLocate = false),
        Building("XC", "五虎崗綜合球場", listOf("五虎崗球場")),
        Building("Y", "自強館（舊代碼）", note = "舊自強館已改為教育館，請確認課表的實際教室代碼是否為 ED。", canLocate = false),
        Building("YY", "瀛苑", note = "瀛苑現為校史館暨張建邦創辦人紀念館所在建物。"),
        Building("Z", "松濤館區", listOf("松濤館", "文錙藝術中心", "文錙美術中心", "美食廣場"), note = "未指定松濤館別，使用宿舍區定位點粗估；ZA 至 ZE、Z1 至 Z5 可辨識各館。"),
        Building("ZA", "松濤一館", listOf("松濤館一館", "松濤1館", "松一館")),
        Building("ZB", "松濤二館", listOf("松濤館二館", "松濤2館", "松二館")),
        Building("ZC", "松濤三館", listOf("松濤館三館", "松濤3館", "松三館")),
        Building("ZD", "松濤四館", listOf("松濤館四館", "松濤4館", "松四館")),
        Building("ZE", "松濤五館", listOf("松濤館五館", "松濤5館", "松五館")),
        Building("ZF", "淡江國際學園", listOf("淡江學園", "利挺建設鑽傳紀念大樓"), note = "淡江國際學園位於校外，步行估算會包含校外路段。")
    )
    // 大樓層級定位點；距離與步行時間為估算。
    // Wikidata：CC0 結構化資料。資料查核日期：2026-10-06。
    // https://www.wikidata.org/wiki/Wikidata:WikiProject_Taiwan/Tamkang_University/Reports/Locations/Buildings
    // OSM 地物中心：© OpenStreetMap contributors，ODbL 1.0。
    // https://www.openstreetmap.org/copyright
    // FF/FP 為 ZD/ZE 的舊名稱；H、W、Z 為同代碼設施的區域定位點。
    private val buildingCoordinates = mapOf(
        "A" to GeoPoint(25.174847, 121.449139), // Q131243833
        "B" to GeoPoint(25.1763683, 121.4499566), // Q131243884
        "C" to GeoPoint(25.1751403, 121.4488896), // Q131244049
        "CH" to GeoPoint(25.173741, 121.4484968), // Q131244063
        "DR" to GeoPoint(25.1736572, 121.4488002), // Q131244077
        "E" to GeoPoint(25.1759529, 121.4515456), // Q131244263
        "ED" to GeoPoint(25.1757271, 121.4526171), // Q131244294
        "F" to GeoPoint(25.1756375, 121.4495702), // Q124661144
        "FF" to GeoPoint(25.1753048, 121.4524769), // OSM way/235418161
        "FL" to GeoPoint(25.1748889, 121.4516784), // Q131260512
        "FP" to GeoPoint(25.1753849, 121.4528244), // OSM way/235418162
        "FT" to GeoPoint(25.175362, 121.4521055), // OSM way/235418160
        "G" to GeoPoint(25.175975, 121.451065), // Q131260926
        "GA" to GeoPoint(25.1738098, 121.4471171), // Q131261184
        "GB" to GeoPoint(25.17674, 121.4504757), // Q131263043
        "GE" to GeoPoint(25.1764667, 121.4483198), // Q131263638
        "GO" to GeoPoint(25.17416, 121.451057), // Q131264167
        "H" to GeoPoint(25.17441, 121.4492821), // Q131264708
        "HC" to GeoPoint(25.1746943, 121.4479357), // Q114979587
        "I" to GeoPoint(25.174302, 121.450866), // Q124661101
        "J" to GeoPoint(25.1761396, 121.447894), // Q131265609
        "K" to GeoPoint(25.176404, 121.450936), // Q124662226
        "L" to GeoPoint(25.1762736, 121.4494249), // Q124662753
        "M" to GeoPoint(25.1760906, 121.4504526), // Q134405794
        "N" to GeoPoint(25.1744117, 121.4472702), // Q134405799
        "O" to GeoPoint(25.1755633, 121.4486355), // Q134405805
        "P" to GeoPoint(25.173943, 121.445709), // Q134405809
        "Q" to GeoPoint(25.1756679, 121.4491764), // Q134405811
        "R" to GeoPoint(25.1747467, 121.4500863), // Q134405819
        "RT" to GeoPoint(25.1750254, 121.4502091), // Q134405822
        "S" to GeoPoint(25.17528, 121.44823), // Q134405825
        "SG" to GeoPoint(25.176305, 121.44892), // Q125968876
        "SS" to GeoPoint(25.175674, 121.4477146), // Q134405830
        "T" to GeoPoint(25.17542, 121.4510364), // Q134405833
        "TH" to GeoPoint(25.1738449, 121.4493573), // OSM node/8991866927
        "U" to GeoPoint(25.174956, 121.4508912), // Q124661987
        "V" to GeoPoint(25.17494, 121.449397), // Q134405839
        "W" to GeoPoint(25.17638, 121.451294), // Q134405842
        "X" to GeoPoint(25.175582, 121.4531627), // Q134405846
        "XC" to GeoPoint(25.1755207, 121.4536587), // Q134405857
        "YY" to GeoPoint(25.1738734, 121.4493287), // OSM way/294124677
        "Z" to GeoPoint(25.175008, 121.4519899), // Q134405865
        "ZA" to GeoPoint(25.1745885, 121.4517342), // OSM way/235418156
        "ZB" to GeoPoint(25.1748516, 121.4522401), // OSM way/454159667
        "ZC" to GeoPoint(25.1748472, 121.4528), // OSM way/454159657
        "ZD" to GeoPoint(25.1753048, 121.4524769), // OSM way/235418161
        "ZE" to GeoPoint(25.1753849, 121.4528244), // OSM way/235418162
        "ZF" to GeoPoint(25.1776015, 121.4428487), // Q134405867
    )

    val builtInBuildingCodes: Set<String>
        get() = buildingCoordinates.keys.toSet()

    fun findKnownLocation(destination: ClassroomDestination): GeoPoint? {
        if (destination.campusName != "淡水校園" || !destination.canLocateBuilding) return null
        return buildingCoordinates[destination.buildingCode]
    }

    private val buildingsByCode = buildings.associateBy { it.code }
    private val codePattern = Regex("^([A-Z]+)([0-9]+[A-Z]?)?$")
    private val roomPattern = Regex("^[0-9]+[A-Z]?$")
    // 官方住宿辦公室使用 Z2200 等編碼：Z2 館 + 200 室。
    private val numberedDormitoryPattern = Regex("^Z([1-5])([0-9]{3,}[A-Z]?)?$")
    private val dormitoryCodes = mapOf("1" to "ZA", "2" to "ZB", "3" to "ZC", "4" to "ZD", "5" to "ZE")

    val supportedBuildingNames: Map<String, String>
        get() = buildings.associate { it.code to it.name }

    fun findDestination(classroom: String): ClassroomDestination? {
        val text = normalize(classroom)
            .removePrefix("淡江大學淡水校園")
            .removePrefix("淡江大學")
            .removePrefix("淡水校園")
        if (text.isEmpty() || text in setOf("未公告", "教室未公告", "無", "線上", "遠距", "ONLINE")) return null
        numberedDormitoryPattern.matchEntire(text)?.let { match ->
            val code = dormitoryCodes.getValue(match.groupValues[1])
            return createDestination(classroom, buildingsByCode.getValue(code), match.groupValues[2])
        }
        codePattern.matchEntire(text)?.let { match ->
            // 讀取完整字母部分：ED 不會誤判為 E；未知 EDX 也不會退回 E。
            val building = buildingsByCode[match.groupValues[1]] ?: return null
            return createDestination(classroom, building, match.groupValues[2])
        }
        // 中文名稱配對以較長名稱優先，例如視聽教育館不能誤判為教育館。
        val nameMatches = buildings.flatMap { building ->
            (listOf(building.name) + building.aliases).distinct().mapNotNull { name ->
                val normalizedName = normalize(name)
                if (!text.startsWith(normalizedName)) return@mapNotNull null
                val suffix = text.removePrefix(normalizedName).removePrefix("教室").removeSuffix("教室").removePrefix(building.code)
                if (suffix.isNotEmpty() && !roomPattern.matches(suffix)) return@mapNotNull null
                Triple(building, suffix, normalizedName.length)
            }
        }
        val longestLength = nameMatches.maxOfOrNull { it.third } ?: return null
        val longestMatches = nameMatches.filter { it.third == longestLength }.distinctBy { it.first.code }
        if (longestMatches.size != 1) return null
        val match = longestMatches.single()
        return createDestination(classroom, match.first, match.second)
    }

    private fun createDestination(original: String, building: Building, room: String) = ClassroomDestination(
        originalClassroom = original,
        buildingCode = building.code,
        buildingName = building.name,
        roomNumber = room,
        searchQuery = "淡江大學${building.campus} ${building.name}",
        campusName = building.campus,
        canLocateBuilding = building.canLocate && building.campus == "淡水校園" &&
                !(building.code == "X" && room.isNotBlank()),
        locationNote = if (building.code == "X" && room.isNotBlank())
            "舊 X 教室代碼可能代表水工實驗室，無法把「$original」定位到機車停車場，請確認完整設施名稱。"
        else building.note
    )

    fun coordinateQueries(destination: ClassroomDestination): List<String> {
        val building = buildingsByCode[destination.buildingCode] ?: return listOf(destination.searchQuery)
        return (listOf(destination.searchQuery) +
                (listOf(building.name) + building.aliases).map { "淡江大學${building.campus} $it" }).distinctBy { normalize(it) }
    }

    fun matchesBuildingLabel(destination: ClassroomDestination, label: String): Boolean {
        val text = normalize(label)
        val matches = buildings.flatMap { building ->
            (listOf(building.name) + building.aliases).distinct().mapNotNull { name ->
                val token = normalize(name)
                if (text.contains(token)) building.code to token.length else null
            }
        }
        val longest = matches.maxOfOrNull { it.second } ?: return false
        val codes = matches.filter { it.second == longest }.map { it.first }.distinct()
        return codes.size == 1 && codes.single() == destination.buildingCode
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .uppercase(Locale.ROOT)
        .replace(Regex("[\\s\\u3000\\-－]"), "")
}