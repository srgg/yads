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
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import com.github.srgg.yads.api.IStorage;
import com.github.srgg.yads.api.message.Messages;
import com.github.srgg.yads.api.messages.ControlMessage;
import com.github.srgg.yads.impl.context.StorageExecutionContext;
import com.github.srgg.yads.impl.api.context.CommunicationContext;
import com.github.srgg.yads.impl.api.context.OperationContext;
import com.github.srgg.yads.impl.StorageNode;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 *  @author Sergey Galkin <srggal at gmail dot com>
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({StorageNode.class})
@FixMethodOrder(MethodSorters.JVM)
public class StorageNodeTest {
    @Mock
    private CommunicationContext communicationContext;

    @Mock
    private OperationContext operationContext;

    @Mock
    private IStorage storage;

    private StorageNode node;
    private StorageExecutionContext nodeContext;

    private StorageOperationRequest allOperations[] = {
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

    @Before
    public void setup() throws Exception {
        JsonUnitInitializer.initialize();

        // TODO: figure out how initialization can be refactored with @InjectMocks
        final StorageNode n = new StorageNode("node1", storage);
        node = spy(n);

        nodeContext = new StorageExecutionContext(communicationContext, node);
        nodeContext = spy(nodeContext);
        n.configure(nodeContext);
        node.configure(nodeContext);

        Mockito.reset(node);
        verifyZeroInteractions(nodeContext);
        verifyZeroInteractions(storage);
        verifyZeroInteractions(node);

        assertEquals("node1", node.getId());
        assertEquals("NEW", node.getState());

        //verify(node, atLeastOnce()).getId();
        //verify(node).getState();

        verifyZeroInteractions(nodeContext, storage, node);

        doAnswer(invocation -> {
                final StorageOperationRequest op = (StorageOperationRequest) invocation.getArguments()[0];
                doReturn(op).when(operationContext).operation();
                return operationContext;
        }).when(nodeContext).contextFor(any(StorageOperationRequest.class));

    }

    @Before
    public void shutdown() {
//        Mockito.verifyNoMoreInteractions(nodeContext, storage, node);
//        verifyNoMoreInteractions(nodeContext, storage, node);
    }

    @Test
    public void checkBehavior_of_StartAndStop() throws Exception {
        startNodeAndVerify();
        stopNodeAndVerify();
    }

    private void manageNode(ControlMessage.Builder builder) {
        final ControlMessage msg = builder
                .setSender("Anonymous")
                .build();

        try {
            nodeContext.onMessage(node.getId(), Messages.MessageTypes.ControlMessage, msg);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException)e;
            }
            throw new RuntimeException(e);
        }
    }

    @Test
    public void checkBehavior_of_StateChangeUsingControlMessage() throws Exception {
        startNodeAndVerify();

        manageNode(
            new ControlMessage.Builder()
                .setState(StorageNode.StorageState.RUNNING)
        );

        assertEquals("RUNNING", node.getState());
        PowerMockito.verifyPrivate(node).invoke("onStateChanged", "STARTED", "RUNNING");
        stopNodeAndVerify();
    }

    @Test
    public void rejectAllStorageOperationsBeingInInappropriateState() throws Exception {

        // -- generate list of "inappropriate" states
        final List<String> states = new LinkedList<>();
        final EnumSet<StorageNode.StorageState> allowedStates = EnumSet.of(StorageNode.StorageState.RECOVERING,
                StorageNode.StorageState.RECOVERED, StorageNode.StorageState.RUNNING);

        for (StorageNode.StorageState s : StorageNode.StorageState.values()) {
            if (!allowedStates.contains(s)) {
                states.add(s.name());
            }
        }

        // --
        for (String s: states) {
            if (!node.getState().equals(s)) {
                manageNode(
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
                } catch (IllegalStateException ex) {
                }
            }
        }
    }

    @Test
    public void sendNodeStateImmediatelyInCaseOfLocalChange() throws Exception {
        verifyZeroInteractions(storage);
        startNodeAndVerify();
        nodeContext.doNodeStateChange(StorageNode.StorageState.FAILED);

        /**
         * If due to whatever reason node state was changed locally,
         * the new state should be propagated to the leader immediately
         */
        verify(communicationContext, after(100)).send(
                eq(CommunicationContext.LEADER_NODE),
                argThat( TestUtils.message(NodeStatus.class,
                        "{sender: 'node1',"
                            + "type: 'Storage',"
                            + "status: 'FAILED'"
                        + "}"))
            );
    }

    @Test
    public void checkBehavior_of_StorageOperationProcessingDuringRecovery() throws Exception {
        verifyZeroInteractions(storage);
        startNodeAndVerify();

        manageNode(
            new ControlMessage.Builder()
                    .setPrevNode("PREV-NODE")
                    .setState(StorageNode.StorageState.RECOVERING)
        );

        for (StorageOperationRequest op : allOperations) {
            node.onStorageRequest(op);
        }

        // all the operations must be queued for further processing after recovery will be completed
        verify(storage, after(2000).never()).process(any());
        verifyZeroInteractions(storage);

        manageNode(
                new ControlMessage.Builder()
                        .setState(StorageNode.StorageState.RECOVERED)
        );

        // should process all previously queued operations
        manageNode(
            new ControlMessage.Builder()
                    .setState(StorageNode.StorageState.RUNNING)
        );

        // all the enqueued operation should be processed
        verify(storage, after(1000).times(allOperations.length)).process(any());

        // each successfully processed operation should be acknowledged
        verify(operationContext, times(allOperations.length)).ackOperation(any());

        stopNodeAndVerify();
    }

    private void startNodeAndVerify() throws Exception {
        node.start();
        verify(nodeContext).stateChanged("STARTED");

        assertEquals("STARTED", node.getState());
        PowerMockito.verifyPrivate(node).invoke("onStateChanged", "NEW", "STARTED");
    }

    private void stopNodeAndVerify() throws Exception {
        final String state = node.getState();
        node.stop();
        verify(nodeContext).stateChanged("STOPPED");

        assertEquals("STOPPED", node.getState());
        PowerMockito.verifyPrivate(node).invoke("onStateChanged", state, "STOPPED");
    }
}