// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApplicationIdTest {

    @Test
    fun applicationIdIsStable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals("com.buge.appmanager", context.packageName)
    }
}
