/* Fixed test-only child tracing probe; no input, output, network or user code. */
#include <errno.h>
#include <signal.h>
#include <sys/prctl.h>
#include <sys/ptrace.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

static volatile sig_atomic_t child_pid = -1;
static void deadline(int signal_number) {
    (void) signal_number;
    if (child_pid > 0) kill(child_pid, SIGKILL);
}
static int wait_child(pid_t child, int *status) {
    pid_t result;
    do { result = waitpid(child, status, 0); } while (result < 0 && errno == EINTR);
    return result == child;
}
int main(void) {
    signal(SIGALRM, deadline);
    alarm(4);
    pid_t child = fork();
    if (child < 0) return 10;
    if (child == 0) {
        if (prctl(PR_SET_PDEATHSIG, SIGKILL) != 0) _exit(11);
        if (ptrace(PTRACE_TRACEME, 0, 0, 0) != 0) _exit(12);
        raise(SIGSTOP);
        (void)getpid();
        _exit(0);
    }
    child_pid = child;
    int status;
    int result = 13;
    if (!wait_child(child, &status) || !WIFSTOPPED(status)) goto cleanup;
    if (ptrace(PTRACE_SYSCALL, child, 0, 0) != 0) { result = 14; goto cleanup; }
    if (!wait_child(child, &status) || !WIFSTOPPED(status)) { result = 15; goto cleanup; }
    if (ptrace(PTRACE_CONT, child, 0, 0) != 0) { result = 16; goto cleanup; }
    if (!wait_child(child, &status) || !WIFEXITED(status) || WEXITSTATUS(status) != 0) { result = 17; goto cleanup; }
    child_pid = -1;
    alarm(0);
    return 0;
cleanup:
    kill(child, SIGKILL);
    wait_child(child, &status);
    child_pid = -1;
    alarm(0);
    return result;
}
