# Instructions for candidates

This is the Java version of the Payment Gateway challenge. If you haven't already read this [README.md](https://github.com/cko-recruitment/) on the details of this exercise, please do so now.

## Requirements
- JDK 17
- Docker

## Template structure

src/ - A skeleton SpringBoot Application

test/ - Some simple JUnit tests

imposters/ - contains the bank simulator configuration. Don't change this

.editorconfig - don't change this. It ensures a consistent set of rules for submissions when reformatting code

docker-compose.yml - configures the bank simulator


## API Documentation
For documentation openAPI is included, and it can be found under the following url: **http://localhost:8090/swagger-ui/index.html**

---

## Implementation Overview

This implementation includes:
- ✅ Payment processing with three statuses (Authorized, Declined, Rejected)
- ✅ Payment retrieval by ID
- ✅ Comprehensive validation with PCI DSS compliance
- ✅ Retry mechanism with exponential backoff
- ✅ Secure logging (no sensitive data exposure)
- ✅ SOLID principles architecture
- ✅ 19 comprehensive tests

---

## Key Design Considerations and Assumptions

### 1. Architecture & Design Patterns

#### SOLID Principles
**Decision**: Refactored to follow all SOLID principles for maintainability and extensibility.

**Implementation**:
- **Single Responsibility**: Separated concerns into focused classes
  - `PaymentGatewayService`: Orchestration only
  - `CompositePaymentValidator`: Validation logic
  - `RestTemplateBankService`: Bank communication
  - `DataMaskingUtil`: Data security

- **Open/Closed**: Extensible through interfaces
  - New validators can be added without modifying existing code
  - New bank providers can be plugged in via `BankService` interface

- **Dependency Inversion**: All dependencies are abstractions (interfaces)
  - Easier to test with mocks
  - Flexible implementation swapping

**Assumption**: The system will evolve with additional features (fraud detection, multiple banks, different validators), so extensibility is prioritized.

**Trade-off**: Slightly more initial complexity for long-term maintainability.

---

### 2. Security & Compliance

#### PCI DSS Compliance
**Decision**: Implement strict PCI DSS Level 1 compliant logging and data handling.

**Implementation**:
- ❌ Never log full card numbers (only last 4 digits in responses)
- ❌ Never log CVV codes
- ❌ Never log expiry dates in logs
- ✅ Authorization codes are masked (show only last 4 chars: `****6789`)
- ✅ All validation errors use generic messages without exposing input values

**Assumption**: This is a production-grade payment system that must comply with PCI DSS 3.2.1.

**Reference**: See `LOGGING_SECURITY_COMPLIANCE.md` for detailed compliance documentation.

---

### 3. Idempotency

#### PUT over POST
**Decision**: Changed from POST to PUT for payment processing endpoint.

**Rationale**:
- PUT is idempotent by nature
- Prevents duplicate payments if client retries
- Payment ID provided in URL path ensures idempotency
- Safer for network retries and timeouts

**Endpoint**: `PUT /payment/{id}`

**Assumption**: Merchants may retry failed requests, and we need to prevent duplicate charges.

**Trade-off**: Requires client to generate UUID upfront, but provides better safety guarantees.

---

### 4. Retry Mechanism & Resilience

#### Exponential Backoff with Spring Retry
**Decision**: Implement automatic retry with exponential backoff for bank communication.

**Configuration**:
```properties
payment.retry.max-attempts=3
payment.retry.initial-delay=1000ms
payment.retry.max-delay=10000ms
payment.retry.multiplier=2.0
```

**Behavior**:
- Attempt 1: Immediate
- Attempt 2: Wait 1s, retry
- Attempt 3: Wait 2s, retry
- Total max wait: 3 seconds

**Retryable Errors**:
- `RestClientException`: Network issues
- `ResourceAccessException`: Connection timeouts

**Non-Retryable**:
- Validation failures (rejected immediately)
- Declined payments (final decision)

**Assumption**: Bank API may have transient failures (network issues, temporary overload) that should be retried.

**Trade-off**: Increased latency for failed requests vs. higher success rate.

**Reference**: See `RETRY_AND_TIMEOUT_CONFIG.md` for detailed configuration.

---

### 5. Validation Strategy

#### Fail-Fast Validation
**Decision**: Validate all input before calling the bank to minimize unnecessary API calls.

**Validation Rules**:
- **Card Number**: 14-19 digits, numeric only
- **Expiry Date**: Valid month (1-12), future date (month+year >= current)
- **Currency**: Must be in supported list (USD, GBP, EUR) - configurable
- **Amount**: Must be positive integer (minor currency unit)
- **CVV**: 3-4 digits (100-9999)

**Assumption**: Invalid requests should be rejected immediately without consuming bank API quota.

**Benefits**:
- Reduced bank API costs
- Faster response for invalid requests
- Better error messages for clients

---

### 6. Error Handling & Status Mapping

#### Three Payment Statuses
**Decision**: Map all outcomes to three clear statuses.

| Status | When | Reason |
|--------|------|--------|
| `AUTHORIZED` | Valid request, bank approves | Payment successful |
| `DECLINED` | Valid request, bank declines | Insufficient funds, fraud, etc. |
| `REJECTED` | Invalid request data | Fails validation before bank call |

**Bank Simulator Behavior** (from `imposters/bank_simulator.ejs`):
- Cards ending in 1,3,5,7,9 → Authorized
- Cards ending in 2,4,6,8 → Declined
- Cards ending in 0 → 503 error (treated as Declined)
- Missing required fields → 400 error (treated as Declined)

**Assumption**: Any bank communication failure (timeout, 5xx errors) should result in DECLINED status, not system error.

**Rationale**: Better UX - merchant sees "Declined" rather than "System Error".

---

### 7. Data Storage

#### In-Memory Repository
**Decision**: Use HashMap for storage (non-persistent).

**Rationale**:
- Sufficient for demonstration/testing
- Easy to swap with database implementation (follows Repository pattern)
- No external dependencies required

**Assumption**: This is a prototype/demo system. Production would use:
- Database (PostgreSQL, MySQL)
- Distributed cache (Redis)
- Event sourcing for audit trail

**Extensibility**: Interface-based design (`PaymentRepository`) makes it easy to add:
```java
@Repository
public class JpaPaymentRepository implements PaymentRepository {
  // Database implementation
}
```

---

### 8. Timeout Configuration

#### Network Timeouts
**Decision**: Implement separate connect and read timeouts.

**Configuration**:
```properties
bank.simulator.timeout.connect=5000ms  # Connection establishment
bank.simulator.timeout.read=10000ms    # Response reading
```

**Assumption**:
- Bank API should respond within 10 seconds
- Longer timeouts can cause thread pool exhaustion
- Clients expect sub-second responses where possible

**Trade-off**: May fail fast on slow responses, but prevents resource exhaustion.

---

### 9. Currency Support

#### Configurable Currency List
**Decision**: Make supported currencies configurable via properties.

**Current**: USD, GBP, EUR

**Configuration**:
```properties
payment.supported.currencies=USD,GBP,EUR
```

**Assumption**: Different regions/merchants may need different currency support.

**Extensibility**: Adding new currencies doesn't require code changes.

---

### 10. Testing Strategy

#### Comprehensive Test Coverage
**Decision**: Test all three payment statuses and validation scenarios.

**Test Coverage** (19 tests):
- ✅ 2 tests for payment retrieval (success, not found)
- ✅ 3 tests for AUTHORIZED scenarios (odd numbers, various card lengths)
- ✅ 2 tests for DECLINED scenarios (even numbers)
- ✅ 12 tests for REJECTED scenarios (all validation rules)

**Assumption**: Production system needs extensive test coverage for confidence.

**Benefits**:
- Catches regressions early
- Documents expected behavior
- Enables safe refactoring

---

### 11. Code Quality & Maintainability

#### Project Lombok
**Decision**: Use Lombok to reduce boilerplate code.

**Benefits**:
- 250+ lines of boilerplate removed
- Cleaner, more readable models
- Less maintenance overhead

**Models Using Lombok**:
- `@Data` for getters/setters/equals/hashCode
- `@AllArgsConstructor` for immutable objects
- `@Getter` for read-only classes

**Trade-off**: Lombok dependency, but widely accepted in Spring ecosystem.

---

### 12. API Design

#### RESTful Design
**Decision**: Follow REST conventions with clear resource naming.

**Endpoints**:
- `PUT /payment/{id}` - Process payment (idempotent)
- `GET /payment/{id}` - Retrieve payment

**Response Format** (JSON):
```json
{
  "id": "uuid",
  "status": "Authorized|Declined|Rejected",
  "card_number_last_four": "1234",
  "expiry_month": 12,
  "expiry_year": 2026,
  "currency": "USD",
  "amount": 1000
}
```

**Assumption**: API will be consumed by various clients (web, mobile, third-party integrations).

---

### 13. Monitoring & Observability

#### Structured Logging
**Decision**: Use SLF4J with clear log levels and contextual information.

**Log Levels**:
- `INFO`: Business events (payment processed, authorized, declined)
- `WARN`: Validation failures, business rule violations
- `DEBUG`: Technical details (bank calls, persistence)
- `ERROR`: System errors, exceptions

**Context**: Always include payment ID for traceability.

**Assumption**: Production needs centralized logging (ELK, Splunk, Datadog).

**Future Enhancement**: Add distributed tracing (Zipkin, Jaeger) for microservices.

---

### 14. Configuration Management

#### Externalized Configuration
**Decision**: All tunable parameters in `application.properties`.

**Configurable**:
- Server port
- Bank URL
- Timeouts (connect, read)
- Retry parameters (attempts, delays, multiplier)
- Supported currencies

**Assumption**: Different environments (dev, staging, prod) need different configs.

**Benefits**:
- No code changes for environment-specific settings
- Easy to tune in production
- Spring Cloud Config ready

---

### 15. Assumptions & Constraints

#### Key Assumptions Made:

1. **Payment ID Generation**: Client provides UUID in URL path
   - Alternative: Server could generate and return in response

2. **Full Card Number**: Request includes full card number
   - Bank simulator needs it for authorization
   - Only last 4 digits stored/returned
   - Never logged (PCI DSS compliance)

3. **Amount Format**: Integer in minor currency unit
   - $10.50 = 1050 cents
   - Avoids floating-point precision issues

4. **Expiry Date Format**: Separate month and year integers
   - Easier validation than string parsing
   - Clear semantics

5. **Synchronous Processing**: Payment processed in request/response cycle
   - Alternative: Async processing with webhooks (more scalable)

6. **Single Bank**: Only one bank simulator
   - Architecture supports multiple banks via interface

7. **No Authentication**: API is open
   - Production would need OAuth2/API keys

8. **No Rate Limiting**: Unlimited requests
   - Production would need rate limiting per merchant

9. **No Duplicate Detection**: Beyond idempotency key
   - Production might need additional fraud detection

10. **Card Validation**: Only format validation, no Luhn algorithm
    - Bank handles actual card validation

---

### 16. Known Limitations & Future Improvements

#### Current Limitations:

1. **In-Memory Storage**: Data lost on restart
   - **Solution**: Add database repository implementation

2. **Single Instance**: No horizontal scaling
   - **Solution**: Add Redis for distributed state

3. **No Circuit Breaker**: Bank failures can cascade
   - **Solution**: Add Resilience4j circuit breaker

4. **No Request Authentication**: Open API
   - **Solution**: Add Spring Security with OAuth2

5. **No Request Rate Limiting**: Can be overwhelmed
   - **Solution**: Add Bucket4j rate limiting

6. **No Async Processing**: Blocking I/O
   - **Solution**: Add WebFlux for reactive processing

#### Potential Enhancements:

1. **Fraud Detection**: ML-based fraud scoring
2. **3D Secure**: Strong Customer Authentication (SCA)
3. **Webhooks**: Notify merchants of payment status changes
4. **Refunds**: Support payment reversals
5. **Partial Captures**: Authorize now, capture later
6. **Multi-Currency**: Dynamic currency conversion
7. **Analytics**: Payment success rates, latency metrics
8. **Audit Trail**: Complete payment lifecycle history
9. **API Versioning**: Support multiple API versions
10. **GraphQL**: Alternative to REST API

---

### 17. Technology Stack Justification

| Technology | Reason |
|------------|--------|
| **Spring Boot 3.1.5** | Industry standard, robust ecosystem |
| **Java 17** | LTS version, modern language features |
| **Lombok** | Reduce boilerplate, cleaner code |
| **Spring Retry** | Built-in retry support, proven in production |
| **RestTemplate** | Mature, well-tested HTTP client |
| **SLF4J** | Standard logging facade |
| **JUnit 5** | Modern testing framework |
| **Mockito** | Powerful mocking for tests |
| **Gradle 8.11** | Modern build tool, Java 17+ support |

---

### 18. Performance Considerations

**Response Time Goals**:
- Validation failures: < 50ms
- Successful authorization: < 500ms (including bank call)
- Bank retries: < 3 seconds max

**Scalability**:
- Stateless design (except in-memory storage)
- Can be deployed behind load balancer
- Ready for containerization (Docker/Kubernetes)

**Bottlenecks**:
- Bank API latency (mitigated with timeouts)
- In-memory storage (would use database in production)

---

### 19. Documentation

Comprehensive documentation provided:

1. **README.md** - Getting started (this file)
2. **RETRY_AND_TIMEOUT_CONFIG.md** - Retry mechanism details
3. **LOGGING_SECURITY_COMPLIANCE.md** - PCI DSS compliance
4. **SOLID_PRINCIPLES_REFACTORING.md** - Architecture decisions

---

## Running the Application

### Start Bank Simulator
```bash
docker-compose up -d
```

### Run Application
```bash
./gradlew bootRun
```

### Run Tests
```bash
./gradlew test
```

### Access Swagger UI
http://localhost:8090/swagger-ui/index.html

---

## Example API Calls

### Process Payment (Authorized)
```bash
curl -X PUT http://localhost:8090/payment/$(uuidgen) \
  -H "Content-Type: application/json" \
  -d '{
    "card_number": "4111111111111111",
    "expiry_month": 12,
    "expiry_year": 2026,
    "currency": "USD",
    "amount": 1000,
    "cvv": 123
  }'
```

### Retrieve Payment
```bash
curl http://localhost:8090/payment/{payment-id}
```

---

## Contact

For questions about design decisions or implementation details, please refer to the documentation files listed above.