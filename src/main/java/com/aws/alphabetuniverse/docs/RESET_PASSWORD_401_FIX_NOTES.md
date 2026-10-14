# Reset Password 401 Fix Notes

## Muc tieu

Sua loi khi test API `POST /api/auth/reset-password` tren Swagger bi tra ve `401 Unauthorized`.

Sau khi sua, endpoint nay khong con bi `401` khi code reset password sai. Thay vao do API tra ve loi ro rang hon, vi du:

```json
{
  "message": "Verification code is incorrect"
}
```

Status luc nay la `400 Bad Request`, khong phai `401 Unauthorized`.

## Nguyen nhan loi

Endpoint `/api/auth/reset-password` da nam trong rule `permitAll`, nen ve ly thuyet khong can JWT.

Tuy nhien khi Cognito nem loi, Spring forward request sang `/error`. Truoc do `/error` khong duoc `permitAll`, nen Spring Security chan `/error` va bien loi that thanh `401 Unauthorized`.

Ngoai ra Swagger dang cau hinh Bearer Authentication toan cuc, lam cac endpoint public nhu `reset-password` cung hien nhu can JWT.

## Cac file da sua

### 1. `src/main/java/com/aws/alphabetuniverse/security/config/SecurityConfig.java`

Da them `/error` vao danh sach public endpoint:

```java
.requestMatchers(
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/error"
)
.permitAll()
```

Ly do sua:

- Khi API co loi, Spring co the forward sang `/error`.
- Neu `/error` bi chan boi security, loi that se bi che thanh `401`.
- Cho phep `/error` giup response hien dung loi goc hon.

### 2. `src/main/java/com/aws/alphabetuniverse/common/exception/GlobalExceptionHandler.java`

Da them `@RestControllerAdvice` va cac handler bat loi Cognito:

- `CodeMismatchException`
- `ExpiredCodeException`
- `InvalidPasswordException`
- `UserNotFoundException`
- `CognitoIdentityProviderException`

Ly do sua:

- `confirmForgotPassword(...)` cua Cognito co the nem exception khi code sai, code het han, password yeu, hoac user khong ton tai.
- Neu khong bat exception, loi co the bi day sang `/error`.
- Sau khi bat loi, API tra ve `400 Bad Request` kem message de hieu.

Vi du khi code sai:

```json
{
  "message": "Verification code is incorrect"
}
```

### 3. `src/main/java/com/aws/alphabetuniverse/config/OpenApiConfig.java`

Da go bo security requirement toan cuc:

```java
.addSecurityItem(
        new SecurityRequirement()
                .addList("Bearer Authentication")
)
```

Ly do sua:

- Cau hinh cu ap Bearer JWT cho tat ca API tren Swagger.
- Cac API public nhu `/api/auth/forgot-password`, `/api/auth/reset-password`, `/api/auth/logout` khong nen bi Swagger danh dau la can token.
- Sau khi go bo, Swagger khong tu ep auth cho reset-password nua.

Van giu lai khai bao security scheme:

```java
.addSecuritySchemes(
        "Bearer Authentication",
        new SecurityScheme()
                .name("Authorization")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
)
```

Ly do giu lai:

- Cac API can login van co the dung Bearer JWT trong Swagger.

### 4. `src/main/java/com/aws/alphabetuniverse/user/controller/UserController.java`

Da them:

```java
@SecurityRequirement(name = "Bearer Authentication")
```

Ly do sua:

- Sau khi bo Bearer auth toan cuc trong Swagger, can danh dau rieng cac controller/endpoint that su can login.
- `/api/users/me` la endpoint can user dang nhap, nen van phai hien yeu cau Bearer token trong Swagger.

## Ket qua kiem tra

Da chay:

```bash
mvn test
```

Ket qua:

```text
BUILD SUCCESS
```

Da test lai:

```http
POST /api/auth/reset-password
```

Voi code sai, ket qua hien tai:

```json
{
  "message": "Verification code is incorrect"
}
```

Status:

```text
400 Bad Request
```

Khong con la:

```text
401 Unauthorized
```

## Luu y khi test

Neu Swagger van hien hanh vi cu, hay restart lai Spring Boot app de server load code moi.

Sau khi restart, test `/api/auth/reset-password` tren Swagger ma khong can Bearer token.

