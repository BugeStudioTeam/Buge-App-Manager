// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class AboutUsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "1.0"

        setContent {
            AboutUsTheme {
                AboutUsExpressiveScreen(
                    versionName = versionName,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onOpenUrl = ::openUrl,
                )
            }
        }
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}

private val SeedBlue = Color(0xFF4F5D92)
private val SeedOrange = Color(0xFF8F4B39)

@Composable
private fun AboutUsTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> darkColorScheme(
            primary = Color(0xFFB9C4FF),
            onPrimary = Color(0xFF14295D),
            primaryContainer = Color(0xFF354778),
            onPrimaryContainer = Color(0xFFDDE2FF),
            secondary = Color(0xFFE7BDB0),
            onSecondary = Color(0xFF44291F),
            secondaryContainer = Color(0xFF5D4036),
            onSecondaryContainer = Color(0xFFFFDBCF),
        )
        else -> lightColorScheme(
            primary = SeedBlue,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDDE2FF),
            onPrimaryContainer = Color(0xFF06164A),
            secondary = SeedOrange,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFFFDBCF),
            onSecondaryContainer = Color(0xFF351108),
            tertiary = Color(0xFF006874),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFF97F0FF),
            onTertiaryContainer = Color(0xFF001F24),
        )
    }

    MaterialTheme(
        colorScheme = colors,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(36.dp),
        ),
        typography = androidx.compose.material3.Typography().run {
            copy(
                displayLarge = displayLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp),
                headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
                headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
                titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
                labelLarge = labelLarge.copy(fontWeight = FontWeight.Bold),
            )
        },
        content = content,
    )
}

private data class StudioLink(
    val title: String,
    val description: String,
    val url: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val links = listOf(
    StudioLink("GitHub", "Open-source projects and code", "https://github.com/BugeStudioTeam", Icons.Rounded.Code),
    StudioLink("Telegram", "News, releases, and conversation", "https://t.me/bugestudio", Icons.Rounded.Send),
    StudioLink("Website", "The studio, in its natural habitat", "https://bugestudio.website/", Icons.Rounded.Language),
    StudioLink("ActivityManager", "A project we are proud to support", "https://github.com/sdex/ActivityManager", Icons.Rounded.MenuBook),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutUsExpressiveScreen(
    versionName: String,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    val filters = listOf("All", "Projects", "Community")

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            androidx.compose.material3.LargeTopAppBar(
                title = {
                    Column {
                        Text("About us")
                        Text(
                            "Buge Studio · $versionName",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    AssistChip(
                        onClick = {
                            scope.launch { snackbarHostState.showSnackbar("You are already in the studio") }
                        },
                        label = { Text("Inside") },
                        leadingIcon = {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, Modifier.size(18.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            leadingIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch { snackbarHostState.showSnackbar("Choose a place to start a conversation") }
                },
                shape = MaterialTheme.shapes.large,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
            ) {
                Icon(Icons.Rounded.Send, contentDescription = "Start a conversation")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            val wide = maxWidth >= 600.dp
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (wide) Modifier.padding(horizontal = 24.dp) else Modifier),
                contentPadding = PaddingValues(
                    start = if (wide) 0.dp else 16.dp,
                    end = if (wide) 0.dp else 16.dp,
                    top = 12.dp,
                    bottom = 112.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    HeroCard(
                        onExplore = {
                            scope.launch { snackbarHostState.showSnackbar("The studio is small, curious, and open") }
                        },
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        filters.forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter) },
                                leadingIcon = if (selectedFilter == filter) {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(18.dp)) }
                                } else null,
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            )
                        }
                    }
                }
                item {
                    Text(
                        "Find us in the wild",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(links.filter { selectedFilter == "All" || (selectedFilter == "Projects" && it.title == "GitHub" || selectedFilter == "Community" && it.title == "Telegram") }) { link ->
                    LinkCard(link = link, onClick = { onOpenUrl(link.url) })
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Made with intention.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("© 2026", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(onExplore: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text("BUge /\nstudio", style = MaterialTheme.typography.displaySmall)
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 10.dp, bottomEnd = 28.dp, bottomStart = 10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    tonalElevation = 2.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Useful things, made with a little more intention.",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onExplore,
                shape = MaterialTheme.shapes.extraLarge,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text("Explore the studio")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.ArrowOutward, contentDescription = null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun LinkCard(link: StudioLink, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "expressiveCardScale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (pressed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        label = "expressiveCardColor",
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        onClick = {
            pressed = true
            onClick()
            pressed = false
        },
        shape = RoundedCornerShape(24.dp, 24.dp, 12.dp, 24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp, 24.dp, 16.dp, 24.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(link.icon, contentDescription = null)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(link.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    link.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.ArrowOutward, contentDescription = "Open ${link.title}")
        }
    }
}
