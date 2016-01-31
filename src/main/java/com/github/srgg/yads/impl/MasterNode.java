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
package com.github.srgg.yads.impl;

import com.github.srgg.yads.impl.api.Chain;
import com.github.srgg.yads.impl.api.context.MasterNodeContext;
import com.github.srgg.yads.impl.util.GenericChain;
import com.github.srgg.yads.api.message.Messages;
import com.github.srgg.yads.api.messages.ControlMessage;
import com.github.srgg.yads.impl.api.context.Subscribe;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 *  @author Sergey Galkin <srggal at gmail dot com>
 */
public class MasterNode extends AbstractNode<MasterNodeContext> {
    private final Map<String, String> nodeStates = new HashMap<>();
    private StateAwareChain chain;

    public static class NodeInfo extends HashMap<String, Object> {
    }

    public MasterNode(final String nodeId) {
        super(nodeId, Messages.NodeType.Master);
        chain = new StateAwareChain();
        chain.registerHandler(new GenericChain.ChainListener<NodeInfo>() {

            /**
             * @apiNote It should be the only place that sends {@link ControlMessage} to the storage nodes.
             */
            @Override
            public void onNodeChanged(final ActionType action, final Chain.INodeInfo<NodeInfo> node,
                                      final Chain.INodeInfo<NodeInfo> prevNode,
                                      final Chain.INodeInfo<NodeInfo> nextNode) throws Exception {
                switch (action) {
                    case NodeAdded:
                        final ControlMessage.Builder builderA = mkSetChainBuilder(node, prevNode, nextNode);

                        /**
                         * There is a case that can't be handled by ControlMessage.Builder in a smart manner:
                         *   first chain node
                         */
                        if (node.isHead() && node.isTail()) {
                            assert nextNode == null;
                            assert prevNode == null;

                            builderA
                                .setType(EnumSet.allOf(ControlMessage.Type.class))
                                .setRoles(EnumSet.of(ControlMessage.Role.Head, ControlMessage.Role.Tail))
                                .setState(StorageNode.StorageState.RUNNING);

                        } else {
                            builderA.setState(StorageNode.StorageState.RECOVERING);
                        }

                        context().manageNode(builderA, node.getId());

                        // -- reconfigure adjacent nodes
                        if (prevNode != null) {

                            assert node.equals(prevNode.nextNode());

                            final ControlMessage.Builder bPrev = mkSetChainBuilder(
                                    prevNode,
                                    prevNode.prevNode(),
                                    prevNode.nextNode()
                                );

                            context().manageNode(bPrev, prevNode.getId());
                        }

                        if (nextNode != null) {

                            assert node.equals(nextNode.prevNode());

                            final ControlMessage.Builder bNext = mkSetChainBuilder(
                                    nextNode,
                                    nextNode.prevNode(),
                                    nextNode.nextNode()
                                );

                            context().manageNode(bNext, nextNode.getId());
                        }

                        logger().info("[NODE CHAINED] '{}' was added to the chain", node);
                        break;

                    case NodeStateChanged:
                        switch (StorageNode.StorageState.valueOf(node.state())) {
                            case RECOVERED:
                                final ControlMessage.Builder builderC = mkSetChainBuilder(node, prevNode, nextNode)
                                        .setState(StorageNode.StorageState.RUNNING);

                                context().manageNode(builderC, node.getId());
                                break;

                            default:
                                // do nothing
                        }
                        break;

                    default:
                        throw new UnsupportedOperationException("Unhandled action '" + action + "'");
                }
            }

            private final String id = MasterNode.this.getId();

            @Override
            public String getId() {
                return id;
            }

            private ControlMessage.Builder mkSetChainBuilder(final Chain.INodeInfo<NodeInfo> node,
                                                             final Chain.INodeInfo<NodeInfo> prevNode,
                                                             final Chain.INodeInfo<NodeInfo> nextNode) {
                final String prevNodeId = prevNode == null ? null : prevNode.getId();
                final String nextNodeId = nextNode == null ? null : nextNode.getId();

                assert prevNodeId != null || node.isHead();
                assert nextNodeId != null || node.isTail();

                return new ControlMessage.Builder()
                        .setSender(MasterNode.this.getId())
                        .setPrevNode(prevNodeId)
                        .setNextNode(nextNodeId);
            }

        });
    }

    protected void onNewNode(final String nodeId, final Messages.NodeType type, final String state) {
        logger().info("[NEW NODE] Node '{}'@{} in state '{}'", nodeId, type, state);
    }

    private static StorageNode.StorageState asStorageState(final String state) {
        return StorageNode.StorageState.valueOf(state);
    }

    @Subscribe
    public void onNodeState(final String nodeId, final Messages.NodeType nodeType,
                            final String state) throws Exception {
        synchronized (nodeStates) {
            final String old;

            final boolean isChanged;
            old = nodeStates.put(nodeId, state);

            if (old == null) {
                onNewNode(nodeId, nodeType, state);
                isChanged = true;
            } else {
                isChanged = !state.equals(old);
                if (isChanged) {
                    logger().info("[NODE State] Node '{}'@{} state was changed: '{}' -> '{}'",
                            getId(), nodeType, old, state);

                    nodeStates.put(nodeId, state);
                }
            }

            if (isChanged && Messages.NodeType.Storage.equals(nodeType)) {
                final StorageNode.StorageState newState = asStorageState(state);
                final StorageNode.StorageState oldState = old == null ? null : asStorageState(old);
                chain.handleStateChanged(nodeId, oldState, newState);
            }
        }
    }

    protected static class StateAwareChain extends GenericChain<NodeInfo> {
        public void handleStateChanged(final String nodeId, final StorageNode.StorageState oldstate,
                                       final StorageNode.StorageState newState) throws Exception {
            // Adjust chain
            switch (newState) {
                case NEW:
                    throw new IllegalStateException(
                            String.format("Somehow 'NEW' state is received from node '%s', "
                                    + "whereas 'STARTED' state is expected", nodeId)
                    );

                case STARTED:
                    addNode(nodeId, newState.name(), null);
                    break;

                case FAILED:
                    removeNode(nodeId, newState.name());
                    break;

                case RECOVERING:
                    fireNodeStateChanged(nodeId, newState.name());
                    break;

                case RECOVERED:
                    fireNodeStateChanged(nodeId, newState.name());
                    break;

                case RUNNING:
                    fireNodeStateChanged(nodeId, newState.name());
                    break;

                default:
                    throw new IllegalStateException(
                            String.format("Node '%s' has unhandled newState '%s'", nodeId, newState)
                    );
            }
        }
    }

    public Chain<NodeInfo> chain() {
        return chain;
    }
}

