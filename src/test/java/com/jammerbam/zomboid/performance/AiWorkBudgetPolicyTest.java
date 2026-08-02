package com.jammerbam.zomboid.performance;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AiWorkBudgetPolicyTest {
    @Test
    public void scalesPathWorkAcrossTpsTiers() {
        assertEquals(8, AiWorkBudgetPolicy.pursuitPathCalculations(20.0D, 1, 8));
        assertEquals(6, AiWorkBudgetPolicy.pursuitPathCalculations(19.0D, 1, 8));
        assertEquals(4, AiWorkBudgetPolicy.pursuitPathCalculations(17.0D, 1, 8));
        assertEquals(2, AiWorkBudgetPolicy.pursuitPathCalculations(10.0D, 1, 8));
    }

    @Test
    public void respectsConfiguredMinimumAndMaximum() {
        assertEquals(3, AiWorkBudgetPolicy.pursuitPathCalculations(10.0D, 3, 8));
        assertEquals(4, AiWorkBudgetPolicy.pursuitPathCalculations(20.0D, 9, 4));
    }
}
