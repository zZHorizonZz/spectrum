package dev.horizon.spectrum.storage;

import dev.horizon.spectrum.openfga.v1.RelationReference;
import dev.horizon.spectrum.openfga.v1.Tuple;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.tuple.ObjectUser;
import dev.horizon.spectrum.tuple.TupleId;
import dev.horizon.spectrum.tuple.TupleObject;
import dev.horizon.spectrum.tuple.TupleUser;
import dev.horizon.spectrum.tuple.UsersetUser;
import dev.horizon.spectrum.tuple.WildcardUser;
import io.vertx.core.Future;
import java.util.List;
import java.util.Optional;

public interface TupleReader {

  /**
   * Selects tuples by their object, relation and user. An empty field selects every value. An object of a type alone,
   * such as {@code doc:}, selects every object of the type, and a user of a type alone, such as {@code user:}, every
   * user of the type.
   */
  record ReadFilter(String object, String relation, String user) {

    public String objectType() {
      int colon = object.indexOf(':');
      return colon < 0 ? "" : object.substring(0, colon);
    }

    public String objectId() {
      int colon = object.indexOf(':');
      return colon < 0 ? object : object.substring(colon + 1);
    }

    /** Tells if the filter selects every user of a type, such as {@code user:}. */
    public boolean everyUserOfType() {
      return user.endsWith(":");
    }

    public boolean matches(TupleKey tuple) {
      TupleObject tupleObject = TupleObject.parse(tuple.getObject());
      if (!objectType().isEmpty() && !objectType().equals(tupleObject.type())
          || !objectId().isEmpty() && !objectId().equals(tupleObject.id())) {
        return false;
      }
      if (!relation.isEmpty() && !relation.equals(tuple.getRelation())) {
        return false;
      }
      if (user.isEmpty()) {
        return true;
      }
      return everyUserOfType() ? tuple.getUser().startsWith(user) : user.equals(tuple.getUser());
    }
  }

  /**
   * Selects the tuples of a relation of an object whose users are usersets or typed wildcards that type restrictions
   * allow.
   *
   * @param allowed the type restrictions, such as {@code group#member} or {@code user:*}, or none for every userset and
   * typed wildcard
   */
  record UsersetFilter(String object, String relation, List<RelationReference> allowed) {

    public boolean matches(TupleKey tuple) {
      if (!object.equals(tuple.getObject()) || !relation.equals(tuple.getRelation())) {
        return false;
      }
      TupleUser user = TupleUser.parse(tuple.getUser());
      if (user instanceof ObjectUser) {
        return false;
      }
      return allowed.isEmpty() || allowed.stream().anyMatch(reference -> reference.getType().equals(user.type())
          && (reference.hasWildcard() ? user instanceof WildcardUser
              : user instanceof UsersetUser userset && reference.getRelation().equals(userset.relation())));
    }
  }

  /**
   * Selects the tuples of a relation of the objects of a type whose users are some users.
   *
   * @param objectType the type of the objects
   * @param users the users, such as {@code user:anne}, {@code user:*} or {@code group:eng#member}
   */
  record UserFilter(String objectType, String relation, List<String> users) {

    public boolean matches(TupleKey tuple) {
      return TupleObject.parse(tuple.getObject()).type().equals(objectType) && relation.equals(tuple.getRelation())
          && users.contains(tuple.getUser());
    }
  }

  Future<List<Tuple>> read(String store, ReadFilter filter);

  Future<Optional<Tuple>> readUserTuple(String store, TupleId tuple);

  Future<List<Tuple>> readUsersetTuples(String store, UsersetFilter filter);

  /**
   * Reads the tuples whose users are some users, for an expansion from a user to the objects it has a relation to.
   */
  Future<List<Tuple>> readStartingWithUser(String store, UserFilter filter);
}
