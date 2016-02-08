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
import com.google.common.base.Preconditions;

import java.util.concurrent.Future;

/**
 *  @author Sergey Galkin <srggal at gmail dot com>
 */
public class ClientImpl extends AbstractNode<ClientExecutionContext> implements Client {
    public ClientImpl(final String id, final Messages.NodeType type) {
        super(id, type);
    }

    private Future<StorageOperationResponse> performOperation(final StorageOperationRequest.Builder builder) throws Exception {
        Preconditions.checkState("RUNNING".equals(getState()));

        builder.setSender(getId());
        return context().perform(builder);
    }

    @Override
    public void store(final String key, final Object data) throws Exception {
        final Future<StorageOperationResponse> f = performOperation(new StorageOperationRequest.Builder()
            .setType(StorageOperationRequest.OperationType.Put)
            .setKey(key)
            .setObject(data)
        );

        f.get();
    }

    @Override
    public Object fetch(final String key) throws Exception {
        final Future<StorageOperationResponse> f = performOperation(new StorageOperationRequest.Builder()
                .setType(StorageOperationRequest.OperationType.Get)
                .setKey(key)
        );

        return f.get().getObject();
    }
}
