package dev.horizon.spectrum.it;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgresContractTest {

  @RegisterExtension
  static final DatabaseExtension DATABASE = new DatabaseExtension(new PostgresTestDatabase());

  @Nested
  class Relations extends RelationsContractTest {
  }

  @Nested
  class Stores extends StoresContractTest {
  }

  @Nested
  class OpenFga extends OpenFgaContractTest {
  }
}
