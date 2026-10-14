package dev.horizon.spectrum;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Where a page ends: the keys of its last item, as an opaque token.
 *
 * @param keys the keys, in key order
 */
record Cursor(List<String> keys) {

  private static final char SEPARATOR = '\u0000';

  Cursor {
    keys = List.copyOf(keys);
  }

  /**
   * Reads a token.
   *
   * @param token the token
   * @param size how many keys it must have
   * @throws IllegalArgumentException if the token is not one of a page of the same kind
   */
  static Cursor of(String token, int size) {
    try {
      String text = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
      List<String> keys = List.of(text.split(String.valueOf(SEPARATOR), -1));
      if (keys.size() != size) {
        throw new IllegalArgumentException("it has " + keys.size() + " keys, not " + size);
      }
      return new Cursor(keys);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("the page token '" + token + "' is not valid", invalid);
    }
  }

  /** Gives the token. */
  String token() {
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(String.join(String.valueOf(SEPARATOR), keys).getBytes(StandardCharsets.UTF_8));
  }
}
