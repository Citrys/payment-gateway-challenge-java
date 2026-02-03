package com.checkout.payment.gateway.requests;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BankPaymentRequest {
  @JsonProperty("card_number")
  private String cardNumber;

  @JsonProperty("expiry_date")
  private String expiryDate;

  private String currency;
  private int amount;
  private String cvv;
}
