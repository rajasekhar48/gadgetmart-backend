# GadgetMart Backend: Auth slice (Gateway + User Service)

## Run locally (without Docker)
```bash
docker compose up -d postgres            # users_db on :5432
mvn -pl user-service spring-boot:run     # :8081 (ephemeral JWT key if none configured)
mvn -pl gateway-service spring-boot:run  # :8080
```

## Run everything with Docker
```bash
./scripts/generate-keys.sh               # creates ./keys/private.pem + public.pem
docker compose up --build
```

## Try it (always through the gateway on :8080)
```bash
# register (role: BUYER default, or SELLER)
curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"asha@example.com","password":"Passw0rd!23","fullName":"Asha K","role":"SELLER"}'

# login
curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"asha@example.com","password":"Passw0rd!23"}'

# call a protected endpoint
curl -s localhost:8080/api/users/me -H "Authorization: Bearer <accessToken>"

# refresh (rotates the refresh token)
curl -s -X POST localhost:8080/api/auth/refresh -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<refreshToken>"}'

# admin only (login as admin@gadgetmart.local / ChangeMe123!)
curl -s localhost:8080/api/admin/users -H "Authorization: Bearer <adminAccessToken>"
```
Expected: no token gives 401, non-admin on /api/admin/** gives 403.
