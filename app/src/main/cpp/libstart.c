/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Buge Studio
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

static const char *APP_PROCESS = "/system/bin/app_process";
static const char *DEFAULT_CLASS = "com.buge.appmanager.adb.AdbShellService";
static const char *LOG_FILE = "/data/local/tmp/adb_shell_service.log";

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
        if (setsid() < 0) {
            _exit(1);
        }

        int log_fd = open(LOG_FILE, O_WRONLY | O_CREAT | O_APPEND, 0666);
        int null_fd = open("/dev/null", O_RDONLY);

        if (null_fd >= 0) {
            dup2(null_fd, STDIN_FILENO);
            close(null_fd);
        }

        if (log_fd >= 0) {
            dup2(log_fd, STDOUT_FILENO);
            dup2(log_fd, STDERR_FILENO);

            if (log_fd > STDERR_FILENO) {
                close(log_fd);
            }
        }

        if (setenv("CLASSPATH", apk, 1) != 0) {
            dprintf(STDERR_FILENO, "libstart: failed to set CLASSPATH\n");
            _exit(1);
        }

        char *const cmd[] = {
            (char *) APP_PROCESS,
            "/system/bin",
            "--nice-name=adb_shell_service",
            klass,
            NULL
        };

        execv(APP_PROCESS, cmd);

        dprintf(STDERR_FILENO, "libstart: execv failed\n");
        _exit(127);
    }

    printf("libstart: service process launched, pid=%d\n", pid);
    return 0;
}
