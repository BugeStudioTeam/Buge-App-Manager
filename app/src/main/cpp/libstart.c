/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Buge Studio
 */

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>

static const char *APP_PROCESS = "/system/bin/app_process";
static const char *DEFAULT_CLASS = "com.buge.appmanager.adb.AdbShellService";

static void die(const char *msg) {
    fprintf(stderr, "libstart: %s\n", msg);
    exit(1);
}

static char *find_arg(int argc, char **argv, const char *prefix) {
    size_t len = strlen(prefix);
    for (int i = 1; i < argc; i++) {
        if (strncmp(argv[i], prefix, len) == 0) {
            return argv[i] + len;
        }
    }
    return NULL;
}

int main(int argc, char **argv) {
    uid_t uid = getuid();
    if (uid != 2000 && uid != 0) {
        die("must run as shell (2000) or root (0)");
    }

    char *apk = find_arg(argc, argv, "--apk=");
    if (apk == NULL || strlen(apk) == 0) {
        die("missing --apk= parameter");
    }

    char *klass = find_arg(argc, argv, "--class=");
    if (klass == NULL || strlen(klass) == 0) {
        klass = (char *) DEFAULT_CLASS;
    }

    pid_t pid = fork();
    if (pid < 0) {
        die("fork failed");
    }

    if (pid == 0) {
        const char *cmd[] = {
            APP_PROCESS,
            "--zygote",
            "--nice-name=adb_shell_service",
            "--runtime-args",
            klass,
            "--apk",
            apk
        };

        execv(APP_PROCESS, (char *const *) cmd);
        fprintf(stderr, "libstart: execv failed\n");
        _exit(1);
    }

    int status = 0;
    waitpid(pid, &status, 0);
    return WIFEXITED(status) ? WEXITSTATUS(status) : 1;
}
