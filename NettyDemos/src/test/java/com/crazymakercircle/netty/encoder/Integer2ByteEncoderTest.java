package com.crazymakercircle.netty.encoder;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class Integer2ByteEncoderTest {
    /**
     * 测试整数编码器
     */
    @Test
    public void testIntegerToByteDecoder() {
        ChannelInitializer<EmbeddedChannel> initializer = new ChannelInitializer<EmbeddedChannel>() {
            protected void initChannel(EmbeddedChannel ch) {
                ch.pipeline().addLast(new Integer2ByteEncoder());
            }
        };

        EmbeddedChannel channel = new EmbeddedChannel(initializer);

        for (int j = 0; j < 100; j++) {
            channel.writeAndFlush(j);
        }
        channel.flush();

        //取得通道的出站数据帧
        ByteBuf buf = channel.readOutbound();

        while (null != buf) {
            log.info("o = " + buf.readInt());
            buf = channel.readOutbound();
        }

        try {
            Thread.sleep(Integer.MAX_VALUE);
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        }
    }

}
