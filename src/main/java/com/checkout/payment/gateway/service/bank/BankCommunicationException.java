package com.checkout.payment.gateway.service.bank;

/**
 * Exception thrown when bank communication fails
 */
public class BankCommunicationException extends Exception {

  public BankCommunicationException(String message) {
    super(message);
  }

  public BankCommunicationException(String message, Throwable cause) {
    super(message, cause);
  }
}
