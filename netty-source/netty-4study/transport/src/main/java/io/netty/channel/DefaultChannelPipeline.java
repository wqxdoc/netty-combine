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
package io.netty.channel;

import io.netty.channel.Channel.Unsafe;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * The default {@link ChannelPipeline} implementation.  It is usually created
 * by a {@link Channel} implementation when the {@link Channel} is created.
 */

/**
 * TODO:  默认的ChannelPipeline实现类，所有的Handler都是添加到该管道
 * 要好好认真读这个类
 */
final class DefaultChannelPipeline implements ChannelPipeline {

    static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultChannelPipeline.class);

    @SuppressWarnings("unchecked")
    private static final WeakHashMap<Class<?>, String>[] nameCaches =
            new WeakHashMap[Runtime.getRuntime().availableProcessors()]; //handler名字的cache数组，该数组的长度默认为CPU的核数

    static {
        for (int i = 0; i < nameCaches.length; i++) {
            nameCaches[i] = new WeakHashMap<Class<?>, String>();
        }
    }

    final AbstractChannel channel;

    final DefaultChannelHandlerContext head;
    final DefaultChannelHandlerContext tail;

    private final Map<String, DefaultChannelHandlerContext> name2ctx =
            new HashMap<String, DefaultChannelHandlerContext>(4); //key :handler的名字  value:ChannelHandlerContext

    //TODO:还没有弄懂这个map的作用
    final Map<EventExecutorGroup, EventExecutor> childExecutors =
            new IdentityHashMap<EventExecutorGroup, EventExecutor>();

    public DefaultChannelPipeline(AbstractChannel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        this.channel = channel;

        //TailHandler是DefaultChannelPipeline的一个默认实现ChannelInboundHandler的一个内部类
        TailHandler tailHandler = new TailHandler();
        tail = new DefaultChannelHandlerContext(this, null, generateName(tailHandler), tailHandler);


        //HeadHandler是DefaultChannelPipeline的一个默认实现ChannelOutboundHandler的一个内部类
        HeadHandler headHandler = new HeadHandler(channel.unsafe());
        head = new DefaultChannelHandlerContext(this, null, generateName(headHandler), headHandler);

        head.next = tail;
        tail.prev = head;
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public ChannelPipeline addFirst(String name, ChannelHandler handler) {
        return addFirst(null, name, handler);
    }


    @Override
    public ChannelPipeline addFirst(EventExecutorGroup group, final String name, ChannelHandler handler) {
        synchronized (this) {
            //检查是否已添加同名的handler
            checkDuplicateName(name);
            //根据pipline,group,name,handler构建出一个新的DefaultChannelHandlerContext
            DefaultChannelHandlerContext newCtx = new DefaultChannelHandlerContext(this, group, name, handler);
            //添加这个newCtx到pipline:通过操作链表，将该handlerContext添加到head的后面
            addFirst0(name, newCtx);
        }

        return this;
    }

    //把参数中的新newCtx添加到head的后面
    private void addFirst0(String name, DefaultChannelHandlerContext newCtx) {
        /**
         * 检查newCtx中的handler是否被重复添加：
         * 对于一个没有加Shareble的hanlder类，如果每次都是new出来不同的对象，是可以重复添加到
         * 同一个pipline的，但是如果是同一个对象实例，是不允许重复添加到同一个pipline的
         */
        checkMultiplicity(newCtx);
        DefaultChannelHandlerContext nextCtx = head.next;
        newCtx.prev = head;
        newCtx.next = nextCtx;
        head.next = newCtx;
        nextCtx.prev = newCtx;

        name2ctx.put(name, newCtx);

        callHandlerAdded(newCtx);
    }

    @Override
    public ChannelPipeline addLast(String name, ChannelHandler handler) {
        return addLast(null, name, handler);
    }


    @Override
    public ChannelPipeline addLast(EventExecutorGroup group, final String name, ChannelHandler handler) {
        synchronized (this) {
            checkDuplicateName(name);
            /**根据pipline,event_loop_group,handler构造出DefaultChannelHandlerContext
             * 并添加到pipline中组装成链表
             */
            DefaultChannelHandlerContext newCtx = new DefaultChannelHandlerContext(this, group, name, handler);
            addLast0(name, newCtx);
        }

        return this;
    }


    //把参数中的新newCtx添加到tail的前面
    private void addLast0(final String name, DefaultChannelHandlerContext newCtx) {
        checkMultiplicity(newCtx);

        DefaultChannelHandlerContext prev = tail.prev;
        newCtx.prev = prev;
        newCtx.next = tail;
        prev.next = newCtx;
        tail.prev = newCtx;

        name2ctx.put(name, newCtx);

        callHandlerAdded(newCtx);
    }

    @Override
    public ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler) {
        return addBefore(null, baseName, name, handler);
    }

    @Override
    public ChannelPipeline addBefore(
            EventExecutorGroup group, String baseName, final String name, ChannelHandler handler) {
        synchronized (this) {
            DefaultChannelHandlerContext ctx = getContextOrDie(baseName);
            checkDuplicateName(name);
            DefaultChannelHandlerContext newCtx = new DefaultChannelHandlerContext(this, group, name, handler);
            addBefore0(name, ctx, newCtx);
        }
        return this;
    }

    private void addBefore0(final String name, DefaultChannelHandlerContext ctx, DefaultChannelHandlerContext newCtx) {
        checkMultiplicity(newCtx);

        newCtx.prev = ctx.prev;
        newCtx.next = ctx;
        ctx.prev.next = newCtx;
        ctx.prev = newCtx;

        name2ctx.put(name, newCtx);

        callHandlerAdded(newCtx);
    }

    @Override
    public ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler) {
        return addAfter(null, baseName, name, handler);
    }


    @Override
    public ChannelPipeline addAfter(
            EventExecutorGroup group, String baseName, final String name, ChannelHandler handler) {
        synchronized (this) {
            DefaultChannelHandlerContext ctx = getContextOrDie(baseName);
            checkDuplicateName(name);
            DefaultChannelHandlerContext newCtx = new DefaultChannelHandlerContext(this, group, name, handler);

            addAfter0(name, ctx, newCtx);
        }

        return this;
    }

    private void addAfter0(final String name, DefaultChannelHandlerContext ctx, DefaultChannelHandlerContext newCtx) {
        //检查是否重名
        checkDuplicateName(name);
        checkMultiplicity(newCtx);

        newCtx.prev = ctx;
        newCtx.next = ctx.next;
        ctx.next.prev = newCtx;
        ctx.next = newCtx;

        name2ctx.put(name, newCtx);

        callHandlerAdded(newCtx);
    }

    @Override
    public ChannelPipeline addFirst(ChannelHandler... handlers) {
        return addFirst(null, handlers);
    }

    @Override
    public ChannelPipeline addFirst(EventExecutorGroup executor, ChannelHandler... handlers) {
        if (handlers == null) {
            throw new NullPointerException("handlers");
        }
        if (handlers.length == 0 || handlers[0] == null) {
            return this;
        }

        int size;
        for (size = 1; size < handlers.length; size++) {
            if (handlers[size] == null) {
                break;
            }
        }

        for (int i = size - 1; i >= 0; i--) {
            ChannelHandler h = handlers[i];
            addFirst(executor, generateName(h), h);
        }

        return this;
    }

    @Override
    public ChannelPipeline addLast(ChannelHandler... handlers) {
        return addLast(null, handlers);
    }

    @Override
    public ChannelPipeline addLast(EventExecutorGroup executor, ChannelHandler... handlers) {
        if (handlers == null) {
            throw new NullPointerException("handlers");
        }

        for (ChannelHandler h : handlers) {
            if (h == null) {
                break;
            }
            addLast(executor, generateName(h), h);
        }

        return this;
    }

    private String generateName(ChannelHandler handler) {
        WeakHashMap<Class<?>, String> cache = nameCaches[(int) (Thread.currentThread().getId() % nameCaches.length)];
        Class<?> handlerType = handler.getClass();
        String name;
        synchronized (cache) {
            name = cache.get(handlerType);
            if (name == null) {
                name = StringUtil.simpleClassName(handlerType) + "#0";
                cache.put(handlerType, name);
            }
        }

        synchronized (this) {
            // It's not very likely for a user to put more than one handler of the same type, but make sure to avoid
            // any name conflicts.  Note that we don't cache the names generated here.
            if (name2ctx.containsKey(name)) {
                String baseName = name.substring(0, name.length() - 1); // Strip the trailing '0'.
                for (int i = 1; ; i++) {
                    String newName = baseName + i;
                    if (!name2ctx.containsKey(newName)) {
                        name = newName;
                        break;
                    }
                }
            }
        }

        return name;
    }

    @Override
    public ChannelPipeline remove(ChannelHandler handler) {
        remove(getContextOrDie(handler));
        return this;
    }

    @Override
    public ChannelHandler remove(String name) {
        return remove(getContextOrDie(name)).handler();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends ChannelHandler> T remove(Class<T> handlerType) {
        return (T) remove(getContextOrDie(handlerType)).handler();
    }

    private DefaultChannelHandlerContext remove(final DefaultChannelHandlerContext ctx) {
        assert ctx != head && ctx != tail;

        DefaultChannelHandlerContext context;
        Future<?> future;

        synchronized (this) {
            if (!ctx.channel().isRegistered() || ctx.executor().inEventLoop()) {
                remove0(ctx);
                return ctx;
            } else {
                future = ctx.executor().submit(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (DefaultChannelPipeline.this) {
                            remove0(ctx);
                        }
                    }
                });
                context = ctx;
            }
        }

        // Run the following 'waiting' code outside of the above synchronized block
        // in order to avoid deadlock

        waitForFuture(future);

        return context;
    }

    void remove0(DefaultChannelHandlerContext ctx) {
        DefaultChannelHandlerContext prev = ctx.prev;
        DefaultChannelHandlerContext next = ctx.next;
        prev.next = next;
        next.prev = prev;
        name2ctx.remove(ctx.name());
        callHandlerRemoved(ctx);
    }

    @Override
    public ChannelHandler removeFirst() {
        if (head.next == tail) {
            throw new NoSuchElementException();
        }
        return remove(head.next).handler();
    }

    @Override
    public ChannelHandler removeLast() {
        if (head.next == tail) {
            throw new NoSuchElementException();
        }
        return remove(tail.prev).handler();
    }

    @Override
    public ChannelPipeline replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler) {
        replace(getContextOrDie(oldHandler), newName, newHandler);
        return this;
    }

    @Override
    public ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler) {
        return replace(getContextOrDie(oldName), newName, newHandler);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ChannelHandler> T replace(
            Class<T> oldHandlerType, String newName, ChannelHandler newHandler) {
        return (T) replace(getContextOrDie(oldHandlerType), newName, newHandler);
    }

    private ChannelHandler replace(
            final DefaultChannelHandlerContext ctx, final String newName,
            ChannelHandler newHandler) {

        assert ctx != head && ctx != tail;

        Future<?> future;
        synchronized (this) {
            boolean sameName = ctx.name().equals(newName);
            if (!sameName) {
                checkDuplicateName(newName);
            }

            final DefaultChannelHandlerContext newCtx =
                    new DefaultChannelHandlerContext(this, ctx.executor, newName, newHandler);

            if (!newCtx.channel().isRegistered() || newCtx.executor().inEventLoop()) {
                replace0(ctx, newName, newCtx);
                return ctx.handler();
            } else {
                future = newCtx.executor().submit(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (DefaultChannelPipeline.this) {
                            replace0(ctx, newName, newCtx);
                        }
                    }
                });
            }
        }

        // Run the following 'waiting' code outside of the above synchronized block
        // in order to avoid deadlock

        waitForFuture(future);

        return ctx.handler();
    }

    private void replace0(DefaultChannelHandlerContext oldCtx, String newName,
                          DefaultChannelHandlerContext newCtx) {
        checkMultiplicity(newCtx);

        DefaultChannelHandlerContext prev = oldCtx.prev;
        DefaultChannelHandlerContext next = oldCtx.next;
        newCtx.prev = prev;
        newCtx.next = next;

        // Finish the replacement of oldCtx with newCtx in the linked list.
        // Note that this doesn't mean events will be sent to the new handler immediately
        // because we are currently at the event handler thread and no more than one handler methods can be invoked
        // at the same time (we ensured that in replace().)
        prev.next = newCtx;
        next.prev = newCtx;

        if (!oldCtx.name().equals(newName)) {
            name2ctx.remove(oldCtx.name());
        }
        name2ctx.put(newName, newCtx);

        // update the reference to the replacement so forward of buffered content will work correctly
        oldCtx.prev = newCtx;
        oldCtx.next = newCtx;

        // Invoke newHandler.handlerAdded() first (i.e. before oldHandler.handlerRemoved() is invoked)
        // because callHandlerRemoved() will trigger inboundBufferUpdated() or flush() on newHandler and those
        // event handlers must be called after handlerAdded().
        callHandlerAdded(newCtx);
        callHandlerRemoved(oldCtx);
    }

    private static void checkMultiplicity(ChannelHandlerContext ctx) {
        ChannelHandler handler = ctx.handler();
        if (handler instanceof ChannelHandlerAdapter) {
            ChannelHandlerAdapter h = (ChannelHandlerAdapter) handler;
            /**
             * 1.判断当前添加的ChannelHandlerContext的handler是否加了注解@Sharable
             * 2.判断handler是否已经被添加过
             * 如果没有添加@Sharable注解，又被添加过，那么将会抛出异常
             * 如果加了@Sharable注解，那么这个handler可以被多次添加
             */
            if (!h.isSharable() && h.added) {
                throw new ChannelPipelineException(
                        h.getClass().getName() +
                                " is not a @Sharable handler, so can't be added or removed multiple times.");
            }
            h.added = true;
        }
    }

    private void callHandlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isRegistered() && !ctx.executor().inEventLoop()) {
            ctx.executor().execute(new Runnable() {
                @Override
                public void run() {
                    callHandlerAdded0(ctx);
                }
            });
            return;
        }
        callHandlerAdded0(ctx);
    }

    private void callHandlerAdded0(final ChannelHandlerContext ctx) {
        try {
            ctx.handler().handlerAdded(ctx);
        } catch (Throwable t) {
            boolean removed = false;
            try {
                remove((DefaultChannelHandlerContext) ctx);
                removed = true;
            } catch (Throwable t2) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Failed to remove a handler: " + ctx.name(), t2);
                }
            }

            if (removed) {
                fireExceptionCaught(new ChannelPipelineException(
                        ctx.handler().getClass().getName() +
                                ".handlerAdded() has thrown an exception; removed.", t));
            } else {
                fireExceptionCaught(new ChannelPipelineException(
                        ctx.handler().getClass().getName() +
                                ".handlerAdded() has thrown an exception; also failed to remove.", t));
            }
        }
    }

    private void callHandlerRemoved(final DefaultChannelHandlerContext ctx) {
        if (ctx.channel().isRegistered() && !ctx.executor().inEventLoop()) {
            ctx.executor().execute(new Runnable() {
                @Override
                public void run() {
                    callHandlerRemoved0(ctx);
                }
            });
            return;
        }
        callHandlerRemoved0(ctx);
    }

    private void callHandlerRemoved0(final DefaultChannelHandlerContext ctx) {
        // Notify the complete removal.
        try {
            ctx.handler().handlerRemoved(ctx);
            ctx.setRemoved();
        } catch (Throwable t) {
            fireExceptionCaught(new ChannelPipelineException(
                    ctx.handler().getClass().getName() + ".handlerRemoved() has thrown an exception.", t));
        }
    }

    /**
     * Waits for a future to finish.  If the task is interrupted, then the current thread will be interrupted.
     * It is expected that the task performs any appropriate locking.
     * <p>
     * If the internal call throws a {@link Throwable}, but it is not an instance of {@link Error} or
     * {@link RuntimeException}, then it is wrapped inside a {@link ChannelPipelineException} and that is
     * thrown instead.</p>
     *
     * @param future wait for this future
     * @throws Error                    if the task threw this.
     * @throws RuntimeException         if the task threw this.
     * @throws ChannelPipelineException with a {@link Throwable} as a cause, if the task threw another type of
     *                                  {@link Throwable}.
     * @see Future#get()
     */
    private static void waitForFuture(Future<?> future) {
        try {
            future.get();
        } catch (ExecutionException ex) {
            // In the arbitrary case, we can throw Error, RuntimeException, and Exception
            PlatformDependent.throwException(ex.getCause());
        } catch (InterruptedException ex) {
            // Interrupt the calling thread (note that this method is not called from the event loop)
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public ChannelHandler first() {
        ChannelHandlerContext first = firstContext();
        if (first == null) {
            return null;
        }
        return first.handler();
    }

    @Override
    public ChannelHandlerContext firstContext() {
        DefaultChannelHandlerContext first = head.next;
        if (first == head) {
            return null;
        }
        return head.next;
    }

    @Override
    public ChannelHandler last() {
        DefaultChannelHandlerContext last = tail.prev;
        if (last == head) {
            return null;
        }
        return last.handler();
    }

    @Override
    public ChannelHandlerContext lastContext() {
        DefaultChannelHandlerContext last = tail.prev;
        if (last == head) {
            return null;
        }
        return last;
    }

    @Override
    public ChannelHandler get(String name) {
        ChannelHandlerContext ctx = context(name);
        if (ctx == null) {
            return null;
        } else {
            return ctx.handler();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends ChannelHandler> T get(Class<T> handlerType) {
        ChannelHandlerContext ctx = context(handlerType);
        if (ctx == null) {
            return null;
        } else {
            return (T) ctx.handler();
        }
    }

    @Override
    public ChannelHandlerContext context(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        synchronized (this) {
            return name2ctx.get(name);
        }
    }

    @Override
    public ChannelHandlerContext context(ChannelHandler handler) {
        if (handler == null) {
            throw new NullPointerException("handler");
        }

        DefaultChannelHandlerContext ctx = head.next;
        for (; ; ) {

            if (ctx == null) {
                return null;
            }

            if (ctx.handler() == handler) {
                return ctx;
            }

            ctx = ctx.next;
        }
    }

    @Override
    public ChannelHandlerContext context(Class<? extends ChannelHandler> handlerType) {
        if (handlerType == null) {
            throw new NullPointerException("handlerType");
        }

        DefaultChannelHandlerContext ctx = head.next;
        for (; ; ) {
            if (ctx == null) {
                return null;
            }
            if (handlerType.isAssignableFrom(ctx.handler().getClass())) {
                return ctx;
            }
            ctx = ctx.next;
        }
    }

    @Override
    public List<String> names() {
        List<String> list = new ArrayList<String>();
        DefaultChannelHandlerContext ctx = head.next;
        for (; ; ) {
            if (ctx == null) {
                return list;
            }
            list.add(ctx.name());
            ctx = ctx.next;
        }
    }

    @Override
    public Map<String, ChannelHandler> toMap() {
        Map<String, ChannelHandler> map = new LinkedHashMap<String, ChannelHandler>();
        DefaultChannelHandlerContext ctx = head.next;
        for (; ; ) {
            if (ctx == tail) {
                return map;
            }
            map.put(ctx.name(), ctx.handler());
            ctx = ctx.next;
        }
    }

    @Override
    public Iterator<Map.Entry<String, ChannelHandler>> iterator() {
        return toMap().entrySet().iterator();
    }

    /**
     * Returns the {@link String} representation of this pipeline.
     */
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(StringUtil.simpleClassName(this));
        buf.append('{');
        DefaultChannelHandlerContext ctx = head.next;
        for (; ; ) {
            if (ctx == tail) {
                break;
            }

            buf.append('(');
            buf.append(ctx.name());
            buf.append(" = ");
            buf.append(ctx.handler().getClass().getName());
            buf.append(')');

            ctx = ctx.next;
            if (ctx == tail) {
                break;
            }

            buf.append(", ");
        }
        buf.append('}');
        return buf.toString();
    }

    @Override
    public ChannelPipeline fireChannelRegistered() {
        head.fireChannelRegistered();
        return this;
    }

    @Override
    public ChannelPipeline fireChannelUnregistered() {
        head.fireChannelUnregistered();

        // Remove all handlers sequentially if channel is closed and unregistered.
        if (!channel.isOpen()) {
            teardownAll();
        }
        return this;
    }

    /**
     * Removes all handlers from the pipeline one by one from tail (exclusive) to head (inclusive) to trigger
     * handlerRemoved().  Note that the tail handler is excluded because it's neither an outbound handler nor it
     * does anything in handlerRemoved().
     */
    private void teardownAll() {
        tail.prev.teardown();
    }


    //当一个Channel标示为active的，那么代表已经有client连接上了
    //todo：think about: 当ServerSocket刚刚监听端口bind的时候，这里会触发一次fireChannelActive事件
    //从head-->tail的顺序，寻找inbound并依次触发
    @Override
    public ChannelPipeline fireChannelActive() {
        //从pipline的head开始触发ChannelActive事件
        head.fireChannelActive();
        //判断isAutoRead的值，如果为true(默认值为true),则自动调用read
        if (channel.config().isAutoRead()) {
            channel.read();
        }
        return this;
    }

    @Override
    public ChannelPipeline fireChannelInactive() {
        head.fireChannelInactive();
        return this;
    }

    @Override
    public ChannelPipeline fireExceptionCaught(Throwable cause) {
        head.fireExceptionCaught(cause);
        return this;
    }

    @Override
    public ChannelPipeline fireUserEventTriggered(Object event) {
        head.fireUserEventTriggered(event);
        return this;
    }



    @Override
    public ChannelPipeline fireChannelRead(Object msg) {
        head.fireChannelRead(msg);
        return this;
    }

    @Override
    public ChannelPipeline fireChannelReadComplete() {
        head.fireChannelReadComplete();
        if (channel.config().isAutoRead()) {
            read();
        }
        return this;
    }

    @Override
    public ChannelPipeline fireChannelWritabilityChanged() {
        head.fireChannelWritabilityChanged();
        return this;
    }


    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return tail.bind(localAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return tail.connect(remoteAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return tail.connect(remoteAddress, localAddress);
    }

    @Override
    public ChannelFuture disconnect() {
        return tail.disconnect();
    }

    @Override
    public ChannelFuture close() {
        return tail.close();
    }

    @Override
    public ChannelFuture deregister() {
        return tail.deregister();
    }

    @Override
    public ChannelPipeline flush() {
        tail.flush();
        return this;
    }


    /**
     * 所有的与ChannelOutboundHandler方法同名的方法，pinpline的调用链接都是tail ---> head
     * TODO:think: 既然在Outbound操作上，pipline要作为接口的门面，那为什么pipline不直接去继承ChannelHandlerContext相关接口
     */

    //bind事件是从tail(是一个Inbound) --->head(是一个Outbound),寻找Outbound并依次触发
    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        return tail.bind(localAddress, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return tail.connect(remoteAddress, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        return tail.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise promise) {
        return tail.disconnect(promise);
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        return tail.close(promise);
    }

    @Override
    public ChannelFuture deregister(final ChannelPromise promise) {
        return tail.deregister(promise);
    }


    @Override
    public ChannelPipeline read() {
        tail.read();
        return this;
    }

    @Override
    public ChannelFuture write(Object msg) {
        return tail.write(msg);
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        return tail.write(msg, promise);
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        return tail.writeAndFlush(msg, promise);
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        return tail.writeAndFlush(msg);
    }


    private void checkDuplicateName(String name) {
        if (name2ctx.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate handler name: " + name);
        }
    }

    private DefaultChannelHandlerContext getContextOrDie(String name) {
        DefaultChannelHandlerContext ctx = (DefaultChannelHandlerContext) context(name);
        if (ctx == null) {
            throw new NoSuchElementException(name);
        } else {
            return ctx;
        }
    }

    private DefaultChannelHandlerContext getContextOrDie(ChannelHandler handler) {
        DefaultChannelHandlerContext ctx = (DefaultChannelHandlerContext) context(handler);
        if (ctx == null) {
            throw new NoSuchElementException(handler.getClass().getName());
        } else {
            return ctx;
        }
    }

    private DefaultChannelHandlerContext getContextOrDie(Class<? extends ChannelHandler> handlerType) {
        DefaultChannelHandlerContext ctx = (DefaultChannelHandlerContext) context(handlerType);
        if (ctx == null) {
            throw new NoSuchElementException(handlerType.getName());
        } else {
            return ctx;
        }
    }

    // A special catch-all handler that handles both bytes and messages.
    static final class TailHandler implements ChannelInboundHandler {

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.warn(
                    "An exceptionCaught() event was fired, and it reached at the tail of the pipeline. " +
                            "It usually means the last handler in the pipeline did not handle the exception.", cause);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            try {
                logger.debug(
                        "Discarded inbound message {} that reached at the tail of the pipeline. " +
                                "Please check your pipeline configuration.", msg);
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        }
    }


    static final class HeadHandler implements ChannelOutboundHandler {

        protected final Unsafe unsafe;

        protected HeadHandler(Unsafe unsafe) {
            this.unsafe = unsafe;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }



        @Override
        public void bind(
                ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise)
                throws Exception {
            unsafe.bind(localAddress, promise);
        }

        @Override
        public void connect(
                ChannelHandlerContext ctx,
                SocketAddress remoteAddress, SocketAddress localAddress,
                ChannelPromise promise) throws Exception {
            unsafe.connect(remoteAddress, localAddress, promise);
        }

        @Override
        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            unsafe.disconnect(promise);
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            unsafe.close(promise);
        }

        @Override
        public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            unsafe.deregister(promise);
        }

        @Override
        public void read(ChannelHandlerContext ctx) {
            unsafe.beginRead();
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            unsafe.write(msg, promise);
        }

        @Override
        public void flush(ChannelHandlerContext ctx) throws Exception {
            unsafe.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            ctx.fireExceptionCaught(cause);
        }
    }
}
