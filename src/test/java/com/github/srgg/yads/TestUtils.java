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

import com.github.srgg.yads.api.messages.Message;
import net.javacrumbs.jsonunit.ConfigurableJsonMatcher;
import net.javacrumbs.jsonunit.core.Option;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import com.github.srgg.yads.impl.api.Chain;
import org.hamcrest.Matcher;
import org.mockito.exceptions.Reporter;
import org.mockito.internal.invocation.InvocationMatcher;
import org.mockito.internal.invocation.InvocationsFinder;
import org.mockito.internal.verification.api.InOrderContext;
import org.mockito.internal.verification.api.VerificationData;
import org.mockito.invocation.Invocation;
import org.mockito.verification.VerificationMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.argThat;

public class TestUtils {

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
        final List<String> splited= Arrays.asList(expectedChain.split("\\s-\\s"));
        final List<String> values = new ArrayList<>(splited.size());

        splited.forEach(
                s -> {
                    s = s.trim();

                    if (!s.isEmpty()) {
                        values.add(s);
                    }
                }
        );
        return values;
    }

    // Format: 'head-node - middle-node1 - ..  - tail-node'
    public static <T> void verifyChain(Chain<T> chain, String expectedChain) {
        final List<String>  nodeSequence = parseExpectedChainTemplate(expectedChain);
        doVerifyChain(chain, nodeSequence);
    }

    public static class MessageMatcher extends BaseMatcher {
        private final Class clazz;
        private final Object ethalon;
        private Object actual;
        private final ConfigurableJsonMatcher matcher;


        protected MessageMatcher(Class messageClass, Object ethalon) {
            this.clazz = messageClass;
            this.ethalon = ethalon;
            matcher = jsonEquals(ethalon)
                    .when(Option.IGNORING_EXTRA_FIELDS);
        }

        @Override
        public boolean matches(Object item) {
            actual = item;
            return item == null && ethalon == null
                    || item != null && clazz.isInstance(item) && matcher.matches(item);
        }

        @Override
        public void describeTo(Description description) {
            matcher.describeTo(description);
        }

        public Object getActual() {
            return actual;
        }
        
        public static MessageMatcher create(Class messageClass, Object expected) {
            return new MessageMatcher(messageClass, expected);
        }
    }

    public static class MessageBuilderMatcher<M extends Message.MessageBuilder> extends BaseMatcher<M> {
        private final Class<M> clazz;
        private final Object ethalon;
        private final ConfigurableJsonMatcher matcher;

        protected MessageBuilderMatcher(Class<M> builderClass, Object ethalon) {
            this.ethalon = ethalon;
            this.clazz = builderClass;

            matcher = jsonEquals(ethalon)
                    .when(Option.IGNORING_EXTRA_FIELDS);
        }

        @Override
        public boolean matches(Object item) {
            if (item == null && ethalon == null) {
                return true;
            }

            if (item != null && Message.MessageBuilder.class.isInstance(item)) {
                final Object m = ((Message.MessageBuilder) item).buildPartial();

                return matcher.matches(m);
            }
            return false;
        }

        @Override
        public void describeTo(Description description) {
            matcher.describeTo(description);
        }

        public static <M extends Message.MessageBuilder> MessageBuilderMatcher<M> create(Class<M> builderClass, Object expected) {
            return new MessageBuilderMatcher<>(builderClass, expected);
        }
    }

    public static <M extends Matcher> M message(Class messageClass, Object expected) {
        return (M) MessageMatcher.create(messageClass, expected);
    }

    public static <M extends Matcher> M message(Class messageClass, String jsonTemplate, Object... args) {
        return (M) MessageMatcher.create(messageClass, String.format(jsonTemplate, args));
    }

    public static final class ZeroInteractions implements VerificationMode {
        @Override
        public void verify(final VerificationData data) {
            final InvocationMatcher matcher = data.getWanted();
            final InOrderContext ctx = new InOrderContext() {
                @Override
                public boolean isVerified(Invocation invocation) {
                    return invocation.isVerified();
                }

                @Override
                public void markVerified(Invocation i) {
                }
            };

            final Invocation unverified = new InvocationsFinder().findFirstMatchingUnverifiedInvocation(data.getAllInvocations(), matcher, ctx);

            if (unverified != null) {
                new Reporter().noMoreInteractionsWanted(unverified, (List) data.getAllInvocations());
            }
        }
    }

    public static ZeroInteractions zeroInteractions() {
        return new ZeroInteractions();
    }
}
