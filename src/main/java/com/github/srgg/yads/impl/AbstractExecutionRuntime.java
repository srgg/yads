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

import com.github.srgg.yads.api.messages.Message;
import com.github.srgg.yads.impl.api.context.CommunicationContext;
import com.github.srgg.yads.impl.util.TaggedLogger;
import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.srgg.yads.api.messages.NodeStatus;
import com.github.srgg.yads.impl.api.context.ExecutionContext;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkState;

/**
 *  @author Sergey Galkin <srggal at gmail dot com>
 */
public abstract class AbstractExecutionRuntime<N extends AbstractNode>
        implements CommunicationContext.MessageListener, ExecutionContext {

    private boolean started;

    private final Logger logger = new TaggedLogger(LoggerFactory.getLogger(getClass())) {
        @Override
        protected String tag() {
            return "rt:" + getId() + ": ";
        }
    };

    private final CommunicationContext messageContext;
    private final N node;
    private final AtomicReference<String> state = new AtomicReference<>();

    private int statusNotificationPeriod = 10;
    private ScheduledFuture stateNotifyFuture;
    private ScheduledExecutorService executor;

    public AbstractExecutionRuntime(final CommunicationContext mc, final N n) {
        this.messageContext = mc;
        this.node = n;
    }

    protected Logger logger() {
        return logger;
    }

    private CommunicationContext communicationContext() {
        return messageContext;
    }

    public N node() {
        return node;
    }

    @Override
    public String getId() {
        return node.getId();
    }

    protected final <M extends Message> M sendMessage(final String recipient,
                                     final Message.MessageBuilder<M, ?> builder) throws Exception {

        final Object b = builder.setSender(getId());

        @SuppressWarnings("unchecked")
        final M m = ((Message.MessageBuilder<M, ?>) b).build();

        communicationContext().send(recipient, m);
        return m;
    }

    private void start() {
        logger().debug("is about to start");
        messageContext.registerHandler(this);

        // TODO: consider introducing flexible configuration
        executor = Executors.newSingleThreadScheduledExecutor();

        doStart();

        started = true;
        rescheduleStatusNotification(2, statusNotificationPeriod);
        logger().info("has been STARTED");
    }

    private void stop() {
        logger().debug("is about to stop");
        doStop();

        if (stateNotifyFuture != null) {
            stateNotifyFuture.cancel(true);
        }

        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger().debug("Got InterruptedException while waiting for executor termination");
        }

        if (!executor.isTerminated()) {
            final List<Runnable> pendingTasks = executor.shutdownNow();
            logger().warn("List of pending tasks that weren't stopped:\n\t{}",
                    Joiner.on("\n\t").join(pendingTasks));
        }

        executor = null;
        messageContext.unregisterHandler(this);
        started = false;
        logger().info("has been STOPPED");
    }

    protected void doStart() {
    }

    protected void doStop() {
    }

    protected void notifyAboutNodeStatus() {
        rescheduleStatusNotification(0, statusNotificationPeriod);
    }

    private void rescheduleStatusNotification(final long initialDelay, final long period) {
        if (!started) {
            logger.warn("CAN'T Reschedule Status Notification, runtime is not in running state.");
            return;
        }

        if (stateNotifyFuture != null && !stateNotifyFuture.isCancelled()) {
            stateNotifyFuture.cancel(true);
        }

        stateNotifyFuture = executor.scheduleAtFixedRate(() -> {
            try {
                final String status = state.get();
                final NodeStatus.Builder b = new NodeStatus.Builder()
                        .setStatus(status)
                        .setNodeType(node.getNodeType());

                sendMessage(CommunicationContext.LEADER_NODE, b);
            } catch (Exception e) {
                logger.error("Can't send status", e);
            }
        }, initialDelay, period, TimeUnit.SECONDS);
    }

    @Override
    public void stateChanged(final String st) {
        switch (st) {
            case "STARTED":
                checkState(!started, "Can't start node runtime '%s', it is already started", getId());
                start();
                break;

            case "STOPPED":
                checkState(started, "Can't stop node runtime '%s', it is not started", getId());
                stop();
                break;

            default:
                // nothing to do
        }
        this.state.set(st);
    }
}
