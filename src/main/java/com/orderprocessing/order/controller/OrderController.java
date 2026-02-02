package com.orderprocessing.order.controller;

import com.orderprocessing.order.domain.OrderStatus;
import com.orderprocessing.order.dto.CreateOrderRequest;
import com.orderprocessing.order.dto.OrderResponse;
import com.orderprocessing.order.service.OrderCommandService;
import com.orderprocessing.order.service.OrderQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Order management API")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderCommandService commandService;
    private final OrderQueryService queryService;

    public OrderController(OrderCommandService commandService, OrderQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    // ==================== COMMAND ENDPOINTS ====================

    @PostMapping
    @Operation(summary = "Create a new order", description = "Creates a new order and initiates the order processing saga")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created successfully",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        log.info("Received create order request for customer: {}", request.customerId());

        OrderResponse response = commandService.createOrder(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel an order", description = "Cancels an order if it hasn't been completed yet")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order cancelled successfully"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Order cannot be cancelled (already completed)")
    })
    public ResponseEntity<OrderResponse> cancelOrder(
            @Parameter(description = "Order ID") @PathVariable String orderId) {
        log.info("Received cancel request for order: {}", orderId);

        OrderResponse response = commandService.cancelOrder(orderId);

        return ResponseEntity.ok(response);
    }

    // ==================== QUERY ENDPOINTS ====================

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by ID", description = "Retrieves a single order by its ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<OrderResponse> getOrder(
            @Parameter(description = "Order ID") @PathVariable String orderId) {
        OrderResponse response = queryService.getOrderById(orderId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get orders by customer", description = "Retrieves all orders for a specific customer")
    public ResponseEntity<List<OrderResponse>> getOrdersByCustomer(
            @Parameter(description = "Customer ID") @RequestParam String customerId) {
        List<OrderResponse> orders = queryService.getOrdersByCustomerId(customerId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get orders by status", description = "Retrieves all orders with a specific status")
    public ResponseEntity<List<OrderResponse>> getOrdersByStatus(
            @Parameter(description = "Order status") @PathVariable OrderStatus status) {
        List<OrderResponse> orders = queryService.getOrdersByStatus(status);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/customer/{customerId}/active")
    @Operation(summary = "Get active orders", description = "Retrieves all active (non-completed, non-cancelled) orders for a customer")
    public ResponseEntity<List<OrderResponse>> getActiveOrders(
            @Parameter(description = "Customer ID") @PathVariable String customerId) {
        List<OrderResponse> orders = queryService.getActiveOrdersByCustomerId(customerId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/customer/{customerId}/summary")
    @Operation(summary = "Get order summary", description = "Retrieves an aggregated summary of a customer's orders")
    public ResponseEntity<OrderQueryService.OrderSummary> getCustomerSummary(
            @Parameter(description = "Customer ID") @PathVariable String customerId) {
        OrderQueryService.OrderSummary summary = queryService.getCustomerOrderSummary(customerId);
        return ResponseEntity.ok(summary);
    }

    // ==================== EXCEPTION HANDLERS ====================

    @ExceptionHandler(OrderCommandService.OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderCommandService.OrderNotFoundException ex) {
        log.warn("Order not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("ORDER_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        log.warn("Illegal state transition: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("INVALID_STATE_TRANSITION", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: ", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    public record ErrorResponse(String code, String message) {}
}