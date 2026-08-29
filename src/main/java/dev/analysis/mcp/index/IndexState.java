package dev.analysis.mcp.index;

/** Lifecycle state of the active in-memory index, from absent through completion or failure. */
public enum IndexState {
    UNINDEXED,
    INDEXING,
    READY,
    FAILED
}
