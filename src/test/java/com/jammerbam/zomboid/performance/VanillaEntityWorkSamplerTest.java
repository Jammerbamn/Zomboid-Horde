package com.jammerbam.zomboid.performance;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VanillaEntityWorkSamplerTest {
    @Test
    public void pathSearchTakesPriorityOverGeneralNavigation() {
        StackTraceElement[] stack = new StackTraceElement[]{
            frame("net.minecraft.pathfinding.PathFinder", "findPath"),
            frame("net.minecraft.pathfinding.PathNavigate", "getPathToEntityLiving"),
            frame("net.minecraft.entity.EntityLiving", "updateEntityActionState")
        };

        assertEquals(VanillaEntityWorkSampler.Category.PATH_SEARCH,
            VanillaEntityWorkSampler.classify(stack));
    }

    @Test
    public void classifiesChunkSnapshotPreparationBeforePathSearch() {
        StackTraceElement[] stack = new StackTraceElement[]{
            frame("net.minecraft.world.ChunkCache", "<init>"),
            frame("net.minecraft.pathfinding.PathNavigate", "getPathToPos"),
            frame("net.minecraft.entity.EntityLiving", "updateEntityActionState")
        };

        assertEquals(VanillaEntityWorkSampler.Category.PATH_SNAPSHOT,
            VanillaEntityWorkSampler.classify(stack));
    }

    @Test
    public void pathSearchTakesPriorityOverChunkSnapshotFrames() {
        StackTraceElement[] stack = new StackTraceElement[]{
            frame("net.minecraft.pathfinding.PathFinder", "findPath"),
            frame("net.minecraft.world.ChunkCache", "getBlockState"),
            frame("net.minecraft.pathfinding.PathNavigate", "getPathToPos")
        };

        assertEquals(VanillaEntityWorkSampler.Category.PATH_SEARCH,
            VanillaEntityWorkSampler.classify(stack));
    }

    @Test
    public void classifiesNavigationAdvancement() {
        StackTraceElement[] stack = new StackTraceElement[]{
            frame("net.minecraft.pathfinding.PathNavigateGround", "onUpdateNavigation"),
            frame("net.minecraft.entity.EntityLiving", "updateEntityActionState")
        };

        assertEquals(VanillaEntityWorkSampler.Category.NAVIGATION,
            VanillaEntityWorkSampler.classify(stack));
    }

    @Test
    public void blockCollisionQueryBeatsOuterMovementFrames() {
        StackTraceElement[] stack = new StackTraceElement[]{
            frame("net.minecraft.world.World", "getCollisionBoxes"),
            frame("net.minecraft.entity.Entity", "move"),
            frame("net.minecraft.entity.EntityLivingBase", "travel")
        };

        assertEquals(VanillaEntityWorkSampler.Category.BLOCK_COLLISION_QUERY,
            VanillaEntityWorkSampler.classify(stack));
    }

    @Test
    public void aabbResolutionBeatsEntityMove() {
        StackTraceElement[] stack = new StackTraceElement[]{
            frame("net.minecraft.util.math.AxisAlignedBB", "calculateXOffset"),
            frame("net.minecraft.entity.Entity", "move")
        };

        assertEquals(VanillaEntityWorkSampler.Category.AABB_RESOLUTION,
            VanillaEntityWorkSampler.classify(stack));
    }

    @Test
    public void classifiesEntityMove() {
        StackTraceElement[] stack = new StackTraceElement[]{
            frame("net.minecraft.entity.Entity", "func_70091_d"),
            frame("net.minecraft.entity.EntityLivingBase", "func_191986_a")
        };

        assertEquals(VanillaEntityWorkSampler.Category.ENTITY_MOVE,
            VanillaEntityWorkSampler.classify(stack));
    }

    @Test
    public void classifiesEntityTravel() {
        StackTraceElement[] stack = new StackTraceElement[]{
            frame("net.minecraft.entity.EntityLivingBase", "travel")
        };

        assertEquals(VanillaEntityWorkSampler.Category.ENTITY_TRAVEL,
            VanillaEntityWorkSampler.classify(stack));
    }

    @Test
    public void classifiesMoveHelperProcessing() {
        StackTraceElement[] stack = new StackTraceElement[]{
            frame("net.minecraft.entity.ai.EntityMoveHelper", "func_75641_c"),
            frame("net.minecraft.entity.EntityLiving", "updateEntityActionState")
        };

        assertEquals(VanillaEntityWorkSampler.Category.MOVE_HELPER,
            VanillaEntityWorkSampler.classify(stack));
    }

    @Test
    public void identifiesCallerBeyondPathfindingFrames() {
        StackTraceElement[] stack = new StackTraceElement[]{
            frame("net.minecraft.world.ChunkCache", "getBlockState"),
            frame("net.minecraft.pathfinding.WalkNodeProcessor", "getSafePoint"),
            frame("net.minecraft.pathfinding.PathFinder", "findPath"),
            frame("net.minecraft.pathfinding.PathNavigateGround", "getPathToPos"),
            frame("net.minecraft.entity.ai.EntityAIAttackMelee", "updateTask")
        };

        assertEquals("EntityAIAttackMelee#updateTask",
            VanillaEntityWorkSampler.pathSearchCaller(stack));
    }

    @Test
    public void pathCallerIsUnknownWithoutPathfindingFrame() {
        StackTraceElement[] stack = new StackTraceElement[]{
            frame("net.minecraft.entity.ai.EntityAIAttackMelee", "updateTask")
        };

        assertEquals("unknown", VanillaEntityWorkSampler.pathSearchCaller(stack));
    }

    @Test
    public void classifiesNearbyEntityPushScanByDeobfuscatedCaller() {
        StackTraceElement[] stack = new StackTraceElement[]{
            frame("net.minecraft.world.chunk.Chunk", "getEntitiesWithinAABBForEntity"),
            frame("net.minecraft.world.World", "getEntitiesWithinAABBExcludingEntity"),
            frame("net.minecraft.entity.EntityLivingBase", "collideWithNearbyEntities")
        };

        assertEquals(VanillaEntityWorkSampler.Category.ENTITY_PUSH_SCAN,
            VanillaEntityWorkSampler.classify(stack));
    }

    @Test
    public void classifiesNearbyEntityPushScanBySrgCaller() {
        StackTraceElement[] stack = new StackTraceElement[]{
            frame("net.minecraft.world.chunk.Chunk", "func_177414_a"),
            frame("net.minecraft.world.World", "func_175674_a"),
            frame("net.minecraft.entity.EntityLivingBase", "func_85033_bc")
        };

        assertEquals(VanillaEntityWorkSampler.Category.ENTITY_PUSH_SCAN,
            VanillaEntityWorkSampler.classify(stack));
    }

    @Test
    public void classifiesEntityPushImpulse() {
        StackTraceElement[] stack = new StackTraceElement[]{
            frame("net.minecraft.entity.Entity", "applyEntityCollision"),
            frame("net.minecraft.entity.EntityLivingBase", "collideWithEntity")
        };

        assertEquals(VanillaEntityWorkSampler.Category.ENTITY_PUSH_SCAN,
            VanillaEntityWorkSampler.classify(stack));
    }

    @Test
    public void unrelatedEntityQueriesRemainChunkWorldWork() {
        StackTraceElement[] stack = new StackTraceElement[]{
            frame("net.minecraft.world.chunk.Chunk", "getEntitiesWithinAABBForEntity"),
            frame("net.minecraft.world.World", "getEntitiesWithinAABBExcludingEntity")
        };

        assertEquals(VanillaEntityWorkSampler.Category.CHUNK_WORLD,
            VanillaEntityWorkSampler.classify(stack));
    }

    @Test
    public void classifiesGoalSelectorWork() {
        StackTraceElement[] stack = new StackTraceElement[]{
            frame("net.minecraft.entity.ai.EntityAITasks", "onUpdateTasks"),
            frame("net.minecraft.entity.EntityLiving", "updateEntityActionState")
        };

        assertEquals(VanillaEntityWorkSampler.Category.AI_SELECTORS,
            VanillaEntityWorkSampler.classify(stack));
    }

    @Test
    public void breaksDownLivingDespawnWork() {
        StackTraceElement[] stack = new StackTraceElement[]{
            frame("net.minecraft.entity.EntityLiving", "func_70623_bb"),
            frame("net.minecraft.entity.EntityLiving", "func_70636_d")
        };

        assertEquals(VanillaEntityWorkSampler.LivingDetail.DESPAWN,
            VanillaEntityWorkSampler.classifyLivingDetail(stack));
    }

    @Test
    public void breaksDownLivingLootEquipmentWork() {
        StackTraceElement[] stack = new StackTraceElement[]{
            frame("net.minecraft.entity.EntityLiving", "updateEquipmentIfNeeded"),
            frame("net.minecraft.entity.EntityLiving", "onLivingUpdate")
        };

        assertEquals(VanillaEntityWorkSampler.LivingDetail.LOOT_EQUIPMENT,
            VanillaEntityWorkSampler.classifyLivingDetail(stack));
    }

    @Test
    public void breaksDownSubclassMobTickWork() {
        StackTraceElement[] stack = new StackTraceElement[]{
            frame("net.minecraft.entity.monster.EntityZombie", "func_70619_bc"),
            frame("net.minecraft.entity.EntityLiving", "func_70626_be")
        };

        assertEquals(VanillaEntityWorkSampler.LivingDetail.MOB_TICK,
            VanillaEntityWorkSampler.classifyLivingDetail(stack));
    }

    @Test
    public void breaksDownCompatibleLookHelperWork() {
        StackTraceElement[] stack = new StackTraceElement[]{
            frame("com.builtbroken.ai.improvements.FixedEntityLookHelper", "onUpdateLook"),
            frame("net.minecraft.entity.EntityLiving", "updateEntityActionState")
        };

        assertEquals(VanillaEntityWorkSampler.LivingDetail.LOOK_CONTROL,
            VanillaEntityWorkSampler.classifyLivingDetail(stack));
    }

    @Test
    public void breaksDownJumpAndBodyControls() {
        assertEquals(VanillaEntityWorkSampler.LivingDetail.JUMP_CONTROL,
            VanillaEntityWorkSampler.classifyLivingDetail(new StackTraceElement[]{
                frame("net.minecraft.entity.ai.EntityJumpHelper", "doJump")
            }));
        assertEquals(VanillaEntityWorkSampler.LivingDetail.BODY_CONTROL,
            VanillaEntityWorkSampler.classifyLivingDetail(new StackTraceElement[]{
                frame("net.minecraft.entity.ai.EntityBodyHelper", "updateRenderAngles")
            }));
    }

    @Test
    public void specificLivingDetailBeatsBaseUpdate() {
        StackTraceElement[] stack = new StackTraceElement[]{
            frame("net.minecraft.entity.EntityLiving", "updateLeashedState"),
            frame("net.minecraft.entity.EntityLiving", "onLivingUpdate")
        };

        assertEquals(VanillaEntityWorkSampler.LivingDetail.LEASH,
            VanillaEntityWorkSampler.classifyLivingDetail(stack));
    }

    @Test
    public void breaksDownStatusAndBaseUpdates() {
        assertEquals(VanillaEntityWorkSampler.LivingDetail.STATUS_EFFECTS,
            VanillaEntityWorkSampler.classifyLivingDetail(new StackTraceElement[]{
                frame("net.minecraft.entity.EntityLivingBase", "updatePotionEffects"),
                frame("net.minecraft.entity.EntityLivingBase", "onLivingUpdate")
            }));
        assertEquals(VanillaEntityWorkSampler.LivingDetail.BASE_UPDATE,
            VanillaEntityWorkSampler.classifyLivingDetail(new StackTraceElement[]{
                frame("net.minecraft.entity.EntityLivingBase", "func_70030_z")
            }));
    }

    @Test
    public void unknownLivingWorkRemainsVisible() {
        assertEquals(VanillaEntityWorkSampler.LivingDetail.OTHER,
            VanillaEntityWorkSampler.classifyLivingDetail(new StackTraceElement[]{
                frame("net.minecraft.entity.EntityLivingBase", "someModdedHook")
            }));
    }

    private static StackTraceElement frame(String className, String method) {
        return new StackTraceElement(className, method, className + ".java", 1);
    }
}
