package com.jammerbam.zomboid.proxy;

import com.jammerbam.zomboid.client.audio.ZombieSoundProtection;
import com.jammerbam.zomboid.client.render.RenderBuffZombie;
import com.jammerbam.zomboid.entity.EntityBuffZombie;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public final class ClientProxy extends CommonProxy {
    @Override
    public void preInit() {
        MinecraftForge.EVENT_BUS.register(new ZombieSoundProtection());
        RenderingRegistry.registerEntityRenderingHandler(
            EntityBuffZombie.class,
            RenderBuffZombie::new
        );
    }
}
