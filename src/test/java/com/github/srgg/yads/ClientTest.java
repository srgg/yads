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
import com.github.srgg.yads.api.messages.StorageOperationRequest;
import com.github.srgg.yads.api.messages.StorageOperationResponse;
import com.github.srgg.yads.impl.ClientImpl;
import com.github.srgg.yads.impl.api.context.ClientExecutionContext;
import com.github.srgg.yads.impl.api.context.CommunicationContext;
import com.github.srgg.yads.impl.context.ClientExecutionContextImpl;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mockito.internal.verification.VerificationModeFactory;
import org.mockito.verification.After;
import org.mockito.verification.VerificationAfterDelay;

import java.util.function.Consumer;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;


/**
 * @author Sergey Galkin <srggal at gmail dot com>
 */
@FixMethodOrder(MethodSorters.JVM)
public class ClientTest extends AbstractNodeTest<ClientImpl, ClientExecutionContextImpl> {

    @Override
    public void setup() throws Exception {
        super.setup();
    }

    @Override
    protected ClientImpl createNode() {
        return new ClientImpl("client-1");
    }

    @Override
    protected ClientExecutionContextImpl createNodeContext(CommunicationContext ctx, ClientImpl node) {
        return new ClientExecutionContextImpl(ctx, node);
    }

    @Test
    public void checkBehavior_of_StartAndStop() throws Exception {
        startNodeAndVerify();
        stopNodeAndVerify();
    }

    @Test
    public void switchToRunningState_onlyIf_ChainIsNotEpty() throws Exception {
        startNodeAndVerify();
        ensureStateChanged("WAITING_4_CHAIN");

        // On No/Empty chain should stay in WAITING_4_CHAIN
        ensureMessageOut(CommunicationContext.LEADER_NODE, ChainInfoRequest.class, "{sender: 'client-1'}");
        simulateChainInfoResponse(new String[0]);
        ensureStateChanged("WAITING_4_CHAIN");

        ensureMessageOut(CommunicationContext.LEADER_NODE, ChainInfoRequest.class, "{sender: 'client-1'}");
        simulateChainInfoResponse("node-1");
        ensureStateChanged("RUNNING");

        ensureThatNoUnexpectedMessages();
    }

    @Test
    public void checkBehavior_of_StorageOperationProcessing() throws Exception {
        final Consumer<StorageOperationResponse> putConsumer = mock(Consumer.class);
        final Consumer<StorageOperationResponse> getConsumer = mock(Consumer.class);

        startNodeAndVerify();

        node.store("1", "42", putConsumer);
        node.fetch("2", getConsumer);


        ensureMessageOut(CommunicationContext.LEADER_NODE, ChainInfoRequest.class, "{sender: 'client-1'}");

        final VerificationAfterDelay noneEvenAfter = new After(1000, VerificationModeFactory.times(0));

        // mustn't be processed
        verify(putConsumer, noneEvenAfter).accept(any());
        verify(getConsumer, noneEvenAfter).accept(any());

        simulateChainInfoResponse("head", "middle", "tail");

        final StorageOperationRequest putReq = ensureMessageOut("head",
                StorageOperationRequest.class,
                "{sender: 'client-1', type: 'Put', key: '1', object: '42'}");

        final StorageOperationRequest getReq = ensureMessageOut("tail",
                StorageOperationRequest.class,
                "{sender: 'client-1', type: 'Get', key: '2', object: null}");

        // since there are no responses, mustn't be processed still
        verify(putConsumer, noneEvenAfter).accept(any());
        verify(getConsumer, noneEvenAfter).accept(any());

        // simulate Get response, as a result the only getConsumer should be triggered
        simulateMessageIn(getReq.getSender(),
                new StorageOperationResponse.Builder()
                    .setRid(getReq.getId())
                    .setSender("tail")
                    .setObject("41")
            );

        verify(getConsumer, after(1000)).accept(
                argThat(TestUtils.message(StorageOperationResponse.class,
                        "{rid: '%s', object: '41'}", getReq.getId()))
            );

        verify(putConsumer, noneEvenAfter).accept(any());

        // simulate Put response
        simulateMessageIn(getReq.getSender(),
                new StorageOperationResponse.Builder()
                        .setRid(putReq.getId())
                        .setSender("tail")
        );

        verify(putConsumer, after(1000)).accept(
                argThat(TestUtils.message(StorageOperationResponse.class,
                        "{rid: '%s', object: null}", putReq.getId()))
        );

        verifyNoMoreInteractions(getConsumer, putConsumer);
    }

    @Override
    protected void startNodeAndVerify() throws Exception {
        super.startNodeAndVerify(ClientExecutionContext.ClientState.WAITING_4_CHAIN.name());
        ensureMessageOut(CommunicationContext.LEADER_NODE, ChainInfoRequest.class, "{sender: 'client-1'}");
    }

//    protected void spinUpClient(String...chain) throws Exception {
//        startNodeAndVerify();
//        ensureMessageOut(CommunicationContext.LEADER_NODE, ChainInfoRequest.class, "{sender: 'client-1'}");
//
//        simulateChainInfoResponse(chain);
//        ensureStateChanged("WAITING_4_CHAIN", "RUNNING");
//    }

    protected final void simulateChainInfoResponse(String...chain) throws Exception {
        simulateMessageIn(new ChainInfoResponse.Builder()
                .setSender("leader-1")
                .addChain(chain)
        );
    }
}
