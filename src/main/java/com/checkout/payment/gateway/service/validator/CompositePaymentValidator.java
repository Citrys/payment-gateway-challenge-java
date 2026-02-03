package com.checkout.payment.gateway.service.validator;

import com.checkout.payment.gateway.requests.ApiPaymentRequest;
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
      return ValidationResult.failure(errors);
    }

    return ValidationResult.success();
  }

  private void validateCardNumber(String cardNumber, List<String> errors) {
    if (cardNumber == null || cardNumber.isEmpty()) {
      errors.add("Card number is required");
      return;
    }
    if (cardNumber.length() < 14 || cardNumber.length() > 19) {
      errors.add("Card number must be between 14 and 19 digits");
      return;
    }
    if (!cardNumber.matches("\\d+")) {
      errors.add("Card number must contain only digits");
    }
  }

  private void validateExpiryDate(int month, int year, List<String> errors) {
    if (month < 1 || month > 12) {
      errors.add("Expiry month must be between 1 and 12");
      return;
    }
    try {
      YearMonth expiryDate = YearMonth.of(year, month);
      YearMonth currentDate = YearMonth.now();
      if (expiryDate.isBefore(currentDate)) {
        errors.add("Card has expired");
      }
    } catch (Exception e) {
      errors.add("Invalid expiry date");
    }
  }

  private void validateCurrency(String currency, List<String> errors) {
    if (currency == null || currency.length() != 3) {
      errors.add("Currency must be a 3-character code");
      return;
    }
    if (!supportedCurrencies.contains(currency.toUpperCase())) {
      errors.add("Currency not supported");
    }
  }

  private void validateAmount(int amount, List<String> errors) {
    if (amount <= 0) {
      errors.add("Amount must be positive");
    }
  }

  private void validateCvv(String cvv, List<String> errors) {
    if (cvv == null || cvv.isEmpty()) {
      errors.add("CVV is required");
      return;
    }
    if (!cvv.matches("\\d{3,4}")) {
      errors.add("CVV must be 3 or 4 digits");
    }
  }
}
