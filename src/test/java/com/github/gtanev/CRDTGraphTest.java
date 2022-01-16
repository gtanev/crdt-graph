package com.github.gtanev;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * <p>This class contains unit tests for validating the state and behavior of CRDTGraph instances.
 *
 * <p>The runWithDelay(Runnable task) method enforces a delay of 5 milliseconds to prevent
 * sequential graph operations from having identical timestamps, where this is relevant. See
 * <a href="https://stackoverflow.com/q/20689055">stackoverflow.com/q/20689055</a>.
 *
 * @author George Tanev (January, 2022)
 */
public class CRDTGraphTest {

  private CRDTGraph<Object> graph;

  @BeforeEach
  void init() {
    graph = new CRDTGraph<>();
  }

  @Test
  void testEmptyGraphInitialization() {
    assertEquals(0, graph.size());
    assertEquals(Collections.emptySet(), graph.vertices());
  }

  @Test
  void testAddRemoveVertex() {
    graph.addVertex(100);
    graph.addVertex("A");
    assertTrue(graph.containsVertex(100));
    assertTrue(graph.containsVertex("A"));

    runWithDelay(() -> graph.removeVertex(100));
    assertFalse(graph.containsVertex(100));
    assertTrue(graph.containsVertex("A"));

    runWithDelay(() -> graph.removeVertex("A"));
    assertFalse(graph.containsVertex(100));
    assertFalse(graph.containsVertex("A"));

    runWithDelay(() -> graph.addVertex(100));
    assertTrue(graph.containsVertex(100));
    assertFalse(graph.containsVertex("A"));

    runWithDelay(() -> graph.addVertex("A"));
    assertTrue(graph.containsVertex(100));
    assertTrue(graph.containsVertex("A"));
  }

  @Test
  void testAddRemoveEdge() {
    graph.addVertex(1);
    graph.addVertex(2);

    runWithDelay(() -> graph.addEdge(1, 2));
    assertTrue(graph.containsEdge(1, 2));
    assertTrue(graph.containsEdge(2, 1));

    runWithDelay(() -> graph.removeEdge(1, 2));
    assertFalse(graph.containsEdge(1, 2));
    assertFalse(graph.containsEdge(2, 1));

    runWithDelay(() -> graph.addEdge(2, 1));
    assertTrue(graph.containsEdge(1, 2));
    assertTrue(graph.containsEdge(2, 1));

    runWithDelay(() -> graph.removeEdge(2, 1));
    assertFalse(graph.containsEdge(1, 2));
    assertFalse(graph.containsEdge(2, 1));
  }

  @Test
  void testVertices() {
    var integerGraph = this.constructIntegerGraph();
    assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7), integerGraph.vertices());
  }

  @Test
  void testNeighborsOf() {
    var integerGraph = this.constructIntegerGraph();

    assertEquals(Set.of(2), integerGraph.neighborsOf(1));
    assertEquals(Set.of(1, 3, 4), integerGraph.neighborsOf(2));
    assertEquals(Set.of(2, 4, 7), integerGraph.neighborsOf(3));
    assertEquals(Set.of(2, 3, 5), integerGraph.neighborsOf(4));
    assertEquals(Set.of(4, 6), integerGraph.neighborsOf(5));
    assertEquals(Set.of(5, 7), integerGraph.neighborsOf(6));
    assertEquals(Set.of(3, 6), integerGraph.neighborsOf(7));
  }

  @Test
  void testNeighborsOf_DisconnectedVertex() {
    graph.addVertex('V');
    assertNotNull(graph.neighborsOf('V'));
    assertEquals(0, graph.neighborsOf('V').size());
  }

  @Test
  void testNeighborsOf_NonExistingVertex() {
    assertNull(graph.neighborsOf('V'));
  }

  @Test
  void testNeighborsOf_RemovedVertex() {
    graph.addVertex('V');
    runWithDelay(() -> graph.removeVertex('V'));
    assertNull(graph.neighborsOf('V'));
  }

  @Test
  void testFindPath() {
    CRDTGraph<Integer> integerGraph = this.constructIntegerGraph();
    List<Integer> path;

    path = integerGraph.findPath(1, 1);
    assertEquals(List.of(1), path);

    path = integerGraph.findPath(1, 2);
    assertEquals(List.of(1, 2), path);

    path = integerGraph.findPath(2, 5);
    assertTrue(
        Set.of(List.of(2, 3, 4, 5), List.of(2, 4, 5), List.of(2, 3, 7, 6, 5)).contains(path)
    );

    path = integerGraph.findPath(7, 4);
    assertTrue(
        Set.of(List.of(7, 3, 4), List.of(7, 3, 2, 4), List.of(7, 6, 5, 4)).contains(path)
    );
  }

  @Test
  void testFindPath_NonExistingPath() {
    graph.addVertex(0);
    graph.addVertex(1);
    assertNotNull(graph.findPath(0, 1));
    assertEquals(0, graph.findPath(0, 1).size());
  }

  @Test
  void testFindPath_NonExistingVertex() {
    graph.addVertex(0);
    graph.addVertex(1);
    assertNull(graph.findPath(-1, 1));
    assertNull(graph.findPath(0, -1));
  }

  @Test
  void testMerge() {
    var integerGraph = this.constructIntegerGraph();
    var anotherGraph = new CRDTGraph<Integer>();

    Stream.of(1, 2, 3, 4, 5, 6, 7).forEach(anotherGraph::addVertex);
    Set<Entry<Integer, Integer>> edges = Set.of(
        Map.entry(1, 7),
        Map.entry(2, 3),
        Map.entry(3, 5),
        Map.entry(3, 6),
        Map.entry(3, 7)
    );
    edges.forEach(pair -> anotherGraph.addEdge(pair.getKey(), pair.getValue()));

    integerGraph.merge(anotherGraph);
    assertAll(edges.stream().map(edge ->
        () -> assertTrue(integerGraph.containsEdge(edge.getKey(), edge.getValue()))
    ));

    anotherGraph.merge(integerGraph);
    assertEquals(integerGraph.toString(), anotherGraph.toString());
  }

  @Test
  void testMerge_EmptyGraph() {
    var integerGraph = this.constructIntegerGraph();
    var emptyGraph = new CRDTGraph<Integer>();

    var initialSize = integerGraph.size();

    integerGraph.merge(emptyGraph);
    assertEquals(0, emptyGraph.size());
    assertEquals(initialSize, integerGraph.size());

    emptyGraph.merge(integerGraph);
    assertEquals(initialSize, emptyGraph.size());
    assertEquals(integerGraph.toString(), emptyGraph.toString());
  }

  @Test
  void testMerge_RemovedEdge() {
    var firstGraph = this.constructIntegerGraph();
    var secondGraph = this.constructIntegerGraph();

    runWithDelay(() -> firstGraph.removeEdge(1, 2));
    assertFalse(firstGraph.containsEdge(1, 2));
    assertEquals(Collections.emptyList(), firstGraph.findPath(1, 2));

    runWithDelay(() -> secondGraph.merge(firstGraph));
    assertFalse(secondGraph.containsEdge(1, 2));
    assertEquals(Collections.emptyList(), secondGraph.findPath(1, 2));

    runWithDelay(() -> secondGraph.addEdge(1, 2));
    assertTrue(secondGraph.containsEdge(1, 2));
    assertEquals(List.of(1, 2), secondGraph.findPath(1, 2));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testMerge_RemovedVertex(boolean preserveEdges) {
    var firstGraph = this.constructIntegerGraph();
    var secondGraph = this.constructIntegerGraph();

    firstGraph.setPreserveConnectedEdges(preserveEdges);

    runWithDelay(() -> firstGraph.removeVertex(4));
    runWithDelay(() -> secondGraph.merge(firstGraph));
    assertFalse(secondGraph.containsVertex(4));
    assertNull(secondGraph.neighborsOf(4));

    runWithDelay(() -> secondGraph.addVertex(4));
    assertTrue(secondGraph.containsVertex(4));

    if (preserveEdges) {
      assertEquals(Set.of(2, 3, 5), secondGraph.neighborsOf(4));
    } else {
      assertEquals(Collections.emptySet(), secondGraph.neighborsOf(4));
    }
  }

  /**
   * Constructs this graph:
   * <pre>
   *  [1]         [7]
   *   |         /   \
   *   |        /     \
   *  [2] ——— [3] ——— [6]
   *    \     /        |
   *     \   /         |
   *      [4] ——————— [5]
   * </pre>
   */
  private CRDTGraph<Integer> constructIntegerGraph() {
    CRDTGraph<Integer> graph = new CRDTGraph<>();

    Stream.of(1, 2, 3, 4, 5, 6, 7).forEach(graph::addVertex);

    Stream.of(
        Map.entry(1, 2),
        Map.entry(2, 3),
        Map.entry(2, 4),
        Map.entry(3, 4),
        Map.entry(3, 7),
        Map.entry(4, 5),
        Map.entry(5, 6),
        Map.entry(6, 7)
    ).forEach(pair -> graph.addEdge(pair.getKey(), pair.getValue()));

    return graph;
  }

  /**
   * Executes a task after a 5 millisecond delay.
   */
  private void runWithDelay(Runnable task) {
    try {
      TimeUnit.MILLISECONDS.sleep(5);
      task.run();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }
}
