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

import com.github.srgg.yads.api.messages.NodeStatus;
import com.github.srgg.yads.api.messages.StorageOperationRequest;
import com.github.srgg.yads.impl.ChainProcessingInfo;
import org.junit.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import com.github.srgg.yads.api.IStorage;
import com.github.srgg.yads.api.messages.ControlMessage;
import com.github.srgg.yads.impl.context.StorageNodeExecutionContext;
import com.github.srgg.yads.impl.api.context.StorageExecutionContext.StorageState;
import com.github.srgg.yads.impl.api.context.CommunicationContext;
import com.github.srgg.yads.impl.api.context.OperationContext;
import com.github.srgg.yads.impl.StorageNode;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 *  @author Sergey Galkin <srggal at gmail dot com>
 */
public class StorageNodeTest extends AbstractNodeTest<StorageNode, StorageNodeExecutionContext> {
    private final static String NODE_ID = "node1";
    @Mock
    private OperationContext operationContext;

    @Mock
    private IStorage storage;

    private final StorageOperationRequest[] allOperations = {
            new StorageOperationRequest.Builder()
                    .setId(UUID.randomUUID())
                    .setSender("Anonymous")
                    .setKey("m1")
                    .setType(StorageOperationRequest.OperationType.Put)
                    .setObject("42")
                    .build(),

            new StorageOperationRequest.Builder()
                    .setId(UUID.randomUUID())
                    .setSender("Anonymous")
                    .setKey("m2")
                    .setType(StorageOperationRequest.OperationType.Get)
                    .build()
        };

    @Override
    protected StorageNode createNode() {
        return new StorageNode(NODE_ID, storage);
    }

    @Override
    protected StorageNodeExecutionContext createNodeContext(CommunicationContext ctx, StorageNode node) {
        return new StorageNodeExecutionContext(ctx, node);
    }

    @Before
    public void setup() throws Exception {
        super.setup();
        verifyZeroInteractions(storage);

        doAnswer(invocation -> {
                final StorageOperationRequest op = (StorageOperationRequest) invocation.getArguments()[0];
                doReturn(op).when(operationContext).operation();
                return operationContext;
        }).when(nodeContext).contextFor(any(StorageOperationRequest.class));

    }

    @Test
    public void checkBehavior_of_StartAndStop() throws Exception {
        startNodeAndVerify();
        stopNodeAndVerify();
    }

    @Test
    public void checkBehavior_of_StateChangeUsingControlMessage() throws Exception {
        startNodeAndVerify();

        simulateMessageIn(
            new ControlMessage.Builder()
                .setState(StorageState.RUNNING)
        );

        assertEquals("RUNNING", node.getState());
        verify(node).onStateChanged("STARTED", "RUNNING");
        stopNodeAndVerify();
    }

    @Test
    public void rejectAllStorageOperationsBeingInInappropriateState() throws Exception {

        // -- generate list of "inappropriate" states
        final List<String> states = new LinkedList<>();
        final EnumSet<StorageState> allowedStates = EnumSet.of(StorageState.RECOVERING,
                StorageState.RECOVERED, StorageState.RUNNING);

        for (StorageState s : StorageState.values()) {
            if (!allowedStates.contains(s)) {
                states.add(s.name());
            }
        }

        // --
        for (String s: states) {
            if (!node.getState().equals(s)) {
                simulateMessageIn(
                    new ControlMessage.Builder()
                            .setState(s)
                );
            }

            assertEquals(s, node.getState());

            for (StorageOperationRequest op : allOperations) {
                // TODO: rewrite with Exception Matcher to introduce message checking
                try {
                    node.onStorageRequest(op);
                    fail();
                } catch (IllegalStateException ignored) {
                }
            }
        }
    }

    @Test
    public void sendNodeStateImmediatelyInCaseOfLocalChange() throws Exception {
        verifyZeroInteractions(storage);
        startNodeAndVerify();
        nodeContext.doNodeStateChange(StorageState.FAILED);

        /**
         * If due to whatever reason node state was changed locally,
         * the new state should be propagated to the leader immediately
         */
        ensureMessageOut(CommunicationContext.LEADER_NODE, NodeStatus.class,
                "{sender: 'node1',"
                        + "type: 'Storage',"
                        + "status: 'FAILED'"
                        + "}"
        );
    }

    @Test
    public void checkBehavior_of_StorageOperationProcessingDuringRecovery() throws Exception {
        verifyZeroInteractions(storage);
        startNodeAndVerify();

        simulateMessageIn(
            new ControlMessage.Builder()
                    .setPrevNode("PREV-NODE")
                    .setState(StorageState.RECOVERING)
        );

        for (StorageOperationRequest op : allOperations) {
            node.onStorageRequest(op);
        }

        // all the operations must be queued for further processing after recovery will be completed
        verify(storage, after(2000).never()).process(any());
        verifyZeroInteractions(storage);

        simulateMessageIn(
                new ControlMessage.Builder()
                        .setState(StorageState.RECOVERED)
        );

        // should process all previously queued operations
        simulateMessageIn(
            new ControlMessage.Builder()
                    .setState(StorageState.RUNNING)
        );

        // all the enqueued operation should be processed
        verify(storage, after(1000).times(allOperations.length)).process(any());

        // each successfully processed operation should be acknowledged
        verify(operationContext, times(allOperations.length)).ackOperation(any());

        stopNodeAndVerify();
    }

    @Test
    public void checkProcessingInfoPopulation() throws Exception {
        final String clientId = "client-42";
        final UUID requestId = UUID.randomUUID();
        final long testStartedAt = System.currentTimeMillis();

        // -- spinup storage node
        startNodeAndVerify();


        // configuring as a single node in the chain, since chain processing is out of the scope of this case
        simulateMessageIn(
                new ControlMessage.Builder()
                        .setRoles(EnumSet.of(ControlMessage.Role.Head, ControlMessage.Role.Tail))
                        .setState(StorageState.RUNNING)
        );

        // -- incoming storage operation
        simulateMessageIn(
                new StorageOperationRequest.Builder(requestId)
                        .setSender(clientId)
                        .setKey("k42")
                        .setType(StorageOperationRequest.OperationType.Put)
                        .setObject("1")
        );

        // ---------- verification

        // checking that storage operation was procesed without unexpected side effects
        final ArgumentCaptor<StorageOperationRequest> captor = ArgumentCaptor.forClass(StorageOperationRequest.class);
        verify(storage, after(200)).process(captor.capture());
        verify(operationContext).ackOperation(any());

        verifyNoMoreInteractions(storage);

        final StorageOperationRequest op = captor.getValue();
        assertThat(op,
                TestUtils.message(StorageOperationRequest.class,
                        "{sender: '%s', id: '%s', type: 'Put', key: 'k42'}",
                        clientId, requestId)
            );

        // check proper generation/population of ChainProcessingInfo
        final ChainProcessingInfo pi = ChainProcessingInfo.getProcessingInfo(op);

        assertNotNull(pi);

        assertEquals(clientId, pi.getClient());
        assertEquals(requestId, pi.getOriginalRId());

        assertEquals(1, pi.getProcessingInfos().size());

        final ChainProcessingInfo.ProcessingInfo npi = pi.getProcessingInfos().get(0);
        assertEquals(NODE_ID, npi.getId());

        final Long startedAt = npi.getStartedAt();
        assertNotNull(startedAt);
        assertTrue(startedAt > testStartedAt);

//        final Long completedAt = npi.getCompletedAt();
//        assertNotNull(completedAt);
//        assertTrue(completedAt > startedAt);
    }
}