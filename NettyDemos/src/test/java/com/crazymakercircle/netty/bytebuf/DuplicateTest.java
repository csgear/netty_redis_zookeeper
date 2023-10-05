package com.crazymakercircle.netty.bytebuf;

import com.crazymakercircle.util.Logger;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.junit.Test;

import static com.crazymakercircle.netty.bytebuf.PrintAttribute.print;


public class DuplicateTest {
    @Test
    public  void testduplicate() {
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(9, 100);
        print("动作：分配 ByteBuf(9, 100)", buffer);

        buffer.writeBytes(new byte[]{1, 2, 3, 4});
        print("动作：写入4个字节 (1,2,3,4)", buffer);


        ByteBuf duplicate = buffer.duplicate();
        print("动作：副本 duplicate", duplicate);

        ByteBuf duplicate1 = buffer.duplicate();
        print("动作：副本 duplicate1", duplicate1);

        duplicate1.readByte();
        duplicate1.readByte();
        print("动作：读取之后 副本 duplicate1", duplicate1);
        print("动作：读取之后 副本 duplicate0", duplicate);
        print("动作：读取之后 buffer", buffer);


        buffer.retain();
        Logger.info("4.0 refCnt(): " + buffer.refCnt());
        Logger.info("4.0 duplicate refCnt(): " + duplicate.refCnt());
        Logger.info("4.0 duplicate1 refCnt(): " + duplicate1.refCnt());
        buffer.release();
        Logger.info("4.0 refCnt(): " + buffer.refCnt());
        Logger.info("4.0 duplicate refCnt(): " + duplicate.refCnt());
        Logger.info("4.0 duplicate1 refCnt(): " + duplicate1.refCnt());
    }

}