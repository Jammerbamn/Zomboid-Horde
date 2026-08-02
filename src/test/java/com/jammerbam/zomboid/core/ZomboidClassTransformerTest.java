package com.jammerbam.zomboid.core;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ZomboidClassTransformerTest {
    private static final String COLLISION_DESCRIPTOR =
        "(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/AxisAlignedBB;)Ljava/util/List;";

    @Test
    public void installsFastPathBeforeWorldCollisionQuery() {
        byte[] transformed = new ZomboidClassTransformer().transform(
            "amu", "net.minecraft.world.World", syntheticWorld()
        );
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);

        boolean hookFound = false;
        for (MethodNode method : node.methods) {
            if (!"getCollisionBoxes".equals(method.name)) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    hookFound |= "com/jammerbam/zomboid/ai/navigation/CrowdCollisionHooks"
                        .equals(call.owner) && "tryGetCollisionBoxes".equals(call.name);
                }
            }
        }
        assertTrue(hookFound);
    }

    @Test
    public void leavesUnrelatedClassesUntouched() {
        byte[] original = syntheticWorld();
        assertSame(original, new ZomboidClassTransformer().transform(
            "example.Unrelated", "example.Unrelated", original
        ));
    }

    @Test
    public void wrapsDeferredEntityTickRepath() {
        byte[] transformed = new ZomboidClassTransformer().transform(
            "ze", "net.minecraft.pathfinding.PathNavigate", syntheticPathNavigate()
        );
        assertHook(transformed, "onUpdateNavigation", "runDeferred");
    }

    @Test
    public void wrapsImmediateBlockChangeRepath() {
        byte[] transformed = new ZomboidClassTransformer().transform(
            "zf", "net.minecraft.pathfinding.PathWorldListener",
            syntheticPathWorldListener()
        );
        assertHook(transformed, "notifyBlockUpdate", "runImmediate");
    }

    @Test
    public void preventsVanillaZombieDaylightBurningThroughCoreHook() {
        byte[] transformed = new ZomboidClassTransformer().transform(
            "adt", "net.minecraft.entity.monster.EntityZombie",
            syntheticZombie()
        );
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        boolean hookFound = false;
        for (MethodNode method : node.methods) {
            if (!"shouldBurnInDay".equals(method.name)) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    hookFound |= "com/jammerbam/zomboid/core/DaylightZombieHooks"
                        .equals(call.owner)
                        && "preventDaylightBurning".equals(call.name);
                }
            }
        }
        assertTrue(hookFound);
    }

    private static void assertHook(byte[] transformed, String methodName,
                                   String hookName) {
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        boolean hookFound = false;
        for (MethodNode method : node.methods) {
            if (!methodName.equals(method.name)) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    hookFound |= "com/jammerbam/zomboid/performance/AutomaticRepathTelemetry"
                        .equals(call.owner) && hookName.equals(call.name);
                }
            }
        }
        assertTrue(hookFound);
    }

    private static byte[] syntheticWorld() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, "net/minecraft/world/World",
            null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC,
            "getCollisionBoxes", COLLISION_DESCRIPTOR, null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 3);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] syntheticPathNavigate() {
        ClassWriter writer = new ClassWriter(0);
        String owner = "net/minecraft/pathfinding/PathNavigate";
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, owner,
            null, "java/lang/Object", null);
        MethodVisitor update = writer.visitMethod(
            Opcodes.ACC_PUBLIC, "updatePath", "()V", null, null
        );
        update.visitCode();
        update.visitInsn(Opcodes.RETURN);
        update.visitMaxs(0, 1);
        update.visitEnd();
        MethodVisitor tick = writer.visitMethod(
            Opcodes.ACC_PUBLIC, "onUpdateNavigation", "()V", null, null
        );
        tick.visitCode();
        tick.visitVarInsn(Opcodes.ALOAD, 0);
        tick.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "updatePath", "()V", false);
        tick.visitInsn(Opcodes.RETURN);
        tick.visitMaxs(1, 1);
        tick.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] syntheticPathWorldListener() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC,
            "net/minecraft/pathfinding/PathWorldListener",
            null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(
            Opcodes.ACC_PUBLIC, "notifyBlockUpdate",
            "(Lnet/minecraft/pathfinding/PathNavigate;)V", null, null
        );
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
            "net/minecraft/pathfinding/PathNavigate", "updatePath", "()V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 2);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] syntheticZombie() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC,
            "net/minecraft/entity/monster/EntityZombie",
            null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(
            Opcodes.ACC_PROTECTED, "shouldBurnInDay", "()Z", null, null
        );
        method.visitCode();
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
