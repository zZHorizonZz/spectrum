package dev.horizon.spectrum.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class IpAddressTest {

  @ParameterizedTest(name = "{0} in {1}: {2}")
  @CsvSource(delimiter = '|', textBlock = """
      10.1.2.3         | 10.0.0.0/8      | true
      11.1.2.3         | 10.0.0.0/8      | false
      192.168.1.1      | 192.168.1.1/32  | true
      192.168.1.2      | 192.168.1.1/32  | false
      10.1.2.3         | 0.0.0.0/0       | true
      2001:db8::1      | 2001:db8::/32   | true
      2001:db9::1      | 2001:db8::/32   | false
      ::ffff:10.1.2.3  | 10.0.0.0/8      | true
      10.1.2.3         | 2001:db8::/32   | false
      """)
  void tellsIfAnAddressIsInABlock(String address, String cidr, boolean in) {
    assertThat(new IpAddress(address).inCidr(cidr)).isEqualTo(in);
  }

  @ParameterizedTest
  @ValueSource(strings = { "10.1.2", "010.1.2.3", "example.com", "10.1.2.3/8", "" })
  void refusesATextThatIsNotAnAddress(String text) {
    assertThatThrownBy(() -> new IpAddress(text)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = { "10.0.0.0", "10.0.0.0/33", "10.0.0.0/x" })
  void refusesATextThatIsNotABlock(String cidr) {
    assertThatThrownBy(() -> new IpAddress("10.1.2.3").inCidr(cidr)).isInstanceOf(IllegalArgumentException.class);
  }
}
