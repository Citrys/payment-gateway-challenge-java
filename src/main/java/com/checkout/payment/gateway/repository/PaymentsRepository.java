package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.ApiPaymentResponse;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentsRepository {

  private final HashMap<UUID, ApiPaymentResponse> payments = new HashMap<>();

  public void add(ApiPaymentResponse payment) {
    payments.put(payment.getId(), payment);
  }

  public Optional<ApiPaymentResponse> get(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }

}
