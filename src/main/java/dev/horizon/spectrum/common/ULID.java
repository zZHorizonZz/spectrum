package dev.horizon.spectrum.common;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * An immutable universally unique lexicographically sortable identifier: 48 bits of time in milliseconds and 80 random
 * bits, written as 26 characters of Crockford base 32. ULIDs sort by time, as numbers and as text.
 *
 * <p>
 * The ULIDs that {@link #randomULID()} gives grow: in one millisecond, each adds one to the random bits of the one
 * before. The constructor takes the bits, as {@link java.util.UUID} does: the 48 bits of time and the first 16 random
 * bits, then the last 64 random bits.
 */
public final class ULID implements Comparable<ULID> {

  private static final char[] DIGITS = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
  private static final int LENGTH = 26;
  private static final int BITS_PER_DIGIT = 5;
  private static final int DIGIT_MASK = 31;
  private static final int RANDOM_HIGH_BITS = 16;
  private static final long RANDOM_HIGH_MASK = 0xFFFFL;
  private static final char MAX_FIRST_DIGIT = '7';
  private static final SecureRandom RANDOM = new SecureRandom();

  private static ULID last = new ULID(0, 0);

  private final long mostSigBits;
  private final long leastSigBits;

  public ULID(long mostSigBits, long leastSigBits) {
    this.mostSigBits = mostSigBits;
    this.leastSigBits = leastSigBits;
  }

  /**
   * Gives a ULID of the current time, greater than every ULID that this method gave before.
   *
   * @throws IllegalStateException if one millisecond had more ULIDs than 80 bits count
   */
  public static synchronized ULID randomULID() {
    long now = System.currentTimeMillis();
    if (now > last.timestamp()) {
      last = new ULID(now << RANDOM_HIGH_BITS | RANDOM.nextLong() & RANDOM_HIGH_MASK, RANDOM.nextLong());
    } else if (last.leastSigBits != -1) {
      last = new ULID(last.mostSigBits, last.leastSigBits + 1);
    } else if ((last.mostSigBits & RANDOM_HIGH_MASK) != RANDOM_HIGH_MASK) {
      last = new ULID(last.mostSigBits + 1, 0);
    } else {
      throw new IllegalStateException("more ULIDs in one millisecond than 80 bits can count");
    }
    return last;
  }

  /**
   * Gives the least ULID of a time, which sorts before every ULID made at or after it.
   *
   * @return the ULID, whose random bits are all zero
   */
  public static ULID startOf(Instant instant) {
    return new ULID(instant.toEpochMilli() << RANDOM_HIGH_BITS, 0);
  }

  /**
   * Reads a ULID from its text.
   *
   * @param text 26 characters of Crockford base 32, in either case
   * @throws IllegalArgumentException if the text is not a ULID
   */
  public static ULID fromString(String text) {
    if (text.length() != LENGTH || Character.toUpperCase(text.charAt(0)) > MAX_FIRST_DIGIT) {
      throw new IllegalArgumentException("invalid ULID: " + text);
    }
    long most = 0;
    long least = 0;
    for (int i = 0; i < LENGTH; i++) {
      int digit = digit(text.charAt(i));
      if (digit < 0) {
        throw new IllegalArgumentException("invalid ULID: " + text);
      }
      most = most << BITS_PER_DIGIT | least >>> (Long.SIZE - BITS_PER_DIGIT);
      least = least << BITS_PER_DIGIT | digit;
    }
    return new ULID(most, least);
  }

  /** Gives the time of the ULID, in milliseconds since the epoch. */
  public long timestamp() {
    return mostSigBits >>> RANDOM_HIGH_BITS;
  }

  public long getMostSignificantBits() {
    return mostSigBits;
  }

  public long getLeastSignificantBits() {
    return leastSigBits;
  }

  @Override
  public int compareTo(ULID other) {
    int most = Long.compareUnsigned(mostSigBits, other.mostSigBits);
    return most != 0 ? most : Long.compareUnsigned(leastSigBits, other.leastSigBits);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ULID ulid && ulid.mostSigBits == mostSigBits && ulid.leastSigBits == leastSigBits;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(mostSigBits) * 31 + Long.hashCode(leastSigBits);
  }

  @Override
  public String toString() {
    char[] text = new char[LENGTH];
    for (int i = LENGTH - 1; i >= 0; i--) {
      int shift = BITS_PER_DIGIT * (LENGTH - 1 - i);
      long digit = shift >= Long.SIZE ? mostSigBits >>> (shift - Long.SIZE)
          : shift + BITS_PER_DIGIT <= Long.SIZE ? leastSigBits >>> shift
              : leastSigBits >>> shift | mostSigBits << (Long.SIZE - shift);
      text[i] = DIGITS[(int) (digit & DIGIT_MASK)];
    }
    return new String(text);
  }

  private static int digit(char c) {
    char upper = Character.toUpperCase(c);
    for (int i = 0; i < DIGITS.length; i++) {
      if (DIGITS[i] == upper) {
        return i;
      }
    }
    return -1;
  }
}
