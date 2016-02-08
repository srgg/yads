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

import com.github.srgg.yads.api.message.Messages;
import com.github.srgg.yads.api.messages.Message;
import com.github.srgg.yads.impl.AbstractExecutionRuntime;
import com.github.srgg.yads.impl.AbstractNode;
import com.github.srgg.yads.impl.api.context.CommunicationContext;
import com.github.srgg.yads.impl.context.communication.AbstractTransport;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * @author Sergey Galkin <srggal at gmail dot com>
 */
@RunWith(MockitoJUnitRunner.class)
@FixMethodOrder(MethodSorters.JVM)
public abstract class AbstractNodeTest<N extends AbstractNode, C extends AbstractExecutionRuntime<N>> {
    @Rule
    public FancyTestWatcher watcher = new FancyTestWatcher();

    @Mock
    protected CommunicationContext communicationContext;

    protected N node;
    protected C nodeContext;

    protected abstract N createNode();

    protected abstract C createNodeContext(final CommunicationContext ctx, final N node);

    @Before
    public void setup() throws Exception {
        JsonUnitInitializer.initialize();

        // TODO: figure out how initialization can be refactored with @InjectMocks
        final N n = createNode();
        node = spy(n);

        nodeContext = createNodeContext(communicationContext, node);
        nodeContext = spy(nodeContext);
        n.configure(nodeContext);
        node.configure(nodeContext);

        Mockito.reset(node);
        verifyZeroInteractions(nodeContext);
        verifyZeroInteractions(node);

        assertEquals("NEW", node.getState());

        verify(node).getState();
        verify(nodeContext).getState();
        reset(nodeContext, node, communicationContext);
    }

//    @After
//    public void shutdown() {
////        Mockito.verifyNoMoreInteractions(nodeContext, storage, node);
////        verifyNoMoreInteractions(nodeContext, storage, node);
//    }

    protected void startNodeAndVerify() throws Exception {
        startNodeAndVerify(null);
    }

    protected void startNodeAndVerify(final String autoState) throws Exception {
        node.start();
        verify(nodeContext).stateChanged("STARTED");
        verify(node).onStateChanged("NEW", "STARTED");

        if (autoState != null) {
            verify(nodeContext).stateChanged(autoState);
            verify(node).onStateChanged("STARTED", autoState);
            assertEquals(autoState, node.getState());
        } else {
            assertEquals("STARTED", node.getState());
        }
    }

    protected void stopNodeAndVerify() throws Exception {
        final String state = node.getState();
        node.stop();
        verify(nodeContext).stateChanged("STOPPED");

        assertEquals("STOPPED", node.getState());
        verify(node).onStateChanged(state, "STOPPED");
    }

    protected <M extends Message> void ensureMessageOut(String recipient, Class<M> messageClass, Object expected) throws Exception {
        verify(communicationContext, after(1000)).send(
                eq(recipient),
                argThat( TestUtils.message(messageClass, expected))
        );
    }

    protected void ensureStateChanged(String to) {
        ensureStateChanged(null, to);
    }

    protected void ensureStateChanged(String from, String to) {
        verify(nodeContext).stateChanged(to);
        verify(node).onStateChanged(from == null ? anyString() : eq(from), eq(to));
        ensureState(to);
    }

    protected final void ensureState(String state) {
        final String actual = nodeContext.getState();
        assertEquals(state, actual);
        verify(nodeContext, atLeast(1)).getState();
    }

    protected void simulateMessageIn(Message.MessageBuilder<?,?> builder) throws Exception {
        simulateMessageIn(node.getId(), builder);
    }

    protected void simulateMessageIn(String recipient, Message.MessageBuilder<?,?> builder) throws Exception {
        final Message msg = builder.build();
        final byte msgCode = AbstractTransport.getMessageCodeFor(msg);
        final Messages.MessageTypes mt = Messages.MessageTypes.valueOf(msgCode);

        nodeContext.onMessage(recipient, mt, msg);
    }
}