package dev.horizon.spectrum.tuple;

import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.openfga.v1.TupleKeyWithoutCondition;

/**
 * The object, relation and user of a tuple, without its condition: two tuples with the same ID are the same tuple.
 *
 * @param object the object, such as {@code doc:1}
 * @param relation the relation, such as {@code viewer}
 * @param user the user, such as {@code user:anne}
 */
public record TupleId(String object, String relation, String user) {

  public TupleId(TupleKey tuple) {
    this(tuple.getObject(), tuple.getRelation(), tuple.getUser());
  }

  public TupleId(TupleKeyWithoutCondition tuple) {
    this(tuple.getObject(), tuple.getRelation(), tuple.getUser());
  }

  @Override
  public String toString() {
    return object + "#" + relation + "@" + user;
  }
}
