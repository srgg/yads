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
import com.github.srgg.yads.api.messages.ControlMessage;
import com.github.srgg.yads.impl.AbstractNode;
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

import static com.google.common.base.Preconditions.checkState;

/**
 *  @author Sergey Galkin <srggal at gmail dot com>
 */
public final class LocalRuntime implements ActivationAware {
    private static LocalTransport transport = new LocalTransport();
    private static HashMap<String, StorageExecutionContext> nodeContexts = new HashMap<>();
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

    private static class LocalStorageExecutionContext extends StorageExecutionContext {
        LocalStorageExecutionContext(final CommunicationContext mc, final StorageNode n) {
            super(mc, n);
        }

        protected void doStart() {
            super.doStart();
            nodeContexts.put(getId(), this);
        }

        @Override
        protected void doStop() {
            nodeContexts.remove(getId());
            super.doStop();
        }
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

    public void nodeControl(final String nodeId, final ControlMessage.Builder builder) throws Exception {
        final ControlMessage msg = builder.build();
        transport.send(nodeId, msg);
    }

    public StorageNode createStorageNode(final String nodeId) throws Exception {
        final StorageNode node = new StorageNode(nodeId, new InMemoryStorage());
        final StorageExecutionContext ctx = new LocalStorageExecutionContext(transport, node);
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

        assert nodeContexts.isEmpty();
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
            Thread.sleep(500);
        }
    }

    public Map<String, StorageExecutionContext.NodeState> getAllStorageStates() {
        final HashMap<String, StorageExecutionContext.NodeState> r = new HashMap<>(nodes.size());
        for (StorageExecutionContext n: nodeContexts.values()) {
            r.put(n.getId(), n.getNodeState());
        }
        return r;
    }

    private static class LocalTransport extends AbstractTransport implements ActivationAware {
        private ScheduledExecutorService executor;

        LocalTransport() {
            super(new JacksonPayloadMapper());
        }

        private void sendImpl(final String sender, final String recipient, final ByteBuffer bb) {
            checkState("STARTED".equals(getState()));

            final AbstractNode n = LocalRuntime.nodes.get(recipient);
            checkState(n != null, "Unknown node with id '%s'", recipient);

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
