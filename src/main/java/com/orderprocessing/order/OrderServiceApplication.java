package com.orderprocessing.order;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;


@SpringBootApplication(exclude = {
        org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration.class
})
@EnableKafka
@EnableJpaAuditing
@OpenAPIDefinition(
        info = @Info(
                title = "Order Processing System API",
                version = "1.0",
                description = "Distributed Event-Driven Order Processing System with CQRS and Saga Pattern",
                contact = @Contact(
                        name = "Your Name",
                        email = "your.email@example.com"
                ),
                license = @License(
                        name = "MIT License",
                        url = "https://opensource.org/licenses/MIT"
                )
        )
)
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}