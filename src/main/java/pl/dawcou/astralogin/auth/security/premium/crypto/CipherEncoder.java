package pl.dawcou.astralogin.auth.security.premium.crypto;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class CipherEncoder extends MessageToByteEncoder<ByteBuf> {

    private final MinecraftCipher cipher;

    public CipherEncoder(MinecraftCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        int readable = msg.readableBytes();
        byte[] heap = new byte[readable];
        msg.readBytes(heap);

        out.writeBytes(cipher.transform(heap));
    }
}