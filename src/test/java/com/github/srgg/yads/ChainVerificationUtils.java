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

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import com.github.srgg.yads.impl.api.Chain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.argThat;

public class ChainVerificationUtils {

    public static Chain.INodeInfo eqNodeId(String nodeId) {
        return argThat(matchNodeId(nodeId));
    }

    private static BaseMatcher<Chain.INodeInfo> matchNodeId(final String nodeId){
        return new BaseMatcher<Chain.INodeInfo>() {
            @Override
            public boolean matches(Object item) {
                return (item == null && nodeId == null) || (item != null
                        && Chain.INodeInfo.class.isInstance(item)
                        && nodeId.equals(((Chain.INodeInfo)item).getId())
                );
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("eqNodeId(")
                        .appendValue(nodeId)
                        .appendText(")");
            }

            @Override
            public void describeMismatch(Object item, Description description) {
                description.appendText("was ")
                        .appendValue(item);
            }

        };
    }

    protected static <T> void doVerifyChain(Chain<T> chain, List<String> expectedNodesSequence) {
        final List<Chain.INodeInfo<T>> chainedNodes = chain.asList();
        final List<String> chainedNodeIds = new ArrayList<>(chainedNodes.size());

        chainedNodes.forEach( n -> chainedNodeIds.add((String)n.getId()));

        // -- verify node sequence
        assertEquals("Unexpected chain", expectedNodesSequence, chainedNodeIds);

        if (!expectedNodesSequence.isEmpty()) {
            // -- verify chain head
            assertEquals("Chain head is pointing to the wrong node",
                    expectedNodesSequence.get(0), chain.head().getId());

            // -- verify chain tail
            final String expectedTail = expectedNodesSequence.get(expectedNodesSequence.size() - 1);
            assertEquals("Chain tail is pointing to the wrong node",
                    expectedTail, chain.tail().getId());
        }
    }

    // Format: 'head-node - middle-node1 - ..  - tail-node'
    protected static List<String> parseExpectedChainTemplate(String expectedChain) {
        final List<String> values = new ArrayList<>(Arrays.asList(expectedChain.split("\\s-\\s")));

        values.forEach(
                s -> {
                    final int idx = values.indexOf(s);
                    s = s.trim();
                    values.set(idx, s);
                }
        );
        return values;
    }

    // Format: 'head-node - middle-node1 - ..  - tail-node'
    public static <T> void verifyChain(Chain<T> chain, String expectedChain) {
        final List<String>  nodeSequence = parseExpectedChainTemplate(expectedChain);
        doVerifyChain(chain, nodeSequence);
    }
}
