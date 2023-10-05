package com.crazymakercircle.netty.encoder;

import com.crazymakercircle.util.Logger;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

/**
 * create by 尼恩 @ 疯狂创客圈
 **/
public class String2IntegerEncoder extends MessageToMessageEncoder<String> {
    @Override
    protected void encode(ChannelHandlerContext c, String s, List<Object> list) throws Exception {
        Logger.info(s);
        char[] array = s.toCharArray();
        for (char a : array) {
            if (a >= 'a' && a <= '9') {
                list.add(Integer.valueOf((int)a));

            }
        }
    }
}
