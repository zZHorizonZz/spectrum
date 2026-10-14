package dev.horizon.spectrum.tuple;

import dev.horizon.spectrum.model.TypeSystem;
import dev.horizon.spectrum.openfga.v1.RelationReference;
import java.util.List;

/**
 * The rule that the type restrictions of the relation of a tuple allow its user: its type, its typed wildcard, or the
 * relation of its userset.
 */
public final class TypeRestrictionRule implements TupleRule {

  private final TypeSystem types;

  public TypeRestrictionRule(TypeSystem types) {
    this.types = types;
  }

  @Override
  public void check(RelationshipTuple tuple) {
    String type = tuple.object().type();
    if (!types.hasTypeInfo(type, tuple.relation())) {
      return;
    }
    List<RelationReference> restrictions = types.directlyRelatedUserTypes(type, tuple.relation());
    String relation = "'" + type + "#" + tuple.relation() + "'";
    switch (tuple.user()) {
      case UsersetUser user -> {
        if (restrictions.stream().noneMatch(restriction -> restriction.getType().equals(user.type())
            && restriction.getRelation().equals(user.relation()))) {
          throw new InvalidTupleException("'" + user.type() + "#" + user.relation()
              + "' is not an allowed type restriction for " + relation);
        }
      }
      case WildcardUser user -> {
        if (restrictions.stream().noneMatch(restriction -> restriction.getType().equals(user.type())
            && restriction.hasWildcard())) {
          throw new InvalidTupleException("the typed wildcard '" + user + "' is not an allowed type restriction for "
              + relation);
        }
      }
      case ObjectUser user -> {
        if (restrictions.stream().noneMatch(restriction -> restriction.getType().equals(user.type())
            && !restriction.hasWildcard() && restriction.getRelation().isEmpty())) {
          throw new InvalidTupleException("type '" + user.type() + "' is not an allowed type restriction for "
              + relation);
        }
      }
    }
  }
}
