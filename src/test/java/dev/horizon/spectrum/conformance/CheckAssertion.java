package dev.horizon.spectrum.conformance;

import com.google.protobuf.Struct;
import dev.horizon.spectrum.openfga.v1.CheckRequestTupleKey;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import java.util.List;

public record CheckAssertion(CheckRequestTupleKey tuple, List<TupleKey> contextualTuples, Struct context,
    boolean expectation, int errorCode) implements Assertion {

  public CheckAssertion {
    tuple = tuple == null ? CheckRequestTupleKey.getDefaultInstance() : tuple;
    contextualTuples = contextualTuples == null ? List.of() : List.copyOf(contextualTuples);
    context = context == null ? Struct.getDefaultInstance() : context;
  }
}
