package cz.lostice.svozodpadu.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import cz.lostice.svozodpadu.MainActivity
import cz.lostice.svozodpadu.model.WasteCalendarData
import cz.lostice.svozodpadu.model.WasteEvent
import cz.lostice.svozodpadu.model.WastePlace
import cz.lostice.svozodpadu.model.WasteType
import cz.lostice.svozodpadu.model.formatCzechDateFull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime

class WasteRepository(private val context: Context) {

    private val gson = Gson()
    private val cacheFile = File(context.cacheDir, "calendar_cache.json")
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "svoz_lostice_prefs"
        const val KEY_DATABASE_URL = "database_url"
        const val KEY_FILTER_WASTE_ID = "filter_waste_id"
        const val KEY_FILTER_PLACE = "filter_place"
        const val KEY_HIDE_YARD_IN_LIST = "hide_yard_in_list"

        const val KEY_NOTIFY_ENABLED = "notify_enabled"
        const val KEY_NOTIFY_WASTE_IDS = "notify_waste_ids"
        const val KEY_NOTIFY_PLACE = "notify_place"
        const val KEY_NOTIFY_DAY_BEFORE = "notify_day_before"
        const val KEY_NOTIFY_DAY_BEFORE_HOUR = "notify_day_before_hour"
        const val KEY_NOTIFY_DAY_OF = "notify_day_of"
        const val KEY_NOTIFY_DAY_OF_HOUR = "notify_day_of_hour"
        const val KEY_SENT_NOTIFICATIONS = "sent_notifications"

        const val DEFAULT_DATABASE_URL = "https://bladecz.github.io/svoz-odpadu-lostice/data/calendar.json"
        const val CHANNEL_ID = "svoz_odpadu_lostice_channel"

        // Výchozí ID typů odpadu pro upozornění (všechny svozy kromě provozní doby sběrného dvora)
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
                description = "Upozornění na nadcházející svozy komunálního odpadu, plastů, bioodpadu a dalších komodit v Lošticích"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    // --- Nastavení URL ---
    fun getDatabaseUrl(): String =
        prefs.getString(KEY_DATABASE_URL, DEFAULT_DATABASE_URL) ?: DEFAULT_DATABASE_URL

    fun setDatabaseUrl(url: String) {
        prefs.edit().putString(KEY_DATABASE_URL, url.trim()).apply()
    }

    // --- Filtry zobrazení v kalendáři ---
    fun getSelectedWasteTypeFilter(): String =
        prefs.getString(KEY_FILTER_WASTE_ID, "0") ?: "0"

    fun setSelectedWasteTypeFilter(wasteTypeId: String) {
        prefs.edit().putString(KEY_FILTER_WASTE_ID, wasteTypeId).apply()
    }

    fun getSelectedPlaceFilter(): String =
        prefs.getString(KEY_FILTER_PLACE, "") ?: ""

    fun setSelectedPlaceFilter(place: String) {
        prefs.edit().putString(KEY_FILTER_PLACE, place).apply()
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

    fun getNotifyPlace(): String =
        prefs.getString(KEY_NOTIFY_PLACE, "") ?: ""

    fun setNotifyPlace(place: String) {
        prefs.edit().putString(KEY_NOTIFY_PLACE, place).apply()
    }

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

    // --- Načtení dat (Online -> Lokální cache -> Bundled Assets) ---
    suspend fun loadCalendarData(forceNetwork: Boolean = true): WasteCalendarData = withContext(Dispatchers.IO) {
        if (forceNetwork) {
            try {
                val url = URL(getDatabaseUrl())
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
                // Při výpadku sítě přejdeme na cache nebo vestavěná data v assets
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

    // --- Kontrola a odeslání naplánovaných upozornění ---
    suspend fun checkAndSendNotifications() {
        if (!isNotificationsEnabled()) return

        val data = loadCalendarData(forceNetwork = true)
        val allowedTypeIds = getNotifyWasteTypeIds()
        val placeFilter = getNotifyPlace()
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val tomorrow = today.plusDays(1)

        val sentSet = (prefs.getStringSet(KEY_SENT_NOTIFICATIONS, emptySet()) ?: emptySet()).toMutableSet()

        // 1) Upozornění den předem (např. po 18:00 na zítřejší svoz)
        if (isNotifyDayBefore() && now.hour >= getNotifyDayBeforeHour()) {
            val tomorrowEvents = data.events.filter { ev ->
                ev.localDate == tomorrow &&
                    allowedTypeIds.contains(ev.wasteTypeId) &&
                    ev.matchesPlace(placeFilter)
            }
            if (tomorrowEvents.isNotEmpty()) {
                val notifKey = "before_${tomorrow}_${tomorrowEvents.joinToString(",") { it.wasteTypeId }}"
                if (!sentSet.contains(notifKey)) {
                    val names = tomorrowEvents.joinToString(", ") { it.shortName }
                    val places = tomorrowEvents.flatMap { it.places }.distinct().joinToString(", ")
                    sendNotification(
                        notificationId = tomorrow.hashCode(),
                        title = "Zítra proběhne svoz odpadu!",
                        message = "$names (${formatCzechDateFull(tomorrow)} • $places)"
                    )
                    sentSet.add(notifKey)
                }
            }
        }

        // 2) Upozornění v den svozu ráno (např. po 06:00 na dnešní svoz)
        if (isNotifyDayOf() && now.hour in getNotifyDayOfHour()..11) {
            val todayEvents = data.events.filter { ev ->
                ev.localDate == today &&
                    allowedTypeIds.contains(ev.wasteTypeId) &&
                    ev.matchesPlace(placeFilter)
            }
            if (todayEvents.isNotEmpty()) {
                val notifKey = "dayof_${today}_${todayEvents.joinToString(",") { it.wasteTypeId }}"
                if (!sentSet.contains(notifKey)) {
                    val names = todayEvents.joinToString(", ") { it.shortName }
                    val places = todayEvents.flatMap { it.places }.distinct().joinToString(", ")
                    sendNotification(
                        notificationId = today.hashCode() + 1000,
                        title = "Dnes probíhá svoz odpadu!",
                        message = "$names (${formatCzechDateFull(today)} • $places)"
                    )
                    sentSet.add(notifKey)
                }
            }
        }

        // Udržet v paměti jen posledních 60 klíčů
        val trimmed = if (sentSet.size > 60) sentSet.toList().takeLast(60).toSet() else sentSet
        prefs.edit().putStringSet(KEY_SENT_NOTIFICATIONS, trimmed).apply()
    }

    // --- Testovací upozornění (najde nejbližší budoucí svoz podle filtru) ---
    fun sendTestNotification(events: List<WasteEvent>) {
        val allowedTypeIds = getNotifyWasteTypeIds()
        val placeFilter = getNotifyPlace()
        val today = LocalDate.now()

        val nextEvent = events.firstOrNull { ev ->
            val d = ev.localDate
            d != null && !d.isBefore(today) &&
                allowedTypeIds.contains(ev.wasteTypeId) &&
                ev.matchesPlace(placeFilter)
        }

        if (nextEvent != null && nextEvent.localDate != null) {
            val sameDayEvents = events.filter { ev ->
                ev.date == nextEvent.date &&
                    allowedTypeIds.contains(ev.wasteTypeId) &&
                    ev.matchesPlace(placeFilter)
            }
            val names = sameDayEvents.joinToString(", ") { it.shortName }
            val places = sameDayEvents.flatMap { it.places }.distinct().joinToString(", ")
            sendNotification(
                notificationId = 99999,
                title = "Nejbližší svoz: ${formatCzechDateFull(nextEvent.localDate!!)}",
                message = "$names • Místo: $places"
            )
        } else {
            sendNotification(
                notificationId = 99999,
                title = "Test upozornění – Svoz odpadu Loštice",
                message = "Upozornění fungují správně! Pro vybraný filtr momentálně není žádný nadcházející záznam."
            )
        }
    }

    private fun sendNotification(notificationId: Int, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(notificationId, notification)
        } catch (_: SecurityException) {
        }
    }
}
