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
package com.github.srgg.yads.impl.util;

import com.github.srgg.yads.api.IStorage;
import com.github.srgg.yads.api.messages.StorageOperation;

import java.util.HashMap;
import java.util.Map;

/**
 *  @author Sergey Galkin <srggal at gmail dot com>
 */
public class InMemoryStorage implements IStorage {
    private Map<Object, Object> data = new HashMap<>();

    @Override
    public Object process(final StorageOperation storageOperation) throws Exception {
        switch (storageOperation.getType()) {
            case Get:
                return data.get(storageOperation.getKey());

            case Put:
                data.put(storageOperation.getKey(), storageOperation.getObject());
                return null;

            default:
                throw new IllegalStateException();
        }
    }
}
