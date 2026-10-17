package app.meteorite

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.meteorite.data.City
import app.meteorite.data.Day
import app.meteorite.data.Forecast
import app.meteorite.data.Hour
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val clock = DateTimeFormatter.ofPattern("H:mm")

@Composable
fun MeteoRoot(vm: MeteoViewModel, onLocate: () -> Unit) {
    BackHandler(enabled = vm.screen != Screen.Main) { vm.screen = Screen.Main }
    when (val screen = vm.screen) {
        Screen.Main -> MainScreen(vm)
        Screen.Search -> SearchScreen(vm, onLocate)
        is Screen.Detail -> DetailScreen(vm, screen.dayIndex)
    }
}

// ---------------------------------------------------------------- Main

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(vm: MeteoViewModel) {
    val city = vm.selectedCity
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Météorite", fontWeight = FontWeight.Bold) },
                actions = {
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
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (city == null) {
                EmptyState(vm)
            } else {
                ScrollableTabRow(
                    selectedTabIndex = vm.selected,
                    edgePadding = 8.dp,
                    containerColor = MaterialTheme.colorScheme.background,
                ) {
                    vm.cities.forEachIndexed { i, c ->
                        Tab(
                            selected = i == vm.selected,
                            onClick = { vm.select(i) },
                            text = { Text(if (c.isGps) "📍 ${c.name}" else c.name, maxLines = 1) },
                        )
                    }
                }
                if (vm.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                CityContent(vm, city)
            }
        }
    }

    if (confirmDelete && city != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ${city.name} ?") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.removeSelected() }) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annuler") }
            },
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
private fun CityContent(vm: MeteoViewModel, city: City) {
    LaunchedEffect(city) { vm.ensureLoaded() }

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
            val forecast = state.forecast
            val today = forecast.current.time.toLocalDate()
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { NowCard(forecast) }
                itemsIndexed(forecast.days) { i, day ->
                    DayCard(day, today) { vm.screen = Screen.Detail(i) }
                }
            }
        }
    }
}

@Composable
private fun NowCard(forecast: Forecast) {
    val now = forecast.current
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
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
        }
    }
}

private fun dayRainText(day: Day): String {
    val rain = dayRain(day.rainMm)
    if (rain == Rain.NONE) return "☂️ ${rainLabel(rain)}"
    val duration = when {
        day.rainHours >= 20 -> " · toute la journée"
        day.rainHours > 0 -> " · environ ${day.rainHours} h"
        else -> ""
    }
    return "${rain.drops} ${rainLabel(rain)}$duration"
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

// ---------------------------------------------------------------- Day detail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(vm: MeteoViewModel, dayIndex: Int) {
    val city = vm.selectedCity
    val forecast = (city?.let { vm.forecasts[it.id] } as? ForecastState.Ready)?.forecast
    val day = forecast?.days?.getOrNull(dayIndex)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(day?.let { fullDayTitle(it.date) } ?: "", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { vm.screen = Screen.Main }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        if (forecast == null || day == null) {
            Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                Text("Données indisponibles")
            }
            return@Scaffold
        }
        val now = forecast.current.time
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { DaySummary(day, city?.name.orEmpty()) }
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
            TopAppBar(
                title = { Text("Ajouter une ville", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { vm.screen = Screen.Main }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
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
