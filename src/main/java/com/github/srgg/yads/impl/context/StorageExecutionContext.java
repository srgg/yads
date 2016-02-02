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
package com.github.srgg.yads.impl.context;

import com.github.srgg.yads.api.messages.*;
import com.github.srgg.yads.impl.AbstractNodeRuntime;
import com.github.srgg.yads.impl.StorageNode;
import com.github.srgg.yads.impl.api.context.CommunicationContext;
import com.github.srgg.yads.impl.api.context.OperationContext;
import com.github.srgg.yads.impl.api.context.StorageNodeContext;
import com.github.srgg.yads.impl.util.MessageUtils;
import com.github.srgg.yads.api.message.Messages;
import com.google.common.annotations.VisibleForTesting;
import org.javatuples.Pair;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkState;

/**
 *  @author Sergey Galkin <srggal at gmail dot com>
 */
public class StorageExecutionContext extends AbstractNodeRuntime<StorageNode> implements StorageNodeContext {
    private static final UUID LOCAL_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private final AtomicReference<NodeState> nodeState = new AtomicReference<>();

    public StorageExecutionContext(final CommunicationContext messageContext, final StorageNode node) {
        super(messageContext, node);
        logger().debug("Created");
    }


    private static class GenericOperationContext<T extends Message, R> implements OperationContext<T, R> {
        private final T operation;
        private final StorageExecutionContext ctx;
        private final UnsafeAcknowledge<T, R> acknowledgeOp;

        @FunctionalInterface
        public interface UnsafeAcknowledge<T extends Message, R> {
            void acknowledge(StorageExecutionContext ctx, T operation, R result) throws Exception;
        }

        protected GenericOperationContext(final StorageExecutionContext context, final T op,
                                          final UnsafeAcknowledge<T, R> doAck) {
            this.operation = op;
            this.ctx = context;
            this.acknowledgeOp = doAck;
        }

        @Override
        public T operation() {
            return operation;
        }

        @Override
        public final void ackOperation(final R result) {
            try {
                acknowledgeOp.acknowledge(ctx, operation, result);
            } catch (Exception ex) {
                ctx.logger().error("Operation acknowledgement was failed", ex);
            }
        }

        @Override
        public void failureOperation(final Exception ex) {
            ctx.logger().error(MessageUtils.dumpMessage(operation,
                    "[ACK-FAILED:%s]  '%s'...", operation.getSender(), operation.getId().toString()), ex);
        }
    }

    private <T extends Message, R> OperationContext<T, R> createOperationContext(final T operation,
                                                final GenericOperationContext.UnsafeAcknowledge<T, R> anckOP) {
        return new GenericOperationContext<>(this, operation, anckOP);
    }

    // TODO: Requires to have default implementation
    @Override
    public OperationContext<StorageOperationRequest, Object> contextFor(final StorageOperationRequest operation) {
        return createOperationContext(operation, (ctx, op, r) -> ctx.sendMessage(operation.getSender(),
                new StorageOperationResponse.Builder()
                    .setObject(r)
        ));
    }

    @Override
    public OperationContext<RecoveryRequest, Pair<Boolean, Map<String, Object>>> contextFor(final RecoveryRequest operation) {
        return createOperationContext(operation, (ctx, op, r) -> ctx.sendMessage(operation.getSender(),
                    new RecoveryResponse.Builder()
                            .setLast(Boolean.TRUE.equals(r.getValue0()))
                            .putAllSnapshot(r.getValue1())
        ));
    }

    @Override
    public OperationContext<RecoveryResponse, Void> contextFor(final RecoveryResponse operation) {
        return createOperationContext(operation, (ctx, op, r) -> {
            if (op.isLast()) {
                logger().info(MessageUtils.dumpMessage(op,
                        "[RECOVERY-COMPLETE:%s]  '%s'...", op.getSender(), op.getId().toString()));

                doNodeStateChange(StorageNode.StorageState.RECOVERED);
            }
        });
    }


    @Override
    public boolean onMessage(final String recipient, final Messages.MessageTypes type,
                             final Message message) throws Exception {

        final NodeState ns = nodeState.get();
        final StorageNode.StorageState state = ns != null ? ns.getState() : null;

        switch (type) {
            case ControlMessage:
                final ControlMessage cm = (ControlMessage) message;

                logger().info(MessageUtils.dumpMessage(message,
                        "[CTRL:%s]  '%s'...", cm.getSender(), cm.getId().toString())
                    );

                updateNodeState(cm);
                break;

            case RecoveryRequest:
                final RecoveryRequest rrm = (RecoveryRequest) message;
                if (StorageNode.StorageState.RUNNING.equals(state)) {
                    logger().info(MessageUtils.dumpMessage(message,
                            "[RECOVERY-REQ:%s]  '%s'...", rrm.getSender(), rrm.getId().toString()));

                    node().onRecoveryRequest(rrm);
                } else {
                    logger().warn("[RECOVERY-REQ:{}]  WILL BE IGNORED due to the inappropriate node state ('{}')."
                            + "\nTo handle Recovery requests node should be in 'RUNNING' state.",
                            message.getSender(), state);
                }
                break;

            case RecoveryResponse:
                final RecoveryResponse rrspm = (RecoveryResponse) message;
                if (StorageNode.StorageState.RECOVERING.equals(state)) {
                    node().onRecoveryResponse(rrspm);
                } else {
                    logger().warn("[RECOVERY-RESP:{}]  WILL BE IGNORED due to the inappropriate node state ('{}')."
                            + "\nTo handle Recovery response node should be in 'RECOVERING' state.",
                            message.getSender(), state);
                }
                break;


            case StorageOperationRequest:
                final StorageOperationRequest sor = (StorageOperationRequest) message;
                node().onStorageRequest(sor);
                break;

            default:
                return false;
        }
        return true;
    }

    public NodeState getNodeState() {
        final NodeState ns = nodeState.get();
        checkState(ns != null);
        return ns;
    }

    private void updateNodeState(final ControlMessage cm) {
        EnumSet<ControlMessage.Role> oldRoles = null;
        boolean chainChanged = false;
        boolean stateChanged = false;

        final NodeState ns = new NodeState();
        final NodeState old = nodeState.get();

        ns.id = cm.getId();
        ns.lastUpdatedBy = cm.getSender();
        ns.lastUpdatedOn = Calendar.getInstance().getTime().getTime();
        if (cm.getType() != null && cm.getType().contains(ControlMessage.Type.SetRole)) {
            checkState(cm.getRoles() != null && !cm.getRoles().isEmpty(), "Roles can't be null");

            if (old != null) {
                oldRoles = old.getRole();
            }

            if (!cm.getRoles().equals(oldRoles)) {
                ns.role = cm.getRoles();
            } else {
                // merge from previous state
                ns.role = oldRoles;
                oldRoles = null;
            }
        } else if (old != null && old.getRole() != null) {
            // merge from previous state
            ns.role = old.getRole();
        }

        if (cm.getType().contains(ControlMessage.Type.SetChain)) {
            String oldNext = null, oldPrev = null;
            if (old != null) {
                oldNext = old.getNextNode();
                oldPrev = old.getPrevNode();
            }

            ns.nextNode = cm.getNextNode();
            ns.prevNode = cm.getPrevNode();

            chainChanged = !Objects.equals(ns.getNextNode(), oldNext)
                    || !Objects.equals(ns.getPrevNode(), oldPrev);
        } else if (old != null) {
            // merge from previous state
            ns.nextNode = old.nextNode;
            ns.prevNode = old.prevNode;
        }

        if (cm.getType().contains(ControlMessage.Type.SetState)) {
            ns.state = StorageNode.StorageState.valueOf(cm.getState());
            stateChanged = true;
        } else if (old != null) {
            ns.state = old.state;
        }

        nodeState.set(ns);

        if (oldRoles != null) {
            onRoleChanged(oldRoles, cm.getRoles());
        }

        if (chainChanged) {
            onChainChanged();
        }

        if (stateChanged) {
            this.node().setState(ns.state.name());
        }
    }

    protected void onChainChanged() {
    }

    protected void onRoleChanged(final EnumSet<ControlMessage.Role> old,
                                 final EnumSet<ControlMessage.Role> current) {
        logger().info("[ROLE] was changed from '{}' to '{}'", old, current);
    }

    public static class NodeState {
        private UUID id;
        private EnumSet<ControlMessage.Role> role;
        private StorageNode.StorageState state;
        private String nextNode;
        private String prevNode;
        private String lastUpdatedBy;
        private long lastUpdatedOn;

        public UUID getId() {
            return id;
        }

        public EnumSet<ControlMessage.Role> getRole() {
            return role;
        }

        public StorageNode.StorageState getState() {
            return state;
        }

        public String getNextNode() {
            return nextNode;
        }

        public String getPrevNode() {
            return prevNode;
        }

        public String getLastUpdatedBy() {
            return lastUpdatedBy;
        }

        public long getLastUpdatedOn() {
            return lastUpdatedOn;
        }
    }

    @Override
    public void stateChanged(final String st) {
        super.stateChanged(st);

        switch (st) {
            case "RECOVERING":
                requestRecovery();
                break;

            default:
                // nothing to do
        }
    }

    private void requestRecovery() {
        final NodeState ns = getNodeState();
        final String recoverySource = ns.getPrevNode();
        checkState(recoverySource != null);

        final RecoveryRequest.Builder b = new RecoveryRequest.Builder();
        try {
            final RecoveryRequest m = sendMessage(recoverySource, b);
            logger().info(MessageUtils.dumpMessage(m,
                    "[RECOVERY-REQ:%s -> %s]  '%s'...", m.getSender(), recoverySource, m.getId())
            );
        } catch (Exception e) {
            logger().error(String.format("Failed to start recovery from '%s'", recoverySource), e);
            doNodeStateChange(StorageNode.StorageState.FAILED);
        }
    }

    @VisibleForTesting
    public void doNodeStateChange(final StorageNode.StorageState state) {
        final ControlMessage cm =  new ControlMessage.Builder()
                .setState(state)
                .setId(LOCAL_ID)
                .setSender("localoop")
                .build();

        updateNodeState(cm);
        notifyAboutNodeStatus();
    }
}
