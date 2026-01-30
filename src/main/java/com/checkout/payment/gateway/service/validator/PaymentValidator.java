package com.checkout.payment.gateway.service.validator;

import com.checkout.payment.gateway.model.ApiPaymentRequest;

/**
 * Interface for payment validation strategies
 * Follows Single Responsibility Principle and Open/Closed Principle
 */
public interface PaymentValidator {

  /**
   * Validates a payment request
   * @param request the payment request to validate
   * @return ValidationResult containing validation status and error messages
   */
  ValidationResult validate(ApiPaymentRequest request);
}
