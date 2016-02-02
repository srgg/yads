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
package com.github.srgg.yads.impl.runtime;

import com.github.srgg.yads.api.ActivationAware;
import com.github.srgg.yads.api.Identifiable;
import com.github.srgg.yads.api.message.Messages;
import com.github.srgg.yads.api.messages.Message;
import com.github.srgg.yads.impl.AbstractNode;
import com.github.srgg.yads.impl.AbstractNodeRuntime;
import com.github.srgg.yads.impl.MasterNode;
import com.github.srgg.yads.impl.StorageNode;
import com.github.srgg.yads.impl.api.Chain;
import com.github.srgg.yads.impl.api.context.NodeContext;
import com.github.srgg.yads.impl.context.MasterNodeExecutionContext;
import com.github.srgg.yads.impl.context.StorageExecutionContext;
import com.github.srgg.yads.impl.util.InMemoryStorage;
import com.github.srgg.yads.impl.context.communication.AbstractTransport;
import com.github.srgg.yads.impl.context.communication.JacksonPayloadMapper;
import com.github.srgg.yads.impl.util.MessageUtils;
import com.github.srgg.yads.impl.util.TaggedLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;

import static com.google.common.base.Preconditions.checkState;

/**
 *  @author Sergey Galkin <srggal at gmail dot com>
 */
public final class LocalRuntime implements ActivationAware {
    private final Logger logger = new TaggedLogger(LoggerFactory.getLogger(LocalRuntime.class), "LocalRuntime: ");
    private final LocalTransport transport = new LocalTransport();

    @Override
    public String getState() {
        return transport.getState();
    }

    @Override
    public void start() throws Exception {
        logger.debug("is about to start");
        transport.start();
        logger.debug("has been STARTED");
    }

    @Override
    public void stop() throws Exception {
        logger.debug("is about to stop");
        transport.stop();
        logger.debug("has been STOPPED");
    }

    @Override
    public String getId() {
        return "local-rt";
    }

    public static class LocalClient implements Identifiable<String> {
        private final String clientId;

        public LocalClient(final String cid) {
            this.clientId = cid;
        }

        public void storeValue(final Object key, final Object value) {
        }

        public Object getValue(final Object key) {
            return null;
        }

        @Override
        public String getId() {
            return clientId;
        }
    }

    public void sendMessage(final String nodeId, final Message.MessageBuilder builder) throws Exception {
        final Message msg = builder
                .setSender("fake-local")
                .build();

        transport.send(nodeId, msg);
    }


    public <M extends Message> M performRequest(final String nodeId, final Message.MessageBuilder builder) throws Exception {
        final String senderId = UUID.randomUUID().toString();

        final SynchronousQueue<Message>  messageQueue = new SynchronousQueue<>(false);

        final AbstractNodeRuntime rt = new AbstractNodeRuntime(transport, null) {
            @Override
            public boolean onMessage(final String recipient, final Messages.MessageTypes type,
                                     final Message message) throws Exception {
                messageQueue.put(message);
                return true;
            }

            @Override
            public String getId() {
                return senderId;
            }
        };

        rt.stateChanged(State.STARTED.name());
        try {
            final Message msg = builder
                    .setSender(senderId)
                    .build();

            transport.send(nodeId, msg);
            //noinspection unchecked
            return (M) messageQueue.take();
        } finally {
            rt.stateChanged(State.STOPPED.name());
        }
    }

    public StorageNode createStorageNode(final String nodeId) throws Exception {
        final StorageNode node = new StorageNode(nodeId, new InMemoryStorage());
        final StorageExecutionContext ctx = new StorageExecutionContext(transport, node);
        node.configure(ctx);
        node.start();
        return node;
    }

    public void nodeShutdown(final String nodeId) {
        final AbstractNode node = transport.getNodeById(nodeId);
        checkState(node != null, "Can't shutdown node '%s', since it doesn't exist", nodeId);
        node.stop();
    }

    public MasterNode createMasterNode(final String nodeId) throws Exception {
        final MasterNode node = new MasterNode(nodeId);
        final MasterNodeExecutionContext ctx = new MasterNodeExecutionContext(transport, node);
        node.configure(ctx);
        node.start();
        return node;
    }

    public List<Chain.INodeInfo<MasterNode.NodeInfo>> waitForCompleteChain() throws InterruptedException {
        final String id = transport.getNearestMaster();
        checkState(id != null, "Can't wait for chain, there is no master at all");
        final MasterNode node = (MasterNode) transport.getNodeById(id);

        for (;;) {
            final Set<StorageExecutionContext> contexts = transport.getNodeContexts(StorageExecutionContext.class);
            final int expected = contexts.size();

            final List<Chain.INodeInfo<MasterNode.NodeInfo>> chain = node.chain().asList();
            if (chain.size() == expected) {
                return chain;
            }
            Thread.sleep(20);
        }
    }

    public List<Chain.INodeInfo<MasterNode.NodeInfo>> waitForRunningChain() throws InterruptedException {
        final List<Chain.INodeInfo<MasterNode.NodeInfo>> r = waitForCompleteChain();

        for (boolean b = false; !b;) {
            Thread.sleep(10);

            for (Chain.INodeInfo<MasterNode.NodeInfo> ni : r) {
                b = StorageNode.StorageState.RUNNING.name().equals(ni.state());
                if (!b) {
                    break;
                }
            }
        }

        return r;
    }


    public Map<String, StorageExecutionContext.NodeState> getAllStorageStates() {
        final Set<StorageExecutionContext> ctxs = transport.getNodeContexts(StorageExecutionContext.class);

        final HashMap<String, StorageExecutionContext.NodeState> r = new HashMap<>(ctxs.size());
        ctxs.forEach((ctx) -> r.put(ctx.getId(), ctx.getNodeState()));
        return r;
    }

    private static class LocalTransport extends AbstractTransport implements ActivationAware {
        private ScheduledExecutorService executor;

        LocalTransport() {
            super(new JacksonPayloadMapper());
            logger().debug("Created");
        }

        private String getMasterId() {
            final Set<MasterNodeExecutionContext> ctxs = getNodeContexts(MasterNodeExecutionContext.class);
            return ctxs.isEmpty() ? null : ctxs.iterator().next().getId();
        }

        protected String getLeader() {
            return getMasterId();
        }

        protected String getNearestMaster() {
            return getMasterId();
        }

        protected <N extends NodeContext> Set<N> getNodeContexts(final Class<N> contextClass) {
            final HashSet<N> r = new HashSet<>();

            forEach((id, h)-> {
                if (contextClass.isInstance(h)) {
                    r.add((N) h);
                }
            });

            return r;
        }

        protected AbstractNode<?> getNodeById(final String nodeId) {
            final AbstractNode<?>[] r = {null};
            forEach((id, h) -> {
                if (h instanceof AbstractNodeRuntime) {
                    final AbstractNode<?> n = ((AbstractNodeRuntime) h).node();

                    if (n.getId().equals(nodeId)) {
                        r[0] = n;
                    }
                }
            });

            return r[0];
        }

        private void sendImpl(final String sender, final String recipient, final ByteBuffer bb) {
            checkState("RUNNING".equals(getState()));

            final AbstractNodeRuntime nrt = (AbstractNodeRuntime) this.handlerById(recipient);
            checkState(nrt != null, "Unknown recipient id:'%s'", recipient);

            executor.submit(() -> {
                final ArrayList<Message> messages = new ArrayList<>(1);
                try {
                    final int i = decode(bb, messages);
                    assert i == -1;
                } catch (Exception e) {
                    // TODO: add hexdump
                    logger().error(
                            String.format("[ERR] receive call failed: '%s' -> '%s'. Binary can't be decoded",
                                    sender, recipient),
                            e
                    );
                }

                checkState(messages.size() == 1);
                final Message msg = messages.get(0);
                try {
                    onReceive(recipient, msg);
                } catch (Exception e) {
                    final byte msgCode = getMessageCodeFor(msg);
                    final Messages.MessageTypes mt = Messages.MessageTypes.valueOf(msgCode);

                    logger().error(
                            MessageUtils.dumpMessage(msg, "[RECEIVE FAILED] '%s' -> '%s': %s@%s",
                                    sender,  recipient, mt, msg.getId()),
                            e
                    );
                }
            });
        }

        @Override
        protected void doSend(final String sender, final String recipient, final ByteBuffer bb) throws Exception {
            switch (recipient) {
                case LEADER_NODE:
                    final String leaderId = getLeader();
                    checkState(leaderId != null);
                    sendImpl(sender, leaderId, bb);
                    break;

                case NEAREST_MASTER:
                    final String masterId = getNearestMaster();
                    checkState(masterId != null);
                    sendImpl(sender, masterId, bb);
                    break;

                case NEAREST_NODE:
                    throw new UnsupportedOperationException();

                default:
                    sendImpl(sender, recipient, bb);
            }
        }

        @Override
        public String getState() {
            return executor == null ? "STOPPED" : "RUNNING";
        }

        @Override
        public void start() throws Exception {
            logger().debug("is about to start");

            executor = Executors.newScheduledThreadPool(10);
            logger().debug("has been STARTED");
        }

        @Override
        public void stop() throws Exception {
            logger().debug("is about to stop");
            final Set<AbstractNodeRuntime> ctxs = getNodeContexts(AbstractNodeRuntime.class);

            for (AbstractNodeRuntime nrt: ctxs) {
                if (nrt.node() != null) {
                    nrt.node().stop();
                } else {
                    nrt.stateChanged(State.STOPPED.name());
                }
            }

            executor.shutdown();
            executor = null;
            logger().debug("has been STOPPED");
        }

        @Override
        public String getId() {
            return "local";
        }
    }

}
