package dev.horizon.spectrum.storage;

import dev.horizon.aquery.aip158.InvalidPageTokenException;
import dev.horizon.aquery.aip158.Page;
import dev.horizon.aquery.aip158.PageToken;
import dev.horizon.aquery.aip158.PageTokenCodec;
import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import java.util.List;

/**
 * A request for one page of a list, as AIP-158 pages it. The continuation token of the request is an encrypted {@link
 * PageToken}, which holds the cursor where the page starts and the fingerprint of the list it belongs to.
 *
 * <p>
 * The constructor takes the codec of the service, the page size of the request, its token, and the arguments that make
 * the list, such as its name and store. A size of zero is the default size of 50. It throws an {@link OpenFgaException}
 * with the code {@code invalid_continuation_token} for a token that the service did not make, or made for another list.
 */
public final class PageQuery {

  public static final int DEFAULT_SIZE = 50;

  private final PageTokenCodec codec;
  private final int size;
  private final String fingerprint;
  private final List<String> cursor;

  public PageQuery(PageTokenCodec codec, int size, String token, String... list) {
    this.codec = codec;
    this.size = size > 0 ? size : DEFAULT_SIZE;
    this.fingerprint = PageToken.fingerprintOf(list);
    try {
      this.cursor = codec.decode(token, fingerprint).map(PageToken::cursor).orElse(List.of());
    } catch (InvalidPageTokenException invalid) {
      throw new OpenFgaException(ErrorCode.invalid_continuation_token, "Invalid continuation token");
    }
  }

  /**
   * Gives the most items of the page.
   *
   * @return the page size of the request, or {@link #DEFAULT_SIZE} if it had none
   */
  public int size() {
    return size;
  }

  /**
   * Gives where the page starts.
   *
   * @return the cursor of the token, such as the ULID of the first tuple of the page, or none for the first page
   */
  public List<String> cursor() {
    return cursor;
  }

  /**
   * Gives the first text of the cursor, for a list whose cursor is one value.
   *
   * @return the text, or empty for the first page
   */
  public String from() {
    return cursor.isEmpty() ? "" : cursor.getFirst();
  }

  /**
   * Gives a page of items, with the token of the next page.
   *
   * @param items the items of the page
   * @param next where the next page starts, or none if this page is the last
   * @param <T> the type of the items
   * @return the page, whose next token is empty if this page is the last
   */
  public <T> Page<T> page(List<T> items, List<String> next) {
    return new Page<>(items, next.isEmpty() ? "" : codec.encode(new PageToken(next, fingerprint)), 0);
  }
}
