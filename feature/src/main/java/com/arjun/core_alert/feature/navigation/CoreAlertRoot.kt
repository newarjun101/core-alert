package com.arjun.core_alert.feature.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.feature.home.HomeAction
import com.arjun.core_alert.feature.home.HomeScreen
import com.arjun.core_alert.feature.home.HomeViewModel
import com.arjun.core_alert.feature.privacy.PrivacyScreen
import com.arjun.core_alert.feature.settings.SettingsScreen
import com.arjun.core_alert.feature.settings.SettingsViewModel
import com.arjun.core_alert.ui.theme.coreAlertColors
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoreAlertRoot() {
    val home: HomeViewModel = koinViewModel()
    val settings: SettingsViewModel =
        koinViewModel { parametersOf({ home.reloadContacts() }) }
    val homeState by home.state.collectAsState()
    val settingsState by settings.state.collectAsState()

    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val colors = coreAlertColors()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = AppRoute.fromRoute(backStackEntry?.destination?.route)

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    LaunchedEffect(current) {
        if (current == AppRoute.HOME) home.handleAction(HomeAction.Refresh)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                selected = current,
                onSelected = { route ->
                    navController.navigate(route.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colors.bg,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(current.labelRes),
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.ink
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_menu),
                                contentDescription = stringResource(R.string.nav_home),
                                tint = colors.ink
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.bg,
                        titleContentColor = colors.ink,
                        navigationIconContentColor = colors.ink
                    )
                )
            },
            floatingActionButton = {
                if (current == AppRoute.HOME) {
                    FloatingActionButton(
                        onClick = { home.handleAction(HomeAction.OpenAddChoice) },
                        containerColor = colors.accent,
                        contentColor = colorResource(R.color.on_primary),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_plus),
                            contentDescription = stringResource(R.string.add_contact)
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = AppRoute.HOME.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                composable(AppRoute.HOME.route) { HomeScreen(homeState, home::handleAction) }
                composable(AppRoute.SETTINGS.route) { SettingsScreen(settingsState, settings::handleAction) }
                composable(AppRoute.PRIVACY.route) { PrivacyScreen() }
            }
        }
    }
}

@Composable
private fun AppDrawer(selected: AppRoute, onSelected: (AppRoute) -> Unit) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        windowInsets = WindowInsets(0)
    ) {
        DrawerHeader()
        AppRoute.entries.forEach { route ->
            DrawerItem(
                label = stringResource(route.labelRes),
                icon = route.iconRes,
                selected = route == selected,
                onClick = { onSelected(route) }
            )
        }
    }
}

@Composable
private fun DrawerHeader() {
    val colors = coreAlertColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 22.dp, top = 20.dp, end = 22.dp, bottom = 22.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(MaterialTheme.colorScheme.primary.copy(0.2f), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_bell),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, //colors.heroOn,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.padding(top = 16.dp),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.subtitle),
            modifier = Modifier.padding(top = 4.dp),
            color = colors.heroFaded,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun DrawerItem(label: String, icon: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = coreAlertColors()
    NavigationDrawerItem(
        label = {
            Text(text = label, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
        },
        icon = { Icon(painter = painterResource(icon), contentDescription = null) },
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.primary,
            unselectedContainerColor = Color.Transparent,
            unselectedTextColor = colors.inkMuted,
            unselectedIconColor = colors.inkMuted
        ),
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}
