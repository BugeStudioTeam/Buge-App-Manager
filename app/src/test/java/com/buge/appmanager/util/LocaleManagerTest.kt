// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocaleManagerTest {

    private val resourceLocales = listOf(
        "en", "fr", "de", "ru", "uk", "tr", "pt", "pt-rBR", "es", "zh", "zh-rTW", "ar", "ja", "ko"
    )

    @Test
    fun systemDefaultUsesEmptyCode() {
        assertThat(LocaleManager.getSupportedLanguages()).containsEntry("", "System Default")
    }

    @Test
    fun everyResourceLocaleIsSelectable() {
        assertThat(LocaleManager.getSupportedLanguages().keys)
            .containsAtLeastElementsIn(resourceLocales)
    }

    @Test
    fun languageCodesUseAndroidQualifierFormat() {
        LocaleManager.getSupportedLanguages().keys
            .filter { it.isNotEmpty() }
            .forEach { code ->
                assertThat(code).matches("[a-z]{2}(-r[A-Z]{2})?")
            }
    }

    @Test
    fun everyLabelIsPresent() {
        LocaleManager.getSupportedLanguages().forEach { (code, label) ->
            assertThat(label.trim()).isNotEmpty()
            assertThat(code.trim()).isEqualTo(code)
        }
    }
}
