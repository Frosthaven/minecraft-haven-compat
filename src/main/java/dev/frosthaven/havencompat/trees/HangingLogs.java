package dev.frosthaven.havencompat.trees;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * The logs Create's tree cutter misses on Ars Nouveau trees. Create 6.0.10 {@code TreeCutter.findTree} follows logs
 * sideways and up only (roots apart). Ars' {@code MagicTrunkPlacer.addBranch} ends each branch with a log one block
 * under the branch tip ({@code d3 above 1} under {@code d3 above 2}), which nothing reaches from the side or from
 * below: it stayed in the air with the leaves it kept alive, one per branch on every harvest.
 */
public final class HangingLogs {
    private HangingLogs() {}

    /**
     * @param logs the logs the cutter found
     * @param below the position one block down
     * @param hangs true for a position above the cut level that holds an Ars log with no log under it
     * @return the positions right under a found log that hang, each once
     */
    public static <P> List<P> under(List<P> logs, UnaryOperator<P> below, Predicate<P> hangs) {
        Set<P> found = new HashSet<>(logs);
        List<P> missed = new ArrayList<>();
        for (P log : logs) {
            P under = below.apply(log);
            if (found.add(under) && hangs.test(under)) {
                missed.add(under);
            }
        }
        return missed;
    }
}
