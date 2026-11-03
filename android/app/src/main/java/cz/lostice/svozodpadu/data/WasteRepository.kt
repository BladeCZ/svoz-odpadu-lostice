package cz.lostice.svozodpadu.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import cz.lostice.svozodpadu.MainActivity
import cz.lostice.svozodpadu.model.AppThemeMode
import cz.lostice.svozodpadu.model.CalendarMarkerStyle
import cz.lostice.svozodpadu.model.WasteCalendarData
import cz.lostice.svozodpadu.model.WasteEvent
import cz.lostice.svozodpadu.model.formatCzechDateFull
import cz.lostice.svozodpadu.model.formatCzechDateShort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class WasteRepository(private val context: Context) {

    private val gson = Gson()
    private val cacheFile = File(context.cacheDir, "calendar_cache.json")
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "svoz_lostice_prefs"
        const val DATABASE_URL = "https://bladecz.github.io/svoz-odpadu-lostice/data/calendar.json"
        const val CHANNEL_ID = "svoz_odpadu_lostice_channel"
        const val ACTIVE_NOTIFICATION_ID = 202611

        // Filtry kalendáře (vícenásobný výběr)
        const val KEY_FILTER_WASTE_IDS = "filter_waste_ids"
        const val KEY_FILTER_PLACES = "filter_places_set"
        const val KEY_HIDE_YARD_IN_LIST = "hide_yard_in_list"

        // Upozornění – filtry a chování
        const val KEY_NOTIFY_ENABLED = "notify_enabled"
        const val KEY_NOTIFY_WASTE_IDS = "notify_waste_ids"
        const val KEY_NOTIFY_PLACES = "notify_places_set"
        const val KEY_NOTIFY_PERSISTENT = "notify_persistent"

        // 1) Dva dny předem
        const val KEY_NOTIFY_TWO_DAYS_BEFORE = "notify_two_days_before"
        const val KEY_NOTIFY_TWO_DAYS_BEFORE_HOUR = "notify_two_days_before_hour"
        const val KEY_NOTIFY_TWO_DAYS_BEFORE_MINUTE = "notify_two_days_before_minute"

        // 2) Den předem
        const val KEY_NOTIFY_DAY_BEFORE = "notify_day_before"
        const val KEY_NOTIFY_DAY_BEFORE_HOUR = "notify_day_before_hour"
        const val KEY_NOTIFY_DAY_BEFORE_MINUTE = "notify_day_before_minute"

        // 3) V den svozu
        const val KEY_NOTIFY_DAY_OF = "notify_day_of"
        const val KEY_NOTIFY_DAY_OF_HOUR = "notify_day_of_hour"
        const val KEY_NOTIFY_DAY_OF_MINUTE = "notify_day_of_minute"

        const val KEY_SENT_NOTIFICATIONS = "sent_notifications"

        // Customizace vzhledu (Nastavení)
        const val KEY_THEME_MODE = "ui_theme_mode"
        const val KEY_CUSTOM_PRIMARY_RGB = "ui_custom_primary_rgb"
        const val KEY_CUSTOM_BG_RGB = "ui_custom_bg_rgb"
        const val KEY_CALENDAR_MARKER_STYLE = "ui_calendar_marker_style"
        const val KEY_COMPACT_CARDS = "ui_compact_cards"
        const val KEY_HIGHLIGHT_CALENDAR_CELLS = "ui_highlight_calendar_cells"

        val DEFAULT_NOTIFY_IDS = setOf("7", "12", "2", "10", "11", "8", "3", "9", "14")
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Upozornění na svoz odpadu",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Upozornění na nadcházející svozy odpadu v Lošticích"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    // --- Filtry zobrazení v kalendáři (prázdná množina = vše vybráno) ---
    fun getSelectedWasteTypeFilters(): Set<String> =
        prefs.getStringSet(KEY_FILTER_WASTE_IDS, emptySet()) ?: emptySet()

    fun setSelectedWasteTypeFilters(wasteTypeIds: Set<String>) {
        prefs.edit().putStringSet(KEY_FILTER_WASTE_IDS, wasteTypeIds).apply()
    }

    fun getSelectedPlaceFilters(): Set<String> =
        prefs.getStringSet(KEY_FILTER_PLACES, emptySet()) ?: emptySet()

    fun setSelectedPlaceFilters(places: Set<String>) {
        prefs.edit().putStringSet(KEY_FILTER_PLACES, places).apply()
    }

    fun isHideYardInList(): Boolean =
        prefs.getBoolean(KEY_HIDE_YARD_IN_LIST, true)

    fun setHideYardInList(hide: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_YARD_IN_LIST, hide).apply()
    }

    // --- Nastavení upozornění ---
    fun isNotificationsEnabled(): Boolean =
        prefs.getBoolean(KEY_NOTIFY_ENABLED, true)

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_ENABLED, enabled).apply()
    }

    fun getNotifyWasteTypeIds(): Set<String> =
        prefs.getStringSet(KEY_NOTIFY_WASTE_IDS, DEFAULT_NOTIFY_IDS) ?: DEFAULT_NOTIFY_IDS

    fun setNotifyWasteTypeIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_NOTIFY_WASTE_IDS, ids).apply()
    }

    fun getNotifyPlaces(): Set<String> =
        prefs.getStringSet(KEY_NOTIFY_PLACES, emptySet()) ?: emptySet()

    fun setNotifyPlaces(places: Set<String>) {
        prefs.edit().putStringSet(KEY_NOTIFY_PLACES, places).apply()
    }

    fun isPersistentNotification(): Boolean =
        prefs.getBoolean(KEY_NOTIFY_PERSISTENT, false)

    fun setPersistentNotification(persistent: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_PERSISTENT, persistent).apply()
    }

    // 1) Dva dny předem
    fun isNotifyTwoDaysBefore(): Boolean =
        prefs.getBoolean(KEY_NOTIFY_TWO_DAYS_BEFORE, false)

    fun setNotifyTwoDaysBefore(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_TWO_DAYS_BEFORE, enabled).apply()
    }

    fun getNotifyTwoDaysBeforeHour(): Int =
        prefs.getInt(KEY_NOTIFY_TWO_DAYS_BEFORE_HOUR, 18)

    fun setNotifyTwoDaysBeforeHour(hour: Int) {
        prefs.edit().putInt(KEY_NOTIFY_TWO_DAYS_BEFORE_HOUR, hour.coerceIn(0, 23)).apply()
    }

    fun getNotifyTwoDaysBeforeMinute(): Int =
        prefs.getInt(KEY_NOTIFY_TWO_DAYS_BEFORE_MINUTE, 0)

    fun setNotifyTwoDaysBeforeMinute(minute: Int) {
        prefs.edit().putInt(KEY_NOTIFY_TWO_DAYS_BEFORE_MINUTE, minute.coerceIn(0, 59)).apply()
    }

    // 2) Den předem
    fun isNotifyDayBefore(): Boolean =
        prefs.getBoolean(KEY_NOTIFY_DAY_BEFORE, true)

    fun setNotifyDayBefore(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_DAY_BEFORE, enabled).apply()
    }

    fun getNotifyDayBeforeHour(): Int =
        prefs.getInt(KEY_NOTIFY_DAY_BEFORE_HOUR, 18)

    fun setNotifyDayBeforeHour(hour: Int) {
        prefs.edit().putInt(KEY_NOTIFY_DAY_BEFORE_HOUR, hour.coerceIn(0, 23)).apply()
    }

    fun getNotifyDayBeforeMinute(): Int =
        prefs.getInt(KEY_NOTIFY_DAY_BEFORE_MINUTE, 0)

    fun setNotifyDayBeforeMinute(minute: Int) {
        prefs.edit().putInt(KEY_NOTIFY_DAY_BEFORE_MINUTE, minute.coerceIn(0, 59)).apply()
    }

    // 3) V den svozu
    fun isNotifyDayOf(): Boolean =
        prefs.getBoolean(KEY_NOTIFY_DAY_OF, false)

    fun setNotifyDayOf(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_DAY_OF, enabled).apply()
    }

    fun getNotifyDayOfHour(): Int =
        prefs.getInt(KEY_NOTIFY_DAY_OF_HOUR, 6)

    fun setNotifyDayOfHour(hour: Int) {
        prefs.edit().putInt(KEY_NOTIFY_DAY_OF_HOUR, hour.coerceIn(0, 23)).apply()
    }

    fun getNotifyDayOfMinute(): Int =
        prefs.getInt(KEY_NOTIFY_DAY_OF_MINUTE, 0)

    fun setNotifyDayOfMinute(minute: Int) {
        prefs.edit().putInt(KEY_NOTIFY_DAY_OF_MINUTE, minute.coerceIn(0, 59)).apply()
    }

    // --- Customizace vzhledu ---
    fun getThemeMode(): AppThemeMode =
        AppThemeMode.fromId(prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.id) ?: AppThemeMode.SYSTEM.id)

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.id).apply()
    }

    fun getCustomPrimaryRgb(): Int =
        prefs.getInt(KEY_CUSTOM_PRIMARY_RGB, 0x10B981)

    fun setCustomPrimaryRgb(rgb: Int) {
        prefs.edit().putInt(KEY_CUSTOM_PRIMARY_RGB, rgb and 0xFFFFFF).apply()
    }

    fun getCustomBackgroundRgb(): Int =
        prefs.getInt(KEY_CUSTOM_BG_RGB, 0x07170E)

    fun setCustomBackgroundRgb(rgb: Int) {
        prefs.edit().putInt(KEY_CUSTOM_BG_RGB, rgb and 0xFFFFFF).apply()
    }

    fun getCalendarMarkerStyle(): CalendarMarkerStyle =
        CalendarMarkerStyle.fromId(
            prefs.getString(KEY_CALENDAR_MARKER_STYLE, CalendarMarkerStyle.DOTS.id) ?: CalendarMarkerStyle.DOTS.id
        )

    fun setCalendarMarkerStyle(style: CalendarMarkerStyle) {
        prefs.edit().putString(KEY_CALENDAR_MARKER_STYLE, style.id).apply()
    }

    fun isCompactCards(): Boolean =
        prefs.getBoolean(KEY_COMPACT_CARDS, false)

    fun setCompactCards(compact: Boolean) {
        prefs.edit().putBoolean(KEY_COMPACT_CARDS, compact).apply()
    }

    fun isHighlightCalendarCells(): Boolean =
        prefs.getBoolean(KEY_HIGHLIGHT_CALENDAR_CELLS, false)

    fun setHighlightCalendarCells(highlight: Boolean) {
        prefs.edit().putBoolean(KEY_HIGHLIGHT_CALENDAR_CELLS, highlight).apply()
    }

    // --- Načtení dat (Online -> Lokální cache -> Bundled Assets) ---
    suspend fun loadCalendarData(forceNetwork: Boolean = true): WasteCalendarData = withContext(Dispatchers.IO) {
        if (forceNetwork) {
            try {
                val url = URL(DATABASE_URL)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                }
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val parsed = gson.fromJson(jsonText, WasteCalendarData::class.java)
                    if (parsed != null && parsed.events.isNotEmpty()) {
                        cacheFile.writeText(jsonText, Charsets.UTF_8)
                        return@withContext parsed
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (cacheFile.exists()) {
            try {
                val cachedText = cacheFile.readText(Charsets.UTF_8)
                val parsed = gson.fromJson(cachedText, WasteCalendarData::class.java)
                if (parsed != null && parsed.events.isNotEmpty()) {
                    return@withContext parsed
                }
            } catch (_: Exception) {
            }
        }

        try {
            val assetText = context.assets.open("calendar.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val parsed = gson.fromJson(assetText, WasteCalendarData::class.java)
            if (parsed != null) {
                return@withContext parsed
            }
        } catch (_: Exception) {
        }

        WasteCalendarData()
    }

    // --- Kontrola a odeslání JEDNOHO souhrnného upozornění ---
    suspend fun checkAndSendNotifications() {
        if (!isNotificationsEnabled()) return

        val data = loadCalendarData(forceNetwork = true)
        val allowedTypeIds = getNotifyWasteTypeIds()
        val placeFilters = getNotifyPlaces()
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val currentTime = now.toLocalTime()

        val sentSet = (prefs.getStringSet(KEY_SENT_NOTIFICATIONS, emptySet()) ?: emptySet()).toMutableSet()
        val summaryLines = mutableListOf<String>()
        var mainTitle = "Upozornění na svoz odpadu"

        fun checkOffset(
            enabled: Boolean,
            daysAhead: Long,
            targetHour: Int,
            targetMinute: Int,
            prefixLabel: String,
            keyPrefix: String
        ) {
            if (!enabled) return
            val triggerTime = LocalTime.of(targetHour, targetMinute)
            if (currentTime.isBefore(triggerTime)) return

            val targetDate = today.plusDays(daysAhead)
            val matchingEvents = data.events.filter { ev ->
                ev.localDate == targetDate &&
                    allowedTypeIds.contains(ev.wasteTypeId) &&
                    ev.matchesPlaces(placeFilters)
            }
            if (matchingEvents.isEmpty()) return

            val notifKey = "${keyPrefix}_${targetDate}"
            if (sentSet.contains(notifKey)) return

            val uniqueNames = matchingEvents.map { it.shortName }.distinct().joinToString(", ")
            val uniquePlaces = matchingEvents.flatMap { it.places }.distinct().joinToString(", ")
            summaryLines.add("$prefixLabel (${formatCzechDateShort(targetDate)}): $uniqueNames [$uniquePlaces]")
            mainTitle = "$prefixLabel proběhne svoz odpadu!"
            sentSet.add(notifKey)
        }

        checkOffset(
            enabled = isNotifyDayOf(),
            daysAhead = 0,
            targetHour = getNotifyDayOfHour(),
            targetMinute = getNotifyDayOfMinute(),
            prefixLabel = "Dnes",
            keyPrefix = "dayof"
        )

        checkOffset(
            enabled = isNotifyDayBefore(),
            daysAhead = 1,
            targetHour = getNotifyDayBeforeHour(),
            targetMinute = getNotifyDayBeforeMinute(),
            prefixLabel = "Zítra",
            keyPrefix = "daybefore"
        )

        checkOffset(
            enabled = isNotifyTwoDaysBefore(),
            daysAhead = 2,
            targetHour = getNotifyTwoDaysBeforeHour(),
            targetMinute = getNotifyTwoDaysBeforeMinute(),
            prefixLabel = "Pozítří",
            keyPrefix = "twodaysbefore"
        )

        if (summaryLines.isNotEmpty()) {
            val finalTitle = if (summaryLines.size > 1) {
                "Připomenutí nadcházejících svozů odpadu"
            } else {
                mainTitle
            }
            val finalBody = summaryLines.joinToString("\n")
            sendNotification(
                notificationId = ACTIVE_NOTIFICATION_ID,
                title = finalTitle,
                message = finalBody
            )

            val trimmed = if (sentSet.size > 90) sentSet.toList().takeLast(90).toSet() else sentSet
            prefs.edit().putStringSet(KEY_SENT_NOTIFICATIONS, trimmed).apply()
        }
    }

    // --- Testovací upozornění (vždy 1 souhrnné oznámení pro nejbližší den svozu) ---
    fun sendTestNotification(events: List<WasteEvent>) {
        val allowedTypeIds = getNotifyWasteTypeIds()
        val placeFilters = getNotifyPlaces()
        val today = LocalDate.now()

        val nextEvent = events.firstOrNull { ev ->
            val d = ev.localDate
            d != null && !d.isBefore(today) &&
                allowedTypeIds.contains(ev.wasteTypeId) &&
                ev.matchesPlaces(placeFilters)
        }

        if (nextEvent != null && nextEvent.localDate != null) {
            val sameDayEvents = events.filter { ev ->
                ev.date == nextEvent.date &&
                    allowedTypeIds.contains(ev.wasteTypeId) &&
                    ev.matchesPlaces(placeFilters)
            }
            val uniqueNames = sameDayEvents.map { it.shortName }.distinct().joinToString(", ")
            val uniquePlaces = sameDayEvents.flatMap { it.places }.distinct().joinToString(", ")
            sendNotification(
                notificationId = ACTIVE_NOTIFICATION_ID,
                title = "Nejbližší svoz: ${formatCzechDateFull(nextEvent.localDate!!)}",
                message = "Bude se svážet: $uniqueNames\nMísto: $uniquePlaces"
            )
        } else {
            sendNotification(
                notificationId = ACTIVE_NOTIFICATION_ID,
                title = "Test upozornění – Svoz odpadu Loštice",
                message = "Upozornění fungují správně! Pro vybraný filtr momentálně není žádný nadcházející záznam."
            )
        }
    }

    fun cancelActiveNotifications() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ACTIVE_NOTIFICATION_ID)
    }

    private fun sendNotification(notificationId: Int, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("FROM_NOTIFICATION", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val persistent = isPersistentNotification()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setOngoing(persistent)
            .setAutoCancel(!persistent)

        if (persistent) {
            builder.addAction(
                android.R.drawable.ic_menu_view,
                "Otevřít a potvrdit",
                pendingIntent
            )
        }

        val notification = builder.build()
        if (persistent) {
            notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(notificationId, notification)
        } catch (_: SecurityException) {
        }
    }
}
