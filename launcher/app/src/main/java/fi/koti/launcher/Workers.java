package fi.koti.launcher;

import android.os.Process;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Background executors at background priority. Threads otherwise inherit the UI
 * thread's urgent priority and compete as equals with the UI and render threads, which
 * on this TV's four 1 GHz cores costs animation frames while images decode.
 */
final class Workers {
    private Workers() {}

    static ExecutorService pool(String name, int threads) {
        ThreadFactory f = r -> {
            Thread t = new Thread(() -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                r.run();
            }, name);
            t.setDaemon(true);
            return t;
        };
        return threads == 1 ? Executors.newSingleThreadExecutor(f) : Executors.newFixedThreadPool(threads, f);
    }
}
