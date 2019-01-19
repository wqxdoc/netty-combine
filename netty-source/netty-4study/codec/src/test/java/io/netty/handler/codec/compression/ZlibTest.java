/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ThreadLocalRandom;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.*;

public abstract class ZlibTest {

    private static final byte[] BYTES_SMALL = new byte[128];
    private static final byte[] BYTES_LARGE = new byte[1024 * 1024];
    static {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        rand.nextBytes(BYTES_SMALL);
        rand.nextBytes(BYTES_LARGE);
    }

    protected abstract ZlibEncoder createEncoder(ZlibWrapper wrapper);
    protected abstract ZlibDecoder createDecoder(ZlibWrapper wrapper);

    @Test
    public void testGZIP2() throws Exception {
        ByteBuf data = Unpooled.wrappedBuffer("message".getBytes(CharsetUtil.UTF_8));
        ByteBuf deflatedData = Unpooled.wrappedBuffer(gzip("message"));

        EmbeddedChannel chDecoderGZip = new EmbeddedChannel(createDecoder(ZlibWrapper.GZIP));
        chDecoderGZip.writeInbound(deflatedData.copy());
        assertTrue(chDecoderGZip.finish());
        ByteBuf buf = (ByteBuf) chDecoderGZip.readInbound();
        assertEquals(buf, data);
        assertNull(chDecoderGZip.readInbound());
        data.release();
        deflatedData.release();
        buf.release();
    }

    private void testCompress0(ZlibWrapper encoderWrapper, ZlibWrapper decoderWrapper, byte[] bytes) throws Exception {
        ByteBuf data = Unpooled.wrappedBuffer(bytes);
        EmbeddedChannel chEncoder = new EmbeddedChannel(createEncoder(encoderWrapper));

        chEncoder.writeOutbound(data.copy());
        chEncoder.flush();

        EmbeddedChannel chDecoderZlib = new EmbeddedChannel(createDecoder(decoderWrapper));
        for (;;) {
            ByteBuf deflatedData = (ByteBuf) chEncoder.readOutbound();
            if (deflatedData == null) {
                break;
            }
            chDecoderZlib.writeInbound(deflatedData);
        }

        byte[] decompressed = new byte[bytes.length];
        int offset = 0;
        for (;;) {
            ByteBuf buf = (ByteBuf) chDecoderZlib.readInbound();
            if (buf == null) {
                break;
            }
            int length = buf.readableBytes();
            buf.readBytes(decompressed, offset, length);
            offset += length;
            buf.release();
            if (offset == decompressed.length) {
                break;
            }
        }
        assertArrayEquals(bytes, decompressed);
        assertNull(chDecoderZlib.readInbound());

        // Closing an encoder channel will generate a footer.
        assertTrue(chEncoder.finish());
        for (;;) {
            Object msg = chEncoder.readOutbound();
            if (msg == null) {
                break;
            }
            ReferenceCountUtil.release(msg);
        }
        // But, the footer will be decoded into nothing. It's only for validation.
        assertFalse(chDecoderZlib.finish());

        data.release();
    }

    private void testCompressNone(ZlibWrapper encoderWrapper, ZlibWrapper decoderWrapper) throws Exception {
        EmbeddedChannel chEncoder = new EmbeddedChannel(createEncoder(encoderWrapper));

        // Closing an encoder channel without writing anything should generate both header and footer.
        assertTrue(chEncoder.finish());

        EmbeddedChannel chDecoderZlib = new EmbeddedChannel(createDecoder(decoderWrapper));
        for (;;) {
            ByteBuf deflatedData = (ByteBuf) chEncoder.readOutbound();
            if (deflatedData == null) {
                break;
            }
            chDecoderZlib.writeInbound(deflatedData);
        }

        // Decoder should not generate anything at all.
        for (;;) {
            ByteBuf buf = (ByteBuf) chDecoderZlib.readInbound();
            if (buf == null) {
                break;
            }

            buf.release();
            fail("should decode nothing");
        }

        assertFalse(chDecoderZlib.finish());
    }

    private void testCompressSmall(ZlibWrapper encoderWrapper, ZlibWrapper decoderWrapper) throws Exception {
        testCompress0(encoderWrapper, decoderWrapper, BYTES_SMALL);
    }

    private void testCompressLarge(ZlibWrapper encoderWrapper, ZlibWrapper decoderWrapper) throws Exception {
        testCompress0(encoderWrapper, decoderWrapper, BYTES_LARGE);
    }

    @Test
    public void testZLIB() throws Exception {
        testCompressNone(ZlibWrapper.ZLIB, ZlibWrapper.ZLIB);
        testCompressSmall(ZlibWrapper.ZLIB, ZlibWrapper.ZLIB);
        testCompressLarge(ZlibWrapper.ZLIB, ZlibWrapper.ZLIB);
    }

    @Test
    public void testNONE() throws Exception {
        testCompressNone(ZlibWrapper.NONE, ZlibWrapper.NONE);
        testCompressSmall(ZlibWrapper.NONE, ZlibWrapper.NONE);
        testCompressLarge(ZlibWrapper.NONE, ZlibWrapper.NONE);
    }

    @Test
    public void testGZIP() throws Exception {
        testCompressNone(ZlibWrapper.GZIP, ZlibWrapper.GZIP);
        testCompressSmall(ZlibWrapper.GZIP, ZlibWrapper.GZIP);
        testCompressLarge(ZlibWrapper.GZIP, ZlibWrapper.GZIP);
    }

    @Test
    public void testZLIB_OR_NONE() throws Exception {
        testCompressNone(ZlibWrapper.NONE, ZlibWrapper.ZLIB_OR_NONE);
        testCompressSmall(ZlibWrapper.NONE, ZlibWrapper.ZLIB_OR_NONE);
        testCompressLarge(ZlibWrapper.NONE, ZlibWrapper.ZLIB_OR_NONE);
    }

    @Test
    public void testZLIB_OR_NONE2() throws Exception {
        testCompressNone(ZlibWrapper.ZLIB, ZlibWrapper.ZLIB_OR_NONE);
        testCompressSmall(ZlibWrapper.ZLIB, ZlibWrapper.ZLIB_OR_NONE);
        testCompressLarge(ZlibWrapper.GZIP, ZlibWrapper.ZLIB_OR_NONE);
    }

    @Test
    public void testZLIB_OR_NONE3() throws Exception {
        testCompressNone(ZlibWrapper.GZIP, ZlibWrapper.ZLIB_OR_NONE);
        testCompressSmall(ZlibWrapper.GZIP, ZlibWrapper.ZLIB_OR_NONE);
        testCompressLarge(ZlibWrapper.GZIP, ZlibWrapper.ZLIB_OR_NONE);
    }

    private static byte[] gzip(String message) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream stream = new GZIPOutputStream(out);
        stream.write(message.getBytes(CharsetUtil.UTF_8));
        stream.close();
        return out.toByteArray();
    }

}
