package dev.horizon.spectrum.conformance;

import com.google.protobuf.Struct;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import java.util.List;

public sealed interface Assertion permits CheckAssertion, ListObjectsAssertion, ListUsersAssertion {

  List<TupleKey> contextualTuples();

  Struct context();

  int errorCode();

  default boolean fails() {
    return errorCode() != 0;
  }
}
