/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandlerAdapter;
import io.netty.channel.DefaultFileRegion;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class SocketFileRegionTest extends AbstractSocketTest {

    private static final Random random = new Random();
    static final byte[] data = new byte[1048576 * 10];

    static {
        random.nextBytes(data);
    }

    @Test
    public void testFileRegion() throws Throwable {
        run();
    }

    public void testFileRegion(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        File file = File.createTempFile("netty-", ".tmp");
        file.deleteOnExit();

        FileOutputStream out = new FileOutputStream(file);
        out.write(data);
        out.close();

        ChannelInboundByteHandlerAdapter ch = new ChannelInboundByteHandlerAdapter() {
            @Override
            public void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
                in.clear();
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                ctx.close();
            }
        };
        TestHandler sh = new TestHandler();

        sb.childHandler(sh);
        cb.handler(ch);

        Channel sc = sb.bind().sync().channel();

        Channel cc = cb.connect().sync().channel();
        ChannelFuture future = cc.sendFile(new DefaultFileRegion(new FileInputStream(file).getChannel(),
                0L, file.length())).syncUninterruptibly();
        assertTrue(future.isSuccess());
        while (sh.counter < data.length) {
            if (sh.exception.get() != null) {
                break;
            }

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }

        sh.channel.close().sync();
        cc.close().sync();
        sc.close().sync();

        if (sh.exception.get() != null && !(sh.exception.get() instanceof IOException)) {
            throw sh.exception.get();
        }

        if (sh.exception.get() != null) {
            throw sh.exception.get();
        }
    }

    private static class TestHandler extends ChannelInboundByteHandlerAdapter {
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        volatile int counter;

        @Override
        public void channelActive(ChannelHandlerContext ctx)
                throws Exception {
            channel = ctx.channel();
        }

        @Override
        public void inboundBufferUpdated(
                ChannelHandlerContext ctx, ByteBuf in)
                throws Exception {
            byte[] actual = new byte[in.readableBytes()];
            in.readBytes(actual);

            int lastIdx = counter;
            for (int i = 0; i < actual.length; i ++) {
                assertEquals(data[i + lastIdx], actual[i]);
            }
            counter += actual.length;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx,
                Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                ctx.close();
            }
        }
    }
}
