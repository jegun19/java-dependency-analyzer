package dev.analysis.mcp.application;

import dev.analysis.mcp.constants.GeneralConstant;
import dev.analysis.mcp.context.AnalysisContext;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves startup arguments into the single analysis context supported by this release.
 *
 * <p>The resolver keeps argument parsing and filesystem validation separate from server startup.
 * Both the explicit {@code --repoPath=} form and the original positional-path form are supported.</p>
 */
public final class StartupContextResolver {

    private StartupContextResolver() {}

    /**
     * Resolves a service context and verifies that its root is a readable directory.
     *
     * @param args command-line arguments supplied to the server
     * @return the normalized service context represented by the arguments
     * @throws IllegalArgumentException if no repository path is supplied or the path is invalid
     */
    public static AnalysisContext resolve(String[] args) {
        Path root = resolveRepoRoot(args);
        if (!Files.isDirectory(root) || !Files.isReadable(root)) {
            throw new IllegalArgumentException("Repository path must be a readable directory: " + root);
        }
        return AnalysisContext.service(root);
    }

    /** Resolves the repository path from the supported command-line argument forms. */
    private static Path resolveRepoRoot(String[] args) {
        for (String arg : args) {
            if (arg.startsWith(GeneralConstant.ARG_REPO_PATH_PREFIX)) {
                String value = arg.substring(GeneralConstant.ARG_REPO_PATH_PREFIX.length());
                if (!value.isBlank()) {
                    return Path.of(value).toAbsolutePath().normalize();
                }
            }
        }
        if (args.length > 0 && !args[0].startsWith("--")) {
            return Path.of(args[0]).toAbsolutePath().normalize();
        }
        throw new IllegalArgumentException(GeneralConstant.USAGE_MESSAGE);
    }
}
