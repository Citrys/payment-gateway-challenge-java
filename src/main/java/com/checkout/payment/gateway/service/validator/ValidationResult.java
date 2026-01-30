package com.checkout.payment.gateway.service.validator;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * Immutable result of validation operation
 */
@Getter
public class ValidationResult {
  private final boolean valid;
  private final List<String> errors;

  private ValidationResult(boolean valid, List<String> errors) {
    this.valid = valid;
    this.errors = new ArrayList<>(errors);
  }

  public static ValidationResult success() {
    return new ValidationResult(true, List.of());
  }

  public static ValidationResult failure(List<String> errors) {
    return new ValidationResult(false, errors);
  }

  public static ValidationResult failure(String error) {
    return new ValidationResult(false, List.of(error));
  }
}
