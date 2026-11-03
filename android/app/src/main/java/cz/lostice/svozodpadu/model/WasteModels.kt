package cz.lostice.svozodpadu.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class AppThemeMode(val id: String, val label: String) {
    SYSTEM("system", "Podle systému"),
    LIGHT("light", "Světlé"),
    DARK("dark", "Tmavé"),
    OLED("oled", "OLED Černé"),
    CUSTOM("custom", "Vlastní");

    companion object {
        fun fromId(id: String): AppThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

enum class CalendarMarkerStyle(val id: String, val label: String) {
    DOTS("dots", "Tečky"),
    ICONS("icons", "Ikony");

    companion object {
        fun fromId(id: String): CalendarMarkerStyle = entries.firstOrNull { it.id == id } ?: DOTS
    }
}

data class WasteCalendarData(
    val municipality: String = "Město Loštice",
    val sourceUrl: String = "https://www.mu-lostice.cz/obcan/komunalni-sluzby/",
    val updatedAt: String = "",
    val wasteTypes: List<WasteType> = emptyList(),
    val places: List<WastePlace> = emptyList(),
    val events: List<WasteEvent> = emptyList()
)

data class WasteType(
    val id: String,
    val name: String,
    val shortName: String,
    val color: String,
    val category: String = "",
    val defaultNotify: Boolean = true,
    val isCollection: Boolean = true
)

data class WastePlace(
    val id: String,
    val name: String
)

data class WasteEvent(
    val id: String,
    val date: String,
    val wasteTypeId: String,
    val wasteTypeName: String,
    val shortName: String,
    val description: String,
    val note: String = "",
    val places: List<String> = emptyList(),
    val color: String = "#00aeef",
    val isCollection: Boolean = true
) {
    val localDate: LocalDate?
        get() = try {
            LocalDate.parse(date)
        } catch (e: Exception) {
            null
        }

    fun matchesPlaces(placeFilters: Set<String>): Boolean {
        if (placeFilters.isEmpty()) return true
        return places.any { eventPlace ->
            placeFilters.any { filterPlace -> eventPlace.equals(filterPlace, ignoreCase = true) }
        }
    }
}

fun getWasteIcon(wasteTypeId: String, wasteName: String = ""): ImageVector {
    val lower = wasteName.lowercase()
    return when {
        wasteTypeId == "7" || wasteTypeId == "12" || lower.contains("směsného") || lower.contains("komunální") ->
            Icons.Default.Delete // Popelnice pro komunální odpad
        wasteTypeId == "2" || lower.contains("plast") ->
            Icons.Default.LocalDrink // PET lahev pro plasty
        wasteTypeId == "10" || wasteTypeId == "11" || wasteTypeId == "8" || lower.contains("bioodpad") ->
            Icons.Default.Eco // Lístek pro bioodpad
        wasteTypeId == "3" || lower.contains("olej") ->
            Icons.Default.WaterDrop // Kapka / lahev pro oleje
        wasteTypeId == "13" || lower.contains("gastro") ->
            Icons.Default.Restaurant // Příbor pro gastro odpad
        wasteTypeId == "9" || lower.contains("listí") ->
            Icons.Default.Park // Listí / park
        wasteTypeId == "14" || lower.contains("větví") || lower.contains("ořez") ->
            Icons.Default.Forest // Stromy / větve
        wasteTypeId == "4" || wasteTypeId == "6" || lower.contains("sběrného dvora") ->
            Icons.Default.Warehouse // Sběrný dvůr
        else -> Icons.Default.Recycling
    }
}

fun parseHexColor(hex: String): Color {
    val cleaned = hex.trim().removePrefix("#")
    return try {
        val fullHex = when (cleaned.length) {
            3 -> cleaned.map { "$it$it" }.joinToString("")
            6 -> cleaned
            else -> "00AEEF"
        }
        val rgb = fullHex.toLong(16)
        if (fullHex.equals("ffff00", ignoreCase = true)) {
            Color(0xFFEAB308)
        } else {
            Color(0xFF000000 or rgb)
        }
    } catch (e: Exception) {
        Color(0xFF00AEEF)
    }
}

private val czechDayNames = mapOf(
    1 to "Pondělí",
    2 to "Úterý",
    3 to "Středa",
    4 to "Čtvrtek",
    5 to "Pátek",
    6 to "Sobota",
    7 to "Neděle"
)

private val czechMonthNames = mapOf(
    1 to "Leden",
    2 to "Únor",
    3 to "Březen",
    4 to "Duben",
    5 to "Květen",
    6 to "Červen",
    7 to "Červenec",
    8 to "Srpen",
    9 to "Září",
    10 to "Říjen",
    11 to "Listopad",
    12 to "Prosinec"
)

private val czechMonthGenitive = mapOf(
    1 to "ledna",
    2 to "února",
    3 to "března",
    4 to "dubna",
    5 to "května",
    6 to "června",
    7 to "července",
    8 to "srpna",
    9 to "září",
    10 to "října",
    11 to "listopadu",
    12 to "prosince"
)

fun formatCzechDateFull(date: LocalDate): String {
    val dayName = czechDayNames[date.dayOfWeek.value] ?: ""
    val monthGen = czechMonthGenitive[date.monthValue] ?: "${date.monthValue}."
    return "$dayName ${date.dayOfMonth}. $monthGen ${date.year}"
}

fun formatCzechDateShort(date: LocalDate): String {
    val dayName = czechDayNames[date.dayOfWeek.value] ?: ""
    return "$dayName ${date.dayOfMonth}. ${date.monthValue}."
}

fun formatCzechMonthYear(year: Int, month: Int): String {
    val m = czechMonthNames[month] ?: "$month"
    return "$m $year"
}

fun relativeDayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val diff = ChronoUnit.DAYS.between(today, date).toInt()
    return when {
        diff == 0 -> "DNES"
        diff == 1 -> "ZÍTRA"
        diff == 2 -> "POZÍTŘÍ"
        diff in 3..4 -> "Za $diff dny"
        diff > 4 -> "Za $diff dní"
        diff == -1 -> "Včera"
        else -> "${date.dayOfMonth}. ${date.monthValue}."
    }
}
