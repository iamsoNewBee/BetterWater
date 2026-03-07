package com.CatFish.BetterWater;

import static org.objectweb.asm.Opcodes.*;

import java.util.Arrays;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class BetterWaterTransformer implements IClassTransformer {

    private static final String[] targetClasses = { "net.minecraft.block.BlockDynamicLiquid" };

    @Override
    public byte[] transform(String name, String transformedName, byte[] targetClass) {
        boolean isObfuscated = !name.equals(transformedName);
        int index = Arrays.asList(targetClasses)
            .indexOf(transformedName);
        return index != -1 ? transform(index, targetClass, isObfuscated) : targetClass;
    }

    private static byte[] transform(int index, byte[] classBeingTransformed, boolean isObfuscated) {
        System.out.println("Transforming: " + targetClasses[index]);
        try {
            ClassNode classNode = new ClassNode();
            ClassReader classReader = new ClassReader(classBeingTransformed);
            classReader.accept(classNode, 0);

            switch (index) {
                case 0:
                    transformBlockDynamicLiquid(classNode, isObfuscated);
                    break;
            }

            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            classNode.accept(classWriter);
            return classWriter.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return classBeingTransformed;
    }

    private static void transformBlockDynamicLiquid(ClassNode classNode, boolean isObfuscated) {
        final String UPDATE_TICK = isObfuscated ? "a" : "updateTick";
        final String UPDATE_TICK_DESC = isObfuscated ? "(Lahb;IIILjava/util/Random;)V"
            : "(Lnet/minecraft/world/World;IIILjava/util/Random;)V";
        final String FIELD_NAME = isObfuscated ? "a" : "field_149815_a";

        for (MethodNode method : classNode.methods) {
            if (method.name.equals(UPDATE_TICK) && method.desc.equals(UPDATE_TICK_DESC)) {
                AbstractInsnNode targetNode = null;
                // 寻找序列：ALOAD 0, GETFIELD, ICONST_2
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn.getOpcode() == ALOAD) {
                        VarInsnNode varInsn = (VarInsnNode) insn;
                        if (varInsn.var == 0) {
                            AbstractInsnNode next = insn.getNext();
                            if (next != null && next.getOpcode() == GETFIELD) {
                                AbstractInsnNode nextNext = next.getNext();
                                if (nextNext != null && nextNext.getOpcode() == ICONST_2) {
                                    targetNode = insn;
                                    break;
                                }
                            }
                        }
                    }
                }

                if (targetNode != null) {
                    InsnList toInsert = new InsnList();
                    LabelNode continueLabel = new LabelNode();

                    String worldDesc = isObfuscated ? "Lahb;" : "Lnet/minecraft/world/World;";
                    String hookDesc = "(" + worldDesc + "III)Z";

                    // 调用 shouldGenerateSource
                    toInsert.add(new VarInsnNode(ALOAD, 1)); // world
                    toInsert.add(new VarInsnNode(ILOAD, 2)); // x
                    toInsert.add(new VarInsnNode(ILOAD, 3)); // y
                    toInsert.add(new VarInsnNode(ILOAD, 4)); // z
                    toInsert.add(
                        new MethodInsnNode(
                            INVOKESTATIC,
                            Type.getInternalName(BetterWaterHooks.class),
                            "shouldGenerateSource",
                            hookDesc,
                            false));
                    toInsert.add(new JumpInsnNode(IFNE, continueLabel));

                    // 如果返回 false，设置 field_149815_a = 0
                    toInsert.add(new VarInsnNode(ALOAD, 0));
                    toInsert.add(new InsnNode(ICONST_0));
                    toInsert.add(
                        new FieldInsnNode(
                            PUTFIELD,
                            isObfuscated ? "akr" : "net/minecraft/block/BlockDynamicLiquid",
                            FIELD_NAME,
                            "I"));
                    toInsert.add(continueLabel);

                    // 插入在目标节点之前
                    method.instructions.insertBefore(targetNode, toInsert);
                    System.out.println("Successfully injected into updateTick.");
                } else {
                    System.out.println("Error: Could not find insertion point in BlockDynamicLiquid.updateTick");
                }
            }
        }
    }
}
