package com.banking.platform.shared.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Banking Platform API",
                version = "1.0.0",
                description = "Complete banking platform with account management, transactions, ACH, wire transfers, rewards, ledger, debit network, and reporting.",
                contact = @Contact(name = "Banking Platform", email = "support@bankingplatform.com")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local development")
        }
)
public class OpenApiConfig {
}

