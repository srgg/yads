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
package com.github.srgg.yads;

import com.github.srgg.yads.api.messages.ChainInfoRequest;
import com.github.srgg.yads.api.messages.ChainInfoResponse;
import com.github.srgg.yads.api.messages.NodeStatus;
import com.github.srgg.yads.impl.api.context.CommunicationContext;
import com.github.srgg.yads.impl.context.MasterNodeExecutionContext;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.runners.MockitoJUnitRunner;
import com.github.srgg.yads.api.message.Messages;
import com.github.srgg.yads.api.messages.ControlMessage;
import com.github.srgg.yads.impl.MasterNode;

@RunWith(MockitoJUnitRunner.class)
@FixMethodOrder(MethodSorters.JVM)
public class MasterNodeTest extends AbstractNodeTest<MasterNode, MasterNodeExecutionContext> {

    @Override
    protected MasterNode createNode() {
        return new MasterNode("master-1");
    }

    @Override
    protected MasterNodeExecutionContext createNodeContext(CommunicationContext ctx, MasterNode node) {
        return new MasterNodeExecutionContext(ctx, node);
    }

    @Test
    public void sendNoChainInfo_EvenIf_NoChain() throws Exception {
        verifyChain("");
        simulateMessageIn(new ChainInfoRequest.Builder()
                .setSender("client-1")
            );

        ensureMessageOut("client-1", ChainInfoResponse.class,
                "{" +
                    "sender: 'master-1', " +
                    "chain: []" +
                "}");

        ensureThatNoUnexpectedMessages();
    }

    @Test
    public void createChain() throws Exception {

        // -- 1st node started
        simulateMessageIn(
                new NodeStatus.Builder()
                        .setSender("node-1")
                        .setNodeType(Messages.NodeType.Storage)
                        .setStatus("STARTED")
            );

        verifyChain("node-1");

        // Since it is the first node there is no node to recovery from,
        // therefore the first node should be in RUNNING state
        verifyManageNode("node-1",
                "{" +
                    "type: ['SetRole', 'SetChain', 'SetState'], " +
                    "roles: ['Head', 'Tail']," +
                    "state:'RUNNING'," +
                    "prevNode: null," +
                    "nextNode: null" +
                "}");

        ensureThatNoUnexpectedMessages();

        // -- 2nd node started
        simulateMessageIn(
                new NodeStatus.Builder()
                        .setSender("node-2")
                        .setNodeType(Messages.NodeType.Storage)
                        .setStatus("STARTED")
        );
        verifyChain("node-1 - node-2 ");

        verifyManageNode("node-2",
                "{" +
                    "type: ['SetRole', 'SetChain', 'SetState'], " +
                    "roles: ['Tail']," +
                    "state:'RECOVERING'," +
                    "prevNode: 'node-1'," +
                    "nextNode: null" +
                "}"
            );


        verifyManageNode("node-1",
                "{" +
                    "type: ['SetRole', 'SetChain']," +
                    "roles: ['Head']," +
                    "nextNode: 'node-2'," +
                    "prevNode: null" +
                "}"
            );

        ensureThatNoUnexpectedMessages();


        // -- 3rd node started
        simulateMessageIn(
                new NodeStatus.Builder()
                        .setSender("node-3")
                        .setNodeType(Messages.NodeType.Storage)
                        .setStatus("STARTED")
        );
        verifyChain("node-1 - node-2 - node-3");

        verifyManageNode("node-3",
                "{" +
                    "type: ['SetRole', 'SetChain', 'SetState'], " +
                    "roles: ['Tail']," +
                    "state:'RECOVERING'," +
                    "prevNode: 'node-2'," +
                    "nextNode: null" +
                "}");

        verifyManageNode("node-2",
                "{" +
                    "type: ['SetRole', 'SetChain']," +
                    "roles: ['Middle']," +
                    "nextNode: 'node-3'," +
                    "prevNode: 'node-1'" +
                "}");

        ensureThatNoUnexpectedMessages();
    }

    private void verifyManageNode(String nodeId, String expected) throws Exception {
        ensureMessageOut(nodeId, ControlMessage.class, expected);
    }

    private void verifyChain(String expectedChain) {
        TestUtils.verifyChain(node.chain(), expectedChain);
    }
}
