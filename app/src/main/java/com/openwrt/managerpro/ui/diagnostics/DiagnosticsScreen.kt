package com.openwrt.managerpro.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openwrt.managerpro.R
import com.openwrt.managerpro.domain.DiagnosticReport
import com.openwrt.managerpro.domain.DiagnosticSeverity
import com.openwrt.managerpro.domain.Finding
import com.openwrt.managerpro.domain.RunDiagnosticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DiagnosticsUiState {
    object Idle : DiagnosticsUiState()
    object Running : DiagnosticsUiState()
    data class Done(val report: DiagnosticReport) : DiagnosticsUiState()
    data class Failed(val message: String) : DiagnosticsUiState()
}

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val runDiagnostics: RunDiagnosticsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<DiagnosticsUiState>(DiagnosticsUiState.Idle)
    val state = _state.asStateFlow()

    fun run() {
        if (_state.value is DiagnosticsUiState.Running) return
        viewModelScope.launch {
            _state.value = DiagnosticsUiState.Running
            try {
                _state.value = DiagnosticsUiState.Done(runDiagnostics())
            } catch (e: Exception) {
                _state.value = DiagnosticsUiState.Failed(e.message ?: "error")
            }
        }
    }
}

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.diag_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.diag_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            when (val st = state) {
                is DiagnosticsUiState.Done -> ScoreCard(st.report.score)
                else -> ScoreCard(null)
            }
        }

        item {
            Button(
                onClick = { viewModel.run() },
                enabled = state !is DiagnosticsUiState.Running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state is DiagnosticsUiState.Running) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.diag_running))
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.diag_run))
                }
            }
        }

        when (val st = state) {
            is DiagnosticsUiState.Failed -> item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        st.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            is DiagnosticsUiState.Done -> {
                item {
                    Text(
                        stringResource(R.string.diag_duration, (st.report.durationMs / 1000.0).toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(st.report.findings, key = { it.checkId }) { finding ->
                    FindingRow(finding)
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun ScoreCard(score: Int?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (score == null) {
                Text("-", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.diag_not_run),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "$score/100",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor(score),
                )
                Text(
                    stringResource(R.string.diag_score),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = score / 100f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun scoreColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF4ADE80)
    score >= 50 -> Color(0xFFFBBF24)
    else -> Color(0xFFF87171)
}

@Composable
private fun FindingRow(finding: Finding) {
    val (icon, color) = when (finding.severity) {
        is DiagnosticSeverity.Ok -> Icons.Filled.CheckCircleOutline to Color(0xFF4ADE80)
        is DiagnosticSeverity.Info -> Icons.Filled.Info to Color(0xFF22D3EE)
        is DiagnosticSeverity.Warning -> Icons.Filled.Warning to Color(0xFFFBBF24)
        is DiagnosticSeverity.Error -> Icons.Filled.ErrorOutline to Color(0xFFF87171)
        is DiagnosticSeverity.Skipped -> Icons.Filled.Info to MaterialTheme.colorScheme.outline
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
            Column {
                Text(
                    checkTitle(finding.checkId),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (finding.details.isNotBlank()) {
                    Text(
                        finding.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun checkTitle(checkId: String): String = when (checkId) {
    "cpu" -> stringResource(R.string.diag_check_cpu)
    "memory" -> stringResource(R.string.diag_check_memory)
    "storage" -> stringResource(R.string.diag_check_storage)
    "temperature" -> stringResource(R.string.diag_check_temperature)
    "wan" -> stringResource(R.string.diag_check_wan)
    "dns" -> stringResource(R.string.diag_check_dns)
    "wifi" -> stringResource(R.string.diag_check_wifi)
    "mesh" -> stringResource(R.string.diag_check_mesh)
    "wifi_security" -> stringResource(R.string.diag_check_wifi_security)
    "firewall" -> stringResource(R.string.diag_check_firewall)
    "logs" -> stringResource(R.string.diag_check_logs)
    else -> checkId
}
