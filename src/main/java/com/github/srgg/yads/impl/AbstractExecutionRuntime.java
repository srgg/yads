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

import com.github.srgg.yads.api.ActivationAware;
import com.github.srgg.yads.api.messages.Message;
import com.github.srgg.yads.impl.api.context.CommunicationContext;
import com.github.srgg.yads.impl.util.TaggedLogger;
import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.srgg.yads.api.messages.NodeStatus;
import com.github.srgg.yads.impl.api.context.ExecutionContext;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkState;

/**
 * @author Sergey Galkin <srggal at gmail dot com>
 */
public abstract class AbstractExecutionRuntime<N extends AbstractNode>
        implements CommunicationContext.MessageListener, ExecutionContext {

    private boolean running;

    private final Logger logger = new TaggedLogger(LoggerFactory.getLogger(getClass())) {
        @Override
        protected String tag() {
            return "rt:" + getId() + ": ";
        }
    };

    private final Map<String, Set<String>> allowedTransitions;

    private final CommunicationContext messageContext;
    private final N node;
    private final AtomicReference<String> state = new AtomicReference<>("NEW");

    private int statusNotificationPeriod = 10000;
    private ScheduledFuture stateNotifyFuture;
    private ScheduledExecutorService executor;

    public AbstractExecutionRuntime(final CommunicationContext mc, final N n) {
        allowedTransitions = createTransitions();
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

        running = true;
        rescheduleStatusNotification(500, statusNotificationPeriod);
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
        running = false;
        logger().info("has been STOPPED");
    }

    protected void doStart() {
    }

    protected void doStop() {
    }

    protected void executeBgTaskImmediately() {
        rescheduleStatusNotification(0, statusNotificationPeriod);
    }

    protected Runnable createBgTask() {
        return () -> {
            try {
                final String status = state.get();
                final NodeStatus.Builder b = new NodeStatus.Builder()
                        .setStatus(status)
                        .setNodeType(node.getNodeType());

                sendMessage(CommunicationContext.LEADER_NODE, b);
            } catch (Exception e) {
                logger.error("Can't send status", e);
            }
        };
    }

    protected void rescheduleStatusNotification(final long initialDelay, final long period) {
        if (!running) {
            logger.warn("CAN'T Reschedule Bg task, runtime is not in a running state.");
            return;
        }

        if (stateNotifyFuture != null && !stateNotifyFuture.isCancelled()) {
            stateNotifyFuture.cancel(true);
        }

        stateNotifyFuture = executor.scheduleAtFixedRate(
                createBgTask(), initialDelay, period, TimeUnit.MILLISECONDS);
    }

    @Override
    public String changeState(final String newState) {
        if ("NEW".equals(newState)) {
            throw new IllegalStateException("Can't change state to 'NEW'");
        }

        for (;;) {
            final String s = state.get();
            if (newState.equals(s)) {
                throwWrongStateTransitions(s, newState);
            }

            final Set<String> availableTransitions = allowedTransitions.get(s);
            if (!availableTransitions.contains(newState)) {
                throwWrongStateTransitions(s, newState);
            }

            if (state.compareAndSet(s, newState)) {
                stateChanged(newState);
                node().onStateChanged(s, newState);
                return s;
            }
        }
    }

    @Override
    public String getState() {
        return state.get();
    }

    public void stateChanged(final String st) {
        switch (st) {
            case "STARTED":
                checkState(!running, "Can't start runtime '%s', it is already started", getId());
                start();
                break;

            case "STOPPED":
                checkState(running, "Can't stop runtime '%s', it is not started", getId());
                stop();
                break;

            default:
                // nothing to do
        }
        this.state.set(st);
    }

    protected static void throwWrongStateTransitions(final String from, final String to) throws IllegalStateException {
        throw new IllegalStateException(String.format("Can't change getState from '%s' to '%s'.", from, to));
    }

    protected Map<String, Set<String>> createTransitions() {
        return TransitionMatrixBuilder.create()
                .add(ActivationAware.State.NEW, ActivationAware.State.STARTED, ActivationAware.State.FAILED)
                .add(ActivationAware.State.STARTED, ActivationAware.State.RUNNING, ActivationAware.State.FAILED, ActivationAware.State.STOPPED)
                .add(ActivationAware.State.STOPPED, ActivationAware.State.STARTED, ActivationAware.State.FAILED)
                .add(ActivationAware.State.FAILED, ActivationAware.State.STOPPED)
                .add(ActivationAware.State.RUNNING, ActivationAware.State.FAILED, ActivationAware.State.STOPPED)
                .build();
    }

    protected static class TransitionMatrixBuilder {
        private Map<String, Set<String>> matrix;

        public TransitionMatrixBuilder() {
            this(new HashMap<>());
        }

        public TransitionMatrixBuilder(final Map<String, Set<String>> m) {
            this.matrix = m;
        }

        @SafeVarargs
        private static <E extends Enum<E>> Set<String> enumAsStringSet(final E... values) {
            if (values.length == 0) {
                //noinspection unchecked
                return Collections.EMPTY_SET;
            }

            final ArrayList<String> strings = new ArrayList<>(values.length);
            for (E e : values) {
                strings.add(e.name());
            }

            return new HashSet<>(strings);
        }

        @SafeVarargs
        public final <E extends Enum<E>> TransitionMatrixBuilder add(final E state, final E... transitions) {
            final Set<String> existingTransitions = matrix.get(state.name());

            if (existingTransitions == null) {
                matrix.put(state.name(), enumAsStringSet(transitions));
            } else {
                existingTransitions.addAll(enumAsStringSet(transitions));
            }
            return this;
        }

        @SafeVarargs
        public final <E extends Enum<E>> TransitionMatrixBuilder replace(final E state, final E... transitions) {
            matrix.put(state.name(), enumAsStringSet(transitions));
            return this;
        }

        public Map<String, Set<String>> build() {
            try {
                return matrix;
            } finally {
                matrix = null;
            }
        }

        public static TransitionMatrixBuilder create() {
            return new TransitionMatrixBuilder();
        }

        public static TransitionMatrixBuilder create(final Map<String, Set<String>> matrix) {
            return new TransitionMatrixBuilder(matrix);
        }
    }
}
