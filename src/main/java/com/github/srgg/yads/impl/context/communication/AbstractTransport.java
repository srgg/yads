/*
 * ⁣​
 * YADS
 * ⁣⁣
 * Copyright (C) 2015 - 2016 srgg
 * ⁣⁣
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ​⁣
 */
package com.github.srgg.yads.impl.context.communication;

import com.github.srgg.yads.api.messages.Message;
import com.github.srgg.yads.api.messages.MessageCode;
import com.github.srgg.yads.impl.api.context.CommunicationContext;
import com.github.srgg.yads.impl.api.context.PayloadMapper;
import com.github.srgg.yads.impl.util.GenericSink;
import com.github.srgg.yads.impl.util.MessageUtils;
import org.inferred.freebuilder.shaded.org.apache.commons.lang3.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.srgg.yads.api.message.Messages;

import java.lang.annotation.Annotation;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;


/**
 * @author Sergey Galkin <srggal at gmail dot com>
 */
public abstract class AbstractTransport extends GenericSink<CommunicationContext.MessageListener> implements CommunicationContext {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private HashMap<Byte, Class<? extends Message>> messageBindings = new HashMap<>();
    private final PayloadMapper payloadMapper;

    public AbstractTransport(final PayloadMapper pm) {
        this.payloadMapper = pm;
    }

    protected final Logger logger() {
        return logger;
    }

    @Override
    public final void send(final String recipient, final Message message) throws Exception {
        final byte msgCode = getMessageCodeFor(message);
        final Class c = getMessageClass(msgCode);
        final byte[] payload = payloadMapper.toBytes(c, message);
        final ByteBuffer bb = encode(msgCode, payload);
        doSend(message.getSender(), recipient, bb);

        final Messages.MessageTypes mt = Messages.MessageTypes.valueOf(msgCode);
        if (logger.isTraceEnabled()) {
            logger.trace(
                    MessageUtils.dumpMessage(message, "[MSG Out] '%s' -> '%s': %s@%s",
                            message.getSender(), recipient, mt, message.getId())
                );
        } else {
            logger.debug("[MSG Out] '{}' -> '{}': {}@{}",
            message.getSender(),  recipient, mt, message.getId());
        }
    }

    protected abstract void doSend(String sender, String recipient, ByteBuffer bb) throws Exception;

    protected final int onReceive(final String sender, final String recipient,
                                  final ByteBuffer bb) throws Exception {
        // -- decode
        if (bb.remaining() < 4) {
            return 4;
        }

        bb.mark();
        final int length = bb.getInt();

        if (bb.remaining() < length) {
            bb.reset();
            return length;
        }

        final byte msgCode = bb.get();
        final byte[] p = new byte[length - 1];
        bb.get(p);

        final Class<? extends Message> c = getMessageClass(msgCode);
        final Message msg = payloadMapper.fromBytes(c, p);

        // -- fire
        final Messages.MessageTypes mt = Messages.MessageTypes.valueOf(msgCode);
        if (logger.isTraceEnabled()) {
            logger.trace(
                    MessageUtils.dumpMessage(msg, "[MSG IN] '%s' -> '%s': %s@%s",
                        msg.getSender(),  recipient, mt, msg.getId())
                );
        } else {
            logger.debug("[MSG IN]  '{}' -> '{}': {}@{}", sender, recipient, mt, msg.getId());
        }

        boolean isHandled = true;
        if (recipient != null) {
            final CommunicationContext.MessageListener listener = handlerById(recipient);
            checkState(listener != null, "Can't process received message '%s' was sent by '%s', recipient '%s' is not registered.",
                    msgCode, sender, recipient);

            isHandled = listener.onMessage(recipient, mt, msg);
        } else {
            for (Map.Entry<String, CommunicationContext.MessageListener> e : handlers()) {
                if (!e.getValue().onMessage(null, mt, msg)) {
                    isHandled = false;
                }
            }
        }

        if (!isHandled) {
            unhandledMessage(sender, recipient, mt, msg);
        }

        return -1;
    }

    protected void unhandledMessage(final String sender, final String recipient,
                                    final Messages.MessageTypes mt, final Message msg) {
        logger.warn(
                MessageUtils.dumpMessage(msg, "[MSG Unhandled] '%s' -> '%s': %s@%s", sender,  recipient, mt, msg.getId())
            );
    }

    private ByteBuffer encode(final byte msgCode, final byte[] payload) throws Exception {
        final int length = payload.length + 1;
        final ByteBuffer b = ByteBuffer.allocateDirect(length + 4 /* sizeof(length header) */)
                .putInt(length)
                .put(msgCode)
                .put(payload);

        assert b.remaining() == 0;
        b.flip();
        return b;
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings("NP_NULL_ON_SOME_PATH")
    private static byte getMessageCodeFor(final Message msg) {
        final List<Class<?>> interfaces =  ClassUtils.getAllInterfaces(msg.getClass());

        Annotation mc = null;
        for (Class c: interfaces) {
            mc = c.getAnnotation(MessageCode.class);
            if (mc != null) {
                break;
            }
        }

        checkState(mc != null, "Can't get message code, message '%s' is not marked with @MessageCode", msg.getClass().getSimpleName());
        return (byte) ((MessageCode) mc).value().getNumber();
    }

    private Class<? extends Message> getMessageClass(final byte msgCode) {
        Class<? extends Message> c = messageBindings.get(msgCode);
        if (c == null) {
            // Try to resolve it
            Messages.MessageTypes mt = Messages.MessageTypes.valueOf(msgCode);
            final String str = mt.name();

            try {
                c = (Class<? extends Message>) Class.forName("com.github.srgg.yads.api.messages." + str);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(String.format("Nothing known about the message with code '%s', is not registered", msgCode));
            }
            messageBindings.put(msgCode, c);
        }
        return c;
    }
}
