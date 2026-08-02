package com.jammerbam.zomboid.sound;

import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** Incremental weighted voxel propagation. World access is supplied by the caller. */
public final class AcousticFieldSolver {
    public interface CostProvider {
        /** Returns positive attenuation, or infinity when the cell cannot be inspected. */
        double cost(BlockPos position);
    }

    private static final int[][] NEIGHBORS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0},
        {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };
    private static final double EPSILON = 0.0001D;

    private final BlockPos source;
    private final double initialStrength;
    private final double maximumDistanceSq;
    private final int maximumNodes;
    private final Map<Long, Double> strengths = new HashMap<>();
    private final Set<Long> finalized = new HashSet<>();
    private final Map<Long, Double> newlyReached = new HashMap<>();
    private final PriorityQueue<Node> frontier = new PriorityQueue<>(
        Comparator.comparingDouble((Node node) -> node.strength).reversed()
    );
    private int processedNodes;
    private boolean complete;

    public AcousticFieldSolver(BlockPos source, double initialStrength, int maximumNodes) {
        this.source = source.toImmutable();
        this.initialStrength = Math.max(0.0D, initialStrength);
        this.maximumDistanceSq = this.initialStrength * this.initialStrength;
        this.maximumNodes = Math.max(1, maximumNodes);
        strengths.put(this.source.toLong(), this.initialStrength);
        frontier.add(new Node(this.source, this.initialStrength));
    }

    public int step(int budget, CostProvider costs) {
        int processedThisStep = 0;
        Node node = pollValidNode();
        if (node == null || processedNodes >= maximumNodes) {
            complete = true;
            return 0;
        }

        // One call advances one attenuation layer. This turns the weighted search into a
        // paced wavefront instead of allowing a large CPU budget to fill the whole field
        // in a single tick.
        double waveStrength = node.strength;
        while (!complete && node != null && processedThisStep < Math.max(1, budget)) {
            if (node.strength + EPSILON < waveStrength) {
                frontier.add(node);
                break;
            }

            long nodeKey = node.position.toLong();
            finalized.add(nodeKey);
            newlyReached.put(nodeKey, node.strength);
            processedNodes++;
            processedThisStep++;
            for (int[] offset : NEIGHBORS) {
                BlockPos neighbor = node.position.add(offset[0], offset[1], offset[2]);
                if (source.distanceSq(neighbor) > maximumDistanceSq) {
                    continue;
                }
                double attenuation = costs.cost(neighbor);
                if (!Double.isFinite(attenuation) || attenuation <= 0.0D) {
                    continue;
                }
                double remaining = node.strength - attenuation;
                if (remaining <= 0.0D) {
                    continue;
                }
                long key = neighbor.toLong();
                Double previous = strengths.get(key);
                if (previous == null || remaining > previous + EPSILON) {
                    if (previous == null && strengths.size() >= maximumNodes) {
                        continue;
                    }
                    strengths.put(key, remaining);
                    frontier.add(new Node(neighbor, remaining));
                }
            }

            if (processedNodes >= maximumNodes) {
                complete = true;
                break;
            }
            if (processedThisStep < Math.max(1, budget)) {
                node = pollValidNode();
            }
        }
        if (frontier.isEmpty() || processedNodes >= maximumNodes) {
            complete = true;
        }
        return processedThisStep;
    }

    public double getStrength(BlockPos position) {
        Double value = strengths.get(position.toLong());
        return value == null ? 0.0D : value;
    }

    public Map<Long, Double> getStrengths() {
        return Collections.unmodifiableMap(strengths);
    }

    /** Returns cells reached by the latest wave step and clears the arrival buffer. */
    public Map<Long, Double> drainNewlyReached() {
        if (newlyReached.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Double> reached = new HashMap<>(newlyReached);
        newlyReached.clear();
        return reached;
    }

    public BlockPos getSource() {
        return source;
    }

    public double getInitialStrength() {
        return initialStrength;
    }

    public int getProcessedNodes() {
        return processedNodes;
    }

    public int getFrontierSize() {
        return frontier.size();
    }

    public boolean isComplete() {
        return complete;
    }

    private Node pollValidNode() {
        Node node;
        while ((node = frontier.poll()) != null) {
            long key = node.position.toLong();
            Double bestKnown = strengths.get(key);
            if (!finalized.contains(key)
                && bestKnown != null
                && node.strength + EPSILON >= bestKnown) {
                return node;
            }
        }
        return null;
    }

    private static final class Node {
        private final BlockPos position;
        private final double strength;

        private Node(BlockPos position, double strength) {
            this.position = position;
            this.strength = strength;
        }
    }
}
