package com.checkout.payment.gateway.service.bank;

import com.checkout.payment.gateway.requests.ApiPaymentRequest;
import com.checkout.payment.gateway.responces.BankPaymentResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for bank communication
 * Follows Dependency Inversion Principle
 * Follows Interface Segregation Principle
 */
public interface BankService {

  /**
   * Authorizes a payment with the bank asynchronously
   * @param paymentId the payment ID (used for idempotency in bank)
   * @param request the payment request
   * @return CompletableFuture with bank response
   * @throws BankCommunicationException if bank communication fails
   */
  CompletableFuture<BankPaymentResponse> authorizePayment(UUID paymentId, ApiPaymentRequest request) throws BankCommunicationException;
}
