package dev.analysis.mcp.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Scans a repository directory for Java source files.
 *
 * <p>This class traverses the repository directory tree to collect all Java
 * source files (files with {@code .java} extension) for dependency analysis.</p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * CodebaseScanner scanner = new CodebaseScanner();
 * Path repoRoot = Path.of("/path/to/my-project");
 * List<Path> javaFiles = scanner.scan(repoRoot);
 * System.out.println("Found " + javaFiles.size() + " Java files");
 * }</pre>
 *
 * <h2>Example Output:</h2>
 * <p>Given a repository structure:</p>
 * <pre>
 * /path/to/my-project/
 *   ├── src/main/java/com/example/App.java
 *   ├── src/main/java/com/example/util/Helper.java
 *   └── src/test/java/com/example/AppTest.java
 * </pre>
 * <p>The scanner will return all three {@code .java} files sorted alphabetically.</p>
 */
public class CodebaseScanner {

    /**
     * Scans the repository root for all Java source files.
     *
     * <p>This method recursively walks the directory tree starting from the given
     * root path and collects all files ending with {@code .java}. The results are
     * sorted in natural order for consistent processing.</p>
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * Path repoRoot = Path.of("/home/user/projects/my-app");
     * List<Path> files = scanner.scan(repoRoot);
     * // Returns: [
     * //   /home/user/projects/my-app/src/main/java/App.java,
     * //   /home/user/projects/my-app/src/main/java/Service.java,
     * //   /home/user/projects/my-app/src/test/java/AppTest.java
     * // ]
     * }</pre>
     *
     * @param repoRoot the root directory of the repository to scan
     * @return a list of paths to Java source files, sorted alphabetically
     * @throws IOException if an I/O error occurs during file traversal
     */
    public List<Path> scan(Path repoRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(repoRoot)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .toList();
        }
    }
}
