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
import com.github.srgg.yads.api.messages.ChainInfoRequest;
import com.github.srgg.yads.api.messages.ChainInfoResponse;
import com.github.srgg.yads.impl.ClientImpl;
import com.github.srgg.yads.impl.api.context.ClientExecutionContext;
import com.github.srgg.yads.impl.api.context.CommunicationContext;
import com.github.srgg.yads.impl.context.ClientExecutionContextImpl;
import org.junit.Test;


/**
 * @author Sergey Galkin <srggal at gmail dot com>
 */
public class ClientTest extends AbstractNodeTest<ClientImpl, ClientExecutionContextImpl> {

    @Override
    public void setup() throws Exception {
        super.setup();
    }

    @Override
    protected ClientImpl createNode() {
        return new ClientImpl("client-1", Messages.NodeType.Client);
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
    public void checkSwitchToRunningState() throws Exception {
        startNodeAndVerify();
        simulateMessageIn(new ChainInfoResponse.Builder()
            .setSender("leader-1")
            .addChain("storage-head")
            .addChain("storage-middle")
            .addChain("storage-tail")
        );

        ensureStateChanged("WAITING_4_CHAIN", "RUNNING");
    }

    protected void startNodeAndVerify() throws Exception {
        super.startNodeAndVerify(ClientExecutionContext.ClientState.WAITING_4_CHAIN.name());
        ensureMessageOut(CommunicationContext.LEADER_NODE, ChainInfoRequest.class, "{sender: 'client-1'}");
    }
}
