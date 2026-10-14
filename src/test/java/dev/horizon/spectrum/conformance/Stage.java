package dev.horizon.spectrum.conformance;

import dev.horizon.spectrum.openfga.v1.TupleKey;
import java.util.List;

public record Stage(String model, List<TupleKey> tuples, List<CheckAssertion> checkAssertions,
    List<ListObjectsAssertion> listObjectsAssertions, List<ListUsersAssertion> listUsersAssertions) {

  private static final String INLINE_CONDITION = "$expression";

  public Stage {
    tuples = tuples == null ? List.of() : List.copyOf(tuples);
    checkAssertions = checkAssertions == null ? List.of() : List.copyOf(checkAssertions);
    listObjectsAssertions = listObjectsAssertions == null ? List.of() : List.copyOf(listObjectsAssertions);
    listUsersAssertions = listUsersAssertions == null ? List.of() : List.copyOf(listUsersAssertions);
  }

  public boolean usesInlineConditions() {
    return model.contains(INLINE_CONDITION);
  }
}
