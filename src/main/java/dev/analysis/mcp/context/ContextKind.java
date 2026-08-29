package dev.analysis.mcp.context;

/**
 * The scope boundary used to isolate indexed source code.
 *
 * <p>The current application creates service contexts. Project and workspace values reserve the
 * vocabulary needed when indexes span modules or coordinate multiple services.</p>
 */
public enum ContextKind {
    SERVICE,
    PROJECT,
    WORKSPACE
}
