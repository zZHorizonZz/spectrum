package dev.horizon.spectrum;

import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.openfga.v1.WriteRequest;
import dev.horizon.spectrum.openfga.v1.WriteRequestWrites;
import java.util.List;
import java.util.stream.Stream;

final class TestStore {

  private final Spectrum spectrum;
  private final String id;
  private String model = "";

  TestStore(Spectrum spectrum, String id) {
    this.spectrum = spectrum;
    this.id = id;
  }

  String id() {
    return id;
  }

  String model() {
    return model;
  }

  String writeModel(String dsl) {
    model = spectrum.client().writeAuthorizationModel(spectrum.model(id, dsl)).await().getAuthorizationModelId();
    return model;
  }

  void write(String... tuples) {
    write(Stream.of(tuples).map(spectrum::tuple).toList());
  }

  void write(List<TupleKey> tuples) {
    spectrum.client().write(WriteRequest.newBuilder().setStoreId(id).setWrites(WriteRequestWrites.newBuilder()
        .addAllTupleKeys(tuples)).build()).await();
  }
}
