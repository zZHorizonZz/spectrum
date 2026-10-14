package dev.horizon.spectrum.storage.sql;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import com.google.protobuf.Timestamp;
import dev.horizon.basepoint.Dialect;
import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.openfga.v1.InternalErrorCode;
import io.vertx.core.buffer.Buffer;
import io.vertx.sqlclient.Row;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * The values that the Vert.x client of a database binds to binary and time columns, and reads from them.
 */
public interface SqlTypes {

  /**
   * Gives the types of the database of a dialect.
   *
   * @return the types: those of PostgreSQL or SQLite for their dialects, and UTC local times for the others
   */
  static SqlTypes of(Dialect dialect) {
    return switch (dialect.name()) {
      case PostgresTypes.NAME -> new PostgresTypes();
      case SqliteTypes.NAME -> new SqliteTypes();
      default -> new LocalTimeTypes();
    };
  }

  Object binary(byte[] bytes);

  Object time(Instant instant);

  /**
   * Reads a text column.
   *
   * @return the text, or empty for a null
   */
  default String text(Row row, String column) {
    Object value = row.getValue(column);
    return value == null ? "" : value.toString();
  }

  /**
   * Reads a binary column.
   *
   * @return the bytes, or null for a null
   */
  default byte[] bytes(Row row, String column) {
    return switch (row.getValue(column)) {
      case null -> null;
      case Buffer buffer -> buffer.getBytes();
      case byte[] array -> array;
      case Object other -> throw new IllegalStateException("the column " + column + " is a " + other.getClass()
          .getName());
    };
  }

  /**
   * Reads a time column, which a database without time zones keeps in UTC and SQLite as ISO 8601 text.
   *
   * @return the time, or the default timestamp for a null
   */
  default Timestamp timestamp(Row row, String column) {
    Instant instant = switch (row.getValue(column)) {
      case null -> null;
      case OffsetDateTime time -> time.toInstant();
      case LocalDateTime time -> time.toInstant(ZoneOffset.UTC);
      case String time -> Instant.parse(time.contains("T") ? time : time.replace(' ', 'T') + "Z");
      case Object other -> throw new IllegalStateException("the column " + column + " is a " + other.getClass()
          .getName());
    };
    return instant == null ? Timestamp.getDefaultInstance()
        : Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
  }

  /**
   * Reads a binary column that holds a message.
   *
   * @throws OpenFgaException with the code {@code internal_error} if the bytes are not a message of the parser
   */
  default <T> T message(Row row, String column, Parser<T> parser) {
    try {
      return parser.parseFrom(bytes(row, column));
    } catch (InvalidProtocolBufferException corrupt) {
      throw new OpenFgaException(InternalErrorCode.internal_error, "a stored message is corrupt: "
          + corrupt.getMessage());
    }
  }
}
