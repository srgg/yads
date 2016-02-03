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

import com.github.srgg.yads.impl.util.AbstractActivationAware;
import com.github.srgg.yads.api.Configurable;
import com.github.srgg.yads.api.message.Messages;
import com.github.srgg.yads.impl.api.context.ExecutionContext;

/**
 * @author Sergey Galkin <srggal at gmail dot com>
 */
public abstract class AbstractNode<C extends ExecutionContext> extends AbstractActivationAware
        implements Configurable<C> {

    private final String nodeId;

    private C context;
    private final Messages.NodeType nodeType;

    protected AbstractNode(final String id, final Messages.NodeType type) {
        super("Node '%s': ", id);
        this.nodeId = id;
        this.nodeType = type;
    }

    protected final C context() {
        return context;
    }

    public final Messages.NodeType getNodeType() {
        return nodeType;
    }

    @Override
    public final String getId() {
        return nodeId;
    }

    @Override
    public void configure(final C ctx) throws Exception {
        this.context = ctx;
        logger().debug("Configured");
    }

    @Override
    protected void doStop() {
        this.context = null;
    }

    @Override
    protected void onStateChanged(final String old, final String current) {
        super.onStateChanged(old, current);
        final C ctx = context();
        if (ctx != null) {
            // propagate state change to the context
            context().stateChanged(current);
        }
    }

    /**
     * DO NOT CALL THIS METHOD DIRECTLY.
     *
     * <p/>
     * It was introduced because of lack of proper visibility support in Java, as a result
     * AbstractNodeRuntime being placed in the separate package can access the only public methods :(
     * (later it might be refactored by using Factories and other Java patterns that hides such visibility issues)
     */
    public final String setState(final String newState) throws IllegalStateException {
        return super.setState(newState);
    }
}
