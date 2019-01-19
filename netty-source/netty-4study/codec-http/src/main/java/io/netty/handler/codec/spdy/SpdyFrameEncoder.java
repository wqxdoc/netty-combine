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
package io.netty.handler.codec.spdy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.UnsupportedMessageTypeException;

import java.util.Set;

import static io.netty.handler.codec.spdy.SpdyCodecUtil.*;

/**
 * Encodes a SPDY Frame into a {@link ByteBuf}.
 */
public class SpdyFrameEncoder extends MessageToByteEncoder<SpdyFrame> {

    private final int version;
    private final SpdyHeaderBlockEncoder headerBlockEncoder;

    /**
     * Creates a new instance with the specified {@code version} and the
     * default {@code compressionLevel (6)}, {@code windowBits (15)},
     * and {@code memLevel (8)}.
     */
    public SpdyFrameEncoder(SpdyVersion version) {
        this(version, 6, 15, 8);
    }

    /**
     * Creates a new instance with the specified parameters.
     */
    public SpdyFrameEncoder(SpdyVersion version, int compressionLevel, int windowBits, int memLevel) {
        this(version, SpdyHeaderBlockEncoder.newInstance(
                    version, compressionLevel, windowBits, memLevel));
    }

    protected SpdyFrameEncoder(SpdyVersion version, SpdyHeaderBlockEncoder headerBlockEncoder) {
        if (version == null) {
            throw new NullPointerException("version");
        }
        this.version = version.getVersion();
        this.headerBlockEncoder = headerBlockEncoder;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                headerBlockEncoder.end();
            }
        });
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, SpdyFrame msg, ByteBuf out) throws Exception {
        if (msg instanceof SpdyDataFrame) {

            SpdyDataFrame spdyDataFrame = (SpdyDataFrame) msg;
            ByteBuf data = spdyDataFrame.content();
            byte flags = spdyDataFrame.isLast() ? SPDY_DATA_FLAG_FIN : 0;
            out.ensureWritable(SPDY_HEADER_SIZE + data.readableBytes());
            out.writeInt(spdyDataFrame.getStreamId() & 0x7FFFFFFF);
            out.writeByte(flags);
            out.writeMedium(data.readableBytes());
            out.writeBytes(data, data.readerIndex(), data.readableBytes());

        } else if (msg instanceof SpdySynStreamFrame) {

            SpdySynStreamFrame spdySynStreamFrame = (SpdySynStreamFrame) msg;
            ByteBuf data = headerBlockEncoder.encode(ctx, spdySynStreamFrame);
            try {
                byte flags = spdySynStreamFrame.isLast() ? SPDY_FLAG_FIN : 0;
                if (spdySynStreamFrame.isUnidirectional()) {
                    flags |= SPDY_FLAG_UNIDIRECTIONAL;
                }
                int headerBlockLength = data.readableBytes();
                int length = 10 + headerBlockLength;
                out.ensureWritable(SPDY_HEADER_SIZE + length);
                out.writeShort(version | 0x8000);
                out.writeShort(SPDY_SYN_STREAM_FRAME);
                out.writeByte(flags);
                out.writeMedium(length);
                out.writeInt(spdySynStreamFrame.getStreamId());
                out.writeInt(spdySynStreamFrame.getAssociatedToStreamId());
                out.writeShort((spdySynStreamFrame.getPriority() & 0xFF) << 13);
                out.writeBytes(data, data.readerIndex(), headerBlockLength);
            } finally {
                data.release();
            }

        } else if (msg instanceof SpdySynReplyFrame) {

            SpdySynReplyFrame spdySynReplyFrame = (SpdySynReplyFrame) msg;
            ByteBuf data = headerBlockEncoder.encode(ctx, spdySynReplyFrame);
            try {
                byte flags = spdySynReplyFrame.isLast() ? SPDY_FLAG_FIN : 0;
                int headerBlockLength = data.readableBytes();
                int length = 4 + headerBlockLength;
                out.ensureWritable(SPDY_HEADER_SIZE + length);
                out.writeShort(version | 0x8000);
                out.writeShort(SPDY_SYN_REPLY_FRAME);
                out.writeByte(flags);
                out.writeMedium(length);
                out.writeInt(spdySynReplyFrame.getStreamId());
                out.writeBytes(data, data.readerIndex(), headerBlockLength);
            } finally {
                data.release();
            }

        } else if (msg instanceof SpdyRstStreamFrame) {

            SpdyRstStreamFrame spdyRstStreamFrame = (SpdyRstStreamFrame) msg;
            out.ensureWritable(SPDY_HEADER_SIZE + 8);
            out.writeShort(version | 0x8000);
            out.writeShort(SPDY_RST_STREAM_FRAME);
            out.writeInt(8);
            out.writeInt(spdyRstStreamFrame.getStreamId());
            out.writeInt(spdyRstStreamFrame.getStatus().getCode());

        } else if (msg instanceof SpdySettingsFrame) {

            SpdySettingsFrame spdySettingsFrame = (SpdySettingsFrame) msg;
            byte flags = spdySettingsFrame.clearPreviouslyPersistedSettings() ?
                SPDY_SETTINGS_CLEAR : 0;
            Set<Integer> IDs = spdySettingsFrame.getIds();
            int numEntries = IDs.size();
            int length = 4 + numEntries * 8;
            out.ensureWritable(SPDY_HEADER_SIZE + length);
            out.writeShort(version | 0x8000);
            out.writeShort(SPDY_SETTINGS_FRAME);
            out.writeByte(flags);
            out.writeMedium(length);
            out.writeInt(numEntries);
            for (Integer id: IDs) {
                byte ID_flags = 0;
                if (spdySettingsFrame.isPersistValue(id)) {
                    ID_flags |= SPDY_SETTINGS_PERSIST_VALUE;
                }
                if (spdySettingsFrame.isPersisted(id)) {
                    ID_flags |= SPDY_SETTINGS_PERSISTED;
                }
                out.writeByte(ID_flags);
                out.writeMedium(id);
                out.writeInt(spdySettingsFrame.getValue(id));
            }

        } else if (msg instanceof SpdyPingFrame) {

            SpdyPingFrame spdyPingFrame = (SpdyPingFrame) msg;
            out.ensureWritable(SPDY_HEADER_SIZE + 4);
            out.writeShort(version | 0x8000);
            out.writeShort(SPDY_PING_FRAME);
            out.writeInt(4);
            out.writeInt(spdyPingFrame.getId());

        } else if (msg instanceof SpdyGoAwayFrame) {

            SpdyGoAwayFrame spdyGoAwayFrame = (SpdyGoAwayFrame) msg;
            out.ensureWritable(SPDY_HEADER_SIZE + 8);
            out.writeShort(version | 0x8000);
            out.writeShort(SPDY_GOAWAY_FRAME);
            out.writeInt(8);
            out.writeInt(spdyGoAwayFrame.getLastGoodStreamId());
            out.writeInt(spdyGoAwayFrame.getStatus().getCode());

        } else if (msg instanceof SpdyHeadersFrame) {

            SpdyHeadersFrame spdyHeadersFrame = (SpdyHeadersFrame) msg;
            ByteBuf data = headerBlockEncoder.encode(ctx, spdyHeadersFrame);
            try {
                byte flags = spdyHeadersFrame.isLast() ? SPDY_FLAG_FIN : 0;
                int headerBlockLength = data.readableBytes();
                int length = 4 + headerBlockLength;
                out.ensureWritable(SPDY_HEADER_SIZE + length);
                out.writeShort(version | 0x8000);
                out.writeShort(SPDY_HEADERS_FRAME);
                out.writeByte(flags);
                out.writeMedium(length);
                out.writeInt(spdyHeadersFrame.getStreamId());
                out.writeBytes(data, data.readerIndex(), headerBlockLength);
            } finally {
                data.release();
            }

        } else if (msg instanceof SpdyWindowUpdateFrame) {

            SpdyWindowUpdateFrame spdyWindowUpdateFrame = (SpdyWindowUpdateFrame) msg;
            out.ensureWritable(SPDY_HEADER_SIZE + 8);
            out.writeShort(version | 0x8000);
            out.writeShort(SPDY_WINDOW_UPDATE_FRAME);
            out.writeInt(8);
            out.writeInt(spdyWindowUpdateFrame.getStreamId());
            out.writeInt(spdyWindowUpdateFrame.getDeltaWindowSize());
        } else {
            throw new UnsupportedMessageTypeException(msg);
        }
    }
}
