/* Fixed test-only PRoot invocation under the isolated UID. No user input. */
#include <errno.h>
#include <limits.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

int main(void) {
    char directory[PATH_MAX], proot[PATH_MAX], loader[PATH_MAX], target[PATH_MAX];
    ssize_t n = readlink("/proc/self/exe", directory, sizeof(directory) - 1);
    if (n <= 0 || n >= (ssize_t)sizeof(directory) - 1) return 110;
    directory[n] = '\0';
    char *slash = strrchr(directory, '/');
    if (!slash) return 111;
    *slash = '\0';
    if (snprintf(proot, sizeof(proot), "%s/libproot.so", directory) >= (int)sizeof(proot) ||
        snprintf(loader, sizeof(loader), "%s/libproot-loader.so", directory) >= (int)sizeof(loader) ||
        snprintf(target, sizeof(target), "%s/libfixture-exec.so", directory) >= (int)sizeof(target)) return 112;
    pid_t parent = getpid();
    pid_t child = fork();
    if (child < 0) return 113;
    if (child == 0) {
        if (setsid() < 0 || prctl(PR_SET_PDEATHSIG, SIGKILL) != 0 || getppid() != parent) _exit(114);
        if (setenv("PROOT_LOADER", loader, 1) != 0 || setenv("PROOT_NO_SECCOMP", "1", 1) != 0) _exit(115);
        execl(proot, proot, "--kill-on-exit", "-r", "/", "--", target, (char *)NULL);
        _exit(116);
    }
    int status = 0;
    struct timespec pause = { .tv_sec = 0, .tv_nsec = 50000000L };
    for (int i = 0; i < 80; ++i) {
        pid_t observed = waitpid(child, &status, WNOHANG);
        if (observed == child) {
            if (WIFEXITED(status)) return WEXITSTATUS(status);
            return 117;
        }
        if (observed < 0 && errno != EINTR) break;
        nanosleep(&pause, NULL);
    }
    /* This fixed PRoot command and its child share this session/process group. */
    kill(-child, SIGKILL);
    kill(child, SIGKILL);
    while (waitpid(child, &status, 0) < 0 && errno == EINTR) {}
    return 124;
}
