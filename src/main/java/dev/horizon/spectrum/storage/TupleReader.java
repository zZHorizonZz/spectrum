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

/**
 * Reads the tuples of a store, for the checks and the expansions of requests. Each read gives the tuples as they are
 * stored, whether or not the model allows them now.
 */
public interface TupleReader {

  /**
   * Selects tuples by their object, relation and user. An empty field selects every value.
   *
   * @param object the object, such as {@code doc:1}, or a type alone, such as {@code doc:}, for every object of the
   * type
   * @param relation the relation, such as {@code viewer}
   * @param user the user, such as {@code user:anne}, or a type alone, such as {@code user:}, for every user of the type
   */
  record ReadFilter(String object, String relation, String user) {

    /**
     * Gives the type of the object of the filter.
     *
     * @return the type, such as {@code doc}, or empty if the object has none
     */
    public String objectType() {
      int colon = object.indexOf(':');
      return colon < 0 ? "" : object.substring(0, colon);
    }

    /**
     * Gives the ID of the object of the filter.
     *
     * @return the ID, such as {@code 1}, or empty for every object of the type
     */
    public String objectId() {
      int colon = object.indexOf(':');
      return colon < 0 ? object : object.substring(colon + 1);
    }

    /**
     * Tells if the filter selects every user of a type.
     *
     * @return true if the user is a type alone, such as {@code user:}
     */
    public boolean everyUserOfType() {
      return user.endsWith(":");
    }

    /**
     * Tells if the filter selects a tuple.
     *
     * @param tuple the tuple
     * @return true if its object, relation and user are those of the filter
     */
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
   * @param object the object, such as {@code doc:1}
   * @param relation the relation, such as {@code viewer}
   * @param allowed the type restrictions, such as {@code group#member} or {@code user:*}, or none for every userset and
   * typed wildcard
   */
  record UsersetFilter(String object, String relation, List<RelationReference> allowed) {

    /**
     * Tells if the filter selects a tuple.
     *
     * @param tuple the tuple
     * @return true if it is of the object and the relation, and its user is a userset or a typed wildcard that the
     * type restrictions allow
     */
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
   * @param objectType the type of the objects, such as {@code doc}
   * @param relation the relation, such as {@code viewer}
   * @param users the users, such as {@code user:anne}, {@code user:*} or {@code group:eng#member}
   */
  record UserFilter(String objectType, String relation, List<String> users) {

    /**
     * Tells if the filter selects a tuple.
     *
     * @param tuple the tuple
     * @return true if it is of the relation of an object of the type, and its user is one of the users
     */
    public boolean matches(TupleKey tuple) {
      return TupleObject.parse(tuple.getObject()).type().equals(objectType) && relation.equals(tuple.getRelation())
          && users.contains(tuple.getUser());
    }
  }

  /**
   * Reads the tuples that a filter selects.
   *
   * @param store the ID of the store
   * @param filter the object, relation and user of the tuples
   * @return the tuples, in no particular order
   */
  Future<List<Tuple>> read(String store, ReadFilter filter);

  /**
   * Reads one tuple by its object, relation and user.
   *
   * @param store the ID of the store
   * @param tuple the ID of the tuple
   * @return the tuple, with its condition, or empty if the store does not have it
   */
  Future<Optional<Tuple>> readUserTuple(String store, TupleId tuple);

  /**
   * Reads the tuples of a relation of an object whose users are usersets or typed wildcards.
   *
   * @param store the ID of the store
   * @param filter the object, the relation and the type restrictions that the users must fit
   * @return the tuples, in no particular order
   */
  Future<List<Tuple>> readUsersetTuples(String store, UsersetFilter filter);

  /**
   * Reads the tuples whose users are some users, for an expansion from a user to the objects it has a relation to.
   *
   * @param store the ID of the store
   * @param filter the type of the objects, the relation and the users
   * @return the tuples, in no particular order, or none if the filter has no user
   */
  Future<List<Tuple>> readStartingWithUser(String store, UserFilter filter);
}
