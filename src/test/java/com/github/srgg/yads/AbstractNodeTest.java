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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.srgg.yads.api.message.Messages;
import com.github.srgg.yads.api.messages.Message;
import com.github.srgg.yads.impl.AbstractExecutionRuntime;
import com.github.srgg.yads.impl.AbstractNode;
import com.github.srgg.yads.impl.api.context.CommunicationContext;
import com.github.srgg.yads.impl.context.communication.AbstractTransport;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mockito;
import org.mockito.internal.invocation.InvocationImpl;
import org.mockito.listeners.InvocationListener;
import org.mockito.listeners.MethodInvocationReport;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * @author Sergey Galkin <srggal at gmail dot com>
 */
@RunWith(MockitoJUnitRunner.class)
@FixMethodOrder(MethodSorters.JVM)
public abstract class AbstractNodeTest<N extends AbstractNode, C extends AbstractExecutionRuntime<N>> {
    protected Logger logger = LoggerFactory.getLogger(getClass());

    @Rule
    public FancyTestWatcher watcher = new FancyTestWatcher();

    protected CommunicationContext communicationContext;

    protected N node;
    protected C nodeContext;
    protected static ObjectMapper mapper;


    protected abstract N createNode();

    protected abstract C createNodeContext(final CommunicationContext ctx, final N node);

    @BeforeClass
    public static void initializeJsonUnit() {
        mapper = JsonUnitInitializer.initialize();
    }

    @Before
    public void setup() throws Exception {
        // http://stackoverflow.com/questions/11802088/how-do-i-enable-mockito-debug-messages
        //communicationContext = mock(CommunicationContext.class, withSettings().verboseLogging());
        communicationContext = mock(CommunicationContext.class, withSettings().invocationListeners(
                new InvocationListener() {
                    @Override
                    public void reportInvocation(MethodInvocationReport methodInvocationReport) {
                        final String str = methodInvocationReport.getInvocation().toString();

                        if (str.startsWith("communicationContext.send(")) {
                            final InvocationImpl impl = (InvocationImpl) methodInvocationReport.getInvocation();
                            final String recipient = (String) impl.getArguments()[0];
                            final Message message = (Message) impl.getArguments()[1];

                            if (message != null) {
                                AbstractTransport.dumpMessage(logger, false, recipient, message);
                            } else {
                                logger.warn("WEIRD");
                            }
                        } else if (str.equals("communicationContext.onMessage(")) {
                            final InvocationImpl impl = (InvocationImpl) methodInvocationReport.getInvocation();
                            final String recipient = (String) impl.getArguments()[0];
                            final Message message = (Message) impl.getArguments()[2];

                            AbstractTransport.dumpMessage(logger, true, recipient, message);
                        }
                    }
                }
        ));

        // TODO: figure out how initialization can be refactored with @InjectMocks
        final N n = createNode();
        node = spy(n);

        nodeContext = spy(createNodeContext(communicationContext, node));
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

    protected <M extends Message> M ensureMessageOut(String recipient, Class<M> messageClass, Object expected) throws Exception {
        // Since we need to capture the only sutable invocation despite the invocation order/seqno, Captors aren't working in this case
        final TestUtils.MessageMatcher matcher = TestUtils.message(messageClass, expected);
        verify(communicationContext, after(1000)).send(
                eq(recipient),
                (Message)argThat(matcher)
        );

        return (M) matcher.getActual();
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
        try {
            builder.getSender();
        } catch (IllegalStateException ex) {
            builder.setSender("Anonymous");
        }

        final Message msg = builder.build();
        final byte msgCode = AbstractTransport.getMessageCodeFor(msg);
        final Messages.MessageTypes mt = Messages.MessageTypes.valueOf(msgCode);

        nodeContext.onMessage(recipient, mt, msg);
        verify(nodeContext).onMessage(eq(recipient), eq(mt), argThat(TestUtils.message(msg.getClass(), msg)));
    }

    protected void ensureThatNoUnexpectedMessages() throws Exception {
        Mockito.verify(nodeContext, TestUtils.zeroInteractions()).onMessage(Mockito.anyString(), Mockito.any(), Mockito.any());
        Mockito.verify(this.communicationContext, TestUtils.zeroInteractions()).send(Mockito.anyString(), Mockito.any());
    }
}