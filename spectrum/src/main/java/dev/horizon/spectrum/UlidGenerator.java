package dev.horizon.spectrum;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;

/**
 * Makes ULIDs: 26 characters of Crockford base 32, 48 bits of time in milliseconds and 80 random bits. The ULIDs of one
 * generator grow: in one millisecond, each adds one to the random bits of the one before.
 */
final class UlidGenerator {

  private static final char[] DIGITS = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

  private final Clock clock;
  private final SecureRandom random = new SecureRandom();
  private long time = -1;
  private long high;
  private long low;

  UlidGenerator(Clock clock) {
    this.clock = clock;
  }

  /** Gives the next ULID. */
  synchronized String next() {
    long now = clock.millis();
    if (now > time) {
      time = now;
      high = random.nextLong() & 0xFFFFL;
      low = random.nextLong();
    } else if (++low == 0 && ++high > 0xFFFFL) {
      throw new IllegalStateException("more ULIDs in one millisecond than 80 bits can count");
    }
    return encode(time, high, low);
  }

  /** Gives the least ULID of a time: one that sorts before every ULID made at or after it. */
  String floor(Instant instant) {
    return encode(instant.toEpochMilli(), 0, 0);
  }

  private static String encode(long time, long high, long low) {
    long hi = time << 16 | high;
    char[] text = new char[26];
    for (int i = 25; i >= 0; i--) {
      int shift = 5 * (25 - i);
      long digit = shift >= 64 ? hi >>> (shift - 64)
          : shift + 5 <= 64 ? low >>> shift : low >>> shift | hi << (64 - shift);
      text[i] = DIGITS[(int) (digit & 31)];
    }
    return new String(text);
  }
}
