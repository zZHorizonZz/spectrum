package dev.horizon.spectrum.storage.sql;

import io.vertx.core.buffer.Buffer;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * The types of the PostgreSQL client: buffers, and times with a UTC offset.
 */
public final class PostgresTypes implements SqlTypes {

  public static final String NAME = "PostgreSQL";

  @Override
  public Object binary(byte[] bytes) {
    return Buffer.buffer(bytes);
  }

  @Override
  public Object time(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
