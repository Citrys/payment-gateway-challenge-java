package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.ApiPaymentResponse;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface for payment repository
 * Follows Dependency Inversion Principle
 * Follows Interface Segregation Principle
 */
public interface PaymentRepository {

  /**
   * Saves a payment
   * @param payment the payment to save
   */
  void save(ApiPaymentResponse payment);

  /**
   * Finds a payment by ID
   * @param id the payment ID
   * @return optional containing the payment if found
   */
  Optional<ApiPaymentResponse> findById(UUID id);
}
