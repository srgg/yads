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

import com.github.srgg.yads.api.Identifiable;
import com.github.srgg.yads.impl.api.Sink;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * @author Sergey Galkin <srggal at gmail dot com>
 */
public class GenericSink<H extends Identifiable<String>> implements Sink<H> {
    private final Map<String, H> registeredHandlers = new ConcurrentHashMap<>();

    @Override
    public void registerHandler(final H handler) {
        final String id = handler.getId();
        registeredHandlers.put(id, handler);
    }

    @Override
    public void unregisterHandler(final H handler) {
        final String id = handler.getId();
        registeredHandlers.remove(id);
    }

    protected H handlerById(final String handlerId) {
        return registeredHandlers.get(handlerId);
    }

    protected Set<Map.Entry<String, H>> handlers() {
        return registeredHandlers.entrySet();
    }

    protected void forEach(final BiConsumer<String, H> action) {
        registeredHandlers.forEach(action);
    }
}

