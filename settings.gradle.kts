// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 BugeStudio Team

/*
 * SPDX-License-Identifier: GPL-3.0
 * Copyright (C) 2026 BugeStudio Team
 */

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "BugeAppManager"
include(":app")
