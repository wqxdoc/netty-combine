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
package io.netty.channel.nio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.FileRegion;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * {@link AbstractNioChannel} base class for {@link Channel}s that operate on bytes.
 */
public abstract class AbstractNioByteChannel extends AbstractNioChannel {
    private Runnable flushTask;

    /**
     * Create a new instance
     *
     * @param parent            the parent {@link Channel} by which this instance was created. May be {@code null}
     * @param ch                the underlying {@link SelectableChannel} on which it operates
     */
    protected AbstractNioByteChannel(Channel parent, SelectableChannel ch) {

        //todo: core &  think about: 为什么NioSocektChannel是设置OP_READ
        super(parent, ch, SelectionKey.OP_READ);
    }

    @Override
    protected AbstractNioUnsafe newUnsafe() {
        return new NioByteUnsafe();
    }

    private final class NioByteUnsafe extends AbstractNioUnsafe {
        private RecvByteBufAllocator.Handle allocHandle;

        private void removeReadOp() {
            SelectionKey key = selectionKey();
            int interestOps = key.interestOps();
            if ((interestOps & readInterestOp) != 0) {
                // only remove readInterestOp if needed
                key.interestOps(interestOps & ~readInterestOp);
            }
        }

        private void closeOnRead(ChannelPipeline pipeline) {
            SelectionKey key = selectionKey();
            setInputShutdown();
            if (isOpen()) {
                if (Boolean.TRUE.equals(config().getOption(ChannelOption.ALLOW_HALF_CLOSURE))) {
                    key.interestOps(key.interestOps() & ~readInterestOp);
                    pipeline.fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
                } else {
                    close(voidPromise());
                }
            }
        }

        private void handleReadException(ChannelPipeline pipeline, ByteBuf byteBuf, Throwable cause, boolean close) {
            if (byteBuf != null) {
                if (byteBuf.isReadable()) {
                    pipeline.fireChannelRead(byteBuf);
                } else {
                    byteBuf.release();
                }
            }
            pipeline.fireChannelReadComplete();
            pipeline.fireExceptionCaught(cause);
            if (close || cause instanceof IOException) {
                closeOnRead(pipeline);
            }
        }

        @Override
        public void read() {

            //得到配置信息
            final ChannelConfig config = config();
            final ChannelPipeline pipeline = pipeline();
            final ByteBufAllocator allocator = config.getAllocator();
            final int maxMessagesPerRead = config.getMaxMessagesPerRead();
            RecvByteBufAllocator.Handle allocHandle = this.allocHandle;
            if (allocHandle == null) {
                this.allocHandle = allocHandle = config.getRecvByteBufAllocator().newHandle();
            }
            if (!config.isAutoRead()) {
                removeReadOp();
            }

            ByteBuf byteBuf = null;
            int messages = 0;
            boolean close = false;
            try {
                int byteBufCapacity = allocHandle.guess();
                int totalReadAmount = 0;
                do {
                    byteBuf = allocator.ioBuffer(byteBufCapacity);
                    int writable = byteBuf.writableBytes();
                    int localReadAmount = doReadBytes(byteBuf);
                    if (localReadAmount <= 0) {
                        // not was read release the buffer
                        byteBuf.release();
                        close = localReadAmount < 0;
                        break;
                    }

                    //触发fireChannelRead事件
                    pipeline.fireChannelRead(byteBuf);
                    byteBuf = null;

                    if (totalReadAmount >= Integer.MAX_VALUE - localReadAmount) {
                        // Avoid overflow.
                        totalReadAmount = Integer.MAX_VALUE;
                        break;
                    }

                    totalReadAmount += localReadAmount;
                    if (localReadAmount < writable) {
                        // Read less than what the buffer can hold,
                        // which might mean we drained the recv buffer completely.
                        break;
                    }
                } while (++ messages < maxMessagesPerRead);

                pipeline.fireChannelReadComplete();
                allocHandle.record(totalReadAmount);

                if (close) {
                    closeOnRead(pipeline);
                    close = false;
                }
            } catch (Throwable t) {
                handleReadException(pipeline, byteBuf, t, close);
            }
        }
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        int writeSpinCount = -1;

        for (;;) {

            //检查当前outboundBuffer是否有数据
            Object msg = in.current();
            if (msg == null) {
                // Wrote all messages.
                clearOpWrite();
                break;
            }

             //检查消息实例msg的类型是否一个字节缓冲ByteBuf类型
            if (msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;

                //检查msg的可读字节数
                int readableBytes = buf.readableBytes();
                if (readableBytes == 0) {
                    in.remove();
                    continue;
                }

                if (!buf.isDirect()) {
                    ByteBufAllocator alloc = alloc();
                    if (alloc.isDirectBufferPooled()) {
                        // Non-direct buffers are copied into JDK's own internal direct buffer on every I/O.
                        // We can do a better job by using our pooled allocator. If the current allocator does not
                        // pool a direct buffer, we rely on JDK's direct buffer pool.
                        buf = alloc.directBuffer(readableBytes).writeBytes(buf);
                        in.current(buf);
                    }
                }

                boolean setOpWrite = false;
                boolean done = false;
                long flushedAmount = 0;
                if (writeSpinCount == -1) {
                    writeSpinCount = config().getWriteSpinCount();
                }
                for (int i = writeSpinCount - 1; i >= 0; i --) {
                    //调用真正写数据的doWriteBytes方法
                    int localFlushedAmount = doWriteBytes(buf);
                    if (localFlushedAmount == 0) {
                        setOpWrite = true;
                        break;
                    }

                    flushedAmount += localFlushedAmount;

                    if (!buf.isReadable()) {
                        done = true;
                        break;
                    }
                }

                in.progress(flushedAmount);

                if (done) {
                    in.remove();
                } else {
                    incompleteWrite(setOpWrite);
                    break;
                }
            } else if (msg instanceof FileRegion) {
                FileRegion region = (FileRegion) msg;
                boolean setOpWrite = false;
                boolean done = false;
                long flushedAmount = 0;
                if (writeSpinCount == -1) {
                    writeSpinCount = config().getWriteSpinCount();
                }
                for (int i = writeSpinCount - 1; i >= 0; i --) {
                    long localFlushedAmount = doWriteFileRegion(region);
                    if (localFlushedAmount == 0) {
                        setOpWrite = true;
                        break;
                    }

                    flushedAmount += localFlushedAmount;
                    if (region.transfered() >= region.count()) {
                        done = true;
                        break;
                    }
                }

                in.progress(flushedAmount);

                if (done) {
                    in.remove();
                } else {
                    incompleteWrite(setOpWrite);
                    break;
                }
            } else {
                throw new UnsupportedOperationException("unsupported message type: " + StringUtil.simpleClassName(msg));
            }
        }
    }

    protected final void incompleteWrite(boolean setOpWrite) {
        // Did not write completely.
        if (setOpWrite) {
            setOpWrite();
        } else {
            // Schedule flush again later so other tasks can be picked up in the meantime
            Runnable flushTask = this.flushTask;
            if (flushTask == null) {
                flushTask = this.flushTask = new Runnable() {
                    @Override
                    public void run() {
                        flush();
                    }
                };
            }
            eventLoop().execute(flushTask);
        }
    }

    /**
     * Write a {@link FileRegion}
     *
     * @param region        the {@link FileRegion} from which the bytes should be written
     * @return amount       the amount of written bytes
     */
    protected abstract long doWriteFileRegion(FileRegion region) throws Exception;

    /**
     * Read bytes into the given {@link ByteBuf} and return the amount.
     */
    protected abstract int doReadBytes(ByteBuf buf) throws Exception;

    /**
     * Write bytes form the given {@link ByteBuf} to the underlying {@link java.nio.channels.Channel}.
     * @param buf           the {@link ByteBuf} from which the bytes should be written
     * @return amount       the amount of written bytes
     */
    protected abstract int doWriteBytes(ByteBuf buf) throws Exception;




    //todo:core 真正给channel的Selectionkey设置OP_WRITE的方法
    protected final void setOpWrite() {
        final SelectionKey key = selectionKey();
        final int interestOps = key.interestOps();
        if ((interestOps & SelectionKey.OP_WRITE) == 0) {
            key.interestOps(interestOps | SelectionKey.OP_WRITE);
        }
    }

    protected final void clearOpWrite() {
        final SelectionKey key = selectionKey();
        final int interestOps = key.interestOps();
        if ((interestOps & SelectionKey.OP_WRITE) != 0) {
            key.interestOps(interestOps & ~SelectionKey.OP_WRITE);
        }
    }
}
