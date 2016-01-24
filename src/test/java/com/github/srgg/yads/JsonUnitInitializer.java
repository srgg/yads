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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import net.javacrumbs.jsonunit.core.internal.JsonUtils;
import net.javacrumbs.jsonunit.core.internal.NodeFactory;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.util.List;

/**
 *  @author Sergey Galkin <srggal at gmail dot com>
 */
public class JsonUnitInitializer {
    public static ObjectMapper initialize() {
        // making JsonAssert to be more tolerant to JSON format
        final Object converter;
        try {
            converter = FieldUtils.readStaticField(JsonUtils.class, "converter", true);
            final List<NodeFactory> factories = (List<NodeFactory>) FieldUtils.readField(converter, "factories", true);

            ObjectMapper mapper;
            for (NodeFactory nf: factories) {
                if (nf.getClass().getSimpleName().equals("Jackson2NodeFactory")) {
                    mapper = (ObjectMapper) FieldUtils.readField(nf, "mapper", true);
                    mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
                            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                            .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
                            .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
                            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

                    return mapper;
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Can't initialize Jackson2 ObjectMapper because of UE", e);
        }

        throw new IllegalStateException("Can't initialize Jackson2 ObjectMapper, Jackson2NodeFactory is not found");
    }
}
