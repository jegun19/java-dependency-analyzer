package dev.analysis.mcp.index;

/**
 * Result of an explicit indexing request.
 *
 * @param status lifecycle status after the request
 * @param reused whether an existing ready index was returned without rebuilding
 * @param successful whether the requested operation completed successfully
 */
public record IndexOperationResult(IndexStatus status, boolean reused, boolean successful) {}
