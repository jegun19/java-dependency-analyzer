package dev.analysis.mcp.utils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodebaseScannerTest {

    private CodebaseScanner scanner;
    private static final Path FIXTURES = Path.of("src/test/resources/fixtures").toAbsolutePath();

    @BeforeEach
    void setUp() {
        scanner = new CodebaseScanner();
    }

    @Test
    void scan_findsJavaFilesInSubdirectories() throws IOException {
        List<Path> files = scanner.scan(FIXTURES);

        assertTrue(files.stream().anyMatch(p -> p.toString().contains("service")));
        assertTrue(files.stream().anyMatch(p -> p.toString().contains("model")));
        assertTrue(files.stream().anyMatch(p -> p.toString().contains("repository")));
    }

    @Test
    void scan_returnsSortedFiles() throws IOException {
        List<Path> files = scanner.scan(FIXTURES);

        for (int i = 1; i < files.size(); i++) {
            assertTrue(files.get(i - 1).compareTo(files.get(i)) <= 0,
                "Files should be sorted alphabetically");
        }
    }
}
