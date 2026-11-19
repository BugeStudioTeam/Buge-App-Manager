// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SignatureValidatorTest {

    @Test
    fun expectedFingerprintIsSha256() {
        assertThat(SignatureValidator.EXPECTED_SHA256).matches("[0-9A-F]{64}")
    }

    @Test
    fun expectedFingerprintIsUpperCase() {
        assertThat(SignatureValidator.EXPECTED_SHA256)
            .isEqualTo(SignatureValidator.EXPECTED_SHA256.uppercase())
    }

    @Test
    fun expectedFingerprintHasNoSeparators() {
        assertThat(SignatureValidator.EXPECTED_SHA256).doesNotContain(":")
    }
}
