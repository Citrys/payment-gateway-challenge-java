package com.checkout.payment.gateway.service.bank;

import com.checkout.payment.gateway.model.ApiPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;

/**
 * Interface for bank communication
 * Follows Dependency Inversion Principle
 * Follows Interface Segregation Principle
 */
public interface BankService {

  /**
   * Authorizes a payment with the bank
   * @param request the payment request
   * @return bank response
   * @throws BankCommunicationException if bank communication fails
   */
  BankPaymentResponse authorizePayment(ApiPaymentRequest request) throws BankCommunicationException;
}
