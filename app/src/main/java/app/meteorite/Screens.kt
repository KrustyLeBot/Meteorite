package app.meteorite

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.meteorite.data.City
import app.meteorite.suite.AboutSettings
import app.meteorite.suite.ConfirmDialog
import app.meteorite.suite.SuiteBanner
import app.meteorite.suite.SuiteTopBar
import app.meteorite.suite.SyncSettings
import app.meteorite.suite.Tone
import app.meteorite.suite.UpdateBanner
import app.meteorite.suite.UpdateSettings
import app.meteorite.data.Day
import app.meteorite.data.Forecast
import app.meteorite.data.Hour
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val clock = DateTimeFormatter.ofPattern("H:mm")

@Composable
fun MeteoRoot(vm: MeteoViewModel, onLocate: () -> Unit, onAllowBackground: () -> Unit, onAllowStorage: () -> Unit) {
    BackHandler(enabled = vm.screen != Screen.Main) { vm.screen = Screen.Main }
    when (val screen = vm.screen) {
        Screen.Main -> MainScreen(vm, onAllowBackground, onLocate, onAllowStorage)
        Screen.Search -> SearchScreen(vm, onLocate)
        is Screen.Detail -> DetailScreen(vm, screen.dayIndex)
        Screen.Settings -> SettingsScreen(vm)
    }
}

// ---------------------------------------------------------------- Main

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(vm: MeteoViewModel, onAllowBackground: () -> Unit, onLocate: () -> Unit, onAllowStorage: () -> Unit) {
    val city = vm.selectedCity
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SuiteTopBar("Météorite") {
                    if (city != null) {
                        IconButton(onClick = { vm.ensureLoaded(manual = true) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Actualiser")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer la ville")
                        }
                    }
                    IconButton(onClick = { vm.openSearch() }) {
                        Icon(Icons.Default.Add, contentDescription = "Ajouter une ville")
                    }
                    IconButton(onClick = { vm.screen = Screen.Settings }) {
                        Icon(Icons.Default.Settings, contentDescription = "Paramètres")
                    }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            vm.storeError?.let { SuiteBanner(it, tone = Tone.Error, title = "Villes illisibles", icon = Icons.Default.ErrorOutline) }
            if (vm.syncing) SuiteBanner("Villes en lecture seule le temps de la synchro.", title = "Synchronisation avec Proton Drive", icon = Icons.Default.Sync, progress = -1f)
            vm.notice?.let { SuiteBanner(it) }
            if (vm.needsStorageAccess) {
                SuiteBanner("Autorise l'accès aux fichiers pour que Synchrotron sauvegarde tes villes (Documents/Meteorite).", icon = Icons.Default.Folder) {
                    TextButton(onClick = onAllowStorage) { Text("Autoriser") }
                }
            }
            UpdateBanner(vm.updates)
            if (city == null) {
                EmptyState(vm)
            } else {
                CityTabs(vm)
                if (vm.cities.size > 1) {
                    Text(
                        "Maintiens un onglet pour le déplacer · le premier s'affiche sur le widget",
                        Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (vm.cities.any { it.isGps } && !vm.backgroundOk) LocationBanner(onAllowBackground)
                if (vm.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                CityContent(vm, city, onLocate)
            }
        }
    }

    if (confirmDelete && city != null) {
        ConfirmDialog(
            "Supprimer ${city.name} ?", null, "Supprimer",
            onConfirm = { vm.removeSelected() }, onDismiss = { confirmDelete = false }, destructive = true,
        )
    }
}

@Composable
private fun EmptyState(vm: MeteoViewModel) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🌤️", fontSize = 72.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Ajoute une ville pour voir la météo",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { vm.openSearch() }) { Text("Ajouter une ville") }
    }
}

@Composable
private fun CityContent(vm: MeteoViewModel, city: City, onLocate: () -> Unit) {
    LaunchedEffect(city) { vm.ensureLoaded() }

    if (!city.hasPosition) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Position pas encore connue sur ce téléphone.", textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onLocate, enabled = !vm.locating) { Text("Me localiser") }
        }
        return
    }

    when (val state = vm.forecasts[city.id]) {
        null, ForecastState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }

        is ForecastState.Error -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(state.message, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { vm.ensureLoaded(manual = true) }) { Text("Réessayer") }
        }

        is ForecastState.Ready -> {
            val forecast = state.forecast.rolled()
            val today = forecast.current.time.toLocalDate()
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    NowCard(city, state.source, forecast, state.loadedAt, vm.refreshErrors[city.id]) {
                        vm.screen = Screen.Detail(0)
                    }
                }
                // Today is the "Maintenant" card above, so the list starts tomorrow.
                forecast.days.forEachIndexed { i, day ->
                    if (day.date != today) {
                        item { DayCard(day, today) { vm.screen = Screen.Detail(i) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowCard(
    city: City,
    source: City,
    forecast: Forecast,
    savedAt: Long,
    refreshError: String?,
    onClick: () -> Unit,
) {
    val now = forecast.current
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            // The place the shown forecast is for (the previous one right after "Ma position" moved).
            val place = if (source.isGps) "📍 ${source.subtitle.ifBlank { source.name }}" else source.name
            val outdatedPlace = source.lat != city.lat || source.lon != city.lon
            Text(place, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Maintenant", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(weatherEmoji(now.code, now.isDay), fontSize = 60.sp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("${now.temp}°", fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${weatherLabel(now.code)} · ressenti ${now.feelsLike}°",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                restOfDaySentence(forecast.todayRemaining(), now.time),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "💨 ${windLabel(now.windKmh)} · ${now.windKmh} km/h",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                updatedText(savedAt) +
                    (if (outdatedPlace) " · ancienne position" else "") +
                    (refreshError?.let { " · actualisation impossible $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun dayRainText(day: Day): String {
    // Same levels as the hourly rows, so the day card and its detail always agree.
    val wet = day.hours.map { hourRain(it.rainMm) }.filter { it != Rain.NONE }
    if (wet.isEmpty() && day.hours.isNotEmpty()) return "☂️ ${rainLabel(Rain.NONE)}"
    val rain = wet.maxOrNull() ?: dayRain(day.rainMm)
    if (rain == Rain.NONE) return "☂️ ${rainLabel(rain)}"
    val wetHours = wet.size
    val duration = when {
        wetHours >= 20 -> " · toute la journée"
        wetHours > 0 -> " · pendant environ $wetHours h"
        else -> ""
    }
    return "${rain.drops} ${rainRangeLabel(wet.minOrNull() ?: rain, rain)}$duration"
}

/** Null when no snow is expected. */
private fun daySnowText(day: Day): String? {
    val level = daySnow(day.snowCm)
    return if (level == Rain.NONE) null else "❄️ ${snowLabel(level)} · ${formatCm(day.snowCm)}"
}

@Composable
private fun DayCard(day: Day, today: LocalDate, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(weatherEmoji(day.code), fontSize = 40.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    dayTitle(day.date, today),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    daySubtitle(day.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(dayRainText(day), style = MaterialTheme.typography.bodyMedium)
                daySnowText(day)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(
                    "💨 ${windLabel(day.windKmh)} · ${day.windKmh} km/h",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${day.tMax}°", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${day.tMin}°",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CityTabs(vm: MeteoViewModel) {
    val haptic = LocalHapticFeedback.current
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val widths = remember { mutableStateMapOf<String, Int>() }

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp)
    ) {
        vm.cities.forEach { city ->
            key(city.id) {
                val index = vm.cities.indexOfFirst { it.id == city.id }
                val dragging = draggingId == city.id
                val selected = index == vm.selected
                Box(
                    Modifier
                        .onSizeChanged { widths[city.id] = it.width }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationX = if (dragging) dragOffset else 0f
                            scaleX = if (dragging) 1.08f else 1f
                            scaleY = if (dragging) 1.08f else 1f
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (dragging) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                        .clickable { vm.select(index) }
                        .pointerInput(city.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingId = city.id
                                    dragOffset = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.x
                                    val i = vm.cities.indexOfFirst { it.id == city.id }
                                    val next = vm.cities.getOrNull(i + 1)
                                    val prev = vm.cities.getOrNull(i - 1)
                                    val nextWidth = next?.let { widths[it.id] } ?: 0
                                    val prevWidth = prev?.let { widths[it.id] } ?: 0
                                    // Swapping moves the tab in the row, so the finger offset shrinks by that width.
                                    if (next != null && dragOffset > nextWidth / 2f) {
                                        vm.moveCity(i, i + 1)
                                        dragOffset -= nextWidth
                                    } else if (prev != null && dragOffset < -prevWidth / 2f) {
                                        vm.moveCity(i, i - 1)
                                        dragOffset += prevWidth
                                    }
                                },
                                onDragEnd = {
                                    draggingId = null
                                    dragOffset = 0f
                                    vm.orderChanged()
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragOffset = 0f
                                    vm.orderChanged()
                                },
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (city.isGps) "📍 ${city.name}" else city.name,
                            maxLines = 1,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .width(24.dp)
                                .height(3.dp)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(2.dp),
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationBanner(onAllow: () -> Unit) {
    SuiteBanner(
        "Pour que le widget suive tes déplacements même quand l'application est fermée, autorise la position « Toujours ».",
        icon = Icons.Default.MyLocation,
    ) { TextButton(onClick = onAllow) { Text("Autoriser") } }
}

// ---------------------------------------------------------------- Settings

@Composable
private fun SettingsScreen(vm: MeteoViewModel) {
    Scaffold(topBar = { SuiteTopBar("Paramètres", onBack = { vm.screen = Screen.Main }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SyncSettings("Documents/Meteorite")
            UpdateSettings(vm.updates)
            AboutSettings(
                "Météorite",
                "Prévisions d'Open-Meteo et noms de lieux d'OpenStreetMap, sans compte ni publicité. " +
                    "Le premier onglet est celui du widget.",
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ---------------------------------------------------------------- Day detail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(vm: MeteoViewModel, dayIndex: Int) {
    val city = vm.selectedCity
    val forecast = (city?.let { vm.forecasts[it.id] } as? ForecastState.Ready)?.forecast?.rolled()
    val day = forecast?.days?.getOrNull(dayIndex)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SuiteTopBar(day?.let { fullDayTitle(it.date) } ?: "", onBack = { vm.screen = Screen.Main })
        },
    ) { padding ->
        if (forecast == null || day == null) {
            Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                Text("Données indisponibles")
            }
            return@Scaffold
        }
        val now = forecast.current.time
        val listState = rememberLazyListState()
        // Today opens on the current hour (item 0 is the summary card, hours follow).
        LaunchedEffect(dayIndex) {
            if (day.date == now.toLocalDate()) {
                val hour = day.hours.indexOfFirst { it.time.hour == now.hour }
                if (hour >= 0) listState.scrollToItem(1 + maxOf(0, hour - 1))
            }
        }
        LazyColumn(
            Modifier.padding(padding),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { DaySummary(day, city?.displayName.orEmpty()) }
            items(day.hours) { hour -> HourRow(hour, current = hour.time.withMinute(0) == now.withMinute(0)) }
        }
    }
}

@Composable
private fun DaySummary(day: Day, cityName: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(weatherEmoji(day.code), fontSize = 56.sp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(cityName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(weatherLabel(day.code), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${day.tMax}° / ${day.tMin}°",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(dayRainText(day), style = MaterialTheme.typography.titleSmall)
            daySnowText(day)?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            Text(
                "💨 ${windLabel(day.windKmh)} · ${day.windKmh} km/h",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "🌅 ${day.sunrise.format(clock)}   🌇 ${day.sunset.format(clock)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun HourRow(hour: Hour, current: Boolean) {
    val rain = hourRain(hour.rainMm)
    val snow = hourSnow(hour.snowCm)
    val background = if (current) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface
    Row(
        Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${hour.time.hour} h", Modifier.width(44.dp), fontWeight = FontWeight.Bold)
        Text(weatherEmoji(hour.code, hour.isDay), fontSize = 28.sp)
        Text(
            "${hour.temp}°",
            Modifier.width(52.dp).padding(start = 10.dp),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
        Column(Modifier.weight(1f)) {
            val text = when {
                snow != Rain.NONE -> "❄️ ${snowLabel(snow)} · ${formatCm(hour.snowCm)}"
                rain != Rain.NONE -> "${rain.drops} ${rainLabel(rain)}"
                else -> "Pas de pluie"
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (rain == Rain.NONE && snow == Rain.NONE) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
        }
        Text("💨 ${hour.windKmh}", style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------- Search

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(vm: MeteoViewModel, onLocate: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SuiteTopBar("Ajouter une ville", onBack = { vm.screen = Screen.Main })
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            Button(onClick = onLocate, enabled = !vm.locating, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.MyLocation, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (vm.locating) "Localisation…" else "Utiliser ma position")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = vm.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text("Rechercher une ville") },
                singleLine = true,
            )
            if (vm.searching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            vm.searchMessage?.let {
                Text(
                    it,
                    Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn {
                items(vm.results) { city ->
                    ListItem(
                        headlineContent = { Text(city.name) },
                        supportingContent = { if (city.subtitle.isNotBlank()) Text(city.subtitle) },
                        modifier = Modifier.clickable { vm.addCity(city) },
                    )
                }
            }
        }
    }
}
