package com.jammerbam.zomboid.network;

import com.jammerbam.zomboid.Zomboid;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class ZomboidNetwork {
    public static final SimpleNetworkWrapper CHANNEL =
        NetworkRegistry.INSTANCE.newSimpleChannel(Zomboid.MOD_ID);

    private ZomboidNetwork() {
    }

    public static void init() {
        CHANNEL.registerMessage(
            ServerTpsMessage.Handler.class,
            ServerTpsMessage.class,
            0,
            Side.CLIENT
        );
    }
}
