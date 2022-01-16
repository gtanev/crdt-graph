# CRDT Graph (LWW Element Graph)

This is a Java implementation of an [undirected graph](https://algs4.cs.princeton.edu/41graph/) as a
[conflict-free replicated data type](https://crdt.tech/) (CRDT). The graph is modeled as a convergent replicated data
type (CvRDT) based on element sets compliant with the last write wins (LWW) conflict resolution strategy.

Supported operations:

- Add vertex
- Remove vertex
- Look up vertex
- Add edge
- Remove edge
- Look up edge
- Fetch neighbors of vertex
- Find path between two vertices (via depth first search)
- Retrieve all vertices
- Retrieve size of graph (number of vertices)
- Print all vertices and their adjacency lists
- Merge another graph

Timestamps are generated with
the [java.time.Instant](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Instant.html)
class and precedence is given to removal operations for elements with identical timestamps.

A configurable
[flag](https://github.com/gtanev/crdt-graph/blob/master/src/main/java/com/github/gtanev/CRDTGraph.java#L40) is used to
specify whether to preserve the edges connected to a vertex upon removal of that vertex. By default, these edges are
deleted alongside the vertex they are connected to. Enabling this flag preserves the edges but hides them unless and
until the same vertex gets re-added to the graph, at which point they are restored.

A complete suite
of [unit tests](https://github.com/gtanev/crdt-graph/blob/master/src/test/java/com/github/gtanev/CRDTGraphTest.java) is
included.
