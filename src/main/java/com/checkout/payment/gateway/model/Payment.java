package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Domain model representing a payment stored in the repository
 * Stores bank authorization code for reference and rejection reason if rejected
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
  private UUID id;
  private PaymentStatus status;
  private String cardNumberLastFour;
  private int expiryMonth;
  private int expiryYear;
  private String currency;
  private int amount;
  private String authorizationCode;
  private String rejectionReason;
}