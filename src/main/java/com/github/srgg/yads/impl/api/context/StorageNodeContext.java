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
package com.github.srgg.yads.impl.api.context;

import com.github.srgg.yads.api.messages.RecoveryRequest;
import com.github.srgg.yads.api.messages.RecoveryResponse;
import com.github.srgg.yads.api.messages.StorageOperation;
import org.javatuples.Pair;

import java.util.Map;

/**
 *  @author Sergey Galkin <srggal at gmail dot com>
 */
public interface StorageNodeContext extends NodeContext {
    OperationContext<StorageOperation, Object> contextFor(StorageOperation operation);
    OperationContext<RecoveryRequest, Pair<Boolean, Map<String, Object>>> contextFor(RecoveryRequest operation);
    OperationContext<RecoveryResponse, Void> contextFor(RecoveryResponse operation);
}
