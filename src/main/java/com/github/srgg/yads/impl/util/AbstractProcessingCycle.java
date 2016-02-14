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
import org.slf4j.Logger;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author Sergey Galkin <srggal at gmail dot com>
 */
public abstract class AbstractProcessingCycle<T> implements Runnable,
        Identifiable<String> {

    private final Logger logger;
    private Thread thread;
    private final LinkedBlockingQueue<T> queuedRequests = new LinkedBlockingQueue<>();

    public AbstractProcessingCycle(final Logger lgr) {
        this.logger = lgr;
    }

    public boolean isRunning() {
        return thread != null;
    }

    protected Logger logger() {
        return logger;
    }

    public void start() {
        logger().debug("is about to start processing cycle");
        if (thread != null) {
            throw new IllegalStateException(
                    String.format("Processing cycle '%s' is already started", getId())
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
        logger().debug("storage operation enqueued: '{}'", operation);
    }

    protected abstract boolean doProcessing() throws Exception;

    @Override
    public void run() {
        Thread.currentThread().setName("prcsngcycle-" + getId());
        for (;;) {
            try {
                final T op = queuedRequests.peek();
                if (op == null) {
                    // TODO: re-introduce as a config parameter
                    Thread.sleep(100L);
                    continue;
                }

                try {
                    if (!doProcessing()) {
                        break;
                    }
                } catch (Exception e) {
                    logger().error("UE raised during processing", e);
                }
            } catch (InterruptedException ex) {
                logger().debug("got InterruptedException, processing cycle will be terminated");
                break;
            }
        }

        logger().info("processing cycle has been terminated");
    }

    protected T poll() {
        return queuedRequests.poll();
    }
}
