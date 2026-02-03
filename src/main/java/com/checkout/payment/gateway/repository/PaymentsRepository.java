package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.Payment;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * In-memory implementation of PaymentRepository
 * Stores Payment domain objects in a HashMap
 */
@Repository
public class PaymentsRepository implements PaymentRepository {

  private final HashMap<UUID, Payment> payments = new HashMap<>();

  @Override
  public void save(Payment payment) {
    payments.put(payment.getId(), payment);
  }

  @Override
  public Optional<Payment> findById(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }
}
