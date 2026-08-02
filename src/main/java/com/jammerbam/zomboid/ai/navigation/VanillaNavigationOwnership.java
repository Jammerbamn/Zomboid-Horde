package com.jammerbam.zomboid.ai.navigation;

import net.minecraft.pathfinding.PathNavigate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;

/** Clears vanilla navigator state that would otherwise rebuild after shared steering takes over. */
public final class VanillaNavigationOwnership {
    private static final Logger LOGGER = LogManager.getLogger("zomboid-navigation");
    private static final Field TRY_UPDATE_PATH = findField("tryUpdatePath", "field_188562_p");
    private static final Field TARGET_POS = findField("targetPos", "field_188564_r");
    private static boolean failureLogged;

    private VanillaNavigationOwnership() {
    }

    public static boolean release(PathNavigate navigator) {
        boolean hadActivePath = !navigator.noPath();
        navigator.clearPath();
        if (TRY_UPDATE_PATH == null || TARGET_POS == null) {
            return hadActivePath;
        }
        try {
            boolean cancelled = hadActivePath || TRY_UPDATE_PATH.getBoolean(navigator)
                || TARGET_POS.get(navigator) != null;
            TRY_UPDATE_PATH.setBoolean(navigator, false);
            TARGET_POS.set(navigator, null);
            return cancelled;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logFailure(exception);
            return false;
        }
    }

    public static boolean isAvailable() {
        return TRY_UPDATE_PATH != null && TARGET_POS != null;
    }

    private static Field findField(String mcpName, String srgName) {
        for (String name : new String[]{mcpName, srgName}) {
            try {
                Field field = PathNavigate.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the name used by the other runtime namespace.
            }
        }
        logFailure(new NoSuchFieldException(mcpName + '/' + srgName));
        return null;
    }

    private static void logFailure(Exception exception) {
        if (!failureLogged) {
            failureLogged = true;
            LOGGER.warn("Could not clear deferred vanilla navigator state; falling back to "
                + "clearPath only.", exception);
        }
    }
}
