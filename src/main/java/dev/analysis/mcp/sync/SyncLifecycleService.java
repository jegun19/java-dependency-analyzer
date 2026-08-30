package dev.analysis.mcp.sync;

import dev.analysis.mcp.index.IndexOperationResult;
import dev.analysis.mcp.index.InMemoryIndexLifecycleService;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates debounced, non-blocking full rebuilds for one service root. */
public class SyncLifecycleService {
    private static final long INITIAL_RETRY_DELAY_MS = 1_000;
    private static final long MAX_RETRY_DELAY_MS = 30_000;
    private static final Logger log = LoggerFactory.getLogger(SyncLifecycleService.class);

    private final InMemoryIndexLifecycleService indexLifecycle;
    private final ConcurrentHashMap<Path, Long> dirtyPaths = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor;
    private final java.util.concurrent.atomic.AtomicLong sequence = new java.util.concurrent.atomic.AtomicLong();
    private boolean fullSyncRequested;
    private boolean scheduled;
    private long retryDelayMs = INITIAL_RETRY_DELAY_MS;

    public SyncLifecycleService(InMemoryIndexLifecycleService indexLifecycle) {
        this.indexLifecycle = indexLifecycle;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "dependency-analyzer-sync");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Records a filesystem change. */
    public void markDirty(Path relativePath) {
        dirtyPaths.put(relativePath, sequence.incrementAndGet());
    }

    /** Requests a full rebuild because watcher events may have been lost. */
    public synchronized void requestFullSync() {
        fullSyncRequested = true;
    }

    /** Schedules a full rebuild of the current pending changes. */
    public synchronized void triggerSync() {
        if (!fullSyncRequested && dirtyPaths.isEmpty()) return;
        schedule(0);
    }

    /** Forces a full rebuild even when no watcher event is pending. */
    public synchronized void forceSync() {
        fullSyncRequested = true;
        schedule(0);
    }

    private synchronized void schedule(long delayMs) {
        if (scheduled) return;
        scheduled = true;
        executor.schedule(this::runSync, delayMs, TimeUnit.MILLISECONDS);
    }

    private void runSync() {
        Map<Path, Long> paths;
        synchronized (this) {
            scheduled = false;
            paths = new HashMap<>(dirtyPaths);
            fullSyncRequested = false;
        }
        IndexOperationResult result = indexLifecycle.index(true);
        if (result.successful()) {
            paths.forEach((path, version) -> dirtyPaths.remove(path, version));
            synchronized (this) {
                retryDelayMs = INITIAL_RETRY_DELAY_MS;
            }
            log.info("Full synchronization completed for {} files", paths.size());
            triggerSync();
        } else {
            synchronized (this) {
                fullSyncRequested = true;
                long delay = retryDelayMs;
                schedule(delay);
                retryDelayMs = Math.min(delay * 2, MAX_RETRY_DELAY_MS);
                log.warn("Synchronization failed; retry scheduled in {} ms", delay);
            }
        }
    }

    public boolean hasPendingUpdates() {
        return fullSyncRequested || !dirtyPaths.isEmpty();
    }

    /** Shuts down synchronization and cancels pending retries. */
    public void shutdown() {
        executor.shutdownNow();
    }
}
