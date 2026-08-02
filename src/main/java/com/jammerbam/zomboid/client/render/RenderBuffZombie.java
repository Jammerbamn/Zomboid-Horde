package com.jammerbam.zomboid.client.render;

import com.jammerbam.zomboid.Zomboid;
import com.jammerbam.zomboid.client.model.ModelBuffZombie;
import com.jammerbam.zomboid.entity.EntityBuffZombie;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;

public final class RenderBuffZombie extends RenderLiving<EntityBuffZombie> {
    private static final ResourceLocation TEXTURE =
        new ResourceLocation(Zomboid.MOD_ID, "textures/entity/buff_zombie.png");

    public RenderBuffZombie(RenderManager renderManager) {
		super(renderManager, new ModelBuffZombie(), 0.7F);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityBuffZombie entity) {
        return TEXTURE;
    }
}
