package dev.horizon.spectrum.storage.sql;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * The types of the JDBC client on SQLite: byte arrays, and times as ISO 8601 text in UTC.
 */
public final class SqliteTypes implements SqlTypes {

  public static final String NAME = "SQLite";

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .withZone(ZoneOffset.UTC);

  @Override
  public Object binary(byte[] bytes) {
    return bytes;
  }

  @Override
  public Object time(Instant instant) {
    return TIME.format(instant);
  }
}
