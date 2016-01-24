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

import com.github.srgg.yads.impl.api.context.CommunicationContext;
import com.github.srgg.yads.impl.util.TaggedLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.srgg.yads.api.messages.NodeStatus;
import com.github.srgg.yads.impl.api.context.NodeContext;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkState;

/**
 *  @author Sergey Galkin <srggal at gmail dot com>
 */
public abstract class AbstractNodeRuntime<N extends AbstractNode>
        implements CommunicationContext.MessageListener, NodeContext {

    private boolean started;

    private final Logger logger = new TaggedLogger(LoggerFactory.getLogger(getClass())) {
        @Override
        protected String tag() {
            return "rt:" + getId() + ": ";
        }
    };

    private final CommunicationContext messageContext;
    private final N node;
    private AtomicReference<String> state = new AtomicReference<>();

    private ScheduledExecutorService executor;

    public AbstractNodeRuntime(final CommunicationContext mc, final N n) {
        this.messageContext = mc;
        this.node = n;
    }

    protected Logger logger() {
        return logger;
    }

    protected CommunicationContext communicationContext() {
        return messageContext;
    }

    protected N node() {
        return node;
    }

    @Override
    public String getId() {
        return node.getId();
    }

    private void start() {
        logger().debug("is about to start");
        messageContext.registerHandler(this);

        // TODO: consider introducing flexible configuration
        executor = Executors.newSingleThreadScheduledExecutor();

        doStart();

        // schedule status notifications
        executor.scheduleAtFixedRate(() -> {
            try {
                final String status = state.get();
                final NodeStatus msg = new NodeStatus.Builder()
                        .setSender(getId())
                        .setStatus(status)
                        .setNodeType(node.getNodeType())
                        .build();

                messageContext.send(CommunicationContext.LEADER_NODE, msg);
            } catch (Exception e) {
                logger.error("Can't send status", e);
            }
        }, 2, 10, TimeUnit.SECONDS);

        logger().info("has been STARTED");
    }

    private void stop() {
        logger().debug("is about to stop");
        doStop();
        executor.shutdown();
        messageContext.unregisterHandler(this);
        logger().info("has been STOPPED");
    }

    protected void doStart() {
    }

    protected void doStop() {
    }

    @Override
    public void stateChanged(final String st) {
        switch (st) {
            case "STARTED":
                checkState(!started, "Can't start node runtime '%s', it is already started", getId());
                start();
                started = true;
                break;

            case "STOPPED":
                checkState(started, "Can't stop node runtime '%s', it is not started", getId());
                stop();
                started = false;
                break;

            default:
                // nothing to do
        }
        this.state.set(st);
    }
}
