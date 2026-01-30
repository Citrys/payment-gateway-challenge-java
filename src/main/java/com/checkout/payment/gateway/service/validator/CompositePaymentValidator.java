package com.checkout.payment.gateway.service.validator;

import com.checkout.payment.gateway.model.ApiPaymentRequest;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Composite validator that combines multiple validation rules
 * Follows Single Responsibility Principle - only validates
 * Follows Open/Closed Principle - new validators can be added without modification
 */
@Component
public class CompositePaymentValidator implements PaymentValidator {

  private static final Logger LOG = LoggerFactory.getLogger(CompositePaymentValidator.class);
  private final Set<String> supportedCurrencies;

  public CompositePaymentValidator(
      @Value("${payment.supported.currencies:USD,GBP,EUR}") Set<String> supportedCurrencies) {
    this.supportedCurrencies = supportedCurrencies;
  }

  @Override
  public ValidationResult validate(ApiPaymentRequest request) {
    List<String> errors = new ArrayList<>();

    validateCardNumber(request.getCardNumber(), errors);
    validateExpiryDate(request.getExpiryMonth(), request.getExpiryYear(), errors);
    validateCurrency(request.getCurrency(), errors);
    validateAmount(request.getAmount(), errors);
    validateCvv(request.getCvv(), errors);

    if (!errors.isEmpty()) {
      LOG.warn("Payment validation failed with {} errors", errors.size());
      return ValidationResult.failure(errors);
    }

    return ValidationResult.success();
  }

  private void validateCardNumber(String cardNumber, List<String> errors) {
    if (cardNumber == null || cardNumber.isEmpty()) {
      LOG.warn("Card number validation failed: null or empty");
      errors.add("Card number is required");
      return;
    }
    if (cardNumber.length() < 14 || cardNumber.length() > 19) {
      LOG.warn("Card number validation failed: invalid length");
      errors.add("Card number must be between 14 and 19 digits");
      return;
    }
    if (!cardNumber.matches("\\d+")) {
      LOG.warn("Card number validation failed: non-numeric characters");
      errors.add("Card number must contain only digits");
    }
  }

  private void validateExpiryDate(int month, int year, List<String> errors) {
    if (month < 1 || month > 12) {
      LOG.warn("Expiry date validation failed: month out of range");
      errors.add("Expiry month must be between 1 and 12");
      return;
    }
    try {
      YearMonth expiryDate = YearMonth.of(year, month);
      YearMonth currentDate = YearMonth.now();
      if (expiryDate.isBefore(currentDate)) {
        LOG.warn("Expiry date validation failed: date in the past");
        errors.add("Card has expired");
      }
    } catch (Exception e) {
      LOG.warn("Expiry date validation failed: invalid date format");
      errors.add("Invalid expiry date");
    }
  }

  private void validateCurrency(String currency, List<String> errors) {
    if (currency == null || currency.length() != 3) {
      LOG.warn("Currency validation failed: null or invalid length");
      errors.add("Currency must be a 3-character code");
      return;
    }
    if (!supportedCurrencies.contains(currency.toUpperCase())) {
      LOG.warn("Currency validation failed: unsupported currency code");
      errors.add("Currency not supported");
    }
  }

  private void validateAmount(int amount, List<String> errors) {
    if (amount <= 0) {
      LOG.warn("Amount validation failed: non-positive value");
      errors.add("Amount must be positive");
    }
  }

  private void validateCvv(int cvv, List<String> errors) {
    if (cvv < 100 || cvv > 9999) {
      LOG.warn("CVV validation failed: invalid length");
      errors.add("CVV must be 3 or 4 digits");
    }
  }
}
