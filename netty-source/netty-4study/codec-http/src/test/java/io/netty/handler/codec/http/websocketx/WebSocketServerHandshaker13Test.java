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
package io.netty.handler.codec.http.websocketx;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.util.ReferenceCountUtil;
import org.junit.Assert;
import org.junit.Test;

import static io.netty.handler.codec.http.HttpHeaders.Values.*;
import static io.netty.handler.codec.http.HttpVersion.*;

public class WebSocketServerHandshaker13Test {

    @Test
    public void testPerformOpeningHandshake() {
        EmbeddedChannel ch = new EmbeddedChannel(
                new HttpObjectAggregator(42), new HttpRequestDecoder(), new HttpResponseEncoder());

        FullHttpRequest req = ReferenceCountUtil.releaseLater(
                new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/chat"));
        req.headers().set(Names.HOST, "server.example.com");
        req.headers().set(Names.UPGRADE, WEBSOCKET.toLowerCase());
        req.headers().set(Names.CONNECTION, "Upgrade");
        req.headers().set(Names.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==");
        req.headers().set(Names.SEC_WEBSOCKET_ORIGIN, "http://example.com");
        req.headers().set(Names.SEC_WEBSOCKET_PROTOCOL, "chat, superchat");
        req.headers().set(Names.SEC_WEBSOCKET_VERSION, "13");

        new WebSocketServerHandshaker13(
                "ws://example.com/chat", "chat", false, Integer.MAX_VALUE).handshake(ch, req);

        ByteBuf resBuf = (ByteBuf) ch.readOutbound();

        EmbeddedChannel ch2 = new EmbeddedChannel(new HttpResponseDecoder());
        ch2.writeInbound(resBuf);
        HttpResponse res = (HttpResponse) ch2.readInbound();

        Assert.assertEquals(
                "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", res.headers().get(Names.SEC_WEBSOCKET_ACCEPT));
        Assert.assertEquals("chat", res.headers().get(Names.SEC_WEBSOCKET_PROTOCOL));
        ReferenceCountUtil.release(res);
    }
}
