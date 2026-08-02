package com.jammerbam.zomboid.sound;

import net.minecraft.util.math.BlockPos;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AcousticFieldSolverTest {
    @Test
    public void eachStepAdvancesOneWaveLayer() {
        AcousticFieldSolver solver = new AcousticFieldSolver(
            BlockPos.ORIGIN, 10.0D, 10000
        );

        solver.step(10000, position -> 1.0D);
        Map<Long, Double> sourceWave = solver.drainNewlyReached();
        assertEquals(1, sourceWave.size());
        assertTrue(sourceWave.containsKey(BlockPos.ORIGIN.toLong()));

        solver.step(10000, position -> 1.0D);
        Map<Long, Double> firstShell = solver.drainNewlyReached();
        assertEquals(6, firstShell.size());
        assertFalse(firstShell.containsKey(new BlockPos(2, 0, 0).toLong()));

        solver.step(10000, position -> 1.0D);
        assertTrue(solver.drainNewlyReached().containsKey(
            new BlockPos(2, 0, 0).toLong()
        ));
    }

    @Test
    public void drainingWaveArrivalsClearsTheBuffer() {
        AcousticFieldSolver solver = new AcousticFieldSolver(
            BlockPos.ORIGIN, 10.0D, 10000
        );
        solver.step(10000, position -> 1.0D);

        assertFalse(solver.drainNewlyReached().isEmpty());
        assertTrue(solver.drainNewlyReached().isEmpty());
    }

    @Test
    public void openAirLosesOneStrengthPerBlock() {
        AcousticFieldSolver solver = new AcousticFieldSolver(
            BlockPos.ORIGIN, 10.0D, 10000
        );
        while (!solver.isComplete()) {
            solver.step(10000, position -> 1.0D);
        }

        assertEquals(6.0D, solver.getStrength(new BlockPos(4, 0, 0)), 0.0001D);
    }

    @Test
    public void fieldRoutesAroundAnExpensiveBlock() {
        BlockPos expensive = new BlockPos(2, 0, 0);
        AcousticFieldSolver solver = new AcousticFieldSolver(
            BlockPos.ORIGIN, 10.0D, 10000
        );
        while (!solver.isComplete()) {
            solver.step(10000, position -> position.equals(expensive) ? 6.0D : 1.0D);
        }

        // Going through the expensive block leaves strength 1. The solver instead bends
        // around it and reaches the listener with strength 4.
        assertEquals(4.0D, solver.getStrength(new BlockPos(4, 0, 0)), 0.0001D);
    }

    @Test
    public void nodeLimitIsARealHardCap() {
        AcousticFieldSolver solver = new AcousticFieldSolver(
            BlockPos.ORIGIN, 100.0D, 25
        );
        while (!solver.isComplete()) {
            solver.step(5, position -> 1.0D);
        }

        assertTrue(solver.getStrengths().size() <= 25);
        assertTrue(solver.getProcessedNodes() <= 25);
    }
}
