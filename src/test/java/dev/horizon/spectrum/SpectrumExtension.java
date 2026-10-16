package dev.horizon.spectrum;

import dev.horizon.spectrum.database.MariaDbTestDatabase;
import dev.horizon.spectrum.database.PostgresTestDatabase;
import dev.horizon.spectrum.database.RunningDatabase;
import dev.horizon.spectrum.database.SqlServerTestDatabase;
import dev.horizon.spectrum.database.SqliteTestDatabase;
import dev.horizon.spectrum.database.TestDatabase;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ClassTemplateInvocationContext;
import org.junit.jupiter.api.extension.ClassTemplateInvocationContextProvider;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

final class SpectrumExtension implements ClassTemplateInvocationContextProvider {

  private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(
      SpectrumExtension.class);

  private static final List<TestDatabase> DATABASES = List.of(new PostgresTestDatabase(), new MariaDbTestDatabase(),
      new SqlServerTestDatabase(), new SqliteTestDatabase());

  @Override
  public boolean supportsClassTemplate(ExtensionContext context) {
    return context.getRequiredTestClass().isAnnotationPresent(SpectrumTest.class);
  }

  @Override
  public Stream<ClassTemplateInvocationContext> provideClassTemplateInvocationContexts(ExtensionContext context) {
    return DATABASES.stream().map(Invocation::new);
  }

  private record Invocation(TestDatabase database) implements ClassTemplateInvocationContext {

    @Override
    public String getDisplayName(int invocationIndex) {
      return database.name();
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
      return List.of(new Resolver(database));
    }
  }

  private record Resolver(TestDatabase database) implements ParameterResolver {

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
      return parameter.getParameter().getType() == Spectrum.class;
    }

    @Override
    public Spectrum resolveParameter(ParameterContext parameter, ExtensionContext context) {
      RunningDatabase running = context.getRoot().getStore(NAMESPACE).computeIfAbsent(database.name(),
          name -> database.open(), RunningDatabase.class);
      return context.getStore(NAMESPACE).computeIfAbsent(Spectrum.class, type -> spectrum(running),
          Spectrum.class);
    }

    private static Spectrum spectrum(RunningDatabase running) {
      try {
        return new Spectrum(running);
      } catch (TimeoutException timedOut) {
        throw new ParameterResolutionException("The database did not empty in " + RunningDatabase.TIMEOUT
            .toSeconds() + " s", timedOut);
      }
    }
  }
}
