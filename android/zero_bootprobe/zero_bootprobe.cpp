// ZERO Native Mario v2.8.1 diagnostic sidecar.
// Runs in the SDL game process only. Never modifies/relinks libmain.so.
#include <jni.h>
#include <pthread.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <ucontext.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <memory>
#include <mutex>
#include <thread>
#include <chrono>
#include <string>

namespace {
constexpr int kProbeSignal = SIGUSR2;
static_assert(std::atomic<uintptr_t>::is_always_lock_free, "ARM64 lock-free address samples required");
std::atomic<uintptr_t> g_samplePc{0}, g_sampleSp{0}, g_sampleLr{0};
std::atomic<unsigned> g_sampleSeq{0};
std::mutex g_lifecycle;
std::condition_variable g_sleep;
std::atomic<bool> g_stop{false};
std::thread g_watcher;
struct sigaction g_previous{};
bool g_ownsSignal = false;
int g_output = -1;
pthread_t g_gameThread{};

// Signal handler: ONLY lock-free atomics. No I/O, locks, heap use or JNI.
void CapturePc(int, siginfo_t*, void* raw) {
#if defined(__aarch64__)
    const auto* u = static_cast<const ucontext_t*>(raw);
    if (u) {
        g_samplePc.store(static_cast<uintptr_t>(u->uc_mcontext.pc), std::memory_order_relaxed);
        g_sampleSp.store(static_cast<uintptr_t>(u->uc_mcontext.sp), std::memory_order_relaxed);
        g_sampleLr.store(static_cast<uintptr_t>(u->uc_mcontext.regs[30]), std::memory_order_relaxed);
    }
#else
    (void)raw;
#endif
    g_sampleSeq.fetch_add(1, std::memory_order_release);
}

void LogSample(int seconds) {
    if (g_output < 0 || !g_ownsSignal) return;
    g_samplePc.store(0, std::memory_order_relaxed);
    const unsigned before = g_sampleSeq.load(std::memory_order_acquire);
    const int sent = pthread_kill(g_gameThread, kProbeSignal);
    if (sent != 0) {
        dprintf(g_output, "[zero-bootprobe] t=%ds: pthread_kill returned %d\n", seconds, sent);
        return;
    }
    for (int i = 0; i < 20 && g_sampleSeq.load(std::memory_order_acquire) == before; ++i)
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
    if (g_sampleSeq.load(std::memory_order_acquire) == before) {
        dprintf(g_output, "[zero-bootprobe] t=%ds: native thread did not acknowledge signal\n", seconds);
        return;
    }
    const uintptr_t pc = g_samplePc.load(std::memory_order_relaxed);
    const uintptr_t sp = g_sampleSp.load(std::memory_order_relaxed);
    const uintptr_t lr = g_sampleLr.load(std::memory_order_relaxed);
    Dl_info info{};
    if (pc && dladdr(reinterpret_cast<void*>(pc), &info) && info.dli_fbase) {
        const auto base = reinterpret_cast<uintptr_t>(info.dli_fbase);
        const char* lib = info.dli_fname ? std::strrchr(info.dli_fname, '/') : nullptr;
        const char* shortName = lib ? lib + 1 : (info.dli_fname ? info.dli_fname : "unknown");
        dprintf(g_output, "[zero-bootprobe] t=%ds: module=%s offset=0x%llx pc=0x%llx sp=0x%llx lr=0x%llx\n",
                seconds, shortName,
                static_cast<unsigned long long>(pc - base),
                static_cast<unsigned long long>(pc),
                static_cast<unsigned long long>(sp),
                static_cast<unsigned long long>(lr));
    } else {
        dprintf(g_output, "[zero-bootprobe] t=%ds: module=unknown pc=0x%llx sp=0x%llx lr=0x%llx\n",
                seconds, static_cast<unsigned long long>(pc),
                static_cast<unsigned long long>(sp),
                static_cast<unsigned long long>(lr));
    }
}
} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_wither_nsmbw_ZeroBootProbe_nativeStart(JNIEnv* env, jclass, jstring outputPath) {
    std::lock_guard<std::mutex> guard(g_lifecycle);
    if (g_watcher.joinable() || !outputPath) return;
    const char* path = env->GetStringUTFChars(outputPath, nullptr);
    if (!path) return;
    g_output = open(path, O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0600);
    env->ReleaseStringUTFChars(outputPath, path);
    if (g_output < 0) return;

    dprintf(g_output, "[zero-bootprobe] v2.8.1 started: pid=%ld tid=%ld; library is a separate diagnostic sidecar\n",
            static_cast<long>(getpid()), static_cast<long>(syscall(SYS_gettid)));
    struct sigaction current{};
    if (sigaction(kProbeSignal, nullptr, &current) != 0 ||
        (current.sa_handler != SIG_DFL && current.sa_handler != SIG_IGN)) {
        dprintf(g_output, "[zero-bootprobe] SIGUSR2 already occupied; refusing to overwrite an existing handler\n");
        close(g_output); g_output = -1;
        return;
    }
    struct sigaction handler{};
    handler.sa_sigaction = &CapturePc;
    sigemptyset(&handler.sa_mask);
    handler.sa_flags = SA_SIGINFO | SA_RESTART;
    if (sigaction(kProbeSignal, &handler, &g_previous) != 0) {
        dprintf(g_output, "[zero-bootprobe] sigaction registration failed\n");
        close(g_output); g_output = -1;
        return;
    }
    g_ownsSignal = true;
    g_gameThread = pthread_self();
    g_stop.store(false, std::memory_order_release);
    g_watcher = std::thread([] {
        const auto t0 = std::chrono::steady_clock::now();
        for (int sec : {12, 30, 60}) {
            std::unique_lock<std::mutex> lk(g_lifecycle);
            if (g_sleep.wait_until(lk, t0 + std::chrono::seconds(sec), [] {
                return g_stop.load(std::memory_order_acquire);
            })) break;
            // No other calls to stop/start touch the signal handler until the lock is released.
            LogSample(sec);
        }
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_wither_nsmbw_ZeroBootProbe_nativeStop(JNIEnv*, jclass) {
    std::thread joiner;
    {
        std::lock_guard<std::mutex> guard(g_lifecycle);
        g_stop.store(true, std::memory_order_release);
        g_sleep.notify_all();
        if (g_watcher.joinable()) joiner = std::move(g_watcher);
    }
    if (joiner.joinable()) joiner.join();
    std::lock_guard<std::mutex> guard(g_lifecycle);
    if (g_ownsSignal) {
        sigaction(kProbeSignal, &g_previous, nullptr);
        g_ownsSignal = false;
    }
    if (g_output >= 0) {
        dprintf(g_output, "[zero-bootprobe] stopped\n");
        close(g_output);
        g_output = -1;
    }
}
