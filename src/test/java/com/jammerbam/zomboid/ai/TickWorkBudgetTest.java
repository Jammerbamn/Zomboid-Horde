package com.jammerbam.zomboid.ai;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TickWorkBudgetTest {
    @Test
    public void limitsWorkWithinOneTickAndResetsOnTheNext() {
        TickWorkBudget budget = new TickWorkBudget();

        assertTrue(budget.tryAcquire(100L, 2));
        assertTrue(budget.tryAcquire(100L, 2));
        assertFalse(budget.tryAcquire(100L, 2));
        assertTrue(budget.tryAcquire(101L, 2));
    }
}
