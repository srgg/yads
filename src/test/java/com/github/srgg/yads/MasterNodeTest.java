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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import com.github.srgg.yads.api.message.Messages;
import com.github.srgg.yads.api.messages.ControlMessage;
import com.github.srgg.yads.impl.api.context.MasterNodeContext;
import com.github.srgg.yads.impl.MasterNode;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
@FixMethodOrder(MethodSorters.JVM)
public class MasterNodeTest {

    @Mock
    private MasterNodeContext nodeContext;

    private MasterNode masterNode;
    private static ObjectMapper mapper;

    @BeforeClass
    public static void initializeJsonUnit() {
        mapper = JsonUnitInitializer.initialize();
    }

    @Before
    public void setup() throws Exception {
        masterNode = new MasterNode("master-1");
        masterNode.configure(nodeContext);
        masterNode = spy(masterNode);

        verifyZeroInteractions(nodeContext, masterNode);
        assertEquals("master-1", masterNode.getId());
        assertEquals("NEW", masterNode.getState());

        //verify(masterNode, atLeastOnce()).getId();
        //verify(masterNode).getState();


        masterNode.start();
        verify(nodeContext).stateChanged("STARTED");
        assertEquals("STARTED", masterNode.getState());

        verifyZeroInteractions(nodeContext);
    }

    @Test
    public void createChain() throws Exception {

        // -- 1st node started
        masterNode.onNodeState("node-1", Messages.NodeType.Storage, "STARTED");
        verifyChain("node-1");

        // Since it is the first node there is no node to recovery from,
        // therefore the first node should be in RUNNING state
        verifyManageNode("node-1", "{" +
                "type: ['SetRole', 'SetChain', 'SetState'], " +
                "roles: ['Head', 'Tail']," +
                "state:'RUNNING'," +
                "prevNode: null," +
                "nextNode: null" +
                "}");

        Mockito.verifyZeroInteractions(nodeContext);


        // -- 2nd node started
        masterNode.onNodeState("node-2", Messages.NodeType.Storage, "STARTED");
        verifyChain("node-1 - node-2 ");

        verifyManageNode("node-2", "{" +
                "type: ['SetRole', 'SetChain', 'SetState'], " +
                "roles: ['Tail']," +
                "state:'RECOVERING'," +
                "prevNode: 'node-1'," +
                "nextNode: null" +
            "}");

        verifyManageNode("node-1", "{" +
                "type: ['SetRole', 'SetChain']," +
                "roles: ['Head']," +
                "nextNode: 'node-2'," +
                "prevNode: null" +
            "}");

        Mockito.verifyZeroInteractions(nodeContext);


        // -- 3rd node started
        masterNode.onNodeState("node-3", Messages.NodeType.Storage, "STARTED");
        verifyChain("node-1 - node-2 - node-3");

        verifyManageNode("node-3", "{" +
                "type: ['SetRole', 'SetChain', 'SetState'], " +
                "roles: ['Tail']," +
                "state:'RECOVERING'," +
                "prevNode: 'node-2'," +
                "nextNode: null" +
                "}");

        verifyManageNode("node-2", "{" +
                "type: ['SetRole', 'SetChain']," +
                "roles: ['Middle']," +
                "nextNode: 'node-3'," +
                "prevNode: 'node-1'" +
                "}");

        Mockito.verifyZeroInteractions(nodeContext);
    }

    // TODO: refactor to give human readable error with clear differences
    private void verifyManageNode(String nodeId, String expected) {
        try {
            final Map<String, Object> values = mapper.readValue(expected, new TypeReference<HashMap<String, Object>>(){});

            // apply defaults, if needed
            if (!values.containsKey("sender")) {
                values.put("sender", masterNode.getId());
            }

            verify(nodeContext, after(100)).manageNode(
                    //(ControlMessage) argThat(jsonEquals(values).when(Option.IGNORING_EXTRA_FIELDS)),
                    (ControlMessage.Builder) argThat(ChainVerificationUtils.MessageBuilderMatcher.create(values)),
                    eq(nodeId));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void verifyChain(String expectedChain) {
        ChainVerificationUtils.verifyChain(masterNode.chain(), expectedChain);
    }
}
