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
import com.github.srgg.yads.impl.api.context.CommunicationContext;
import com.github.srgg.yads.impl.context.MasterNodeExecutionContext;
import com.github.srgg.yads.impl.context.StorageExecutionContext;
import com.github.srgg.yads.impl.util.InMemoryStorage;
import com.github.srgg.yads.impl.context.communication.AbstractTransport;
import com.github.srgg.yads.impl.context.communication.JacksonPayloadMapper;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.function.BiConsumer;

import static com.google.common.base.Preconditions.checkState;

/**
 *  @author Sergey Galkin <srggal at gmail dot com>
 */
public final class LocalRuntime implements ActivationAware {
    private static LocalTransport transport = new LocalTransport();
    private static ConcurrentHashMap<String, AbstractNode> nodes = new ConcurrentHashMap<>();
    private static HashSet<String> masterIds = new HashSet<>();

    @Override
    public String getState() {
        return transport.getState();
    }

    @Override
    public void start() throws Exception {
        transport.start();
    }

    @Override
    public void stop() throws Exception {
        shutdownAll();
        transport.stop();
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
        nodes.put(nodeId, node);
        return node;
    }

    public void nodeShutdown(final String nodeId) {
        final AbstractNode node = nodes.get(nodeId);
        checkState(node != null, "Can't shutdown node '%s', since it doesn't exist", nodeId);

        node.stop();
        masterIds.remove(nodeId);
        nodes.remove(nodeId);
    }

    private void shutdownAll() {
        for (String nid: nodes.keySet()) {
            nodeShutdown(nid);
        }
        assert nodes.isEmpty();
    }

    public MasterNode createMasterNode(final String nodeId) throws Exception {
        final MasterNode node = new MasterNode(nodeId);
        final MasterNodeExecutionContext ctx = new MasterNodeExecutionContext(transport, node);
        node.configure(ctx);
        node.start();
        nodes.put(nodeId, node);
        masterIds.add(nodeId);
        return node;
    }

    public List<Chain.INodeInfo<MasterNode.NodeInfo>> waitForCompleteChain() throws InterruptedException {
        checkState(!masterIds.isEmpty(), "Can't wait for chain, there is no master at all");
        final String id = masterIds.iterator().next();
        final MasterNode node = (MasterNode) nodes.get(id);

        for (;;) {
            final int expected = nodes.size() - masterIds.size();

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
        final HashMap<String, StorageExecutionContext.NodeState> r = new HashMap<>();
        transport.forEach((id, h) -> {
            if (h instanceof StorageExecutionContext) {
                r.put(id, ((StorageExecutionContext) h).getNodeState());
            }
        });
        return r;
    }

    private static class LocalTransport extends AbstractTransport implements ActivationAware {
        private ScheduledExecutorService executor;

        LocalTransport() {
            super(new JacksonPayloadMapper());
        }

        @SuppressWarnings("PMD.UselessOverridingMethod")
        @Override
        protected void forEach(final BiConsumer<String, CommunicationContext.MessageListener> action) {
            super.forEach(action);
        }


        private void sendImpl(final String sender, final String recipient, final ByteBuffer bb) {
            checkState("STARTED".equals(getState()));

            final AbstractNode n = LocalRuntime.nodes.get(recipient);
            final AbstractNodeRuntime nrt = (AbstractNodeRuntime) this.handlerById(recipient);

            checkState(n != null || nrt != null, "Unknown message recipient with id '%s'", recipient);

            executor.submit(() -> {
                try {
                    onReceive(sender, recipient, bb);
                } catch (Exception e) {
                    logger().error(
                            String.format("[ERR] receive call failed: '{%s}' -> '{%s}'",
                                    sender, recipient),
                            e
                        );
                }
            });
        }

        @Override
        protected void doSend(final String sender, final String recipient, final ByteBuffer bb) throws Exception {
            switch (recipient) {
                case LEADER_NODE:
                case NEAREST_MASTER:
                    checkState(!masterIds.isEmpty());
                    final String masterId = masterIds.iterator().next();
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
            return executor == null ? "STOPPED" : "STARTED";
        }

        @Override
        public void start() throws Exception {
            executor = Executors.newScheduledThreadPool(10);
        }

        @Override
        public void stop() throws Exception {
            executor.shutdown();
            executor = null;
        }

        @Override
        public String getId() {
            return "local-transport";
        }
    }

}
