package dev.analysis.mcp.index;

import dev.analysis.mcp.context.AnalysisContext;
import java.time.Instant;

/**
 * Immutable view of index lifecycle state suitable for MCP status responses.
 *
 * @param context context associated with the index
 * @param state current lifecycle state
 * @param revision number of successfully published index revisions
 * @param startedAt time the most recent indexing attempt began
 * @param completedAt time the most recent indexing attempt ended
 * @param statistics counts from the most recently completed successful index
 * @param lastError failure message from the most recent failed attempt, if any
 */
public record IndexStatus(
        AnalysisContext context,
        IndexState state,
        long revision,
        Instant startedAt,
        Instant completedAt,
        IndexStatistics statistics,
        String lastError) {

    /** Returns whether queries can use the currently published graph. */
    public boolean isReady() {
        return state == IndexState.READY;
    }
}
