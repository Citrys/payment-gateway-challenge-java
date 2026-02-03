package com.checkout.payment.gateway.service.bank;

import com.checkout.payment.gateway.requests.ApiPaymentRequest;
import com.checkout.payment.gateway.requests.BankPaymentRequest;
import com.checkout.payment.gateway.responces.BankPaymentResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
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
  @Async("bankServiceExecutor")
  @Retryable(
      retryFor = {RestClientException.class, ResourceAccessException.class},
      maxAttemptsExpression = "#{${payment.retry.max-attempts}}",
      backoff =
          @Backoff(
              delayExpression = "#{${payment.retry.initial-delay}}",
              maxDelayExpression = "#{${payment.retry.max-delay}}",
              multiplierExpression = "#{${payment.retry.multiplier}}"))
  public CompletableFuture<BankPaymentResponse> authorizePayment(UUID paymentId, ApiPaymentRequest request)
      throws BankCommunicationException {

    try {
      BankPaymentRequest bankRequest = createBankRequest(request);

      // Add payment ID in headers for bank idempotency check
      HttpHeaders headers = new HttpHeaders();
      headers.set("Idempotency-Key", paymentId.toString());
      HttpEntity<BankPaymentRequest> entity = new HttpEntity<>(bankRequest, headers);

      ResponseEntity<BankPaymentResponse> responseEntity =
          restTemplate.exchange(
              bankUrl + "/payments",
              HttpMethod.POST,
              entity,
              BankPaymentResponse.class);

      BankPaymentResponse response = responseEntity.getBody();
      if (response == null) {
        throw new BankCommunicationException("Bank returned null response");
      }

      return CompletableFuture.completedFuture(response);
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
