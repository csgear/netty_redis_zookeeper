package com.crazymakercircle.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.junit.Test;

import static org.junit.jupiter.api.Assertions.*;

public class InHandlerDemoTest {

    @Test
    public void testInHandlerLifeCircle() {

        final InHandlerDemo inHandler = new InHandlerDemo();

        EmbeddedChannel channel = new EmbeddedChannel(inHandler);
        channel.pipeline().remove(inHandler) ;
        channel.close();
    }


    @Test
    public void testInHandlerProcess() {

        final InHandlerDemo inHandler = new InHandlerDemo();

        //创建嵌入式通道
        EmbeddedChannel channel = new EmbeddedChannel(inHandler,new LoggingHandler(LogLevel.INFO));

        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(1);

        //模拟入站，写一个入站包
        channel.writeInbound(buf);
        channel.flush();

        //通道关闭
        channel.close();
    }

    @Test
    public void testInHandlerProcessWithChannelInitializer() {
        final InHandlerDemo inHandler = new InHandlerDemo();
        //初始化处理器
        ChannelInitializer<EmbeddedChannel> initializer = new ChannelInitializer<EmbeddedChannel>() {
            protected void initChannel(EmbeddedChannel ch) {
                ch.pipeline().addLast(inHandler);
                ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG));
            }
        };
        //创建嵌入式通道
        EmbeddedChannel channel = new EmbeddedChannel(initializer);
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(1);
        //模拟入站，写一个入站包
        channel.writeInbound(buf);
        channel.flush();
        //模拟入站，再写一个入站包
        channel.writeInbound(buf);
        channel.flush();
        //通道关闭
        channel.close();
    }

    @Test
    public void testSharable() {

        final InHandlerDemo inHandler = new InHandlerDemo();
        //初始化处理器
        ChannelInitializer<EmbeddedChannel> initializer = new ChannelInitializer<EmbeddedChannel>() {
            @Override
            protected void initChannel(EmbeddedChannel ch) throws Exception {
                ch.pipeline().addLast(inHandler);
                ch.pipeline().addLast(inHandler);
            }
        };
        //创建嵌入式通道
        EmbeddedChannel channel = new EmbeddedChannel(initializer);
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(1);
        //模拟入站，写一个入站包
        channel.writeInbound(buf);
        channel.flush();
        channel.close();

        System.out.println(" 再来一个 channel" );
        EmbeddedChannel channel2 = new EmbeddedChannel(initializer);

        //模拟入站，再写一个入站包
        channel2.writeInbound(buf);
        channel2.flush();
        //通道关闭
        channel2.close();

    }

}