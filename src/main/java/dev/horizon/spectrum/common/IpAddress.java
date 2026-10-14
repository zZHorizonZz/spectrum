package dev.horizon.spectrum.common;

import com.google.common.net.InetAddresses;
import java.net.InetAddress;

/**
 * An IP address, the value of an {@code ipaddress} parameter of a condition. CEL expressions call {@code
 * ip.in_cidr("10.0.0.0/8")} on it.
 *
 * <p>
 * The constructor reads an address literal without a host name lookup. An IPv4 address has four decimal octets without
 * leading zeros. It throws {@link IllegalArgumentException} for a text that is not an address.
 */
public final class IpAddress {

  private final InetAddress address;

  public IpAddress(String text) {
    try {
      this.address = InetAddresses.forString(text);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("expected a well-formed IP address, but found: '" + text + "'", invalid);
    }
  }

  /** Gives the bytes of the address: 4 for IPv4, 16 for IPv6. */
  byte[] bytes() {
    return address.getAddress();
  }

  /**
   * Tells if the address is in a CIDR block.
   *
   * @param cidr the block, such as {@code 10.0.0.0/8}
   * @return true if the address is in the block, false if it is not or the block is of the other IP version
   * @throws IllegalArgumentException if the text is not a CIDR block
   */
  public boolean inCidr(String cidr) {
    return new CidrBlock(cidr).contains(this);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof IpAddress ip && ip.address.equals(address);
  }

  @Override
  public int hashCode() {
    return address.hashCode();
  }

  @Override
  public String toString() {
    return InetAddresses.toAddrString(address);
  }
}
