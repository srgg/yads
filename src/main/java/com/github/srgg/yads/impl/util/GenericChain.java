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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.ListenableDirectedGraph;
import org.jgrapht.traverse.DepthFirstIterator;
import com.github.srgg.yads.api.Identifiable;
import com.github.srgg.yads.api.messages.ControlMessage;
import com.github.srgg.yads.impl.api.Sink;
import com.github.srgg.yads.impl.api.Chain;

import java.util.*;

/**
 * @author Sergey Galkin <srggal at gmail dot com>
 */
public class GenericChain<E> extends GenericSink<GenericChain.ChainListener<E>>
        implements Chain<E>, Sink<GenericChain.ChainListener<E>> {

    public interface ChainListener<E> extends Identifiable<String> {
        enum ActionType {
            NodeAdded,
            NodeRemoved,
            NodeStateChanged
        }

        void onNodeChanged(ActionType action, Chain.INodeInfo<E> node, Chain.INodeInfo<E> prevNode,
                           Chain.INodeInfo<E> nextNode) throws Exception;
    }

    // Type alis for Graph
    private static class ChainGraph extends ListenableDirectedGraph<NodeVertex, DefaultEdge> {
        ChainGraph(final Class<? extends DefaultEdge> edgeClass) {
            super(edgeClass);
        }
    }

    private static final class NodeVertex<E> implements Chain.INodeInfo<E> {
        private final ChainGraph graph;
        private final String nodeId;
        private String prevState;
        private String state;
        private final E extendedInfo;
        private EnumSet<ControlMessage.Role> roles = null;

        private NodeVertex(final ChainGraph g, final String nid, final String st, final E info) {
            this.graph = g;
            this.nodeId = nid;
            this.state = st;
            this.extendedInfo = info;
            this.state = "CREATED";
        }

        @Override
        public boolean isHead() {
            return roles != null && roles.contains(ControlMessage.Role.Head);
        }

        void addRole(final ControlMessage.Role role) {
            if (roles == null) {
                roles = EnumSet.of(role);
            } else {
                roles.add(role);
            }
        }

        void removeRole(final ControlMessage.Role role) {
            if (roles != null) {
                roles.remove(ControlMessage.Role.Tail);
            }
        }

        public String state() {
            return state;
        }

        private void setState(final String st) {
            prevState = this.state;
            this.state = st;
        }

        public String previousState() {
            return prevState;
        }

        @Override
        public boolean isTail() {
            return roles != null && roles.contains(ControlMessage.Role.Tail);
        }

        @Override
        public E extendedInfo() {
            return extendedInfo;
        }

        @Override
        public String getId() {
            return nodeId;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }

            if (!(o instanceof NodeVertex)) {
                return false;
            }

            NodeVertex<?> that = (NodeVertex<?>) o;
            return Objects.equals(nodeId, that.nodeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nodeId);
        }

        @Override
        public String toString() {
            return String.format("NodeVertex{nodeId='%s'}", nodeId);
        }

        @Override
        public INodeInfo<E> nextNode() {
            checkState(graph.containsVertex(this), "Node was deleted");

            final Set<DefaultEdge> outgoing = graph.outgoingEdgesOf(this);
            checkState(outgoing.size() <= 1);

             if (!outgoing.isEmpty()) {
                assert !isTail();
                final DefaultEdge e = outgoing.iterator().next();
                return graph.getEdgeTarget(e);
            } else {
                assert isTail();
                return null;
            }
        }

        @Override
        public INodeInfo<E> prevNode() {
            checkState(graph.containsVertex(this), "Node was deleted");

            final Set<DefaultEdge> incoming = graph.incomingEdgesOf(this);
            checkState(incoming.size() <= 1);

            if (!incoming.isEmpty()) {
                assert !isHead();
                final DefaultEdge e = incoming.iterator().next();
                return graph.getEdgeSource(e);
            } else {
                assert isHead();
                return null;
            }
        }

        public static <T> NodeVertex<T> create(final ChainGraph g, final String nodeId, final String state, final T info) {
            return new NodeVertex<>(g, nodeId, state, info);
        }
    }

    private NodeVertex<E> head;
    private NodeVertex<E> tail;

    private final ChainGraph theChain = new ChainGraph(DefaultEdge.class);

    protected ChainGraph chain() {
        return theChain;
    }

    protected synchronized GenericChain addNode(final String nodeId, final String state, final E nodeInfo) throws Exception {
        final NodeVertex<E> nv = NodeVertex.create(theChain, nodeId, state, nodeInfo);
        checkArgument(theChain.addVertex(nv),
                "Can't add '%s' node to the theChain, since it is already in the theChain",
                nodeId);

        final NodeVertex<E> oldTail = tail;
        if (tail == null) {
            head = nv;
            nv.addRole(ControlMessage.Role.Head);
        } else {
            theChain.addEdge(tail, nv);
            tail.removeRole(ControlMessage.Role.Tail);
        }

        tail = nv;
        nv.addRole(ControlMessage.Role.Tail);

        checkLinerity(nv);
        fireNodeChange(ChainListener.ActionType.NodeAdded, nv, oldTail, null);
        return this;
    }

    private synchronized void checkLinerity(final NodeVertex<E> vertex) {
        final Set<DefaultEdge> incoming = theChain.incomingEdgesOf(vertex);
        final Set<DefaultEdge> outgoing = theChain.outgoingEdgesOf(vertex);

        final boolean itIsHead = vertex.isHead();
        assert itIsHead == vertex.equals(head);

        final boolean itIsTail = vertex.isTail();
        assert itIsTail == vertex.equals(tail);

        checkArgument(theChain.containsVertex(vertex), "Can't check '%s' vertex structure, "
                + "since it is not in the chain", vertex);

        checkState(!itIsHead || incoming.isEmpty(), "Head mustn't have incoming edges");
        checkState(!itIsTail || outgoing.isEmpty(), "Tail mustn't have outgoing edges");

        checkState(itIsHead || incoming.size() == 1, "All the vertices except the Head "
                + "have to have exactly the one incoming edge from the previous vertex");

        checkState(itIsTail || outgoing.size() == 1, "All the vertices except the Tail "
                + "have to have exactly the one outgoing edge to the next vertex");
    }

    private NodeVertex<E> vertexByNodeId(final String nodeId) {
        for (NodeVertex<E> nv: theChain.vertexSet()) {
            if (nodeId.equalsIgnoreCase(nv.getId())) {
                return nv;
            }
        }
        return null;
    }

    protected synchronized Chain.INodeInfo<E> removeNode(final String nodeId, final String state) throws Exception {
        final NodeVertex<E> nv = vertexByNodeId(nodeId);
        checkArgument(nv != null, "Can't remove '%s' node, since it is not in the chain", nodeId);
        checkLinerity(nv);
        nv.setState(state);

        NodeVertex<E> next = null, prev = null;

        if (!nv.isTail()) {
            assert !nv.equals(tail);
            final Set<DefaultEdge> outgoing = theChain.outgoingEdgesOf(nv);
            assert outgoing.size() == 1;

            final DefaultEdge edge = outgoing.iterator().next();
            next = theChain.getEdgeTarget(edge);
        } else {
            assert nv.equals(tail);
        }

        if (!nv.isHead()) {
            assert !nv.equals(head);
            final Set<DefaultEdge> incoming = theChain.incomingEdgesOf(nv);
            assert incoming.size() == 1;

            final DefaultEdge edge = incoming.iterator().next();
            prev = theChain.getEdgeSource(edge);
        } else {
            assert nv.equals(head);
        }

        theChain.removeVertex(nv);

        if (prev != null && next != null) {
            //  node in the middle
            theChain.addEdge(prev, next);
        } else {
            if (prev == null) {
                // head
                checkState(nv.isHead());
                assert nv.equals(head);
                head = next;

                if (next != null) {
                    next.addRole(ControlMessage.Role.Head);
                }
            }

            if (next == null) {
                // tail
                checkState(nv.isTail());
                assert nv.equals(tail);
                tail = prev;

                if (prev != null) {
                    prev.addRole(ControlMessage.Role.Tail);
                }
            }
        }

        if (prev != null) {
            checkLinerity(prev);
        }

        if (next != null) {
            checkLinerity(next);
        }

        fireNodeChange(ChainListener.ActionType.NodeRemoved, nv, prev, next);
        return nv;
    }

    protected void fireNodeStateChanged(final String nodeId, final String state) throws Exception {
        final NodeVertex<E> v = vertexByNodeId(nodeId);
        checkState(v != null, "Node with id '%s' is unknown", nodeId);

        final INodeInfo<E> nv = v.nextNode();
        final INodeInfo<E> pv = v.prevNode();

        v.setState(state);
        fireNodeChange(ChainListener.ActionType.NodeStateChanged, v, pv, nv);
    }

    private void fireNodeChange(final ChainListener.ActionType action, final Chain.INodeInfo<E> node,
                                final Chain.INodeInfo<E> prev, final Chain.INodeInfo<E> next) throws Exception {
        for (Map.Entry<String, ChainListener<E>> e: handlers()) {
            e.getValue().onNodeChanged(action, node, prev, next);
        }
    }

    @Override
    public Chain.INodeInfo<E> head() {
        return head;
    }

    @Override
    public Chain.INodeInfo<E> tail() {
        return tail;
    }

    // TODO: consider backing up with COW List
    @Override
    public List<Chain.INodeInfo<E>> asList() {
        if (theChain.vertexSet().isEmpty()) {
            return Collections.EMPTY_LIST;
        }

        final ArrayList<Chain.INodeInfo<E>> result = new ArrayList<>(theChain.vertexSet().size());
        final DepthFirstIterator<NodeVertex<E>, ?> iterator = new DepthFirstIterator(theChain, head());

        while (iterator.hasNext()) {
            final NodeVertex<E> n = iterator.next();
            checkLinerity(n);
            result.add(n);
        }

        return Collections.unmodifiableList(result);
    }

    @Override
    public Iterator<Chain.INodeInfo<E>> iterator() {
        return asList().iterator();
    }

    @Override
    public boolean contains(final String nodeId) {
        return vertexByNodeId(nodeId) != null;
    }
}
