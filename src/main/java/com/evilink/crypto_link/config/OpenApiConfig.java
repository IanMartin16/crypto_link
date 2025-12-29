package com.evilink.crypto_link.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
  info = @Info(
    title = "CryptoLink API",
    version = "v1",
    description = "API de cotizaciones crypto con planes, rate limit y SSE."
  )
)
@SecurityScheme(
  name = "apiKeyAuth",
  type = SecuritySchemeType.APIKEY,
  in = SecuritySchemeIn.HEADER,
  paramName = "x-api-key"
)
public class OpenApiConfig {}
