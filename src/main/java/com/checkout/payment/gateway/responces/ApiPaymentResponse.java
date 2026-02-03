package com.checkout.payment.gateway.responces;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Data;

@Data
public class ApiPaymentResponse {
  private UUID id;
  private PaymentStatus status;
  @JsonProperty("card_number_last_four")
  private String cardNumberLastFour;
  @JsonProperty("expiry_month")
  private int expiryMonth;
  @JsonProperty("expiry_year")
  private int expiryYear;
  private String currency;
  private int amount;
  @JsonProperty("rejection_reason")
  private String rejectionReason;
}
