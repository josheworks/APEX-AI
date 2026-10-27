package com.apex.ai.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apex.ai.mobile.ui.screens.MainTerminalScreen
import com.apex.ai.mobile.ui.screens.SettingsScreen
import com.apex.ai.mobile.ui.theme.ApexMobileTheme
import com.apex.ai.mobile.ui.viewmodel.ApexViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as ApexApplication

        val viewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ApexViewModel(
                    modelAdapter = app.localModelAdapter,
                    toolRegistry = app.toolRegistry,
                    policyEngine = app.policyEngine,
                    auditLogger = app.auditLogger,
                    workflowEngine = app.workflowEngine
                ) as T
            }
        }

        setContent {
            ApexMobileTheme {
                val viewModel: ApexViewModel = viewModel(factory = viewModelFactory)
                val uiState by viewModel.uiState.collectAsState()
                var currentScreen by remember { mutableStateOf("terminal") }

                if (currentScreen == "settings") {
                    SettingsScreen(
                        uiState = uiState,
                        onBack = { currentScreen = "terminal" }
                    )
                } else {
                    MainTerminalScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onOpenSettings = { currentScreen = "settings" }
                    )
                }
            }
        }
    }
}
