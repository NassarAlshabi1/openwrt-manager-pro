package com.openwrt.managerpro.ui.firmware

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openwrt.managerpro.R
import com.openwrt.managerpro.domain.CheckFirmwareUseCase
import com.openwrt.managerpro.domain.FirmwareStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FirmwareUiState(
    val checking: Boolean = false,
    val error: String? = null,
    val status: FirmwareStatus? = null,
)

@HiltViewModel
class FirmwareViewModel @Inject constructor(
    private val checkFirmware: CheckFirmwareUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(FirmwareUiState())
    val state = _state.asStateFlow()

    fun check() {
        viewModelScope.launch {
            _state.value = FirmwareUiState(checking = true)
            try {
                _state.value = FirmwareUiState(checking = false, status = checkFirmware())
            } catch (e: Exception) {
                _state.value = FirmwareUiState(checking = false, error = e.message ?: "error")
            }
        }
    }
}

@Composable
fun FirmwareScreen(viewModel: FirmwareViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.fw_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.fw_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val st = state.status
        if (st != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow(stringResource(R.string.fw_current), st.currentVersion)
                    InfoRow(
                        stringResource(R.string.fw_latest),
                        st.latestVersion ?: stringResource(R.string.fw_unknown),
                    )
                    st.target?.let { InfoRow(stringResource(R.string.fw_target), it) }
                    st.model?.let { InfoRow(stringResource(R.string.fw_model), it) }
                }
            }

            if (st.updateAvailable) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.fw_update_available, st.latestVersion ?: ""),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            st.releaseUrl?.let { uriHandler.openUri(it) }
                        }) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.fw_release_page))
                        }
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.fw_up_to_date),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        if (state.error != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    state.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Button(
            onClick = { viewModel.check() },
            enabled = !state.checking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.checking) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.fw_checking))
            } else {
                Icon(Icons.Filled.SystemUpdate, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.fw_check))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
