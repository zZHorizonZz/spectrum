package dev.horizon.spectrum.common;

/**
 * A CIDR block: a network address and the number of its leading bits that the addresses of the block share, such as
 * {@code 10.0.0.0/8} or {@code 2001:db8::/32}. The constructor throws {@link IllegalArgumentException} for a text that
 * is not a CIDR block.
 */
public final class CidrBlock {

  private static final int BYTE_MASK = 0xFF;

  private final String text;
  private final byte[] network;
  private final int prefix;

  public CidrBlock(String text) {
    this.text = text;
    int slash = text.indexOf('/');
    if (slash < 0) {
      throw invalid();
    }
    this.network = new IpAddress(text.substring(0, slash)).bytes();
    try {
      this.prefix = Integer.parseInt(text.substring(slash + 1));
    } catch (NumberFormatException notANumber) {
      throw invalid();
    }
    if (prefix < 0 || prefix > network.length * Byte.SIZE) {
      throw invalid();
    }
  }

  /**
   * Tells if an address is in the block.
   *
   * @param address the address
   * @return true if its leading bits are those of the network, false if not or it is of the other IP version
   */
  public boolean contains(IpAddress address) {
    byte[] bytes = address.bytes();
    if (bytes.length != network.length) {
      return false;
    }
    for (int bit = 0; bit < prefix; bit += Byte.SIZE) {
      int index = bit / Byte.SIZE;
      int mask = BYTE_MASK << Byte.SIZE - Math.min(Byte.SIZE, prefix - bit) & BYTE_MASK;
      if ((bytes[index] & mask) != (network[index] & mask)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    return text;
  }

  private IllegalArgumentException invalid() {
    return new IllegalArgumentException("'" + text + "' is not a CIDR block");
  }
}
