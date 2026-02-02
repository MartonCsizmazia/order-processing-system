# Distributed Order Processing System

A production-grade, event-driven order processing system demonstrating distributed systems patterns including CQRS (Command Query Responsibility Segregation) and the Saga pattern for distributed transaction management.

## Architecture Highlights

- **Event-Driven Architecture** — Loosely coupled services communicating via Apache Kafka
- **CQRS Pattern** — Separated read/write models for optimized query and command handling
- **Saga Pattern** — Choreography-based distributed transactions with compensating actions
- **Domain-Driven Design** — Rich domain models with aggregate roots and value objects

## Tech Stack

- Java 21
- Spring Boot 3.2
- Apache Kafka
- PostgreSQL
- Spring Data JPA
- Testcontainers for integration testing
- OpenAPI/Swagger documentation

## Key Features

- Distributed transaction management across Order, Inventory, and Payment services
- Automatic compensation/rollback on failures
- Optimistic locking for concurrent order modifications
- Event sourcing-ready event structure
- Comprehensive state machine for order lifecycle

## Project Structure
```
├── order-service/      # Order management, saga orchestration
├── inventory-service/  # Stock reservation and management  
├── payment-service/    # Payment processing
└── common/             # Shared events and utilities
```

## Running Locally
```bash
# Start infrastructure
docker-compose up -d

# Run the application
./mvnw spring-boot:run
