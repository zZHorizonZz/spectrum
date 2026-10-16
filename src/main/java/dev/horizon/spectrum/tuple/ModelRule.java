package dev.horizon.spectrum.tuple;

import dev.horizon.spectrum.model.TypeSystem;

/**
 * The rule that the user, the object and the relation of a tuple are in the model. The parts can be checked on their
 * own, for a request that names only some of them.
 */
public final class ModelRule implements TupleRule {

  private final TypeSystem types;

  public ModelRule(TypeSystem types) {
    this.types = types;
  }

  @Override
  public void check(RelationshipTuple tuple) {
    checkUser(tuple.user());
    checkObject(tuple.object());
    checkRelation(tuple.object(), tuple.relation());
  }

  /**
   * Checks that the type of a user is in the model, and the relation of a userset.
   *
   * @param user the user
   * @throws InvalidTupleException if one is not
   */
  public void checkUser(TupleUser user) {
    checkType(user.type());
    if (user instanceof UsersetUser userset && types.relation(userset.type(), userset.relation()).isEmpty()) {
      throw new InvalidTupleException("relation '" + userset.type() + "#" + userset.relation() + "' not found");
    }
  }

  /**
   * Checks that an object is not a typed wildcard, and that its type is in the model.
   *
   * @param object the object
   * @throws InvalidTupleException if it is a typed wildcard or its type is not in the model
   */
  public void checkObject(TupleObject object) {
    if (object.id().equals(TupleUser.WILDCARD)) {
      throw new InvalidTupleException("the 'object' field cannot reference a typed wildcard");
    }
    checkType(object.type());
  }

  /**
   * Checks that a relation is well formed, and is a relation of the type of an object.
   *
   * @param object the object, whose type must define the relation
   * @param relation the relation
   * @throws InvalidTupleException if it is malformed or not in the model
   */
  public void checkRelation(TupleObject object, String relation) {
    if (relation.isEmpty() || relation.chars().anyMatch(c -> Character.isISOControl(c) || c == '#' || c == ':'
        || c == '@' || c == ' ')) {
      throw new InvalidTupleException("the 'relation' field is malformed");
    }
    checkType(object.type());
    if (types.relation(object.type(), relation).isEmpty()) {
      throw new InvalidTupleException("relation '" + object.type() + "#" + relation + "' not found");
    }
  }

  private void checkType(String type) {
    if (types.typeDefinition(type).isEmpty()) {
      throw new InvalidTupleException("type '" + type + "' not found");
    }
  }
}
