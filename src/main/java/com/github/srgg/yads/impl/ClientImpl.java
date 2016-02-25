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
package com.github.srgg.yads.impl;

import com.github.srgg.yads.api.Client;
import com.github.srgg.yads.api.message.Messages;
import com.github.srgg.yads.api.messages.StorageOperationRequest;
import com.github.srgg.yads.api.messages.StorageOperationResponse;
import com.github.srgg.yads.impl.api.context.ClientExecutionContext;
import com.github.srgg.yads.impl.api.context.ClientExecutionContext.ClientState;
import com.github.srgg.yads.impl.api.context.Subscribe;
import com.github.srgg.yads.impl.util.AbstractProcessingCycle;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 *  @author Sergey Galkin <srggal at gmail dot com>
 */
public class ClientImpl extends AbstractNode<ClientExecutionContext> implements Client {
    private final ConcurrentHashMap<UUID, StorageOpFuture> activeFutures = new ConcurrentHashMap<>();

    private static class StorageOpFuture extends CompletableFuture<StorageOperationResponse> {
        private final StorageOperationRequest.Builder builder;

        StorageOpFuture(final StorageOperationRequest.Builder bld) {
            this.builder = bld;
        }

        StorageOperationRequest.Builder getBuilder() {
            return builder;
        }
    }

    private final AbstractProcessingCycle<StorageOpFuture> processingCycle =
            new AbstractProcessingCycle<StorageOpFuture>(this.logger()) {

                @Override
                public String getId() {
                    return ClientImpl.this.getId();
                }

                @Override
                protected boolean doProcessing() throws Exception {
                    final StorageOpFuture f = this.poll();
                    activeFutures.put(f.builder.getId(), f);
                    context().performStorageOperation(f.builder);
                    return true;
                }
            };

    public ClientImpl(final String id) {
        super(id, Messages.NodeType.Client);
    }

    private StorageOpFuture enqueueOperation(final StorageOperationRequest.Builder builder,
                                             final Consumer<StorageOperationResponse> consumer) throws Exception {
        builder.setSender(getId());
        final StorageOpFuture f = new StorageOpFuture(builder);

        if (consumer != null) {
            f.thenAccept(consumer);
        }

        processingCycle.enqueue(f);
        return f;
    }

    @Override
    public CompletableFuture<StorageOperationResponse> store(final String key, final Object data,
                                                             final Consumer<StorageOperationResponse> consumer) throws Exception {
        return enqueueOperation(
                new StorageOperationRequest.Builder()
                        .setType(StorageOperationRequest.OperationType.Put)
                        .setKey(key)
                        .setObject(data),
                consumer
        );
    }

    @Override
    public void store(final String key, final Object data) throws Exception {
        final CompletableFuture<StorageOperationResponse> f = store(key, data, null);
        f.get();
    }

    @Override
    public CompletableFuture<StorageOperationResponse> fetch(final String key,
                                                             final Consumer<StorageOperationResponse> consumer) throws Exception {

        return enqueueOperation(
                new StorageOperationRequest.Builder()
                        .setType(StorageOperationRequest.OperationType.Get)
                        .setKey(key),
                consumer
            );
    }

    @Override
    public Object fetch(final String key) throws Exception {
        final CompletableFuture<StorageOperationResponse> f = fetch(key, null);
        return f.get().getObject();
    }

    @Override
    public void onStateChanged(final String old, final String current) {
        super.onStateChanged(old, current);
        if (ClientState.RUNNING.name().equals(current)) {
            processingCycle.start();
        } else if (ClientState.STOPPED.name().equals(current)) {
            processingCycle.stop();
        }
    }

    @Subscribe
    public void onStorageResponse(final StorageOperationResponse response) throws Exception {
        final StorageOpFuture f = activeFutures.remove(response.getRid());

        if (f == null) {
            logger().warn("Storage response will be skipped");
        } else {
            if (f.isDone()) {
                logger().debug("Corresponding future already done (isCanceled: {}, isCompletedExceptionally: {})",
                        f.isCancelled(), f.isCompletedExceptionally());
            } else {
                f.complete(response);
            }
        }
    }
}
