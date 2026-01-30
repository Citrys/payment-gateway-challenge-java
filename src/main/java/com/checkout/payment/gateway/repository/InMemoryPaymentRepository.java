package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.ApiPaymentResponse;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * In-memory implementation of PaymentRepository
 * Follows Dependency Inversion Principle - implements interface
 */
@Repository
public class InMemoryPaymentRepository implements PaymentRepository {

  private final HashMap<UUID, ApiPaymentResponse> payments = new HashMap<>();

  @Override
  public void save(ApiPaymentResponse payment) {
    payments.put(payment.getId(), payment);
  }

  @Override
  public Optional<ApiPaymentResponse> findById(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }
}
