// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AboutUsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "1.0"

        setContent {
            AboutUsTheme {
                AboutUsScreen(
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

private val Ink = Color(0xFF20201D)
private val Paper = Color(0xFFF4F1EA)
private val Clay = Color(0xFFC66345)
private val Moss = Color(0xFF4F6653)
private val Sand = Color(0xFFE4D6BF)
private val MutedInk = Color(0xFF77736B)

@Composable
private fun AboutUsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Clay,
            onPrimary = Color.White,
            secondary = Moss,
            onSecondary = Color.White,
            tertiary = Sand,
            background = Paper,
            onBackground = Ink,
            surface = Paper,
            onSurface = Ink,
            surfaceVariant = Sand,
            onSurfaceVariant = MutedInk,
            outline = Color(0xFFB9B0A2),
        ),
        typography = androidx.compose.material3.Typography(
            displayLarge = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                fontSize = 66.sp,
                lineHeight = 62.sp,
                letterSpacing = (-2.5).sp,
            ),
            headlineSmall = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                fontSize = 28.sp,
                lineHeight = 32.sp,
                letterSpacing = (-0.5).sp,
            ),
            bodyLarge = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 18.sp,
                lineHeight = 27.sp,
            ),
            labelSmall = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.8.sp,
            ),
        ),
        content = content,
    )
}

@Composable
private fun AboutUsScreen(
    versionName: String,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
        ) {
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "BUge / studio",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "CLOSE",
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(54.dp))

            Text(
                text = "ABOUT\nUS",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "A small studio making useful things\nwith a little more intention.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(38.dp))
            EditorialRule()
            Spacer(Modifier.height(30.dp))

            Text(
                text = "A NOTE FROM THE DESK",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = "We believe software can feel calm,\nclear, and quietly delightful.",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(Modifier.height(46.dp))

            Text(
                text = "FIND US IN THE WILD",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )

            Spacer(Modifier.height(12.dp))

            LinkRow("GITHUB", "BugeStudioTeam", "https://github.com/BugeStudioTeam", onOpenUrl)
            LinkRow("TELEGRAM", "@bugestudio", "https://t.me/bugestudio", onOpenUrl)
            LinkRow("WEBSITE", "bugestudio.website", "https://bugestudio.website/", onOpenUrl)
            LinkRow("ACTIVITYMANAGER", "github.com/sdex/ActivityManager", "https://github.com/sdex/ActivityManager", onOpenUrl)

            Spacer(Modifier.height(34.dp))
            EditorialRule()
            Spacer(Modifier.height(18.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "VERSION $versionName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "© 2026",
                    modifier = Modifier.align(Alignment.CenterEnd),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun EditorialRule() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(3.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
    }
}

@Composable
private fun LinkRow(
    category: String,
    value: String,
    url: String,
    onOpenUrl: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onOpenUrl(url) },
            )
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = category,
            modifier = Modifier.width(126.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
