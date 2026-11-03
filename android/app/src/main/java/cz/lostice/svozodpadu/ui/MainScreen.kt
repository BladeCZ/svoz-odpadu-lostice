package cz.lostice.svozodpadu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.lostice.svozodpadu.data.WasteRepository
import cz.lostice.svozodpadu.model.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(repository: WasteRepository) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var calendarData by remember { mutableStateOf(WasteCalendarData()) }
    var isLoading by remember { mutableStateOf(true) }
    var activeTab by remember { mutableIntStateOf(0) }
    var showFilterPanel by remember { mutableStateOf(false) }

    // Stav filtrů kalendáře a seznamu
    var selectedWasteId by remember { mutableStateOf(repository.getSelectedWasteTypeFilter()) }
    var selectedPlace by remember { mutableStateOf(repository.getSelectedPlaceFilter()) }
    var hideYardInList by remember { mutableStateOf(repository.isHideYardInList()) }

    // Stav nastavení upozornění
    var notifyEnabled by remember { mutableStateOf(repository.isNotificationsEnabled()) }
    var notifyWasteIds by remember { mutableStateOf(repository.getNotifyWasteTypeIds()) }
    var notifyPlace by remember { mutableStateOf(repository.getNotifyPlace()) }
    var notifyDayBefore by remember { mutableStateOf(repository.isNotifyDayBefore()) }
    var notifyDayBeforeHour by remember { mutableIntStateOf(repository.getNotifyDayBeforeHour()) }
    var notifyDayOf by remember { mutableStateOf(repository.isNotifyDayOf()) }
    var notifyDayOfHour by remember { mutableIntStateOf(repository.getNotifyDayOfHour()) }
    var dbUrl by remember { mutableStateOf(repository.getDatabaseUrl()) }

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

    LaunchedEffect(Unit) {
        // Nejprve okamžitě načíst z lokální cache / assets, poté zaktualizovat ze sítě
        calendarData = repository.loadCalendarData(forceNetwork = false)
        isLoading = false
        refreshData(forceOnline = true)
    }

    // Filtrované události podle zvoleného filtru "Typ události", "Místo" a přepínače Sběrného dvora
    val filteredEvents = remember(calendarData.events, selectedWasteId, selectedPlace, hideYardInList) {
        calendarData.events.filter { ev ->
            val matchWaste = selectedWasteId == "0" || ev.wasteTypeId == selectedWasteId
            val matchPlace = ev.matchesPlace(selectedPlace)
            val matchYard = if (hideYardInList && selectedWasteId == "0") ev.isCollection else true
            matchWaste && matchPlace && matchYard
        }
    }

    val activeFiltersCount = (if (selectedWasteId != "0") 1 else 0) + (if (selectedPlace.isNotBlank()) 1 else 0)

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
            // Rozbalovací panel "Filtr" (přesně podle webu MÚ Loštice)
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
                        onSelectWasteId = {
                            selectedWasteId = it
                            repository.setSelectedWasteTypeFilter(it)
                        }
                    )
                    1 -> CalendarTab(
                        events = filteredEvents,
                        wasteTypes = calendarData.wasteTypes
                    )
                    2 -> NotificationsTab(
                        wasteTypes = calendarData.wasteTypes,
                        places = calendarData.places,
                        notifyEnabled = notifyEnabled,
                        onNotifyEnabledChange = {
                            notifyEnabled = it
                            repository.setNotificationsEnabled(it)
                        },
                        notifyWasteIds = notifyWasteIds,
                        onNotifyWasteIdsChange = {
                            notifyWasteIds = it
                            repository.setNotifyWasteTypeIds(it)
                        },
                        notifyPlace = notifyPlace,
                        onNotifyPlaceChange = {
                            notifyPlace = it
                            repository.setNotifyPlace(it)
                        },
                        notifyDayBefore = notifyDayBefore,
                        onNotifyDayBeforeChange = {
                            notifyDayBefore = it
                            repository.setNotifyDayBefore(it)
                        },
                        notifyDayBeforeHour = notifyDayBeforeHour,
                        onNotifyDayBeforeHourChange = {
                            notifyDayBeforeHour = it
                            repository.setNotifyDayBeforeHour(it)
                        },
                        notifyDayOf = notifyDayOf,
                        onNotifyDayOfChange = {
                            notifyDayOf = it
                            repository.setNotifyDayOf(it)
                        },
                        notifyDayOfHour = notifyDayOfHour,
                        onNotifyDayOfHourChange = {
                            notifyDayOfHour = it
                            repository.setNotifyDayOfHour(it)
                        },
                        dbUrl = dbUrl,
                        onDbUrlSave = { newUrl ->
                            dbUrl = newUrl
                            repository.setDatabaseUrl(newUrl)
                            refreshData(forceOnline = true)
                            scope.launch {
                                snackbarHostState.showSnackbar("URL uložena a kalendář aktualizován")
                            }
                        },
                        onTestNotification = {
                            repository.sendTestNotification(calendarData.events)
                            scope.launch {
                                snackbarHostState.showSnackbar("Testovací upozornění bylo odesláno")
                            }
                        }
                    )
                }
            }
        }
    }
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

            // Rozevírací seznam: Typ události
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
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(wt.color))
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

            // Rozevírací seznam: Místo
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

            // Přepínač pro skrytí provozní doby sběrného dvora
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
fun UpcomingTab(
    events: List<WasteEvent>,
    wasteTypes: List<WasteType>,
    selectedWasteId: String,
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
        // Rychlé štítky pro nejčastější druhy odpadu
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
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(wt.color))
                            )
                        },
                        label = { Text(wt.shortName) }
                    )
                }
            }
        }

        // Karta "Nejbližší svoz"
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
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(ev.color))
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
                DayEventsCard(date = dateObj, events = dayEvents, today = today)
            }
        }
    }
}

@Composable
fun DayEventsCard(date: LocalDate, events: List<WasteEvent>, today: LocalDate) {
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
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatCzechDateFull(date),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
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
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(parseHexColor(ev.color))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ev.wasteTypeName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        if (ev.description.isNotBlank() && ev.description != ev.wasteTypeName) {
                            Text(
                                text = ev.description,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
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
    wasteTypes: List<WasteType>
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
        // Hlavička měsíce
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

                    // Dny v týdnu Po - Ne
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

                    // Mřížka kalendáře
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

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(0.92f)
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                when {
                                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                                    isToday -> MaterialTheme.colorScheme.surfaceVariant
                                                    else -> Color.Transparent
                                                }
                                            )
                                            .then(
                                                if (isToday && !isSelected) {
                                                    Modifier.border(
                                                        1.dp,
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
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            Text(
                                                text = "$dayNum",
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected || isToday || cellEvents.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            // Barevné tečky událostí v daný den
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                cellEvents.take(4).forEach { ev ->
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
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Události vybraného dne
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
                DayEventsCard(date = selectedDate, events = selectedDayEvents, today = today)
            }
        }

        // Barevná legenda
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
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(wt.color))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsTab(
    wasteTypes: List<WasteType>,
    places: List<WastePlace>,
    notifyEnabled: Boolean,
    onNotifyEnabledChange: (Boolean) -> Unit,
    notifyWasteIds: Set<String>,
    onNotifyWasteIdsChange: (Set<String>) -> Unit,
    notifyPlace: String,
    onNotifyPlaceChange: (String) -> Unit,
    notifyDayBefore: Boolean,
    onNotifyDayBeforeChange: (Boolean) -> Unit,
    notifyDayBeforeHour: Int,
    onNotifyDayBeforeHourChange: (Int) -> Unit,
    notifyDayOf: Boolean,
    onNotifyDayOfChange: (Boolean) -> Unit,
    notifyDayOfHour: Int,
    onNotifyDayOfHourChange: (Int) -> Unit,
    dbUrl: String,
    onDbUrlSave: (String) -> Unit,
    onTestNotification: () -> Unit
) {
    var placeExpanded by remember { mutableStateOf(false) }
    var urlInput by remember(dbUrl) { mutableStateOf(dbUrl) }

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
                                text = "Automatické připomenutí vybraných komodit v telefonu",
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

        // Kdy upozornit
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Kdy posílat upozornění",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Den předem večer", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "Připomene připravit popelnici nebo pytel večer před svozem (od $notifyDayBeforeHour:00)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = notifyDayBefore, onCheckedChange = onNotifyDayBeforeChange)
                    }

                    if (notifyDayBefore) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf(15, 16, 17, 18, 19, 20, 21)) { h ->
                                FilterChip(
                                    selected = notifyDayBeforeHour == h,
                                    onClick = { onNotifyDayBeforeHourChange(h) },
                                    label = { Text("$h:00") }
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("V den svozu ráno", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "Ranní připomenutí v den svozu (od $notifyDayOfHour:00)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = notifyDayOf, onCheckedChange = onNotifyDayOfChange)
                    }

                    if (notifyDayOf) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf(5, 6, 7, 8)) { h ->
                                FilterChip(
                                    selected = notifyDayOfHour == h,
                                    onClick = { onNotifyDayOfHourChange(h) },
                                    label = { Text("0$h:00") }
                                )
                            }
                        }
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

        // Výběr typů odpadu pro upozornění (všechny položky z tlačítka "Filtr")
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
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(wt.color))
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

        // Zdroj dat (GitHub Pages JSON)
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
                        text = "Zdroj dat (GitHub Pages)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("URL JSON databáze") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(onClick = { onDbUrlSave(urlInput) }) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Uložit a synchronizovat")
                        }
                    }
                }
            }
        }
    }
}
