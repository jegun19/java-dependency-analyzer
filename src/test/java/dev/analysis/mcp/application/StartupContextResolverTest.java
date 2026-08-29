package dev.analysis.mcp.application;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class StartupContextResolverTest {

    @Test
    void resolvesAStableServiceContextForValidRepoPath(@TempDir Path tempDir) {
        var context = StartupContextResolver.resolve(new String[] {"--repoPath=" + tempDir});

        assertEquals(tempDir.toAbsolutePath().normalize(), context.rootPath());
        assertEquals(context.id(), StartupContextResolver.resolve(new String[] {tempDir.toString()}).id());
    }

    @Test
    void rejectsMissingOrUnreadableRepositoryPath(@TempDir Path tempDir) {
        assertThrows(IllegalArgumentException.class, () -> StartupContextResolver.resolve(new String[0]));
        assertThrows(IllegalArgumentException.class,
                () -> StartupContextResolver.resolve(new String[] {"--repoPath=" + tempDir.resolve("missing")}));
    }
}
