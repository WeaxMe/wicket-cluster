package com.weaxme.wicket.cluster;

import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@Slf4j
public class TestHttpGraph {

    private Graph graph;

    @Before
    public void init() {
        graph = new Graph();

        SessionVertex vertex1 = new SessionVertex("id1", "node1", Collections.emptyList());
        SessionVertex vertex2 = new SessionVertex("id2", "node2", Collections.emptyList());
        SessionVertex vertex3 = new SessionVertex("id3", "node3", Collections.emptyList());

        graph.addVertex(vertex1);
        graph.addVertex(vertex2);
        graph.addVertex(vertex3);

        graph.addEdge(new SessionEdge("id1", "id1"));
        graph.addEdge(new SessionEdge("id1", "id2"));
        graph.addEdge(new SessionEdge("id1", "id3"));
    }

    @Test
    public void testGraph() {
        Set<SessionVertex> vertices = graph.getVertices();
        vertices.forEach(this::printVertex);
    }

    @Test
    public void testFirstThenSecondNode() {
        String id = "id2";
        String node = "node1";
        Set<SessionVertex> vertices = graph.getVertices();

        SessionVertex vertex = vertices.stream()
                .filter(v -> v.id.equals(id))
                .findFirst().orElse(null);

        SessionVertex result = findVertex(id, node);
        if (result != null) {
            printVertex(result);
        } else log.info("There is no vertex with id {}", id);

    }

    @Test
    public void testCreateVertex() {
        graph.clear();
        String id1 = "id1";
        String node1 = "node1";

        log.info("User request to {} with id {}", node1, id1);

        SessionVertex vertex = findVertex(id1, node1);
        assertNull(vertex);
        createVertex(id1, id1, node1);
        graph.getVertices().forEach(this::printVertex);

        String id2 = "id2";
        String node2 = "node2";
        log.info("");
        log.info("User request to {} with id {}", node2, id2);

        vertex = findVertex(id1, node2);
        assertNull(vertex);
        createVertex(id2, id1, node2);
        graph.getVertices().forEach(this::printVertex);

        log.info("");
        log.info("User request to {} with id {}", node1, id2);
        vertex = findVertex(id2, node1);
        assertNotNull(vertex);

        String id3 = "id3";
        String node3 = "node3";
        log.info("");
        log.info("User request to {} with id {}", node3, id1);
        vertex = findVertex(id1, node3);
        assertNull(vertex);
        createVertex(id3, id1, node3);
        graph.getVertices().forEach(this::printVertex);

        log.info("");
        log.info("User request to {} with id {}", node3, id2);
        vertex = findVertex(id2, node3);
        assertNotNull(vertex);
        printVertex(vertex);


        String id4 = "id4";
        String node4 = "node4";
        log.info("");
        log.info("User request to {} with id {}", node4, id3);
        vertex = findVertex(id3, node4);
        assertNull(vertex);
        createVertex(id4, id3, node4);
        graph.getVertices().forEach(this::printVertex);


        log.info("");
        log.info("User request to {} with id {}", node4, id1);
        vertex = findVertex(id1, node4);
        assertNotNull(vertex);
        printVertex(vertex);

    }

    private SessionVertex findVertex(String inputId, String currentNode) {
        Set<SessionVertex> vertices = graph.getVertices();

        SessionVertex vertex = vertices.stream()
                .filter(v -> v.id.equals(inputId))
                .findFirst().orElse(null);

        SessionVertex result = null;

        if (vertex != null && vertex.getId().equals(inputId) && vertex.getNode().equals(currentNode)) {
            result = vertex;
        }

        if (vertex != null && result == null) {
            Set<SessionVertex> visited = new HashSet<>();
            List<SessionVertex> queue = new LinkedList<>(convertToVerticles(vertex.getEdges()));

            log.info("");

            while (queue.size() > 0) {
                SessionVertex v = queue.remove(0);
                if (!visited.contains(v)) {
                    if (v.node.equals(currentNode)) {
                        result = v;
                        break;
                    }
                    visited.add(v);
                    queue.addAll(convertToVerticles(v.getEdges()));
                }
            }
//            log.info("graph: {}", graph.getVertices().size());
//            log.info("visited: {}", visited.size());
        }
        return result;
    }

    private List<SessionVertex> convertToVerticles(List<SessionEdge> edges) {
        return edges.stream()
                .flatMap(edge -> Stream.of(graph.getVertex(edge.in), graph.getVertex(edge.out)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private SessionVertex createVertex(String id, String creatorId, String node) {
        SessionEdge edge = new SessionEdge(creatorId, id);
        SessionVertex vertex = new SessionVertex(id, node, Collections.emptyList());
        graph.addVertex(vertex);
        graph.addEdge(edge);
        return vertex;
    }

    private void printVertex(SessionVertex vertex) {
        if (vertex == null) {
            log.info("Given vertex is null");
            return;
        }
        String outEdges = vertex.getEdges()
                .stream()
                .filter(edge -> edge.out.equals(vertex.id))
                .map(edge -> edge.out + " -> " + edge.in)
                .reduce((acc, edge) -> acc + ", " + edge)
                .orElse(null);
        String inEdges = vertex.getEdges()
                .stream()
                .filter(edge -> !edge.out.equals(vertex.id) || (edge.out.equals(vertex.id) && edge.in.equals(vertex.id)))
                .map(edge -> edge.in + " <- " + edge.out)
                .reduce((acc, edge) -> acc + ", " + edge)
                .orElse(null);

        log.info("id = {}, node = {}, in edges = {}, out edges = {}", vertex.getId(), vertex.getNode(), inEdges, outEdges);
    }

    @Value
    private static class Graph {
        private Set<SessionVertex> vertices;

        public Graph() {
            this.vertices = new HashSet<>();
        }

        public synchronized void addEdge(SessionEdge edge) {
            SessionVertex in = getVertexById(edge.in).orElse(null);
            SessionVertex out = getVertexById(edge.out).orElse(null);

            if (in != null && out != null) {
                removeVertex(in);
                removeVertex(out);

                List<SessionEdge> edges = new LinkedList<>(in.getEdges());
                edges.add(edge);
                addVertex(new SessionVertex(in.id, in.node, edges));

                edges = new LinkedList<>(out.getEdges());
                edges.add(edge);

                addVertex(new SessionVertex(out.id, out.node, edges));
            }
        }

        public Optional<SessionVertex> getVertexById(String id) {
            return vertices.stream()
                    .filter(v -> v.getId().equals(id))
                    .findFirst();
        }

        public SessionVertex getVertex(String id) {
            return getVertexById(id).orElse(null);
        }

        public Set<SessionVertex> getVertices() {
            return Collections.unmodifiableSet(vertices);
        }

        public synchronized void addVertex(SessionVertex vertex) {
            vertices.add(vertex);
        }

        public synchronized void removeVertex(SessionVertex vertex) {
            vertices.remove(vertex);
        }

        public synchronized void clear() {
            vertices.clear();
        }
    }

    @Value
    private static class SessionVertex {
        private String id;
        private String node;
        private List<SessionEdge> edges;

        public List<SessionEdge> getEdges() {
            return Collections.unmodifiableList(edges);
        }
    }

    @Value
    private static class SessionEdge {
        private String out;
        private String in;
    }
}
