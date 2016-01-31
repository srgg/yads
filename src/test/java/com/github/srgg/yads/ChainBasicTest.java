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
package com.github.srgg.yads;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import com.github.srgg.yads.impl.util.GenericChain;
import com.github.srgg.yads.impl.util.GenericChain.ChainListener.ActionType;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkState;
import static net.javacrumbs.jsonunit.JsonAssert.assertJsonEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

/**
 *  @author Sergey Galkin <srggal at gmail dot com>
 */
@RunWith(MockitoJUnitRunner.class)
public class ChainBasicTest {

    public static class TestChain extends GenericChain<Object> {
        public TestChain addNodeT(String nodeId) throws Exception {
            super.addNode(nodeId, null, null);
            return this;
        }

        public TestChain removeNodeT(String nodeId) throws Exception {
            super.removeNode(nodeId, null);
            return this;
        }
    }

    private TestChain chain;

    @Mock
    private TestChain.ChainListener chainListener;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void initialize() throws Exception {
        chain = mkChain(0);
        JsonUnitInitializer.initialize();
    }

    @Test
    public void check_if_nodesAddedOnlyToTheTail() throws Exception {
        assertJsonEquals("[]", chain);
        assertNull(chain.head());
        assertNull(chain.tail());
        verifyZeroInteractions(chainListener);

        chain.addNodeT("1");
        verifyChainOperation(ActionType.NodeAdded, "(1)");

        chain.addNodeT("2");
        verifyChainOperation(ActionType.NodeAdded, "1 - (2)");

        chain.addNodeT("3");
        verifyChainOperation(ActionType.NodeAdded, "1 - 2 - (3)");

        verifyNoMoreInteractions(chainListener);
    }

    @Test
    public void fail_on_addingNodeTwice() throws Exception {
        String dublicate = UUID.randomUUID().toString();
        chain.addNodeT(dublicate);

        exception.expect(IllegalArgumentException.class);
        chain.addNodeT(dublicate);
    }

    @Test(expected = IllegalArgumentException.class)
    public void fail_on_removingNonExistentNode() throws Exception {
        chain.removeNodeT(UUID.randomUUID().toString());
    }

    @Test
    public void removeNodesWithoutBreakingTheChain() throws Exception {
        // removing middle node
        mkChain(3).removeNodeT("2");
        verifyChainOperation(ActionType.NodeRemoved, "1 - (2) - 3");


        // removing tail node
        mkChain(3).removeNodeT("3");
        verifyChainOperation(ActionType.NodeRemoved, "1 - 2 - (3)");


        // removing head node
        mkChain(3).removeNodeT("1");
        verifyChainOperation(ActionType.NodeRemoved, "(1) - 2 - 3");


        // removing the last node
        mkChain(1).removeNodeT("1");
        verifyChainOperation(ActionType.NodeRemoved, "(1)");

        verifyNoMoreInteractions(chainListener);
    }

    private TestChain mkChain(int nodes) throws Exception {
        chain = new TestChain();
        for (int i=1; i<= nodes; ++i) {
            chain.addNodeT(Integer.toString(i));
        }

        doReturn("simple-listener")
                .when(chainListener)
                .getId();

        chain.registerHandler(chainListener);

        reset(chainListener);
        doReturn("simple-listener")
                .when(chainListener)
                .getId();
        return chain;
    }

    // Format: 'head-node - middle-node1 - ..  - (changed-node) - .. - tail-node'
    private void verifyChainOperation(ActionType action, String expectedChain) throws Exception {
        final List<String> values = ChainVerificationUtils.parseExpectedChainTemplate(expectedChain);
        final AtomicReference<String> changedNodeId = new AtomicReference<>(null);
        final AtomicInteger changedIdx = new AtomicInteger(-1);

        values.forEach(
            s -> {
                final int idx = values.indexOf(s);
                s = s.trim();
                if (s.startsWith("(") && s.endsWith(")")) {
                    s = s.substring(1,s.length())
                            .substring(0, s.length()-2)
                            .trim();

                    changedNodeId.set(s);
                    changedIdx.set(idx);
                }
                values.set(idx, s);
            });

        checkState(changedNodeId.get() != null, "Chain template has wrong format, it seems that changed node is not marked by brackets");

        // -- determine parameters that should be passed to callback
        final String prevNodeId = changedIdx.get() > 0 ? values.get(changedIdx.get() -1) : null;
        final String nextNodeId = changedIdx.get() < (values.size() - 1) ? values.get(changedIdx.get() +1): null;


        if (ActionType.NodeRemoved.equals(action)) {
            values.remove(changedIdx.get());
        }

        ChainVerificationUtils.doVerifyChain(chain, values);

        // -- verify callback
        verify(chainListener).onNodeChanged(same(action),
                ChainVerificationUtils.eqNodeId(changedNodeId.get()),
                ChainVerificationUtils.eqNodeId(prevNodeId),
                ChainVerificationUtils.eqNodeId(nextNodeId)
            );

        verifyZeroInteractions(chainListener);
    }
}
