package com.jammerbam.zomboid.network;

import com.jammerbam.zomboid.performance.ClientTpsState;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class ServerTpsMessage implements IMessage {
    private float ticksPerSecond;

    public ServerTpsMessage() {
    }

    public ServerTpsMessage(float ticksPerSecond) {
        this.ticksPerSecond = ticksPerSecond;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        ticksPerSecond = buffer.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeFloat(ticksPerSecond);
    }

    public static final class Handler
            implements IMessageHandler<ServerTpsMessage, IMessage> {
        @Override
        public IMessage onMessage(ServerTpsMessage message,
                                  MessageContext context) {
            ClientTpsState.update(message.ticksPerSecond);
            return null;
        }
    }
}
