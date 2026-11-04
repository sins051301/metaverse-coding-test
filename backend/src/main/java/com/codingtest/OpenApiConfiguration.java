package com.codingtest;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI 의 Authorize 버튼에 토큰을 넣으면 @SecurityRequirement(name = "bearer") 가 붙은 API 에 Authorization: Bearer 헤더가 붙는다.
 */
@Configuration
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfiguration {
}
