package com.jammerbam.zomboid.command;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ZomboidCommandTest {
    @Test
    public void exposesOneRootWithNestedCommandCompletion() {
        ZomboidCommand command = new ZomboidCommand();

        assertEquals("zomboid", command.getName());
        assertEquals("/zomboid <sound|population>", command.getUsage(null));

        List<String> roots = command.getTabCompletions(
            null, null, new String[]{""}, null
        );
        assertTrue(roots.contains("sound"));
        assertTrue(roots.contains("population"));

        List<String> population = command.getTabCompletions(
            null, null, new String[]{"population", ""}, null
        );
        assertTrue(population.contains("stats"));
        assertTrue(population.contains("regenerate"));

        List<String> sound = command.getTabCompletions(
            null, null, new String[]{"sound", ""}, null
        );
        assertTrue(sound.contains("status"));
        assertTrue(sound.contains("realisticSimulation"));
    }
}
