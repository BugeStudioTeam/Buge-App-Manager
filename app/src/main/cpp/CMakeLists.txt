#
# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 Buge Studio
#
cmake_minimum_required(VERSION 3.22.1)
project(bugeappmanager)

add_library(start SHARED libstart.c)
target_link_libraries(start log)
