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
package com.github.srgg.yads.api.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.github.srgg.yads.api.message.Messages;
import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.shaded.com.google.common.annotations.VisibleForTesting;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 *  @author Sergey Galkin <srggal at gmail dot com>
 */
@MessageCode(Messages.MessageTypes.StorageOperationResponse)
@JsonDeserialize(builder = StorageOperationResponse.Builder.class)
@FreeBuilder
public interface StorageOperationResponse extends Message {
    /**
     *
     * @return request Id
     */
    @JsonProperty("rid")
    UUID getRid();

    @Nullable
    @JsonProperty("object")
    Object getObject();

    class Builder extends StorageOperationResponse_Builder implements Message.MessageBuilder<StorageOperationResponse, StorageOperationResponse_Builder> {
        public Builder() {
            setId(UUID.randomUUID());
        }

        public Builder(final UUID id) {
            setId(id);
        }

        @VisibleForTesting
        @Override
        public String toString() {
            return buildPartial().toString();
        }
    }
}
