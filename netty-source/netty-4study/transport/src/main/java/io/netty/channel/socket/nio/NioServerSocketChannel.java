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
package io.netty.channel.socket.nio;

import io.netty.channel.ChannelException;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.nio.AbstractNioMessageChannel;
import io.netty.channel.socket.DefaultServerSocketChannelConfig;
import io.netty.channel.socket.ServerSocketChannelConfig;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;

/**
 * A {@link io.netty.channel.socket.ServerSocketChannel} implementation which uses
 * NIO selector based implementation to accept new connections.
 */

public class NioServerSocketChannel extends AbstractNioMessageChannel
                             implements io.netty.channel.socket.ServerSocketChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private final ServerSocketChannelConfig config;

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioServerSocketChannel.class);



    //创建ServerSocketChannel实例
    private static ServerSocketChannel newSocket() {
        try {
            return ServerSocketChannel.open();
        } catch (IOException e) {
            throw new ChannelException(
                    "Failed to open a server socket.", e);
        }
    }



    /**
     * Create a new instance
     */
    public NioServerSocketChannel() {

        //调用父类的构造函数
        //todo:core 这里构造函数的第三个参数，是设置的NioServerSocketChannel注册的selector感兴趣的selectionkey值即属性readInterestOp
        //因为这里是ServerSocket，所以设置的是OP_ACCEPT,在调用unsafe的beginRead设置selectionKey.interestOps(interestOps |  readInterestOp）;
        super(null, newSocket(), SelectionKey.OP_ACCEPT);
        config = new DefaultServerSocketChannelConfig(this, javaChannel().socket());
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public ServerSocketChannelConfig config() {
        return config;
    }

    @Override
    public boolean isActive() {
        return javaChannel().socket().isBound();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return null;
    }

    @Override
    protected ServerSocketChannel javaChannel() {
        return (ServerSocketChannel) super.javaChannel();
    }


    @Override
    protected SocketAddress localAddress0() {
        return javaChannel().socket().getLocalSocketAddress();
    }


    //调用ServerSocketChannel的bind方法
    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        //
        javaChannel().socket().bind(localAddress, config.getBacklog());
    }

    @Override
    protected void doClose() throws Exception {
        javaChannel().close();
    }


    //todo:核心的NioServerSokcetChannel  accept客户端连接的方法
    @Override
    protected int doReadMessages(List<Object> buf) throws Exception {
        //1.accept客户端的连接,并产生对应的一个cLient SocketChannel
        SocketChannel ch = javaChannel().accept();
        try {
            if (ch != null) {
                /**
                 * 2.创建一个NioSocketChannel，通过构造函数，把对应的ServerSocketChannel和产生的新的SocketChannel传入进去
                 * 同时把产生的(new NioSocketChannel(this, ch)放入到List<Object> buf
                 */
                buf.add(new NioSocketChannel(this, ch));
                return 1;
            }
        } catch (Throwable t) {
            logger.warn("Failed to create a new channel from an accepted socket.", t);

            try {
                ch.close();
            } catch (Throwable t2) {
                logger.warn("Failed to close a socket.", t2);
            }
        }

        return 0;
    }

    // Unnecessary stuff
    @Override
    protected boolean doConnect(
            SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doFinishConnect() throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return null;
    }

    @Override
    protected void doDisconnect() throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean doWriteMessage(Object msg, ChannelOutboundBuffer in) throws Exception {
        throw new UnsupportedOperationException();
    }
}
