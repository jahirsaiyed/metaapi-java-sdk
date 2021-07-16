package cloud.metaapi.sdk.clients;

import cloud.metaapi.sdk.clients.error_handler.ValidationException;

/**
 * Class for validating API options.
 */
public class OptionsValidator {
  
  /**
   * Validates a number parameter to be above zero
   * @param value value to validate
   * @param defaultValue default value for an option
   * @param name option name
   * @returns validated value
   * @throws ValidationError if value is invalid
   */
  public void validateNonZeroInt(int value, String name) throws ValidationException {
    if (value <= 0) {
      throw new ValidationException("Parameter " + name + " must be bigger than 0", null);
    }
  }
  
  /**
   * Validates a number parameter to be above zero
   * @param value value to validate
   * @param defaultValue default value for an option
   * @param name option name
   * @returns validated value
   * @throws ValidationError if value is invalid
   */
  public void validateNonZeroLong(long value, String name) throws ValidationException {
    if (value <= 0) {
      throw new ValidationException("Parameter " + name + " must be bigger than 0", null);
    }
  }
}
