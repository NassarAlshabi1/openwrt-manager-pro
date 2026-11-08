package com.openwrt.managerpro.ui.onboarding

import android.content.Context
import android.net.ConnectivityManager
import android.net.RouteInfo
import android.net.LinkProperties
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openwrt.managerpro.R
import com.openwrt.managerpro.domain.RouterProfile
import com.openwrt.managerpro.domain.TestConnectionUseCase
import com.openwrt.managerpro.domain.TestResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

object GatewayDiscovery {
    /** Best-effort default gateway of the active network (the router address). */
    fun currentGateway(context: Context): String? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
            val lp: LinkProperties = cm.getLinkProperties(cm.activeNetwork) ?: return null
            lp.routes.firstOrNull { it is RouteInfo && it.isDefaultRoute }
                ?.let { it.gateway?.hostAddress }
                ?.takeIf { !it.isNullOrEmpty() && it != "0.0.0.0" }
        } catch (e: Exception) {
            null
        }
    }
}

sealed class OnboardingState {
    object Idle : OnboardingState()
    object Connecting : OnboardingState()
    data class Success(val hostname: String?, val model: String?, val version: String?) : OnboardingState()
    data class Failure(val detail: String) : OnboardingState()
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val testConnection: TestConnectionUseCase,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow<OnboardingState>(OnboardingState.Idle)
    val state = _state.asStateFlow()

    fun suggestedGateway(): String? = GatewayDiscovery.currentGateway(appContext)

    fun connect(ip: String, username: String, password: String) {
        val base = normalizeBaseUrl(ip)
        if (base == null) {
            _state.value = OnboardingState.Failure("invalid address")
            return
        }
        viewModelScope.launch {
            _state.value = OnboardingState.Connecting
            val result: TestResult = testConnection(RouterProfile(base, username.ifBlank { "root" }, password))
            _state.value = if (result.ok) {
                OnboardingState.Success(result.hostname, result.model, result.version)
            } else {
                OnboardingState.Failure(result.detail)
            }
        }
    }

    companion object {
        fun normalizeBaseUrl(input: String): String? {
            val raw = input.trim()
            if (raw.isEmpty()) return null
            val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
            return try {
                val url = java.net.URL(withScheme)
                val host = url.host ?: return null
                if (host.isBlank()) null else "${url.protocol}://$host${if (url.port in 1..65535 && url.port != 80) ":${url.port}" else ""}"
            } catch (e: Exception) {
                null
            }
        }
    }
}

@Composable
fun OnboardingScreen(
    onConnected: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    var ip by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("root") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var suggestionsLoaded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!suggestionsLoaded) {
            suggestionsLoaded = true
            val gw = viewModel.suggestedGateway()
            if (!gw.isNullOrEmpty() && ip.isBlank()) ip = gw
        }
    }

    LaunchedEffect(state) {
        if (state is OnboardingState.Success) {
            onConnected()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Router,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.ob_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it },
            label = { Text(stringResource(R.string.ob_ip)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        if (ip.isNotBlank()) {
            TextButton(onClick = { ip = "192.168.1.1" }) {
                Text(stringResource(R.string.ob_preset, "192.168.1.1"))
            }
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(R.string.ob_username)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.ob_password)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(stringResource(if (showPassword) R.string.ob_hide else R.string.ob_show))
                }
            },
        )
        Spacer(Modifier.height(16.dp))

        val st = state
        when (st) {
            is OnboardingState.Failure -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.ob_failed),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(st.detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            else -> Unit
        }

        Button(
            onClick = { viewModel.connect(ip, username, password) },
            enabled = st !is OnboardingState.Connecting && ip.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (st is OnboardingState.Connecting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.ob_connecting))
            } else {
                Text(stringResource(R.string.ob_connect))
            }
        }
    }
}
