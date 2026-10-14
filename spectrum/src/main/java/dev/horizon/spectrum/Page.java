package dev.horizon.spectrum;

import java.util.List;

/**
 * A page of items in order, and the token of the next page.
 *
 * @param items the items
 * @param next the token that reads the next page, or empty if this page is the last
 * @param <T> the type of the items
 */
public record Page<T>(List<T> items, String next) {

  public Page {
    items = List.copyOf(items);
  }

  /**
   * Tells if a next page follows.
   *
   * @return true if {@link #next} reads more items
   */
  public boolean hasNext() {
    return !next.isEmpty();
  }
}
