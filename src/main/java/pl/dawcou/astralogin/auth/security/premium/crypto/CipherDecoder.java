package pl.dawcou.astralogin.auth.security.premium.crypto;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class CipherDecoder extends ByteToMessageDecoder {

    private final MinecraftCipher cipher;

    public CipherDecoder(MinecraftCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int readable = in.readableBytes();

        if (readable <= 0) {
            return;
        }

        byte[] encrypted = new byte[readable];
        in.readBytes(encrypted);

        byte[] decryptedBytes = cipher.transform(encrypted);

        ByteBuf decrypted = ctx.alloc().buffer(decryptedBytes.length);
        decrypted.writeBytes(decryptedBytes);

        out.add(decrypted);
    }
}