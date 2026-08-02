package com.jammerbam.zomboid.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.AbstractInsnNode;

/** Installs crowded-zombie collision and automatic-repath instrumentation hooks. */
public final class ZomboidClassTransformer implements IClassTransformer {
    private static final Logger LOGGER = LogManager.getLogger("zomboid-core");
    private static final String WORLD = "net.minecraft.world.World";
    private static final String PATH_NAVIGATE = "net.minecraft.pathfinding.PathNavigate";
    private static final String PATH_WORLD_LISTENER =
        "net.minecraft.pathfinding.PathWorldListener";
    private static final String ENTITY_ZOMBIE =
        "net.minecraft.entity.monster.EntityZombie";
    private static final String HOOK =
        "com/jammerbam/zomboid/ai/navigation/CrowdCollisionHooks";
    private static final String REPATH_HOOK =
        "com/jammerbam/zomboid/performance/AutomaticRepathTelemetry";
    private static final String DAYLIGHT_HOOK =
        "com/jammerbam/zomboid/core/DaylightZombieHooks";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || (!WORLD.equals(transformedName)
            && !PATH_NAVIGATE.equals(transformedName)
            && !PATH_WORLD_LISTENER.equals(transformedName)
            && !ENTITY_ZOMBIE.equals(transformedName))) {
            return basicClass;
        }
        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassNode node = new ClassNode();
            reader.accept(node, ClassReader.EXPAND_FRAMES);
            boolean changed;
            if (WORLD.equals(transformedName)) {
                changed = transformWorld(node);
            } else if (ENTITY_ZOMBIE.equals(transformedName)) {
                changed = transformDaylightBurning(node);
            } else {
                changed = transformAutomaticRepaths(
                    node, PATH_NAVIGATE.equals(transformedName)
                );
            }
            if (!changed) {
                LOGGER.warn("Could not install Zomboid core hook in {}.", transformedName);
                return basicClass;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            LOGGER.info("Installed Zomboid core hook in {}.", transformedName);
            return writer.toByteArray();
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to install Zomboid core hook in {}.",
                transformedName, exception);
            return basicClass;
        }
    }

    private static boolean transformWorld(ClassNode node) {
        for (MethodNode method : node.methods) {
            if (isCollisionQuery(method)) {
                injectFastPath(node.name, method);
                return true;
            }
        }
        return false;
    }

    private static boolean transformDaylightBurning(ClassNode node) {
        for (MethodNode method : node.methods) {
            if (!"()Z".equals(method.desc)
                || !("shouldBurnInDay".equals(method.name)
                    || "func_190730_o".equals(method.name)
                    || "p".equals(method.name))) {
                continue;
            }
            LabelNode vanilla = new LabelNode();
            InsnList prefix = new InsnList();
            prefix.add(new VarInsnNode(Opcodes.ALOAD, 0));
            prefix.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                DAYLIGHT_HOOK,
                "preventDaylightBurning",
                "(L" + node.name + ";)Z",
                false
            ));
            prefix.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
            prefix.add(new InsnNode(Opcodes.ICONST_0));
            prefix.add(new InsnNode(Opcodes.IRETURN));
            prefix.add(vanilla);
            prefix.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            method.instructions.insert(prefix);
            return true;
        }
        return false;
    }

    private static boolean transformAutomaticRepaths(ClassNode node,
                                                      boolean pathNavigate) {
        int replacements = 0;
        for (MethodNode method : node.methods) {
            if (pathNavigate && !isNavigationTick(method)) {
                continue;
            }
            if (!pathNavigate && !isBlockUpdateListener(method)) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (!isUpdatePathCall(call)) {
                    continue;
                }
                String descriptor = Type.getMethodDescriptor(
                    Type.VOID_TYPE, Type.getObjectType(call.owner)
                );
                MethodInsnNode hook = new MethodInsnNode(
                    Opcodes.INVOKESTATIC, REPATH_HOOK,
                    pathNavigate ? "runDeferred" : "runImmediate",
                    descriptor, false
                );
                method.instructions.set(call, hook);
                replacements++;
            }
        }
        return replacements > 0;
    }

    private static boolean isNavigationTick(MethodNode method) {
        return "()V".equals(method.desc)
            && ("onUpdateNavigation".equals(method.name)
                || "func_75501_e".equals(method.name)
                || "d".equals(method.name));
    }

    private static boolean isBlockUpdateListener(MethodNode method) {
        return "notifyBlockUpdate".equals(method.name)
            || "func_184376_a".equals(method.name)
            || "a".equals(method.name);
    }

    private static boolean isUpdatePathCall(MethodInsnNode call) {
        return "()V".equals(call.desc)
            && ("updatePath".equals(call.name)
                || "func_188554_j".equals(call.name)
                || "j".equals(call.name));
    }

    private static boolean isCollisionQuery(MethodNode method) {
        Type[] arguments = Type.getArgumentTypes(method.desc);
        if (arguments.length != 2
            || Type.getReturnType(method.desc).getSort() != Type.OBJECT
            || !"java/util/List".equals(Type.getReturnType(method.desc).getInternalName())) {
            return false;
        }
        if ("getCollisionBoxes".equals(method.name)
            || "func_184144_a".equals(method.name)) {
            return true;
        }
        return "a".equals(method.name)
            && "vg".equals(arguments[0].getInternalName())
            && "bhb".equals(arguments[1].getInternalName());
    }

    private static void injectFastPath(String worldName, MethodNode method) {
        Type[] arguments = Type.getArgumentTypes(method.desc);
        Type returnType = Type.getReturnType(method.desc);
        String hookDescriptor = Type.getMethodDescriptor(returnType,
            Type.getObjectType(worldName), arguments[0], arguments[1]);
        LabelNode vanilla = new LabelNode();
        InsnList prefix = new InsnList();
        prefix.add(new VarInsnNode(Opcodes.ALOAD, 0));
        prefix.add(new VarInsnNode(Opcodes.ALOAD, 1));
        prefix.add(new VarInsnNode(Opcodes.ALOAD, 2));
        prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
            "tryGetCollisionBoxes", hookDescriptor, false));
        prefix.add(new InsnNode(Opcodes.DUP));
        prefix.add(new JumpInsnNode(Opcodes.IFNULL, vanilla));
        prefix.add(new InsnNode(Opcodes.ARETURN));
        prefix.add(vanilla);
        prefix.add(new FrameNode(Opcodes.F_NEW, 3,
            new Object[]{worldName, arguments[0].getInternalName(),
                arguments[1].getInternalName()},
            1, new Object[]{"java/util/List"}));
        prefix.add(new InsnNode(Opcodes.POP));
        method.instructions.insert(prefix);
    }
}
