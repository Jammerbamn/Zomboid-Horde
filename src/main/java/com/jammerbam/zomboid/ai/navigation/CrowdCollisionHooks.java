package com.jammerbam.zomboid.ai.navigation;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Fast path injected ahead of World#getCollisionBoxes for managed pursuit cohorts. */
public final class CrowdCollisionHooks {
    private static final Logger LOGGER = LogManager.getLogger("zomboid-collision");
    private static final Method BLOCK_COLLISION_METHOD = findBlockCollisionMethod();
    private static boolean disabled;
    private static boolean failureLogged;

    private CrowdCollisionHooks() {
    }

    /** Returns null when vanilla must handle the complete query. */
    @Nullable
    public static List<AxisAlignedBB> tryGetCollisionBoxes(World world, Entity entity,
                                                           AxisAlignedBB query) {
        if (disabled || BLOCK_COLLISION_METHOD == null
            || !CrowdNavigationManager.canOptimizeCollisionQuery(entity)) {
            return null;
        }
        List<AxisAlignedBB> boxes = new ArrayList<>();
        try {
            BLOCK_COLLISION_METHOD.invoke(world, entity, query, false, boxes);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            disabled = true;
            logFailure(exception);
            return null;
        }
        CrowdNavigationManager.appendOptimizedEntityCollisions(entity, query, boxes);
        MinecraftForge.EVENT_BUS.post(new GetCollisionBoxesEvent(world, entity, query, boxes));
        return boxes;
    }

    @Nullable
    private static Method findBlockCollisionMethod() {
        for (Method method : World.class.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (method.getReturnType() == boolean.class && parameters.length == 4
                && parameters[0] == Entity.class && parameters[1] == AxisAlignedBB.class
                && parameters[2] == boolean.class && parameters[3] == List.class) {
                try {
                    method.setAccessible(true);
                    return method;
                } catch (RuntimeException exception) {
                    logFailure(exception);
                    return null;
                }
            }
        }
        logFailure(new NoSuchMethodException("World block-only collision query"));
        return null;
    }

    private static void logFailure(Exception exception) {
        if (!failureLogged) {
            failureLogged = true;
            LOGGER.warn("Cohort collision-query optimization is unavailable; vanilla collision "
                + "queries will remain active.", exception);
        }
    }
}
