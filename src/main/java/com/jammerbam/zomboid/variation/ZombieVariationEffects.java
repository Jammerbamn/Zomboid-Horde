package com.jammerbam.zomboid.variation;

import net.minecraft.entity.EntityList;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public final class ZombieVariationEffects {
    private static final int AURA_INTERVAL_TICKS = 10;

    private ZombieVariationEffects() {
    }

    public static void tickAura(EntityZombie zombie) {
        if (!(zombie.world instanceof WorldServer)
            || Math.floorMod(zombie.ticksExisted + zombie.getEntityId(), AURA_INTERVAL_TICKS)
                != 0) {
            return;
        }
        ZombieVariationDefinition definition = definitionFor(zombie);
        if (definition == null || definition.getOnHitEffects().isEmpty()) {
            return;
        }

        WorldServer world = (WorldServer) zombie.world;
        for (ZombieVariationDefinition.OnHitEffect configured
            : definition.getOnHitEffects()) {
            if (configured.getChancePercent() <= 0.0D) {
                continue;
            }
            Potion potion = ForgeRegistries.POTIONS.getValue(
                new ResourceLocation(configured.getPotionId())
            );
            if (potion == null) {
                continue;
            }
            int color = potion.getLiquidColor();
            double red = ((color >> 16) & 255) / 255.0D;
            double green = ((color >> 8) & 255) / 255.0D;
            double blue = (color & 255) / 255.0D;
            world.spawnParticle(
                EnumParticleTypes.SPELL_MOB,
                zombie.posX,
                zombie.posY + zombie.height * 0.65D,
                zombie.posZ,
                0,
                red,
                green,
                blue,
                1.0D
            );
        }
    }

    public static void applyOnHit(EntityZombie zombie, EntityPlayer player) {
        if (player.world.isRemote) {
            return;
        }
        ZombieVariationDefinition definition = definitionFor(zombie);
        if (definition == null) {
            return;
        }
        for (ZombieVariationDefinition.OnHitEffect configured
            : definition.getOnHitEffects()) {
            if (configured.getChancePercent() < 100.0D
                && zombie.getRNG().nextDouble() * 100.0D
                    >= configured.getChancePercent()) {
                continue;
            }
            Potion potion = ForgeRegistries.POTIONS.getValue(
                new ResourceLocation(configured.getPotionId())
            );
            if (potion != null) {
                player.addPotionEffect(new PotionEffect(
                    potion,
                    configured.getDurationTicks(),
                    configured.getAmplifier()
                ));
            }
        }
    }

    private static ZombieVariationDefinition definitionFor(EntityZombie zombie) {
        String variationId = VariationTags.getVariationId(zombie);
        ResourceLocation entityId = EntityList.getKey(zombie);
        return variationId.isEmpty() || entityId == null
            ? null
            : ZombieVariationDefinitions.get().get(variationId, entityId.toString());
    }
}
