package com.jammerbam.zomboid.compat;

import com.jammerbam.zomboid.Zomboid;
import net.minecraft.entity.EntityLiving;
import net.minecraftforge.fml.common.Loader;

/** Optional, dependency-free integration with AI Improvements. */
public final class AiImprovementsCompatibility {
    public static final String MOD_ID = "aiimprovements";
    private static final String FIXED_LOOK_HELPER =
        "com.builtbroken.ai.improvements.FixedEntityLookHelper";

    private static boolean loaded;
    private static boolean loggedReplacement;

    private AiImprovementsCompatibility() {
    }

    public static void detect() {
        loaded = Loader.isModLoaded(MOD_ID);
        if (loaded) {
            Zomboid.logger.info(
                "AI Improvements detected. Zomboid will preserve its look-helper replacement "
                    + "and any user-selected look-task removals."
            );
        }
    }

    public static void observe(EntityLiving entity) {
        if (!loaded || loggedReplacement || entity.getLookHelper() == null) {
            return;
        }
        if (isReplacementHelperClassName(entity.getLookHelper().getClass().getName())) {
            loggedReplacement = true;
            Zomboid.logger.info(
                "AI Improvements' FixedEntityLookHelper is active on Zomboid zombies."
            );
        }
    }

    static boolean isReplacementHelperClassName(String className) {
        return FIXED_LOOK_HELPER.equals(className);
    }
}
