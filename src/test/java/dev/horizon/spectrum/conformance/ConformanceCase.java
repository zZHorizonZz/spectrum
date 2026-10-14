package dev.horizon.spectrum.conformance;

import java.util.List;

public record ConformanceCase(String name, List<Stage> stages) {

  public ConformanceCase {
    stages = stages == null ? List.of() : List.copyOf(stages);
  }
}
