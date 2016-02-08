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

import com.github.srgg.yads.api.message.Messages;
import com.github.srgg.yads.api.messages.*;
import com.github.srgg.yads.impl.AbstractExecutionRuntime;
import com.github.srgg.yads.impl.ClientImpl;
import com.github.srgg.yads.impl.api.context.ClientExecutionContext;
import com.github.srgg.yads.impl.api.context.CommunicationContext;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Sergey Galkin <srggal at gmail dot com>
 */
public class ClientExecutionContextImpl extends AbstractExecutionRuntime<ClientImpl> implements ClientExecutionContext {
    private static class StorageOpFuture extends CompletableFuture<StorageOperationResponse> {
    }

    private final ConcurrentHashMap<UUID, StorageOpFuture> activeFeatures = new ConcurrentHashMap<>();
    private final AtomicReference<ChainInfoResponse> chainState = new AtomicReference<>();

    public ClientExecutionContextImpl(final CommunicationContext mc, final ClientImpl client) {
        super(mc, client);
    }

    @Override
    public boolean onMessage(final String recipient, final Messages.MessageTypes type,
                             final Message message) throws Exception {
        switch (type) {
            case StorageOperationResponse:
                final StorageOperationResponse sor = (StorageOperationResponse) message;
                final UUID rid = sor.getId();
                final StorageOpFuture f = activeFeatures.get(rid);
                f.complete(sor);
                break;

            case ChainInfoResponse:
                final ChainInfoResponse cir = (ChainInfoResponse) message;
                chainState.set(cir);

                if (getState().equals(ClientState.WAITING_4_CHAIN.name())) {
                    changeState(ClientState.RUNNING.name());
                }
                break;

            default:
                // nothing to do
        }
        return false;
    }

    protected Set<String> getChainInfo() {
        final ChainInfoResponse chain = chainState.get();
        return chain != null ? chain.getChain() : null;
    }

    @Override
    public Future<StorageOperationResponse> perform(final StorageOperationRequest.Builder builder) throws Exception {
        final UUID rid = builder.getId();
        final StorageOpFuture f = new StorageOpFuture();
        activeFeatures.put(rid, f);

        sendMessage("", builder);
        return f;
    }

    @Override
    protected Runnable createBgTask() {
        return () -> {
            try {
                final ChainInfoRequest.Builder b = new ChainInfoRequest.Builder();
                sendMessage(CommunicationContext.LEADER_NODE, b);
            } catch (Exception e) {
                logger().error("Can't send ChainInfoRequest", e);
            }
        };
    }

    @Override
    protected Map<String, Set<String>> createTransitions() {
        return TransitionMatrixBuilder.create(super.createTransitions())
                .add(ClientState.STARTED, ClientState.WAITING_4_CHAIN)
                .add(ClientState.FAILED, ClientState.WAITING_4_CHAIN)
                .add(ClientState.WAITING_4_CHAIN, ClientState.RUNNING, ClientState.FAILED, ClientState.STOPPED)
                .build();
    }

    @Override
    public void stateChanged(final String st) {
        super.stateChanged(st);
        switch (st) {
            case "STARTED":
                changeState(ClientState.WAITING_4_CHAIN.name());
                // there is nothing to do, chain status request will be executed as a bg task
                break;

            default:
                // nothing to do
        }
    }
}
