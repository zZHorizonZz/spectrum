package dev.horizon.spectrum.conformance;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.protobuf.Struct;
import dev.horizon.spectrum.openfga.v1.CheckRequestTupleKey;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

public final class ConformanceCases {

  private record File(List<ConformanceCase> tests) {
  }

  private final YAMLMapper yaml = YAMLMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .addModule(new SimpleModule()
          .addDeserializer(TupleKey.class, new ProtoDeserializer<>(TupleKey::newBuilder))
          .addDeserializer(CheckRequestTupleKey.class, new ProtoDeserializer<>(CheckRequestTupleKey::newBuilder))
          .addDeserializer(Struct.class, new ProtoDeserializer<>(Struct::newBuilder)))
      .build();

  public List<ConformanceCase> read(String... files) {
    List<ConformanceCase> cases = new ArrayList<>();
    for (String file : files) {
      try (InputStream input = ConformanceCases.class.getResourceAsStream("/openfga/tests/" + file)) {
        cases.addAll(yaml.readValue(input, File.class).tests());
      } catch (IOException unreadable) {
        throw new UncheckedIOException(file, unreadable);
      }
    }
    return cases;
  }
}
