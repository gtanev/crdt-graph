package com.github.gtanev;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>A class for creating and manipulating undirected graph objects as a conflict-free replicated
 * data type (CRDT) based on element sets compliant with the last write wins (LWW) conflict
 * resolution strategy.
 *
 * <p>This LWW-Element-Set implementation relies on the native java.time.Instant class for
 * generating timestamps and is biased towards removal operations when evaluating elements with
 * identical timestamps.
 *
 * <p>The preserveConnectedEdges boolean flag is used to decide whether to preserve the edges
 * connected to a vertex upon removal of that vertex. The default value is false which implies that
 * edges are deleted together with the vertex they are connected to. Setting this flag to true
 * preserves the edges but hides them unless and until the same vertex gets re-added to the graph,
 * at which point the edges are restored, i.e. they become visible again.
 *
 * @param <V> the type of vertices maintained by this graph
 *
 * @author George Tanev (January, 2022)
 */
public class CRDTGraph<V> {

  private final Map<V, Instant> verticesAddSet;
  private final Map<V, Instant> verticesRemoveSet;
  private final Map<V, Map<V, Instant>> edgesAddSet;
  private final Map<V, Map<V, Instant>> edgesRemoveSet;

  private boolean preserveConnectedEdges;

  public CRDTGraph() {
    verticesAddSet = new HashMap<>();
    verticesRemoveSet = new HashMap<>();
    edgesAddSet = new HashMap<>();
    edgesRemoveSet = new HashMap<>();
  }

  public void setPreserveConnectedEdges(boolean flag) {
    preserveConnectedEdges = flag;
  }

  public void addVertex(V vertex) {
    verticesAddSet.put(vertex, Instant.now());
  }

  public void removeVertex(V vertex) {
    if (!containsVertex(vertex)) {
      return;
    }

    if (!preserveConnectedEdges) {
      if (edgesAddSet.containsKey(vertex)) {
        edgesAddSet.get(vertex).keySet().forEach(neighbor -> removeEdge(vertex, neighbor));
      }
    }

    verticesRemoveSet.put(vertex, Instant.now());
  }

  public void addEdge(V fromVertex, V toVertex) {
    if (!containsVertex(fromVertex) || !containsVertex(toVertex) || fromVertex.equals(toVertex)) {
      return;
    }

    updateEdgeSet(fromVertex, toVertex, edgesAddSet);
  }

  public void removeEdge(V fromVertex, V toVertex) {
    if (!containsEdge(fromVertex, toVertex) && !containsEdge(toVertex, fromVertex)) {
      return;
    }

    updateEdgeSet(fromVertex, toVertex, edgesRemoveSet);
  }

  public boolean containsVertex(V vertex) {
    return verticesAddSet.containsKey(vertex)
           && (!verticesRemoveSet.containsKey(vertex)
               || verticesAddSet.get(vertex).isAfter(verticesRemoveSet.get(vertex)));
  }

  public boolean containsEdge(V vertexFrom, V vertexTo) {
    if (!containsVertex(vertexFrom) || !containsVertex(vertexTo)) {
      return false;
    }

    Map<V, Instant> addedEdges = edgesAddSet.getOrDefault(vertexFrom, Collections.emptyMap());
    Map<V, Instant> removedEdges = edgesRemoveSet.getOrDefault(vertexFrom, Collections.emptyMap());

    return addedEdges.containsKey(vertexTo)
           && (!removedEdges.containsKey(vertexTo)
               || addedEdges.get(vertexTo).isAfter(removedEdges.get(vertexTo)));
  }

  public Set<V> vertices() {
    return verticesAddSet.keySet()
        .stream()
        .filter(this::containsVertex)
        .collect(Collectors.toUnmodifiableSet());
  }

  public Set<V> neighborsOf(V vertex) {
    if (!containsVertex(vertex)) {
      return null;
    }

    if (!edgesAddSet.containsKey(vertex)) {
      return Collections.emptySet();
    }

    return edgesAddSet.get(vertex)
        .keySet()
        .stream()
        .filter(neighbor -> containsEdge(vertex, neighbor))
        .collect(Collectors.toSet());
  }

  public List<V> findPath(V vertexFrom, V vertexTo) {
    if (!containsVertex(vertexFrom) || !containsVertex(vertexTo)) {
      return null;
    }

    return depthFirstPath(vertexFrom, vertexTo, new HashSet<>(), new ArrayList<>());
  }

  public void merge(CRDTGraph<V> graph) {
    graph.verticesAddSet.forEach((v, t) -> mergeVertex(v, t, this.verticesAddSet));
    graph.verticesRemoveSet.forEach((v, t) -> mergeVertex(v, t, this.verticesRemoveSet));
    graph.edgesAddSet.forEach((v, e) -> mergeEdges(v, e, this.edgesAddSet));
    graph.edgesRemoveSet.forEach((v, e) -> mergeEdges(v, e, this.edgesRemoveSet));
  }

  public long size() {
    return verticesAddSet.keySet()
        .stream()
        .filter(this::containsVertex)
        .count();
  }

  @Override
  public String toString() {
    return verticesAddSet.keySet()
        .stream()
        .filter(this::containsVertex)
        .sorted()
        .map(v -> !edgesAddSet.containsKey(v)
            ? v.toString()
            : v + " -> " + edgesAddSet.get(v)
                .keySet()
                .stream()
                .filter(u -> this.containsEdge(v, u))
                .sorted()
                .map(Objects::toString)
                .collect(Collectors.joining(", ")))
        .collect(Collectors.joining("\n"));
  }

  private void updateEdgeSet(V fromVertex, V toVertex, Map<V, Map<V, Instant>> edgeSet) {
    final Instant timestamp = Instant.now();

    if (edgeSet.containsKey(fromVertex)) {
      edgeSet.get(fromVertex).put(toVertex, timestamp);
    } else {
      edgeSet.put(fromVertex, new HashMap<>(Map.of(toVertex, timestamp)));
    }

    if (edgeSet.containsKey(toVertex)) {
      edgeSet.get(toVertex).put(fromVertex, timestamp);
    } else {
      edgeSet.put(toVertex, new HashMap<>(Map.of(fromVertex, timestamp)));
    }
  }

  private List<V> depthFirstPath(V fromVertex, V toVertex, Set<V> visitedVertices, List<V> path) {
    if (fromVertex.equals(toVertex)) {
      return new ArrayList<>(List.of(fromVertex));
    }

    visitedVertices.add(fromVertex);

    for (V neighbor : neighborsOf(fromVertex)) {
      if (!visitedVertices.contains(neighbor)) {
        path = depthFirstPath(neighbor, toVertex, visitedVertices, path);

        if (!path.isEmpty()) {
          path.add(0, fromVertex);
          return path;
        }
      }
    }

    return Collections.emptyList();
  }

  private void mergeVertex(V remoteVertex, Instant remoteTimestamp, Map<V, Instant> localSet) {
    if (localSet.containsKey(remoteVertex)) {
      Instant localTimestamp = localSet.get(remoteVertex);
      if (!remoteTimestamp.isAfter(localTimestamp)) {
        return;
      }
    }

    localSet.put(remoteVertex, remoteTimestamp);
  }

  private void mergeEdges(V remoteVertex, Map<V, Instant> remoteEdges,
      Map<V, Map<V, Instant>> localSet) {
    if (localSet.containsKey(remoteVertex)) {
      Map<V, Instant> localEdges = localSet.get(remoteVertex);
      remoteEdges.forEach((vertex, timestamp) -> mergeVertex(vertex, timestamp, localEdges));
    } else {
      localSet.put(remoteVertex, remoteEdges);
    }
  }
}
