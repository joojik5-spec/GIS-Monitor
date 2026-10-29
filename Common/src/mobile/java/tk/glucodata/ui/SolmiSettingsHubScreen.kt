package tk.glucodata.ui

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import tk.glucodata.R

private enum class SolmiSettingsCategory(
    val titleRes: Int,
    val descriptionRes: Int,
    val icon: ImageVector,
) {
    Appearance(R.string.settings_hub_appearance, R.string.settings_hub_appearance_desc, Icons.Default.Palette),
    Alerts(R.string.settings_hub_alerts, R.string.settings_hub_alerts_desc, Icons.Default.Notifications),
    Data(R.string.settings_hub_data, R.string.settings_hub_data_desc, Icons.Default.Backup),
    Connections(R.string.settings_hub_connections, R.string.settings_hub_connections_desc, Icons.Default.Devices),
    Devices(R.string.settings_hub_devices, R.string.settings_hub_devices_desc, Icons.Default.Devices),
    Advanced(R.string.settings_hub_advanced, R.string.settings_hub_advanced_desc, Icons.Default.SettingsSuggest),
}

private data class SolmiSettingsDestination(
    val category: SolmiSettingsCategory,
    val titleRes: Int,
    val descriptionRes: Int,
    val route: String,
)

private val hubDestinations = listOf(
    SolmiSettingsDestination(SolmiSettingsCategory.Appearance, R.string.display_colors_title, R.string.display_colors_desc, "settings/display-colors"),
    SolmiSettingsDestination(SolmiSettingsCategory.Appearance, R.string.notification_settings_title, R.string.notification_settings_subtitle, "settings/notification-display"),
    SolmiSettingsDestination(SolmiSettingsCategory.Appearance, R.string.settings_hub_all_settings, R.string.settings_hub_all_settings_desc, "settings/all"),
    SolmiSettingsDestination(SolmiSettingsCategory.Alerts, R.string.glucose_alerts_title, R.string.settings_hub_alerts_desc, "settings/alerts"),
    SolmiSettingsDestination(SolmiSettingsCategory.Alerts, R.string.floatglucose, R.string.settings_hub_floating_desc, "settings/floating-display"),
    SolmiSettingsDestination(SolmiSettingsCategory.Alerts, R.string.lock_screen_aod, R.string.settings_hub_aod_desc, "settings/aod-display"),
    SolmiSettingsDestination(SolmiSettingsCategory.Data, R.string.journal_title, R.string.settings_hub_journal_desc, "settings/journal"),
    SolmiSettingsDestination(SolmiSettingsCategory.Data, R.string.app_updates_title, R.string.app_updates_auto_desc, "settings/app-updates"),
    SolmiSettingsDestination(SolmiSettingsCategory.Data, R.string.settings_hub_all_settings, R.string.settings_hub_backup_desc, "settings/all"),
    SolmiSettingsDestination(SolmiSettingsCategory.Connections, R.string.mirror, R.string.mirror_desc, "settings/mirror"),
    SolmiSettingsDestination(SolmiSettingsCategory.Connections, R.string.nightscout_config, R.string.nightscout_desc, "settings/nightscout"),
    SolmiSettingsDestination(SolmiSettingsCategory.Connections, R.string.outbound_api_title, R.string.outbound_api_desc, "settings/outbound-api"),
    SolmiSettingsDestination(SolmiSettingsCategory.Connections, R.string.dexcom_share_title, R.string.dexcom_share_desc, "settings/dexcom-share"),
    SolmiSettingsDestination(SolmiSettingsCategory.Connections, R.string.webserver, R.string.settings_hub_webserver_desc, "settings/webserver"),
    SolmiSettingsDestination(SolmiSettingsCategory.Devices, R.string.nav_sensors, R.string.settings_hub_sensors_desc, "sensors"),
    SolmiSettingsDestination(SolmiSettingsCategory.Devices, R.string.meterlist, R.string.glucose_meters_desc, "settings/glucose-meters"),
    SolmiSettingsDestination(SolmiSettingsCategory.Devices, R.string.insulin_pens_title, R.string.settings_hub_pens_desc, "settings/insulin-pens"),
    SolmiSettingsDestination(SolmiSettingsCategory.Devices, R.string.watches, R.string.settings_hub_watches_desc, "settings/watch"),
    SolmiSettingsDestination(SolmiSettingsCategory.Advanced, R.string.cgm_readiness_title, R.string.cgm_readiness_settings_desc, "settings/cgm-readiness"),
    SolmiSettingsDestination(SolmiSettingsCategory.Advanced, R.string.graph_smoothing_title, R.string.settings_hub_smoothing_desc, "settings/data-smoothing"),
    SolmiSettingsDestination(SolmiSettingsCategory.Advanced, R.string.predictive_simulation_title, R.string.predictive_simulation_summary, "settings/predictive-simulation"),
    SolmiSettingsDestination(SolmiSettingsCategory.Advanced, R.string.debug_logs, R.string.debug_logs_desc, "settings/debug"),
    SolmiSettingsDestination(SolmiSettingsCategory.Advanced, R.string.settings_hub_all_settings, R.string.settings_hub_all_settings_desc, "settings/all"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolmiSettingsHubScreen(navController: NavController, themeMode: ThemeMode, onThemeChanged: (ThemeMode) -> Unit, viewModel: tk.glucodata.ui.viewmodel.DashboardViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var categoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedCategory = categoryName?.let { saved ->
        SolmiSettingsCategory.entries.firstOrNull { it.name == saved }
    }
    if (selectedCategory != null && selectedCategory != SolmiSettingsCategory.Devices) {
        androidx.activity.compose.BackHandler { categoryName = null }
        ExpressiveSettingsScreen(navController, themeMode, onThemeChanged, viewModel,
            category = selectedCategory.name,
            onCategoryBack = { categoryName = null })
        return
    }
    val normalizedQuery = query.trim().lowercase()
    val visibleCategories = SolmiSettingsCategory.entries.filter { category ->
        normalizedQuery.isEmpty() || stringResource(category.titleRes).lowercase().contains(normalizedQuery) ||
            stringResource(category.descriptionRes).lowercase().contains(normalizedQuery)
    }
    val visibleDestinations = hubDestinations.filter { destination ->
        (selectedCategory == null || destination.category == selectedCategory) &&
            (normalizedQuery.isEmpty() ||
                stringResource(destination.titleRes).lowercase().contains(normalizedQuery) ||
                stringResource(destination.descriptionRes).lowercase().contains(normalizedQuery))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "heading") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedCategory != null) {
                        IconButton(onClick = { categoryName = null; query = "" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                        }
                    }
                    if (selectedCategory != null) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(selectedCategory.titleRes),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.settings_hub_search)) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                )
            }
        }

        item(key = "hints") {
            SettingsHubCard(stringResource(R.string.dashboard_help), "", null,
                onClick = { DashboardLayout.tutorialStep = 0; navController.navigate("dashboard") })
        }
        item(key = "all-settings") {
            SettingsHubCard(stringResource(R.string.settings_hub_all_settings), "", null,
                onClick = { navController.navigate("settings/all") })
        }
        if (selectedCategory == null) {
            items(visibleCategories, key = { it.name }) { category ->
                SettingsHubCard(
                    title = stringResource(category.titleRes),
                    description = stringResource(category.descriptionRes),
                    icon = category.icon,
                    onClick = { categoryName = category.name },
                )
            }
        }
        if (selectedCategory != null || normalizedQuery.isNotEmpty()) {
            items(visibleDestinations, key = { "${it.category.name}:${it.route}:${it.titleRes}" }) { destination ->
                SettingsHubCard(
                    title = stringResource(destination.titleRes),
                    description = stringResource(destination.descriptionRes),
                    icon = null,
                    onClick = { navController.navigate(destination.route) },
                )
            }
            if (visibleDestinations.isEmpty() && (selectedCategory != null || visibleCategories.isEmpty())) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.settings_hub_nothing_found),
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsHubCard(
    title: String,
    description: String,
    icon: ImageVector?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (description.isNotBlank()) Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
