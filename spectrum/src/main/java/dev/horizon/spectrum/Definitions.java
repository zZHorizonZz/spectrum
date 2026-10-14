package dev.horizon.spectrum;

import dev.horizon.spectrum.v1.Direct;
import dev.horizon.spectrum.v1.Model;
import dev.horizon.spectrum.v1.Relation;
import dev.horizon.spectrum.v1.Rewrite;
import dev.horizon.spectrum.v1.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A model, checked, with its relations by type: each name it uses must be a type or a relation it has, each rewrite
 * must be set, and the relation that {@code from} reads the objects of names only objects, with {@code direct}.
 */
final class Definitions {

  private final Model model;
  private final Map<String, Map<String, Rewrite>> types = new LinkedHashMap<>();
  private final boolean monotone;

  Definitions(Model model) {
    this.model = model;
    for (Type type : model.getTypesList()) {
      if (!Ref.name(type.getName())) {
        throw new IllegalArgumentException("the type '" + type.getName() + "' is not a name: at most 64 lower case"
            + " letters, digits and '_', starting with a letter");
      }
      if (types.containsKey(type.getName())) {
        throw new IllegalArgumentException("the type '" + type.getName() + "' is defined two times");
      }
      Map<String, Rewrite> relations = new LinkedHashMap<>();
      for (Relation relation : type.getRelationsList()) {
        String where = "the relation " + type.getName() + "#" + relation.getName();
        if (!Ref.name(relation.getName())) {
          throw new IllegalArgumentException(where + " is not a name: at most 64 lower case letters, digits and"
              + " '_', starting with a letter");
        }
        if (relations.put(relation.getName(), relation.getRewrite()) != null) {
          throw new IllegalArgumentException(where + " is defined two times");
        }
      }
      types.put(type.getName(), relations);
    }
    boolean all = true;
    for (Map.Entry<String, Map<String, Rewrite>> type : types.entrySet()) {
      for (Map.Entry<String, Rewrite> relation : type.getValue().entrySet()) {
        all &= check(type.getKey(), relation.getValue(), "the relation " + type.getKey() + "#" + relation.getKey());
      }
    }
    this.monotone = all;
  }

  /** Gives the model. */
  Model model() {
    return model;
  }

  /** Tells if the model has a type. */
  boolean has(String type) {
    return types.containsKey(type);
  }

  /** Finds the rewrite of a relation of a type. */
  Optional<Rewrite> rewrite(String type, String relation) {
    return Optional.ofNullable(types.get(type)).map(relations -> relations.get(relation));
  }

  /** Tells if no relation has an intersection or a difference, so that a subject reaches only relations it has. */
  boolean monotone() {
    return monotone;
  }

  /** Tells if a relationship of a relation of a type can name a subject. */
  boolean allows(String type, String relation, Ref subject) {
    return rewrite(type, relation).map(rewrite -> directs(rewrite).stream().anyMatch(direct -> allows(direct, subject)))
        .orElse(false);
  }

  /** Tells if a direct rewrite allows a subject. */
  static boolean allows(Direct direct, Ref subject) {
    String allowed = subject.userset() ? subject.type() + "#" + subject.relation()
        : subject.wildcard() ? subject.type() + ":*" : subject.type();
    return direct.getSubjectsList().contains(allowed);
  }

  /**
   * Finds the relations of a type whose subjects include those of a relation of the same object: the relations whose
   * rewrite computes it, other than in what a difference leaves out.
   */
  List<String> computedFrom(String type, String relation) {
    List<String> found = new ArrayList<>();
    types.get(type).forEach((name, rewrite) -> {
      if (positive(rewrite).stream().anyMatch(part -> part.hasComputed()
          && part.getComputed().getRelation().equals(relation))) {
        found.add(name);
      }
    });
    return found;
  }

  /**
   * Finds the relations whose subjects include those of a relation of the objects that a tupleset names: the relations
   * whose rewrite reads it with {@code from}, other than in what a difference leaves out.
   *
   * @return each as {@code type#relation}
   */
  List<Ref> fromRules(String tupleset, String relation) {
    List<Ref> found = new ArrayList<>();
    types.forEach((type, relations) -> relations.forEach((name, rewrite) -> {
      if (positive(rewrite).stream().anyMatch(part -> part.hasFrom() && part.getFrom().getTupleset().equals(tupleset)
          && part.getFrom().getRelation().equals(relation))) {
        found.add(new Ref(type, "", name));
      }
    }));
    return found;
  }

  /** Gives the direct rewrites that a relation has, in what a difference does not leave out. */
  private static List<Direct> directs(Rewrite rewrite) {
    return positive(rewrite).stream().filter(Rewrite::hasDirect).map(Rewrite::getDirect).toList();
  }

  /** Gives the rewrites in a rewrite that give it subjects: all but what a difference leaves out. */
  private static List<Rewrite> positive(Rewrite rewrite) {
    List<Rewrite> found = new ArrayList<>();
    switch (rewrite.getRewriteCase()) {
      case UNION -> rewrite.getUnion().getRewritesList().forEach(child -> found.addAll(positive(child)));
      case INTERSECTION -> rewrite.getIntersection().getRewritesList().forEach(child -> found.addAll(positive(child)));
      case DIFFERENCE -> found.addAll(positive(rewrite.getDifference().getBase()));
      default -> found.add(rewrite);
    }
    return found;
  }

  /**
   * Checks a rewrite of a relation of a type.
   *
   * @return true if it has no intersection and no difference
   */
  private boolean check(String type, Rewrite rewrite, String where) {
    return switch (rewrite.getRewriteCase()) {
      case DIRECT -> {
        if (rewrite.getDirect().getSubjectsCount() == 0) {
          throw new IllegalArgumentException(where + " has a direct rewrite with no subjects");
        }
        rewrite.getDirect().getSubjectsList().forEach(subject -> checkDirect(subject, where));
        yield true;
      }
      case COMPUTED -> {
        String relation = rewrite.getComputed().getRelation();
        if (rewrite(type, relation).isEmpty()) {
          throw new IllegalArgumentException(where + " is computed from '" + relation + "', which the type '" + type
              + "' does not have");
        }
        yield true;
      }
      case FROM -> {
        checkFrom(type, rewrite.getFrom().getTupleset(), rewrite.getFrom().getRelation(), where);
        yield true;
      }
      case UNION -> checkAll(type, rewrite.getUnion().getRewritesList(), where);
      case INTERSECTION -> {
        checkAll(type, rewrite.getIntersection().getRewritesList(), where);
        yield false;
      }
      case DIFFERENCE -> {
        check(type, rewrite.getDifference().getBase(), where);
        check(type, rewrite.getDifference().getSubtract(), where);
        yield false;
      }
      case REWRITE_NOT_SET -> throw new IllegalArgumentException(where + " has a rewrite that is not set");
    };
  }

  private boolean checkAll(String type, List<Rewrite> rewrites, String where) {
    if (rewrites.isEmpty()) {
      throw new IllegalArgumentException(where + " combines no rewrites");
    }
    boolean all = true;
    for (Rewrite child : rewrites) {
      all &= check(type, child, where);
    }
    return all;
  }

  /** Checks what a direct rewrite allows: {@code user}, {@code user:*} or {@code group#member}. */
  private void checkDirect(String subject, String where) {
    String type = subject;
    String relation = null;
    if (subject.endsWith(":*")) {
      type = subject.substring(0, subject.length() - 2);
    } else if (subject.contains("#")) {
      type = subject.substring(0, subject.indexOf('#'));
      relation = subject.substring(subject.indexOf('#') + 1);
    }
    if (!has(type)) {
      throw new IllegalArgumentException(where + " allows '" + subject + "', whose type '" + type + "' the model does"
          + " not have");
    }
    if (relation != null && rewrite(type, relation).isEmpty()) {
      throw new IllegalArgumentException(where + " allows '" + subject + "', but the type '" + type + "' has no"
          + " relation '" + relation + "'");
    }
  }

  /** Checks a {@code from}: the tupleset names only objects, and one of their types has the relation. */
  private void checkFrom(String type, String tupleset, String relation, String where) {
    Rewrite set = rewrite(type, tupleset).orElseThrow(() -> new IllegalArgumentException(where + " reads '" + relation
        + "' from '" + tupleset + "', which the type '" + type + "' does not have"));
    List<String> subjects = set.getDirect().getSubjectsList();
    if (!set.hasDirect() || subjects.stream().anyMatch(subject -> subject.contains("#") || subject.endsWith(":*"))) {
      throw new IllegalArgumentException(where + " reads from '" + tupleset + "', which must be a direct rewrite of"
          + " types only, such as direct { subjects: \"folder\" }");
    }
    if (subjects.stream().noneMatch(subject -> rewrite(subject, relation).isPresent())) {
      throw new IllegalArgumentException(where + " reads '" + relation + "' from '" + tupleset + "', but none of "
          + subjects + " has the relation '" + relation + "'");
    }
  }
}
