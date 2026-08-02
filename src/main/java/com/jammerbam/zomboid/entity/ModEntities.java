package com.jammerbam.zomboid.entity;

import com.jammerbam.zomboid.Zomboid;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.EntityRegistry;

public final class ModEntities {
    public static final ResourceLocation BUFF_ZOMBIE =
        new ResourceLocation(Zomboid.MOD_ID, "buff_zombie");

    private ModEntities() {
    }

    public static void register() {
        EntityRegistry.registerModEntity(
            BUFF_ZOMBIE,
            EntityBuffZombie.class,
            "buff_zombie",
            0,
            Zomboid.instance,
            64,
            3,
            true,
            0x3A4A32,
            0x6B4B32
        );
    }
}
