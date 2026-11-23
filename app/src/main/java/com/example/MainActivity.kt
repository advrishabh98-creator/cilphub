package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.data.ai.ClipAiAnalyzer
import com.example.data.repository.ClipPulseRepository
import com.example.firebase.attemptAutoSignIn
import com.example.firebase.authStateFlow
import com.example.firebase.configureAppCheck
import com.example.firebase.signOutUser
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ClipStudioScreen
import com.example.ui.screens.ClipsFeedScreen
import com.example.ui.screens.NewScanSheet
import com.example.ui.screens.ScanInputMode
import com.example.ui.screens.StreamsHistoryScreen
import com.example.ui.theme.ClipPulseTheme
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.viewmodel.ClipPulseViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mandatory: Configure Firebase App Check with launch intent before Compose UI or ViewModels
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            ClipPulseTheme {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

private enum class StudioTab(val labelRes: Int) {
    HIGHLIGHTS(R.string.tab_highlights),
    STREAMS(R.string.tab_streams)
}

@Composable
fun AppNavigation() {
    val auth = Firebase.auth
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedStudioRoot(
                user = user,
                onSignOut = {
                    signOutUser(credentialManager = credentialManager, scope = scope)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedStudioRoot(
    user: FirebaseUser,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext

    val viewModel: ClipPulseViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ClipPulseViewModel(
                    repository = ClipPulseRepository(db),
                    aiAnalyzer = ClipAiAnalyzer(),
                    currentUserId = user.uid
                )
            }
        }
    )

    val navController = rememberNavController()
    var activeTab by rememberSaveable { mutableStateOf(StudioTab.HIGHLIGHTS) }
    var showScanSheet by rememberSaveable { mutableStateOf(false) }
    var initialScanMode by rememberSaveable { mutableStateOf(ScanInputMode.VOD_VIDEO) }
    var showAccountMenu by remember { mutableStateOf(false) }

    val clipsState by viewModel.filteredClipsState.collectAsStateWithLifecycle()
    val allClips by viewModel.allClips.collectAsStateWithLifecycle()
    val streamsState by viewModel.streamsState.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    NavHost(
        navController = navController,
        startDestination = "studio_home"
    ) {
        composable("studio_home") {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavRail = maxWidth >= 600.dp

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            },
                            actions = {
                                Box {
                                    IconButton(
                                        onClick = { showAccountMenu = true },
                                        modifier = Modifier.testTag("account_menu_button")
                                    ) {
                                        val photoUri = user.photoUrl
                                        if (photoUri != null) {
                                            AsyncImage(
                                                model = photoUri,
                                                contentDescription = "Account menu",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                            )
                                        } else {
                                            Surface(
                                                color = ElectricViolet,
                                                shape = CircleShape,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = (user.displayName?.firstOrNull()
                                                            ?: user.email?.firstOrNull()
                                                            ?: 'C').uppercase(),
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showAccountMenu,
                                        onDismissRequest = { showAccountMenu = false }
                                    ) {
                                        user.email?.let { email ->
                                            DropdownMenuItem(
                                                text = { Text(email, style = MaterialTheme.typography.bodySmall) },
                                                onClick = {}
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sign_out)) },
                                            leadingIcon = {
                                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                                            },
                                            onClick = {
                                                showAccountMenu = false
                                                onSignOut()
                                            }
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    },
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick = {
                                viewModel.resetScanState()
                                initialScanMode = ScanInputMode.VOD_VIDEO
                                showScanSheet = true
                            },
                            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            text = { Text(stringResource(R.string.action_new_scan)) },
                            containerColor = ElectricViolet,
                            contentColor = Color.White,
                            modifier = Modifier.testTag("fab_new_scan")
                        )
                    },
                    bottomBar = {
                        if (!useNavRail) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                StudioTab.entries.forEach { tab ->
                                    val selected = activeTab == tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { activeTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = when (tab) {
                                                    StudioTab.HIGHLIGHTS -> if (selected) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome
                                                    StudioTab.STREAMS -> if (selected) Icons.Filled.Movie else Icons.Outlined.Movie
                                                },
                                                contentDescription = stringResource(tab.labelRes)
                                            )
                                        },
                                        label = { Text(stringResource(tab.labelRes)) },
                                        modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useNavRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                StudioTab.entries.forEach { tab ->
                                    val selected = activeTab == tab
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { activeTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = when (tab) {
                                                    StudioTab.HIGHLIGHTS -> if (selected) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome
                                                    StudioTab.STREAMS -> if (selected) Icons.Filled.Movie else Icons.Outlined.Movie
                                                },
                                                contentDescription = stringResource(tab.labelRes)
                                            )
                                        },
                                        label = { Text(stringResource(tab.labelRes)) }
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            when (activeTab) {
                                StudioTab.HIGHLIGHTS -> {
                                    ClipsFeedScreen(
                                        clipsState = clipsState,
                                        allClips = allClips,
                                        filterState = filterState,
                                        onSearchChanged = viewModel::updateSearchQuery,
                                        onSelectCategory = viewModel::selectCategoryFilter,
                                        onToggleStarredOnly = viewModel::toggleStarredOnly,
                                        onToggleSortOrder = viewModel::toggleSortOrder,
                                        onClearStreamFilter = { viewModel.filterByStream(null) },
                                        onOpenClip = { clipId ->
                                            navController.navigate("clip_studio/$clipId")
                                        },
                                        onToggleStar = viewModel::toggleClipStarred,
                                        onLaunchScan = { mode ->
                                            viewModel.resetScanState()
                                            initialScanMode = mode
                                            showScanSheet = true
                                        }
                                    )
                                }
                                StudioTab.STREAMS -> {
                                    StreamsHistoryScreen(
                                        streamsState = streamsState,
                                        onOpenStreamClips = { streamId ->
                                            viewModel.filterByStream(streamId)
                                            activeTab = StudioTab.HIGHLIGHTS
                                        },
                                        onDeleteStream = viewModel::deleteStream
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        composable("clip_studio/{clipId}") { backStackEntry ->
            val clipId = backStackEntry.arguments?.getString("clipId").orEmpty()
            val clip = allClips.firstOrNull { it.id == clipId }
            val streamsList = (streamsState as? UiState.Success)?.data ?: emptyList()
            val parentStreamDuration = clip?.let { c ->
                streamsList.firstOrNull { it.id == c.streamId }?.durationSeconds?.toInt()
            } ?: 60

            if (clip == null) {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            } else {
                ClipStudioScreen(
                    clip = clip,
                    streamDurationSec = parentStreamDuration,
                    exportState = exportState,
                    onBack = { navController.popBackStack() },
                    onToggleStar = { viewModel.toggleClipStarred(clip) },
                    onSaveDetails = { title, hook, cat, start, end ->
                        viewModel.updateClipDetails(clip.id, title, hook, cat, start, end)
                    },
                    onExportMp4 = { start, end ->
                        viewModel.exportAndShareMp4(appContext, clip, start, end)
                    },
                    onResetExportState = viewModel::resetExportState,
                    onMarkSharedSummary = { viewModel.markClipSharedAsSummary(clip.id) },
                    onDeleteClip = {
                        viewModel.deleteClip(clip.id) {
                            navController.popBackStack()
                        }
                    }
                )
            }
        }
    }

    if (showScanSheet) {
        NewScanSheet(
            initialMode = initialScanMode,
            scanState = scanState,
            onDismiss = {
                showScanSheet = false
                viewModel.resetScanState()
            },
            onFetchTwitchMetadata = viewModel::fetchTwitchMetadata,
            onStartVideoScan = { uri, title, channel, extra ->
                viewModel.scanVideoStream(appContext, uri, title, channel, extra)
            },
            onStartChatScan = { title, channel, url, chat, mins ->
                viewModel.scanChatOrTranscript(title, channel, url, chat, mins)
            },
            onScanCompleted = {
                showScanSheet = false
                viewModel.resetScanState()
                viewModel.filterByStream(null)
                activeTab = StudioTab.HIGHLIGHTS
            }
        )
    }
}
