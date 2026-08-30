package dev.analysis.mcp.sync;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.function.Consumer;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Watches one service root and debounces filesystem notifications. */
public class WatchServiceManager implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(WatchServiceManager.class);
    private final Path root;
    private final Consumer<Path> onChange;
    private final Runnable onQuiet;
    private final Runnable onOverflow;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "dependency-analyzer-watch");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService eventExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "dependency-analyzer-watch-events");
        thread.setDaemon(true);
        return thread;
    });
    private final String[] exclusionArray;
    private WatchService watchService;
    private volatile boolean running;
    private ScheduledFuture<?> debounceFuture;

    public WatchServiceManager(Path root, Consumer<Path> onChange, Runnable onQuiet, Runnable onOverflow, String exclusions) {
        this.root = root;
        this.onChange = onChange;
        this.onQuiet = onQuiet;
        this.onOverflow = onOverflow;
        this.exclusionArray = exclusions.split(",");
    }

    public WatchServiceManager(Path root, Consumer<Path> onChange, Runnable onQuiet, Runnable onOverflow) {
        this(root, onChange, onQuiet, onOverflow, dev.analysis.mcp.constants.GeneralConstant.WATCH_SERVICE_EXCLUSIONS);
    }

    public synchronized void start() throws IOException {
        if (running) return;
        watchService = FileSystems.getDefault().newWatchService();
        registerAll(root);
        running = true;
        eventExecutor.submit(this::processEvents);
        log.info("Watch service started for {}", root);
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        try {
            watchService.close();
        } catch (IOException exception) {
            log.warn("Error closing watch service: {}", exception.getMessage());
        }
        if (debounceFuture != null) debounceFuture.cancel(false);
        scheduler.shutdownNow();
        eventExecutor.shutdownNow();
    }

    private void eventReceived() {
        if (debounceFuture != null) debounceFuture.cancel(false);
        debounceFuture = scheduler.schedule(onQuiet,
                dev.analysis.mcp.constants.GeneralConstant.DEFAULT_DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void processEvents() {
        try {
            while (running) {
                WatchKey key = watchService.take();
                Path dir = (Path) key.watchable();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        onOverflow.run();
                        eventReceived();
                        continue;
                    }
                    Path child = dir.resolve((Path) event.context());
                    if (isExcluded(child)) continue;
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(child)) {
                        try {
                            registerAll(child);
                        } catch (IOException exception) {
                            log.warn("Failed to register new directory {}: {}", child, exception.getMessage());
                        }
                        onOverflow.run();
                        eventReceived();
                        continue;
                    }
                    if (kind == StandardWatchEventKinds.ENTRY_DELETE && !child.getFileName().toString().endsWith(".java")) {
                        onOverflow.run();
                        eventReceived();
                        continue;
                    }
                    if (child.getFileName().toString().endsWith(".java")) {
                        onChange.accept(root.relativize(child));
                        eventReceived();
                    }
                }
                if (!key.reset()) key.cancel();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void registerAll(Path start) throws IOException {
        try (var paths = Files.walk(start)) {
            paths.filter(Files::isDirectory).filter(p -> !isExcluded(p)).forEach(this::register);
        }
    }

    private void register(Path directory) {
        try {
            directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
        } catch (IOException exception) {
            log.warn("Failed to register {}: {}", directory, exception.getMessage());
        }
    }

    private boolean isExcluded(Path path) {
        String name = path.getFileName().toString();
        for (String exclusion : exclusionArray) if (name.equals(exclusion.trim())) return true;
        return false;
    }

    @Override
    public void close() {
        stop();
    }
}
