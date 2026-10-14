package dev.horizon.spectrum.it;

import java.util.List;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.params.converter.ArgumentConverter;

final class SpaceSeparated implements ArgumentConverter {

  @Override
  public Object convert(Object source, ParameterContext context) {
    return source == null || source.toString().isBlank() ? List.of()
        : List.of(source.toString().trim().split("\\s+"));
  }
}
