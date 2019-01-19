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

import io.netty.buffer.Unpooled;
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
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.Assert;
import org.junit.Test;

import static io.netty.handler.codec.http.HttpHeaders.Values.*;
import static io.netty.handler.codec.http.HttpVersion.*;

public class WebSocketServerHandshaker00Test {

    @Test
    public void testPerformOpeningHandshake() {
        EmbeddedChannel ch = new EmbeddedChannel(
                new HttpObjectAggregator(42), new HttpRequestDecoder(), new HttpResponseEncoder());

        FullHttpRequest req = ReferenceCountUtil.releaseLater(new DefaultFullHttpRequest(
                HTTP_1_1, HttpMethod.GET, "/chat", Unpooled.copiedBuffer("^n:ds[4U", CharsetUtil.US_ASCII)));

        req.headers().set(Names.HOST, "server.example.com");
        req.headers().set(Names.UPGRADE, WEBSOCKET.toLowerCase());
        req.headers().set(Names.CONNECTION, "Upgrade");
        req.headers().set(Names.ORIGIN, "http://example.com");
        req.headers().set(Names.SEC_WEBSOCKET_KEY1, "4 @1  46546xW%0l 1 5");
        req.headers().set(Names.SEC_WEBSOCKET_KEY2, "12998 5 Y3 1  .P00");
        req.headers().set(Names.SEC_WEBSOCKET_PROTOCOL, "chat, superchat");

        new WebSocketServerHandshaker00(
                "ws://example.com/chat", "chat", Integer.MAX_VALUE).handshake(ch, req);

        EmbeddedChannel ch2 = new EmbeddedChannel(new HttpResponseDecoder());
        ch2.writeInbound(ch.readOutbound());
        HttpResponse res = (HttpResponse) ch2.readInbound();

        Assert.assertEquals("ws://example.com/chat", res.headers().get(Names.SEC_WEBSOCKET_LOCATION));
        Assert.assertEquals("chat", res.headers().get(Names.SEC_WEBSOCKET_PROTOCOL));
        LastHttpContent content = (LastHttpContent) ch2.readInbound();

        Assert.assertEquals("8jKS'y:G*Co,Wxa-", content.content().toString(CharsetUtil.US_ASCII));
        content.release();
    }
}
