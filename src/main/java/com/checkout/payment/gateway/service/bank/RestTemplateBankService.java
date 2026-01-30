package com.checkout.payment.gateway.service.bank;

import com.checkout.payment.gateway.model.ApiPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Bank service implementation using RestTemplate
 * Lacking of circuit breaker or limiters
 * No tracing/log correlation
 */
@Service
public class RestTemplateBankService implements BankService {

  private static final Logger LOG = LoggerFactory.getLogger(RestTemplateBankService.class);

  private final RestTemplate restTemplate;
  private final String bankUrl;

  public RestTemplateBankService(
      RestTemplate restTemplate,
      @Value("${bank.simulator.url}") String bankUrl) {
    this.restTemplate = restTemplate;
    this.bankUrl = bankUrl;
  }

  @Override
  @Retryable(
      retryFor = {RestClientException.class, ResourceAccessException.class},
      maxAttemptsExpression = "#{${payment.retry.max-attempts}}",
      backoff =
          @Backoff(
              delayExpression = "#{${payment.retry.initial-delay}}",
              maxDelayExpression = "#{${payment.retry.max-delay}}",
              multiplierExpression = "#{${payment.retry.multiplier}}"))
  public BankPaymentResponse authorizePayment(ApiPaymentRequest request)
      throws BankCommunicationException {
    LOG.debug("Initiating bank authorization request");

    try {
      BankPaymentRequest bankRequest = createBankRequest(request);

      BankPaymentResponse response =
          restTemplate.postForObject(
              bankUrl + "/payments", bankRequest, BankPaymentResponse.class);

      if (response == null) {
        LOG.error("Null response received from bank");
        throw new BankCommunicationException("Bank returned null response");
      }

      return response;
    } catch (RestClientException e) {
      LOG.error("Bank communication failed: {}", e.getClass().getSimpleName());
      throw new BankCommunicationException("Failed to communicate with bank", e);
    }
  }

  private BankPaymentRequest createBankRequest(ApiPaymentRequest paymentRequest) {
    BankPaymentRequest bankRequest = new BankPaymentRequest();
    bankRequest.setCardNumber(paymentRequest.getCardNumber());
    bankRequest.setExpiryDate(paymentRequest.getExpiryDate());
    bankRequest.setCurrency(paymentRequest.getCurrency());
    bankRequest.setAmount(paymentRequest.getAmount());
    bankRequest.setCvv(paymentRequest.getCvv());
    return bankRequest;
  }
}
