package dev.analysis.mcp.index;

/**
 * Counts produced by one completed full index.
 *
 * @param fileCount number of Java source files scanned
 * @param nodeCount number of graph vertices produced
 * @param edgeCount number of dependency relationships produced
 */
public record IndexStatistics(long fileCount, long nodeCount, long edgeCount) {}
