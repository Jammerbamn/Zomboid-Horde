package com.ethan.zomboidzombies.population;

import net.minecraft.entity.monster.EntityHusk;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.EntityZombieVillager;
import net.minecraft.world.World;

public enum ZombieKind {
    NORMAL,
    HUSK,
    VILLAGER;

    public EntityZombie create(World world) {
        switch (this) {
            case HUSK:
                return new EntityHusk(world);
            case VILLAGER:
                return new EntityZombieVillager(world);
            case NORMAL:
            default:
                return new EntityZombie(world);
        }
    }
}
