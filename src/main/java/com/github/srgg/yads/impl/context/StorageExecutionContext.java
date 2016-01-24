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

import com.github.srgg.yads.api.messages.ControlMessage;
import com.github.srgg.yads.api.messages.Message;
import com.github.srgg.yads.api.messages.StorageOperation;
import com.github.srgg.yads.impl.AbstractNodeRuntime;
import com.github.srgg.yads.impl.StorageNode;
import com.github.srgg.yads.impl.api.context.CommunicationContext;
import com.github.srgg.yads.impl.api.context.OperationExecutionContext;
import com.github.srgg.yads.impl.api.context.StorageNodeContext;
import com.github.srgg.yads.impl.util.MessageUtils;
import com.github.srgg.yads.api.message.Messages;

import java.util.Calendar;
import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkState;

/**
 *  @author Sergey Galkin <srggal at gmail dot com>
 */
public class StorageExecutionContext extends AbstractNodeRuntime<StorageNode> implements StorageNodeContext {
    private AtomicReference<NodeState> nodeState = new AtomicReference<>();

    public StorageExecutionContext(final CommunicationContext messageContext, final StorageNode node) {
        super(messageContext, node);
    }

    // TODO: Requires to have default implementation
    @Override
    public OperationExecutionContext contextFor(final StorageOperation operation) {
        return null;
    }

    @Override
    public boolean onMessage(final String recipient, final Messages.MessageTypes type,
                             final Message message) throws Exception {
        switch (type) {
            case ControlMessage:
                final ControlMessage cm = (ControlMessage) message;

                logger().info(MessageUtils.dumpMessage(message,
                        "[CTRL:%s]  '%s'...", cm.getSender(), cm.getId().toString())
                    );

                updateNodeState(cm);
                break;

            default:
                return false;
        }
        return true;
    }

    public NodeState getNodeState() {
        return nodeState.get();
    }

    private void updateNodeState(final ControlMessage cm) {
        final NodeState ns = new NodeState();
        final NodeState old = nodeState.get();

        ns.id = cm.getId();
        ns.lastUpdatedBy = cm.getSender();
        ns.lastUpdatedOn = Calendar.getInstance().getTime().getTime();
        if (cm.getType() != null && cm.getType().contains(ControlMessage.Type.SetRole)) {
            checkState(cm.getRoles() != null && !cm.getRoles().isEmpty(), "Roles can't be null");

            EnumSet<ControlMessage.Role> oldRoles = null;
            if (old != null) {
                oldRoles = old.getRole();
            }

            if (!cm.getRoles().equals(oldRoles)) {
                ns.role = cm.getRoles();
                onRoleChanged(oldRoles, cm.getRoles());
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

            if (!Objects.equals(ns.getNextNode(), oldNext)
                    || !Objects.equals(ns.getPrevNode(), oldPrev)) {
                onChainChanged();
            }
        } else if (old != null) {
            // merge from previous state
            ns.nextNode = old.nextNode;
            ns.prevNode = old.prevNode;
        }

        if (cm.getType().contains(ControlMessage.Type.SetState)) {
            final StorageNode.StorageState st = StorageNode.StorageState.valueOf(cm.getState());
            ns.state = st;
            this.node().setState(st.name());
        } else if (old != null) {
            ns.state = old.state;
        }

        nodeState.set(ns);
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
}
