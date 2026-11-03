package cz.lostice.svozodpadu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.lostice.svozodpadu.data.WasteRepository
import cz.lostice.svozodpadu.model.*
import cz.lostice.svozodpadu.worker.WasteNotificationWorker
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: WasteRepository,
    currentThemeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var calendarData by remember { mutableStateOf(WasteCalendarData()) }
    var isLoading by remember { mutableStateOf(true) }
    var activeTab by remember { mutableIntStateOf(0) }
    var showFilterPanel by remember { mutableStateOf(false) }
    var showCustomizationSheet by remember { mutableStateOf(false) }

    // Stav filtrů kalendáře a seznamu
    var selectedWasteId by remember { mutableStateOf(repository.getSelectedWasteTypeFilter()) }
    var selectedPlace by remember { mutableStateOf(repository.getSelectedPlaceFilter()) }
    var hideYardInList by remember { mutableStateOf(repository.isHideYardInList()) }

    // Stav customizace rozhraní
    var markerStyle by remember { mutableStateOf(repository.getCalendarMarkerStyle()) }
    var compactCards by remember { mutableStateOf(repository.isCompactCards()) }
    var highlightCalendarCells by remember { mutableStateOf(repository.isHighlightCalendarCells()) }

    // Stav nastavení upozornění
    var notifyEnabled by remember { mutableStateOf(repository.isNotificationsEnabled()) }
    var notifyWasteIds by remember { mutableStateOf(repository.getNotifyWasteTypeIds()) }
    var notifyPlace by remember { mutableStateOf(repository.getNotifyPlace()) }
    var notifyPersistent by remember { mutableStateOf(repository.isPersistentNotification()) }

    // 1) Dva dny předem
    var notifyTwoDaysBefore by remember { mutableStateOf(repository.isNotifyTwoDaysBefore()) }
    var notifyTwoDaysBeforeHour by remember { mutableIntStateOf(repository.getNotifyTwoDaysBeforeHour()) }
    var notifyTwoDaysBeforeMinute by remember { mutableIntStateOf(repository.getNotifyTwoDaysBeforeMinute()) }

    // 2) Den předem
    var notifyDayBefore by remember { mutableStateOf(repository.isNotifyDayBefore()) }
    var notifyDayBeforeHour by remember { mutableIntStateOf(repository.getNotifyDayBeforeHour()) }
    var notifyDayBeforeMinute by remember { mutableIntStateOf(repository.getNotifyDayBeforeMinute()) }

    // 3) V den svozu
    var notifyDayOf by remember { mutableStateOf(repository.isNotifyDayOf()) }
    var notifyDayOfHour by remember { mutableIntStateOf(repository.getNotifyDayOfHour()) }
    var notifyDayOfMinute by remember { mutableIntStateOf(repository.getNotifyDayOfMinute()) }

    fun refreshData(forceOnline: Boolean) {
        scope.launch {
            isLoading = true
            calendarData = repository.loadCalendarData(forceNetwork = forceOnline)
            isLoading = false
            if (forceOnline) {
                repository.checkAndSendNotifications()
            }
        }
    }

    fun rescheduleNotifications() {
        WasteNotificationWorker.schedule(context)
    }

    LaunchedEffect(Unit) {
        calendarData = repository.loadCalendarData(forceNetwork = false)
        isLoading = false
        refreshData(forceOnline = true)
    }

    val filteredEvents = remember(calendarData.events, selectedWasteId, selectedPlace, hideYardInList) {
        calendarData.events.filter { ev ->
            val matchWaste = selectedWasteId == "0" || ev.wasteTypeId == selectedWasteId
            val matchPlace = ev.matchesPlace(selectedPlace)
            val matchYard = if (hideYardInList && selectedWasteId == "0") ev.isCollection else true
            matchWaste && matchPlace && matchYard
        }
    }

    val activeFiltersCount = (if (selectedWasteId != "0") 1 else 0) + (if (selectedPlace.isNotBlank()) 1 else 0)

    if (showCustomizationSheet) {
        CustomizationDialog(
            currentThemeMode = currentThemeMode,
            onThemeModeChange = onThemeModeChange,
            markerStyle = markerStyle,
            onMarkerStyleChange = {
                markerStyle = it
                repository.setCalendarMarkerStyle(it)
            },
            highlightCalendarCells = highlightCalendarCells,
            onHighlightCalendarCellsChange = {
                highlightCalendarCells = it
                repository.setHighlightCalendarCells(it)
            },
            compactCards = compactCards,
            onCompactCardsChange = {
                compactCards = it
                repository.setCompactCards(it)
            },
            notifyPersistent = notifyPersistent,
            onNotifyPersistentChange = {
                notifyPersistent = it
                repository.setPersistentNotification(it)
            },
            onDismiss = { showCustomizationSheet = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Svoz odpadu Loštice",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                        Text(
                            text = if (selectedPlace.isBlank()) "Všechna místa • MÚ Loštice" else "Místo: $selectedPlace",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (activeTab == 0 || activeTab == 1) {
                        FilledTonalButton(
                            onClick = { showFilterPanel = !showFilterPanel },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filtr",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (activeFiltersCount > 0) "Filtr ($activeFiltersCount)" else "Filtr",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = if (showFilterPanel) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else if (activeTab == 2) {
                        // V kartě Upozornění je na místě Filtru tlačítko Nastavení (customizace rozhraní)
                        FilledTonalButton(
                            onClick = { showCustomizationSheet = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Nastavení vzhledu",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Nastavení",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    IconButton(onClick = {
                        refreshData(forceOnline = true)
                        scope.launch {
                            snackbarHostState.showSnackbar("Kalendář svozů byl zaktualizován")
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Aktualizovat")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                    label = { Text("Budoucí svozy") }
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                    label = { Text("Kalendář") }
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (notifyEnabled) {
                                    Badge { Text("${notifyWasteIds.size}") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null)
                        }
                    },
                    label = { Text("Upozornění") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = showFilterPanel && (activeTab == 0 || activeTab == 1)) {
                FilterDropdownPanel(
                    wasteTypes = calendarData.wasteTypes,
                    places = calendarData.places,
                    selectedWasteId = selectedWasteId,
                    onSelectWasteId = {
                        selectedWasteId = it
                        repository.setSelectedWasteTypeFilter(it)
                    },
                    selectedPlace = selectedPlace,
                    onSelectPlace = {
                        selectedPlace = it
                        repository.setSelectedPlaceFilter(it)
                    },
                    hideYardInList = hideYardInList,
                    onToggleHideYard = {
                        hideYardInList = it
                        repository.setHideYardInList(it)
                    },
                    onReset = {
                        selectedWasteId = "0"
                        selectedPlace = ""
                        repository.setSelectedWasteTypeFilter("0")
                        repository.setSelectedPlaceFilter("")
                    }
                )
            }

            if (isLoading && calendarData.events.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (activeTab) {
                    0 -> UpcomingTab(
                        events = filteredEvents,
                        wasteTypes = calendarData.wasteTypes,
                        selectedWasteId = selectedWasteId,
                        markerStyle = markerStyle,
                        compactCards = compactCards,
                        onSelectWasteId = {
                            selectedWasteId = it
                            repository.setSelectedWasteTypeFilter(it)
                        }
                    )
                    1 -> CalendarTab(
                        events = filteredEvents,
                        wasteTypes = calendarData.wasteTypes,
                        markerStyle = markerStyle,
                        highlightCalendarCells = highlightCalendarCells,
                        compactCards = compactCards
                    )
                    2 -> NotificationsTab(
                        wasteTypes = calendarData.wasteTypes,
                        places = calendarData.places,
                        markerStyle = markerStyle,
                        notifyEnabled = notifyEnabled,
                        onNotifyEnabledChange = {
                            notifyEnabled = it
                            repository.setNotificationsEnabled(it)
                            rescheduleNotifications()
                        },
                        notifyPersistent = notifyPersistent,
                        onNotifyPersistentChange = {
                            notifyPersistent = it
                            repository.setPersistentNotification(it)
                        },
                        notifyWasteIds = notifyWasteIds,
                        onNotifyWasteIdsChange = {
                            notifyWasteIds = it
                            repository.setNotifyWasteTypeIds(it)
                            rescheduleNotifications()
                        },
                        notifyPlace = notifyPlace,
                        onNotifyPlaceChange = {
                            notifyPlace = it
                            repository.setNotifyPlace(it)
                            rescheduleNotifications()
                        },
                        notifyTwoDaysBefore = notifyTwoDaysBefore,
                        onNotifyTwoDaysBeforeChange = {
                            notifyTwoDaysBefore = it
                            repository.setNotifyTwoDaysBefore(it)
                            rescheduleNotifications()
                        },
                        notifyTwoDaysBeforeHour = notifyTwoDaysBeforeHour,
                        onNotifyTwoDaysBeforeHourChange = {
                            notifyTwoDaysBeforeHour = it
                            repository.setNotifyTwoDaysBeforeHour(it)
                            rescheduleNotifications()
                        },
                        notifyTwoDaysBeforeMinute = notifyTwoDaysBeforeMinute,
                        onNotifyTwoDaysBeforeMinuteChange = {
                            notifyTwoDaysBeforeMinute = it
                            repository.setNotifyTwoDaysBeforeMinute(it)
                            rescheduleNotifications()
                        },
                        notifyDayBefore = notifyDayBefore,
                        onNotifyDayBeforeChange = {
                            notifyDayBefore = it
                            repository.setNotifyDayBefore(it)
                            rescheduleNotifications()
                        },
                        notifyDayBeforeHour = notifyDayBeforeHour,
                        onNotifyDayBeforeHourChange = {
                            notifyDayBeforeHour = it
                            repository.setNotifyDayBeforeHour(it)
                            rescheduleNotifications()
                        },
                        notifyDayBeforeMinute = notifyDayBeforeMinute,
                        onNotifyDayBeforeMinuteChange = {
                            notifyDayBeforeMinute = it
                            repository.setNotifyDayBeforeMinute(it)
                            rescheduleNotifications()
                        },
                        notifyDayOf = notifyDayOf,
                        onNotifyDayOfChange = {
                            notifyDayOf = it
                            repository.setNotifyDayOf(it)
                            rescheduleNotifications()
                        },
                        notifyDayOfHour = notifyDayOfHour,
                        onNotifyDayOfHourChange = {
                            notifyDayOfHour = it
                            repository.setNotifyDayOfHour(it)
                            rescheduleNotifications()
                        },
                        notifyDayOfMinute = notifyDayOfMinute,
                        onNotifyDayOfMinuteChange = {
                            notifyDayOfMinute = it
                            repository.setNotifyDayOfMinute(it)
                            rescheduleNotifications()
                        },
                        onTestNotification = {
                            repository.sendTestNotification(calendarData.events)
                            scope.launch {
                                snackbarHostState.showSnackbar("Souhrnné testovací upozornění bylo odesláno")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CustomizationDialog(
    currentThemeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    markerStyle: CalendarMarkerStyle,
    onMarkerStyleChange: (CalendarMarkerStyle) -> Unit,
    highlightCalendarCells: Boolean,
    onHighlightCalendarCellsChange: (Boolean) -> Unit,
    compactCards: Boolean,
    onCompactCardsChange: (Boolean) -> Unit,
    notifyPersistent: Boolean,
    onNotifyPersistentChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Nastavení a vzhled aplikace", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1) Barevné téma
                Text("Barevný motiv rozhraní", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onThemeModeChange(mode) }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentThemeMode == mode,
                                onClick = { onThemeModeChange(mode) }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(mode.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                HorizontalDivider()

                // 2) Zobrazení v kalendáři: Tečky vs. Ikony
                Text("Značky v kalendáři a seznamu", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalendarMarkerStyle.entries.forEach { style ->
                        FilterChip(
                            selected = markerStyle == style,
                            onClick = { onMarkerStyleChange(style) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (style == CalendarMarkerStyle.ICONS) Icons.Default.Delete else Icons.Default.Circle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(if (style == CalendarMarkerStyle.ICONS) "Ikony odpadu" else "Tečky") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 3) Podbarvení políček v kalendáři
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Barevně podbarvit dny v kalendáři", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            "Jemně zvýrazní celé políčko dne barvou svozu",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = highlightCalendarCells, onCheckedChange = onHighlightCalendarCellsChange)
                }

                // 4) Kompaktní režim karet
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Kompaktní přehled svozů", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            "Stručné karty bez dlouhých popisů pro zobrazení více termínů najednou",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = compactCards, onCheckedChange = onCompactCardsChange)
                }

                HorizontalDivider()

                // 5) Trvalé upozornění
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Trvalé upozornění (ochrana smazání)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            "Nelze omylem smazat přejetím ani tlačítkem „Vymazat vše“ – zmizí až po klepnutí na oznámení a otevření aplikace",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = notifyPersistent, onCheckedChange = onNotifyPersistentChange)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Hotovo")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDropdownPanel(
    wasteTypes: List<WasteType>,
    places: List<WastePlace>,
    selectedWasteId: String,
    onSelectWasteId: (String) -> Unit,
    selectedPlace: String,
    onSelectPlace: (String) -> Unit,
    hideYardInList: Boolean,
    onToggleHideYard: (Boolean) -> Unit,
    onReset: () -> Unit
) {
    var wasteExpanded by remember { mutableStateOf(false) }
    var placeExpanded by remember { mutableStateOf(false) }

    val selectedWasteName = wasteTypes.firstOrNull { it.id == selectedWasteId }?.name ?: "Všechny typy událostí"
    val selectedPlaceName = if (selectedPlace.isBlank()) "Všechna místa (celé město)" else selectedPlace

    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filtr kalendáře (podle webu MÚ Loštice)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (selectedWasteId != "0" || selectedPlace.isNotBlank()) {
                    TextButton(onClick = onReset, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Zrušit filtr", fontSize = 12.sp)
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = wasteExpanded,
                onExpandedChange = { wasteExpanded = !wasteExpanded }
            ) {
                OutlinedTextField(
                    value = selectedWasteName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Typ události") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = wasteExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = wasteExpanded,
                    onDismissRequest = { wasteExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Všechny typy událostí", fontWeight = FontWeight.SemiBold) },
                        onClick = {
                            onSelectWasteId("0")
                            wasteExpanded = false
                        }
                    )
                    wasteTypes.forEach { wt ->
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = getWasteIcon(wt.id, wt.name),
                                    contentDescription = null,
                                    tint = parseHexColor(wt.color),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            text = { Text(wt.name) },
                            onClick = {
                                onSelectWasteId(wt.id)
                                wasteExpanded = false
                            }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = placeExpanded,
                onExpandedChange = { placeExpanded = !placeExpanded }
            ) {
                OutlinedTextField(
                    value = selectedPlaceName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Místo / Ulice") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = placeExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = placeExpanded,
                    onDismissRequest = { placeExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Všechna místa (celé město)", fontWeight = FontWeight.SemiBold) },
                        onClick = {
                            onSelectPlace("")
                            placeExpanded = false
                        }
                    )
                    places.forEach { place ->
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            text = { Text(place.name) },
                            onClick = {
                                onSelectPlace(place.name)
                                placeExpanded = false
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Skrýt provozní dobu sběrného dvora (zobrazit jen svozy)",
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = hideYardInList,
                    onCheckedChange = onToggleHideYard
                )
            }
        }
    }
}

@Composable
fun WasteMarkerBadge(
    wasteTypeId: String,
    wasteName: String,
    colorHex: String,
    markerStyle: CalendarMarkerStyle,
    sizeDp: Int = 16
) {
    val c = parseHexColor(colorHex)
    if (markerStyle == CalendarMarkerStyle.ICONS) {
        Icon(
            imageVector = getWasteIcon(wasteTypeId, wasteName),
            contentDescription = wasteName,
            tint = c,
            modifier = Modifier.size(sizeDp.dp)
        )
    } else {
        Box(
            modifier = Modifier
                .size((sizeDp - 2).coerceAtLeast(6).dp)
                .clip(CircleShape)
                .background(c)
        )
    }
}

@Composable
fun UpcomingTab(
    events: List<WasteEvent>,
    wasteTypes: List<WasteType>,
    selectedWasteId: String,
    markerStyle: CalendarMarkerStyle,
    compactCards: Boolean,
    onSelectWasteId: (String) -> Unit
) {
    val today = LocalDate.now()
    val upcomingEvents = remember(events, today) {
        events.filter { ev ->
            val d = ev.localDate
            d != null && !d.isBefore(today)
        }
    }

    val groupedByDate = remember(upcomingEvents) {
        upcomingEvents.groupBy { it.date }.toSortedMap()
    }

    val nextCollectionEntry = remember(groupedByDate) {
        groupedByDate.entries.firstOrNull { entry -> entry.value.any { it.isCollection } }
            ?: groupedByDate.entries.firstOrNull()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedWasteId == "0",
                        onClick = { onSelectWasteId("0") },
                        label = { Text("Vše") }
                    )
                }
                items(wasteTypes.filter { it.isCollection }) { wt ->
                    val selected = selectedWasteId == wt.id
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectWasteId(if (selected) "0" else wt.id) },
                        leadingIcon = {
                            WasteMarkerBadge(
                                wasteTypeId = wt.id,
                                wasteName = wt.name,
                                colorHex = wt.color,
                                markerStyle = markerStyle,
                                sizeDp = 15
                            )
                        },
                        label = { Text(wt.shortName) }
                    )
                }
            }
        }

        if (nextCollectionEntry != null) {
            val nextDate = LocalDate.parse(nextCollectionEntry.key)
            val nextItems = nextCollectionEntry.value
            val relLabel = relativeDayLabel(nextDate, today)

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NEJBLIŽŠÍ SVOZ ODPADU",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = relLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = formatCzechDateFull(nextDate),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        nextItems.forEach { ev ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                WasteMarkerBadge(
                                    wasteTypeId = ev.wasteTypeId,
                                    wasteName = ev.wasteTypeName,
                                    colorHex = ev.color,
                                    markerStyle = markerStyle,
                                    sizeDp = 18
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = ev.shortName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = ev.places.joinToString(", "),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (groupedByDate.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventBusy,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Žádné budoucí svozy pro zvolený filtr",
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Zkuste upravit výběr v tlačítku Filtr nahoře.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            item {
                Text(
                    text = "Přehled nadcházejících termínů (${upcomingEvents.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            items(groupedByDate.entries.toList(), key = { it.key }) { (dateStr, dayEvents) ->
                val dateObj = LocalDate.parse(dateStr)
                DayEventsCard(
                    date = dateObj,
                    events = dayEvents,
                    today = today,
                    markerStyle = markerStyle,
                    compact = compactCards
                )
            }
        }
    }
}

@Composable
fun DayEventsCard(
    date: LocalDate,
    events: List<WasteEvent>,
    today: LocalDate,
    markerStyle: CalendarMarkerStyle,
    compact: Boolean = false
) {
    val rel = relativeDayLabel(date, today)
    val isTodayOrTomorrow = date == today || date == today.plusDays(1)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isTodayOrTomorrow) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(14.dp)
                    )
                } else Modifier
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatCzechDateFull(date),
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 14.sp else 15.sp
                )
                Surface(
                    color = if (isTodayOrTomorrow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isTodayOrTomorrow) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = rel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            events.forEach { ev ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = if (compact) Alignment.CenterVertically else Alignment.Top
                ) {
                    Box(modifier = Modifier.padding(top = if (compact) 0.dp else 3.dp)) {
                        WasteMarkerBadge(
                            wasteTypeId = ev.wasteTypeId,
                            wasteName = ev.wasteTypeName,
                            colorHex = ev.color,
                            markerStyle = markerStyle,
                            sizeDp = 18
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (compact) ev.shortName else ev.wasteTypeName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        if (!compact && ev.description.isNotBlank() && ev.description != ev.wasteTypeName) {
                            Text(
                                text = ev.description,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = if (compact) 1.dp else 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = ev.places.joinToString(", "),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarTab(
    events: List<WasteEvent>,
    wasteTypes: List<WasteType>,
    markerStyle: CalendarMarkerStyle,
    highlightCalendarCells: Boolean,
    compactCards: Boolean
) {
    val today = LocalDate.now()
    var currentMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDate by remember { mutableStateOf(today) }

    val eventsByDate = remember(events) {
        events.groupBy { it.date }
    }

    val selectedDayEvents = remember(eventsByDate, selectedDate) {
        eventsByDate[selectedDate.toString()] ?: emptyList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Předchozí měsíc")
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = formatCzechMonthYear(currentMonth.year, currentMonth.monthValue),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (currentMonth != YearMonth.from(today)) {
                                TextButton(
                                    onClick = {
                                        currentMonth = YearMonth.from(today)
                                        selectedDate = today
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Přejít na dnešek", fontSize = 12.sp)
                                }
                            }
                        }
                        IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Další měsíc")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val weekDays = listOf("Po", "Út", "St", "Čt", "Pá", "So", "Ne")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        weekDays.forEach { d ->
                            Text(
                                text = d,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val firstDayOfMonth = currentMonth.atDay(1)
                    val leadingEmptyDays = firstDayOfMonth.dayOfWeek.value - 1
                    val daysInMonth = currentMonth.lengthOfMonth()
                    val totalCells = ((leadingEmptyDays + daysInMonth + 6) / 7) * 7

                    for (week in 0 until (totalCells / 7)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            for (dow in 0..6) {
                                val cellIndex = week * 7 + dow
                                val dayNum = cellIndex - leadingEmptyDays + 1
                                if (dayNum in 1..daysInMonth) {
                                    val cellDate = currentMonth.atDay(dayNum)
                                    val cellEvents = eventsByDate[cellDate.toString()] ?: emptyList()
                                    val isSelected = cellDate == selectedDate
                                    val isToday = cellDate == today
                                    val primaryEventColor = cellEvents.firstOrNull()?.let { parseHexColor(it.color) }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(0.88f)
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                when {
                                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                                    highlightCalendarCells && primaryEventColor != null ->
                                                        primaryEventColor.copy(alpha = 0.22f)
                                                    isToday -> MaterialTheme.colorScheme.surfaceVariant
                                                    else -> Color.Transparent
                                                }
                                            )
                                            .then(
                                                if (isToday && !isSelected) {
                                                    Modifier.border(
                                                        1.5.dp,
                                                        MaterialTheme.colorScheme.primary,
                                                        RoundedCornerShape(10.dp)
                                                    )
                                                } else Modifier
                                            )
                                            .clickable { selectedDate = cellDate },
                                        contentAlignment = Alignment.TopCenter
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(top = 3.dp)
                                        ) {
                                            Text(
                                                text = "$dayNum",
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected || isToday || cellEvents.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val maxMarkers = if (markerStyle == CalendarMarkerStyle.ICONS) 3 else 4
                                                cellEvents.take(maxMarkers).forEach { ev ->
                                                    if (markerStyle == CalendarMarkerStyle.ICONS) {
                                                        Icon(
                                                            imageVector = getWasteIcon(ev.wasteTypeId, ev.wasteTypeName),
                                                            contentDescription = null,
                                                            tint = parseHexColor(ev.color),
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .clip(CircleShape)
                                                                .background(parseHexColor(ev.color))
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Vybraný den: ${formatCzechDateFull(selectedDate)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (selectedDayEvents.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "V tento den není naplánován žádný svoz odpadu pro vybraný filtr.",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            item {
                DayEventsCard(
                    date = selectedDate,
                    events = selectedDayEvents,
                    today = today,
                    markerStyle = markerStyle,
                    compact = compactCards
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Legenda typů svozu a událostí",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    wasteTypes.forEach { wt ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            WasteMarkerBadge(
                                wasteTypeId = wt.id,
                                wasteName = wt.name,
                                colorHex = wt.color,
                                markerStyle = markerStyle,
                                sizeDp = 15
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = wt.name,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Posuvný výběr čísla (hodiny 00-23 nebo minuty 00-59) šoupáním nahoru a dolů.
 * Pohlcuje vertikální scroll uvnitř válce, aby nedocházelo k posouvání celé stránky při šoupání času.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelNumberPicker(
    range: IntRange,
    selectedValue: Int,
    onValueSelected: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val items = remember(range) { range.toList() }
    val count = items.size
    // Velký počet opakování pro plynulé nekonečné otáčení nahoru i dolů
    val repeatFactor = 200
    val totalCount = count * repeatFactor
    val middleStart = (repeatFactor / 2) * count + (selectedValue - range.first).coerceIn(0, count - 1)

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = middleStart)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val scope = rememberCoroutineScope()

    // Pohlcení zbylého vertikálního scrollu, aby se při šoupání hodin/minut neposouvala celá karta
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = Offset(0f, available.y)

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(0f, available.y)
        }
    }

    val centeredIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isEmpty()) {
                middleStart
            } else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                layoutInfo.visibleItemsInfo.minByOrNull { item ->
                    abs((item.offset + item.size / 2) - viewportCenter)
                }?.index ?: middleStart
            }
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val actualVal = items[centeredIndex % count]
            if (actualVal != selectedValue) {
                onValueSelected(actualVal)
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(74.dp)
                .height(114.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .nestedScroll(nestedScrollConnection),
            contentAlignment = Alignment.Center
        ) {
            // Zvýrazňovací pruh vybrané střední položky
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .padding(horizontal = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp)
                    )
            )

            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                contentPadding = PaddingValues(vertical = 38.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(totalCount) { idx ->
                    val num = items[idx % count]
                    val isCenter = idx == centeredIndex
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    listState.animateScrollToItem(idx)
                                    onValueSelected(num)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "%02d".format(num),
                            fontSize = if (isCenter) 20.sp else 14.sp,
                            fontWeight = if (isCenter) FontWeight.ExtraBold else FontWeight.Normal,
                            color = if (isCenter) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.alpha(if (isCenter) 1f else 0.45f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReminderTimeRowWithWheel(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    hour: Int,
    onHourChange: (Int) -> Unit,
    minute: Int,
    onMinuteChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (enabled) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "%02d:%02d".format(hour, minute),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }

        AnimatedVisibility(visible = enabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WheelNumberPicker(
                    range = 0..23,
                    selectedValue = hour,
                    onValueSelected = onHourChange,
                    label = "Hodiny"
                )
                Text(
                    text = ":",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
                )
                WheelNumberPicker(
                    range = 0..59,
                    selectedValue = minute,
                    onValueSelected = onMinuteChange,
                    label = "Minuty"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsTab(
    wasteTypes: List<WasteType>,
    places: List<WastePlace>,
    markerStyle: CalendarMarkerStyle,
    notifyEnabled: Boolean,
    onNotifyEnabledChange: (Boolean) -> Unit,
    notifyPersistent: Boolean,
    onNotifyPersistentChange: (Boolean) -> Unit,
    notifyWasteIds: Set<String>,
    onNotifyWasteIdsChange: (Set<String>) -> Unit,
    notifyPlace: String,
    onNotifyPlaceChange: (String) -> Unit,
    notifyTwoDaysBefore: Boolean,
    onNotifyTwoDaysBeforeChange: (Boolean) -> Unit,
    notifyTwoDaysBeforeHour: Int,
    onNotifyTwoDaysBeforeHourChange: (Int) -> Unit,
    notifyTwoDaysBeforeMinute: Int,
    onNotifyTwoDaysBeforeMinuteChange: (Int) -> Unit,
    notifyDayBefore: Boolean,
    onNotifyDayBeforeChange: (Boolean) -> Unit,
    notifyDayBeforeHour: Int,
    onNotifyDayBeforeHourChange: (Int) -> Unit,
    notifyDayBeforeMinute: Int,
    onNotifyDayBeforeMinuteChange: (Int) -> Unit,
    notifyDayOf: Boolean,
    onNotifyDayOfChange: (Boolean) -> Unit,
    notifyDayOfHour: Int,
    onNotifyDayOfHourChange: (Int) -> Unit,
    notifyDayOfMinute: Int,
    onNotifyDayOfMinuteChange: (Int) -> Unit,
    onTestNotification: () -> Unit
) {
    var placeExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hlavní zapnutí upozornění + test
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Upozornění na svoz odpadu",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Všechny události dne se spojí do jednoho přehledného oznámení",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Switch(
                            checked = notifyEnabled,
                            onCheckedChange = onNotifyEnabledChange
                        )
                    }

                    Button(
                        onClick = onTestNotification,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vyzkoušet upozornění (nejbližší svoz)")
                    }
                }
            }
        }

        // Kdy posílat upozornění (Dva dny předem, Den předem, V den svozu – každé s vlastním šoupáním hodin a minut)
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Kdy posílat upozornění",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Aktivujte požadované předstihy a šoupáním nahoru/dolů nastavte přesnou hodinu a minutu:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 1) Dva dny předem
                    ReminderTimeRowWithWheel(
                        title = "Dva dny předem",
                        subtitle = "Upozorní 2 dny před termínem svozu v nastavený čas",
                        enabled = notifyTwoDaysBefore,
                        onEnabledChange = onNotifyTwoDaysBeforeChange,
                        hour = notifyTwoDaysBeforeHour,
                        onHourChange = onNotifyTwoDaysBeforeHourChange,
                        minute = notifyTwoDaysBeforeMinute,
                        onMinuteChange = onNotifyTwoDaysBeforeMinuteChange
                    )

                    HorizontalDivider()

                    // 2) Den předem
                    ReminderTimeRowWithWheel(
                        title = "Den předem",
                        subtitle = "Připomene nachystat popelnici nebo pytel den před svozem",
                        enabled = notifyDayBefore,
                        onEnabledChange = onNotifyDayBeforeChange,
                        hour = notifyDayBeforeHour,
                        onHourChange = onNotifyDayBeforeHourChange,
                        minute = notifyDayBeforeMinute,
                        onMinuteChange = onNotifyDayBeforeMinuteChange
                    )

                    HorizontalDivider()

                    // 3) V den svozu
                    ReminderTimeRowWithWheel(
                        title = "V den svozu",
                        subtitle = "Připomenutí přímo v den, kdy probíhá svoz odpadu",
                        enabled = notifyDayOf,
                        onEnabledChange = onNotifyDayOfChange,
                        hour = notifyDayOfHour,
                        onHourChange = onNotifyDayOfHourChange,
                        minute = notifyDayOfMinute,
                        onMinuteChange = onNotifyDayOfMinuteChange
                    )

                    HorizontalDivider()

                    // Trvalé upozornění (ochrana proti smazání všech upozornění)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Trvalé upozornění (ochrana proti přehlédnutí)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Nelze omylem smazat přejetím ani při smazání všech oznámení – zmizí až po kliknutí na oznámení a přechodu do aplikace",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = notifyPersistent,
                            onCheckedChange = onNotifyPersistentChange
                        )
                    }
                }
            }
        }

        // Filtr místa pro upozornění
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Místo / Lokalita pro upozornění",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    ExposedDropdownMenuBox(
                        expanded = placeExpanded,
                        onExpandedChange = { placeExpanded = !placeExpanded }
                    ) {
                        OutlinedTextField(
                            value = if (notifyPlace.isBlank()) "Všechna místa (Loštice, Žádlovice, Vlčice...)" else notifyPlace,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Vyberte místo") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = placeExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = placeExpanded,
                            onDismissRequest = { placeExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Všechna místa (bez omezení)", fontWeight = FontWeight.SemiBold) },
                                onClick = {
                                    onNotifyPlaceChange("")
                                    placeExpanded = false
                                }
                            )
                            places.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.name) },
                                    onClick = {
                                        onNotifyPlaceChange(p.name)
                                        placeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Výběr typů odpadu pro upozornění
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Na jaké odpady upozorňovat",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        TextButton(
                            onClick = {
                                val allCollectionIds = wasteTypes.filter { it.isCollection }.map { it.id }.toSet()
                                onNotifyWasteIdsChange(
                                    if (notifyWasteIds.containsAll(allCollectionIds)) emptySet() else allCollectionIds
                                )
                            }
                        ) {
                            Text("Všechny svozy", fontSize = 12.sp)
                        }
                    }
                    Text(
                        text = "Zaškrtněte typy svozu z rozbalovacího seznamu „Filtr“ na webu města Loštice, na které chcete dostávat upozornění:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    wasteTypes.forEach { wt ->
                        val isChecked = notifyWasteIds.contains(wt.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val next = notifyWasteIds.toMutableSet()
                                    if (isChecked) next.remove(wt.id) else next.add(wt.id)
                                    onNotifyWasteIdsChange(next)
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    val next = notifyWasteIds.toMutableSet()
                                    if (checked) next.add(wt.id) else next.remove(wt.id)
                                    onNotifyWasteIdsChange(next)
                                }
                            )
                            WasteMarkerBadge(
                                wasteTypeId = wt.id,
                                wasteName = wt.name,
                                colorHex = wt.color,
                                markerStyle = markerStyle,
                                sizeDp = 18
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = wt.shortName,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = wt.name,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
