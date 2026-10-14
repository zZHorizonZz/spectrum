package dev.horizon.spectrum.conformance;

import com.google.protobuf.Struct;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import java.util.List;

public record ListUsersAssertion(Query request, List<TupleKey> contextualTuples, Struct context,
    List<String> expectation, int errorCode) implements Assertion {

  public record Query(List<String> filters, String object, String relation) {

    public Query {
      filters = filters == null ? List.of() : List.copyOf(filters);
    }
  }

  public ListUsersAssertion {
    contextualTuples = contextualTuples == null ? List.of() : List.copyOf(contextualTuples);
    context = context == null ? Struct.getDefaultInstance() : context;
    expectation = expectation == null ? List.of() : List.copyOf(expectation);
  }
}
