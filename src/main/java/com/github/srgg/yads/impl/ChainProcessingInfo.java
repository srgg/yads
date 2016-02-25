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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.srgg.yads.api.Identifiable;
import com.github.srgg.yads.api.messages.Message;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.util.*;

import static com.google.common.base.Preconditions.checkState;

/**
 *  @author Sergey Galkin <srggal at gmail dot com>
 */
public class ChainProcessingInfo {

    private static final String PROCESSOR_INFO = "PRC_INFO";

    public static class ProcessingInfo {
        @JsonProperty
        private String id;

        @JsonProperty
        private Long startedAt;

        @JsonProperty
        private Long completedAt;

        public String getId() {
            return id;
        }

        public Long getStartedAt() {
            return startedAt;
        }

        public Long getCompletedAt() {
            return completedAt;
        }

        public ProcessingInfo(final String processorId) {
            this.id = processorId;
            startedAt = System.currentTimeMillis();
        }

        public void markAsCompleted() {
            checkState(startedAt != null);
            completedAt = System.currentTimeMillis();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }

            if (!(o instanceof ProcessingInfo)) {
                return false;
            }

            final ProcessingInfo that = (ProcessingInfo) o;
            return Objects.equals(getId(), that.getId());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getId());
        }
    }

    @JsonProperty
    private String client;

    @JsonProperty
    private UUID originalRId;

    @JsonProperty
    private List<ProcessingInfo> processingInfos;

    public String getClient() {
        return client;
    }

    public UUID getOriginalRId() {
        return originalRId;
    }

    public List<ProcessingInfo> getProcessingInfos() {
        return processingInfos;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof ChainProcessingInfo)) {
            return false;
        }

        final ChainProcessingInfo that = (ChainProcessingInfo) o;
        return Objects.equals(client, that.client)
                && Objects.equals(originalRId, that.originalRId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(client, originalRId);
    }

    public static boolean didProcessingStart(final Message message) {
        return getProcessingInfo(message) != null;
    }

    public static <M extends Message> M startProcessing(final M message, final Identifiable<String> processor) {
        checkState(!didProcessingStart(message));

        // inject mutable meta
        final Map<String, Object> meta = new HashMap<>(message.getMeta());
        try {
            FieldUtils.writeField(message, "meta", meta, true);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        // create processing info, if needed
        if (meta.get(PROCESSOR_INFO) == null) {
            final ChainProcessingInfo info = new ChainProcessingInfo();
            info.client = message.getSender();
            info.originalRId = message.getId();
            info.processingInfos = new LinkedList<>();
            meta.put(PROCESSOR_INFO, info);
        }

        final ChainProcessingInfo info = getProcessingInfo(message);
        checkState(info != null);

        info.processingInfos.add(new ProcessingInfo(processor.getId()));

        return message;
    }

    public static ChainProcessingInfo getProcessingInfo(final Message message) {
        if (message.getMeta() != null) {
            return (ChainProcessingInfo) message.getMeta().get(PROCESSOR_INFO);
        }
        return null;
    }

    public static <M extends Message> M completeProcessing(final M message, final Identifiable<String> processor) {
        final ChainProcessingInfo processingInfo = getProcessingInfo(message);
        checkState(processingInfo != null);

        processingInfo.processingInfos
            .stream()
                .filter(pi -> pi.getId().equals(processor.getId()))
                .findFirst() // since id MUST BE unique, should be the only node
                .orElseThrow(IllegalStateException::new)
                .markAsCompleted();

        return message;
    }
}
