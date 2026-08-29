package dev.analysis.mcp.context;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * Identifies the source boundary for one analysis index.
 *
 * <p>This release creates exactly one {@link ContextKind#SERVICE} context. Keeping the
 * boundary explicit lets later workspace and persistence work add contexts without changing
 * the indexing lifecycle API.</p>
 */
public record AnalysisContext(
        String id,
        ContextKind kind,
        Path rootPath,
        String displayName,
        String parentContextId) {

    public AnalysisContext {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(rootPath, "rootPath");
        Objects.requireNonNull(displayName, "displayName");
        rootPath = rootPath.toAbsolutePath().normalize();
    }

    /**
     * Creates a service context whose identifier is stable for the same normalized root path.
     *
     * @param rootPath service source root
     * @return a normalized service context
     */
    public static AnalysisContext service(Path rootPath) {
        Path normalizedRoot = rootPath.toAbsolutePath().normalize();
        String id = "service-" + UUID.nameUUIDFromBytes(
                normalizedRoot.toString().getBytes(StandardCharsets.UTF_8));
        Path fileName = normalizedRoot.getFileName();
        String displayName = fileName == null ? normalizedRoot.toString() : fileName.toString();
        return new AnalysisContext(id, ContextKind.SERVICE, normalizedRoot, displayName, null);
    }
}
