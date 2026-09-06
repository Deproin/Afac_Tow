package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appViewModel: AppViewModel = viewModel()
            val currentScreen by appViewModel.currentScreen.collectAsState()
            val isDarkMode by appViewModel.isDarkMode.collectAsState()

            MyApplicationTheme(darkTheme = isDarkMode) {
                BackHandler(enabled = currentScreen != AppScreen.DASHBOARD && currentScreen != AppScreen.LOGIN) {
                    appViewModel.navigateTo(AppScreen.DASHBOARD)
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Animated transition between screens for high professional polish
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "screen_transition"
                    ) { screen ->
                        when (screen) {
                            AppScreen.LOGIN -> LoginScreen(appViewModel)
                            
                            AppScreen.DASHBOARD -> DashboardScreen(
                                viewModel = appViewModel,
                                onNavigate = { target -> appViewModel.navigateTo(target) }
                            )
                            
                            AppScreen.USER_MANAGEMENT -> UserManagementScreen(
                                viewModel = appViewModel,
                                onBack = { appViewModel.navigateTo(AppScreen.DASHBOARD) }
                            )
                            
                            AppScreen.INVENTORY -> InventoryScreen(
                                viewModel = appViewModel,
                                onBack = { appViewModel.navigateTo(AppScreen.DASHBOARD) }
                            )
                            
                            AppScreen.CONTACTS -> ContactsScreen(
                                viewModel = appViewModel,
                                onBack = { appViewModel.navigateTo(AppScreen.DASHBOARD) }
                            )
                            
                            AppScreen.SALES -> SalesPurchasesScreen(
                                viewModel = appViewModel,
                                mode = "SALE",
                                onBack = { appViewModel.navigateTo(AppScreen.DASHBOARD) }
                            )
                            
                            AppScreen.PURCHASES -> SalesPurchasesScreen(
                                viewModel = appViewModel,
                                mode = "PURCHASE",
                                onBack = { appViewModel.navigateTo(AppScreen.DASHBOARD) }
                            )

                            AppScreen.SALES_RETURN -> SalesPurchasesScreen(
                                viewModel = appViewModel,
                                mode = "SALE_RETURN",
                                onBack = { appViewModel.navigateTo(AppScreen.DASHBOARD) }
                            )

                            AppScreen.PURCHASES_RETURN -> SalesPurchasesScreen(
                                viewModel = appViewModel,
                                mode = "PURCHASE_RETURN",
                                onBack = { appViewModel.navigateTo(AppScreen.DASHBOARD) }
                            )
                            
                            AppScreen.TREASURY -> AccountingScreen(
                                viewModel = appViewModel,
                                initialTab = 2,
                                onBack = { appViewModel.navigateTo(AppScreen.DASHBOARD) }
                            )
                            
                            AppScreen.JOURNAL_ENTRIES -> AccountingScreen(
                                viewModel = appViewModel,
                                initialTab = 0,
                                onBack = { appViewModel.navigateTo(AppScreen.DASHBOARD) }
                            )
                            
                            AppScreen.REPORTS -> ReportsScreen(
                                viewModel = appViewModel,
                                onBack = { appViewModel.navigateTo(AppScreen.DASHBOARD) }
                            )
                            
                            AppScreen.SETTINGS -> SettingsScreen(
                                viewModel = appViewModel,
                                onBack = { appViewModel.navigateTo(AppScreen.DASHBOARD) }
                            )
                            
                            AppScreen.AI_ASSISTANT -> AiAssistantScreen(
                                viewModel = appViewModel,
                                onBack = { appViewModel.navigateTo(AppScreen.DASHBOARD) }
                            )

                            AppScreen.LICENSE -> LicenseScreen(
                                viewModel = appViewModel,
                                onBack = { appViewModel.navigateTo(AppScreen.DASHBOARD) }
                            )

                            AppScreen.OPERATIONS -> com.example.ui.screens.OperationsScreen(
                                viewModel = appViewModel,
                                onNavigate = { target -> appViewModel.navigateTo(target) },
                                onBack = { appViewModel.navigateTo(AppScreen.DASHBOARD) }
                            )

                            AppScreen.PARTNERS -> com.example.ui.screens.PartnersScreen(
                                viewModel = appViewModel,
                                onNavigate = { target -> appViewModel.navigateTo(target) }
                            )
                        }

                    }
                }
            }
        }
    }
}
