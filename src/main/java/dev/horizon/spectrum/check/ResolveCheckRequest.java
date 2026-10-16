package dev.horizon.spectrum.check;

import com.google.protobuf.Struct;
import dev.horizon.spectrum.tuple.RelationshipTuple;
import dev.horizon.spectrum.tuple.TupleId;
import java.util.HashSet;
import java.util.Set;

/**
 * A check of one tuple in the tree of checks that answers a request.
 *
 * @param store the ID of the store
 * @param tuple the tuple to check
 * @param context the context of the request
 * @param visited the tuples that the path to this check has checked, to find cycles
 * @param depth the number of dispatches through usersets and tuplesets on the path to this check
 */
public record ResolveCheckRequest(String store, RelationshipTuple tuple, Struct context, Set<TupleId> visited,
    int depth) {

  public ResolveCheckRequest {
    visited = Set.copyOf(visited);
  }

  public ResolveCheckRequest(String store, RelationshipTuple tuple, Struct context) {
    this(store, tuple, context, Set.of(), 0);
  }

  /**
   * Gives the check of another tuple at the same depth, such as another relation of the same object.
   *
   * @param other the tuple to check
   * @return the check, with the visited tuples and the depth of this one
   */
  public ResolveCheckRequest rewritten(RelationshipTuple other) {
    return new ResolveCheckRequest(store, other, context, visited, depth);
  }

  /**
   * Gives the check of a tuple one level down, through a userset or a tupleset.
   *
   * @param other the tuple to check
   * @return the check, with the visited tuples of this one and a depth of one more
   */
  public ResolveCheckRequest dispatched(RelationshipTuple other) {
    return new ResolveCheckRequest(store, other, context, visited, depth + 1);
  }

  /**
   * Marks the tuple of this check as visited, so that a path that comes back to it is a cycle.
   *
   * @return the check, with its tuple among the visited tuples
   */
  public ResolveCheckRequest visiting() {
    Set<TupleId> path = new HashSet<>(visited);
    path.add(tuple.id());
    return new ResolveCheckRequest(store, tuple, context, path, depth);
  }

  /**
   * Tells if the path to this check has checked its tuple before.
   *
   * @return true if the tuple is among the visited tuples
   */
  public boolean cycle() {
    return visited.contains(tuple.id());
  }
}
