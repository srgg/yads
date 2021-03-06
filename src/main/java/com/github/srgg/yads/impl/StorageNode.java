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

import com.github.srgg.yads.api.IStorage;
import com.github.srgg.yads.api.messages.RecoveryRequest;
import com.github.srgg.yads.api.messages.RecoveryResponse;
import com.github.srgg.yads.api.messages.StorageOperationRequest;
import com.github.srgg.yads.impl.api.context.Subscribe;
import com.github.srgg.yads.api.message.Messages;
import com.github.srgg.yads.impl.api.context.OperationContext;
import com.github.srgg.yads.impl.api.context.StorageExecutionContext;
import com.github.srgg.yads.impl.api.context.StorageExecutionContext.StorageState;

import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import com.github.srgg.yads.impl.util.InMemoryStorage;
import org.javatuples.Pair;

import static com.google.common.base.Preconditions.checkState;

/**
 *  @author Sergey Galkin <srggal at gmail dot com>
 */
public class StorageNode extends AbstractNode<StorageExecutionContext> {
    private final IStorage storage;

    private final AbstractProcessingCycle<StorageOperationRequest> processingCycle =
            new AbstractProcessingCycle<StorageOperationRequest>() {

        @Override
        protected OperationContext<StorageOperationRequest, Object> getContextFor(final StorageOperationRequest op) {
            final StorageExecutionContext ctx = context();
            checkState(ctx != null, "Storage context can't be null");
            return ctx.contextFor(op);
        }
    };

    public StorageNode(final String nid, final IStorage s) {
        super(nid, Messages.NodeType.Storage);
        this.storage = s;
        logger().debug("Created");
    }

    @Override
    public void onStateChanged(final String old, final String current) {
        super.onStateChanged(old, current);
        if (StorageState.RUNNING.name().equals(current)) {
            processingCycle.start();
        } else if (StorageState.STOPPED.name().equals(current)) {
            processingCycle.stop();
        }
    }

    @Subscribe
    public void onRecoveryResponse(final RecoveryResponse response) throws Exception {
        checkState(!processingCycle.isRunning(), "To apply recovery chunk the processing cycle shouldn't be running.");
        final OperationContext<?, ?> ctx = this.context().contextFor(response);

        if (!(storage instanceof InMemoryStorage)) {
            final Exception ex = new IllegalStateException("Recovery is only available for InMemoryStorage");
            ex.setStackTrace(Thread.currentThread().getStackTrace());
            ctx.failureOperation(ex);
        } else {
            final InMemoryStorage ims = (InMemoryStorage) storage;
            ims.applySnapshot(response.getSnapshot());
            ctx.ackOperation(null);
        }
    }

    @Subscribe
    public void onRecoveryRequest(final RecoveryRequest request) throws Exception {
        final OperationContext<RecoveryRequest, Pair<Boolean, Map<String, Object>>>
                ctx = this.context().contextFor(request);

        if (!(storage instanceof InMemoryStorage)) {
            final Exception ex = new IllegalStateException("Recovery is only available for InMemoryStorage");
            ex.setStackTrace(Thread.currentThread().getStackTrace());
            ctx.failureOperation(ex);
        } else {
            final InMemoryStorage ims = (InMemoryStorage) storage;
            final Map<String, Object> snapshot = ims.snapshot();
            ctx.ackOperation(new Pair<>(Boolean.TRUE, snapshot));
        }
    }

    @Subscribe
    public void onStorageRequest(final StorageOperationRequest operation) throws Exception {
        final String s = getState();
        if (!StorageState.RUNNING.name().equals(s) && !StorageState.RECOVERING.name().equals(s)) {
            throw new IllegalStateException(
                    String.format("Can't execute Storage Request, wrong state '%s'", s)
            );
        }
        processingCycle.enqueue(operation);
    }

    private abstract class AbstractProcessingCycle<T extends StorageOperationRequest> implements Runnable {
        private Thread thread;
        private final LinkedBlockingQueue<T> queuedRequests = new LinkedBlockingQueue<>();

        public boolean isRunning() {
            return thread != null;
        }

        public void start() {
            logger().debug("is about to start processing cycle");
            if (thread != null) {
                throw new IllegalStateException(
                        String.format("Node '%s': processing cycle is already started", getId())
                );
            }

            thread = new Thread(this);
            thread.start();
            logger().info("processing cycle has been STARTED");
        }

        public void stop() {
            if (thread == null) {
                logger().warn("Processing cycle can't be stopped since it hasn't been started");
            } else {
                logger().debug("processing cycle is about to stop");
                thread.interrupt();
                try {
                    thread.join();
                } catch (InterruptedException ex) {
                    logger().debug("Got InterruptedException during JOIN to the processing cycle thread");
                }
                logger().info("processing cycle has been STOPPED");
            }
        }

        public void enqueue(final T operation) {
            queuedRequests.offer(operation);
            logger().debug("storage operation enqueued {}:'{}'",
                    operation.getType(), operation.getId());
        }

        protected abstract OperationContext<StorageOperationRequest, Object> getContextFor(StorageOperationRequest op);

        @Override
        public void run() {
            Thread.currentThread().setName("node-" + getId() + "-prcsngcycle");
            for (;;) {
                try {
                    final StorageOperationRequest op = queuedRequests.peek();
                    if (op == null) {
                        // TODO: re-introduce as a config parameter
                        Thread.sleep(100L);
                        continue;
                    }

                    if (!State.RUNNING.name().equals(getState())) {
                        logger().debug("is not to be running, processing cycle will be terminated");
                        break;
                    }

                    final OperationContext<StorageOperationRequest, Object> ctx = getContextFor(queuedRequests.poll());
                    assert ctx != null;
                    StorageOperationRequest operation = null;
                    try {
                        operation = ctx.operation();
                        final Object result = storage.process(operation);

                        logger().debug("storage operation succeeded {}:'{}'",
                                getId(), operation.getType(), operation.getId());

                        ctx.ackOperation(result);
                    } catch (Exception ex) {
                        logger().error(
                                String.format("storage operation %s:'%s' is failed to process",
                                        operation == null ? null : operation.getType(),
                                        operation == null ? null : operation.getId()
                                    ),
                                ex
                            );

                        ctx.failureOperation(ex);
                    }
                } catch (InterruptedException ex) {
                    logger().debug("got InterruptedException, processing cycle will be terminated");
                    break;
                }
            }

            logger().info("processing cycle has been terminated");
        }
    }
}
