#!/usr/bin/env bash
# Generates the RSA key pair used by User Service to sign JWTs.
# In real environments the private key lives in a secret manager (Vault / AWS Secrets Manager / K8s Secret).
set -euo pipefail
mkdir -p keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/private.pem
openssl rsa -pubout -in keys/private.pem -out keys/public.pem
chmod 600 keys/private.pem
echo "Keys written to ./keys"
