package dev.horizon.spectrum.storage.sql;

import io.vertx.core.buffer.Buffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * The types of a client whose time columns have no time zone, such as those of MariaDB and SQL Server: buffers, and
 * local times in UTC.
 */
public final class LocalTimeTypes implements SqlTypes {

  @Override
  public Object binary(byte[] bytes) {
    return Buffer.buffer(bytes);
  }

  @Override
  public Object time(Instant instant) {
    return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
