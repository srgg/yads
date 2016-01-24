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

import com.github.srgg.yads.api.messages.Message;
import com.github.srgg.yads.impl.AbstractNodeRuntime;
import com.github.srgg.yads.impl.MasterNode;
import com.github.srgg.yads.impl.api.context.CommunicationContext;
import com.github.srgg.yads.impl.api.context.MasterNodeContext;
import com.github.srgg.yads.impl.util.MessageUtils;
import com.github.srgg.yads.api.message.Messages;
import com.github.srgg.yads.api.messages.ControlMessage;
import com.github.srgg.yads.api.messages.NodeStatus;

/**
 *  @author Sergey Galkin <srggal at gmail dot com>
 */
public class MasterNodeExecutionContext extends AbstractNodeRuntime<MasterNode> implements MasterNodeContext {

    public MasterNodeExecutionContext(final CommunicationContext messageContext, final MasterNode node) {
        super(messageContext, node);
    }

    @Override
    public void manageNode(final ControlMessage message, final Iterable<String> nodeIds) throws Exception {
        for (String id: nodeIds) {
            logger().info(MessageUtils.dumpMessage(message,
                    "[CTRL:%s -> %s]  '%s'...", message.getSender(), id, message.getId().toString())
            );
            communicationContext().send(id, message);
        }
    }

    @Override
    public boolean onMessage(final String recipient, final Messages.MessageTypes type,
                             final Message message) throws Exception {
        switch (type) {
            case NodeStatus:
                final NodeStatus status = (NodeStatus) message;
                node().onNodeState(status.getSender(), status.getNodeType(), status.getStatus());
                break;

            default:
                return false;
        }
        return true;
    }
}
