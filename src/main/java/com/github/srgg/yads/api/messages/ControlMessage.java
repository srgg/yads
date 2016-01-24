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
import org.inferred.freebuilder.FreeBuilder;
import com.github.srgg.yads.api.message.Messages;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.UUID;

/**
 *  @author Sergey Galkin <srggal at gmail dot com>
 */
@MessageCode(Messages.MessageTypes.ControlMessage)
@JsonDeserialize(builder = ControlMessage.Builder.class)
@FreeBuilder
public interface ControlMessage extends Message {
    enum Role { Head, Middle, Tail }

    enum Type {
        SetRole,
        SetChain,
        SetState
    }

    @JsonProperty("type")
    @Nullable
    EnumSet<Type> getType();

    @JsonProperty("roles")
    @Nullable
    EnumSet<Role> getRoles();

    @JsonProperty("state")
    @Nullable
    String getState();

    @JsonProperty("nextNode")
    @Nullable
    String getNextNode();

    @JsonProperty("prevNode")
    @Nullable
    String getPrevNode();

    class Builder extends ControlMessage_Builder {
        public Builder() {
            setId(UUID.randomUUID());
        }

        public Builder(final UUID id) {
            setId(id);
        }

        @Override
        public ControlMessage build() {
            final LinkedList<Type> types = new LinkedList<>();
            LinkedList<Role> roles = null;

            if (getNextNode() != null || getPrevNode() != null) {
                roles = new LinkedList<>();
                types.add(Type.SetChain);

                if (getNextNode() != null && getPrevNode() != null) {
                    roles.add(Role.Middle);
                } else if (getNextNode() != null) {
                    roles.add(Role.Head);
                } else {
                    roles.add(Role.Tail);
                }
            }

            if (getRoles() == null && roles != null && !roles.isEmpty()) {
                types.add(ControlMessage.Type.SetRole);
                setRoles(EnumSet.copyOf(roles));
            }

            if (getState() != null) {
                types.add(ControlMessage.Type.SetState);
            }

            if (getType() == null) {
                setType(EnumSet.copyOf(types));
            }
            return super.build();
        }
    }
}
