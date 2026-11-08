package com.openwrt.managerpro.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openwrt.managerpro.R
import com.openwrt.managerpro.data.prefs.SettingsStore
import com.openwrt.managerpro.domain.RouterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val monitoring: Boolean = true,
    val notifyNew: Boolean = true,
    val notifyOffline: Boolean = true,
    val notifyFirmware: Boolean = true,
    val intervalMin: Int = 15,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val routerRepository: RouterRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    private val _forgotten = MutableStateFlow(false)
    val forgotten = _forgotten.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.monitoringEnabled.collect { _state.value = _state.value.copy(monitoring = it) }
        }
        viewModelScope.launch {
            settingsStore.notifyNewDevice.collect { _state.value = _state.value.copy(notifyNew = it) }
        }
        viewModelScope.launch {
            settingsStore.notifyOffline.collect { _state.value = _state.value.copy(notifyOffline = it) }
        }
        viewModelScope.launch {
            settingsStore.notifyFirmware.collect { _state.value = _state.value.copy(notifyFirmware = it) }
        }
        viewModelScope.launch {
            settingsStore.monitorIntervalMin.collect { _state.value = _state.value.copy(intervalMin = it) }
        }
    }

    fun setMonitoring(v: Boolean) = viewModelScope.launch { settingsStore.setMonitoringEnabled(v) }
    fun setNotifyNew(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyNewDevice(v) }
    fun setNotifyOffline(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyOffline(v) }
    fun setNotifyFirmware(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyFirmware(v) }
    fun setInterval(min: Int) = viewModelScope.launch { settingsStore.setMonitorIntervalMin(min) }

    fun forgetRouter() {
        viewModelScope.launch {
            routerRepository.clearProfile()
            _forgotten.value = true
        }
    }
}

@Composable
fun SettingsScreen(
    onProfileCleared: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val forgotten by viewModel.forgotten.collectAsStateWithLifecycle()

    if (forgotten) {
        onProfileCleared()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.set_title), style = MaterialTheme.typography.headlineSmall)

        SettingsCard(title = stringResource(R.string.set_background_title)) {
            ToggleRow(
                label = stringResource(R.string.set_monitoring),
                checked = state.monitoring,
                onChange = { viewModel.setMonitoring(it) },
            )
            ToggleRow(
                label = stringResource(R.string.set_notify_new),
                checked = state.notifyNew,
                onChange = { viewModel.setNotifyNew(it) },
            )
            ToggleRow(
                label = stringResource(R.string.set_notify_offline),
                checked = state.notifyOffline,
                onChange = { viewModel.setNotifyOffline(it) },
            )
            ToggleRow(
                label = stringResource(R.string.set_notify_fw),
                checked = state.notifyFirmware,
                onChange = { viewModel.setNotifyFirmware(it) },
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.set_interval),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (min in listOf(15, 30, 60)) {
                        AssistChip(
                            onClick = { viewModel.setInterval(min) },
                            label = {
                                Text(
                                    stringResource(R.string.set_interval_min, min),
                                    fontWeight = if (state.intervalMin == min) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.set_connection_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.forgetRouter() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.set_forget), color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.set_about),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.set_about_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
