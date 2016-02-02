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

import com.github.srgg.yads.api.ActivationAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Sergey Galkin <srggal at gmail dot com>
 */
public abstract class AbstractActivationAware implements ActivationAware {
    private final Logger logger;

    private final AtomicReference<String> state = new AtomicReference<>(State.NEW.name());

    private final Map<String, Set<String>> allowedTransitions;

    protected AbstractActivationAware() {
        logger = LoggerFactory.getLogger(getClass());
        allowedTransitions = createTransitions();
    }

    protected AbstractActivationAware(final String tagFormat, final Object... args) {
        logger = new TaggedLogger(LoggerFactory.getLogger(getClass()), String.format(tagFormat, args));
        allowedTransitions = createTransitions();
    }

    protected final Logger logger() {
        return logger;
    }

    @Override
    public final String getState() {
        return state.get();
    }

    @Override
    public final void start() {
        logger().debug("is about to start");
        doStart();
        setState(State.STARTED.name());
        logger().info("has been STARTED");
    }

    @Override
    public final void stop() {
        logger().debug("is about to stop");
        setState(State.STOPPED.name());
        doStop();
        logger().info("has been STOPPED");
    }

    protected void doStart() {
    }

    protected void doStop() {
    }

    protected static void throwWrongStateTransitions(final String from, final String to) throws IllegalStateException {
        throw new IllegalStateException(String.format("Can't change getState from '%s' to '%s'.", from, to));
    }

    protected void onStateChanged(final String old, final String current) {
        logger().debug("[STATE] changed '{}' -> '{}'", old, current);
    }

    protected Map<String, Set<String>> createTransitions() {
        return TransitionMatrixBuilder.create()
                .add(State.NEW, State.STARTED, State.FAILED)
                .add(State.STARTED, State.RUNNING, State.FAILED, State.STOPPED)
                .add(State.STOPPED, State.STARTED, State.FAILED)
                .add(State.FAILED, State.STOPPED)
                .add(State.RUNNING, State.FAILED, State.STOPPED)
                .build();
    }

    protected String setState(final String newState) throws IllegalStateException {
        if (State.NEW.name().equals(newState)) {
            throw new IllegalStateException("Can't change getState to 'New'");
        }

        for (;;) {
            final String s = state.get();
            if (s.equals(newState)) {
                throwWrongStateTransitions(s, newState);
            }

            final Set<String> availableTransitions = allowedTransitions.get(s);
            if (!availableTransitions.contains(newState)) {
                throwWrongStateTransitions(s, newState);
            }

            if (state.compareAndSet(s, newState)) {
                onStateChanged(s, newState);
                return s;
            }
        }
    }

    protected static final class TransitionMatrixBuilder {
        private Map<String, Set<String>> matrix;

        public TransitionMatrixBuilder() {
            this(new HashMap<>());
        }

        public TransitionMatrixBuilder(final Map<String, Set<String>> m) {
            this.matrix = m;
        }

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

        public <E extends Enum<E>> TransitionMatrixBuilder add(final E state, final E... transistions) {
            final Set<String> existingTransitions = matrix.get(state.name());

            if (existingTransitions == null) {
                matrix.put(state.name(), enumAsStringSet(transistions));
            } else {
                existingTransitions.addAll(enumAsStringSet(transistions));
            }
            return this;
        }

        public <E extends Enum<E>> TransitionMatrixBuilder replace(final E state, final E... transistions) {
            matrix.put(state.name(), enumAsStringSet(transistions));
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
