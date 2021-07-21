package cloud.metaapi.sdk.clients;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cloud.metaapi.sdk.clients.error_handler.ValidationException;

/**
 * Test {@link OptionsValidator}
 */
class OptionsValidatorTest {

  static OptionsValidator validator;
  
  @BeforeAll
  static void setUpBeforeClass() {
    validator = new OptionsValidator();
  }

  /**
   * Tests {@link OptionsValidator#validateNonZero}
   */
  @Test
  void testValidatesNonzerOption() throws ValidationException {
    validator.validateNonZeroInt(3, "opt");
  };

  /**
   * Tests {@link OptionsValidator#validateNonZero}
   */
  @Test
  void testThrowsErrorIfValueIsZero() {
    try {
      validator.validateNonZeroInt(0, "opt");
      fail();
    } catch (Throwable err) {
      assertTrue(err instanceof ValidationException);
      assertEquals("Parameter opt must be bigger than 0", err.getMessage());
    }
  }
}
