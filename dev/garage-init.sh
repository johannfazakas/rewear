#!/usr/bin/env bash
# One-time provisioning for the local Garage dev container: assign a storage
# layout, create the photos bucket, and import a fixed dev access key so the
# credentials in .env are stable across machines. Safe to re-run.
set -euo pipefail

COMPOSE="docker compose"
GARAGE="$COMPOSE exec -T garage /garage"

BUCKET="rewear-photos"
ACCESS_KEY="GK0000000000000000000000de"
SECRET_KEY="0000000000000000000000000000000000000000000000000000000000000000"

NODE_ID="$($GARAGE node id -q 2>/dev/null | cut -d@ -f1)"

if $GARAGE status | grep -q "NO ROLE ASSIGNED"; then
  $GARAGE layout assign -z dc1 -c 1G "$NODE_ID"
  $GARAGE layout apply --version 1
fi

$GARAGE bucket create "$BUCKET" 2>/dev/null || true
$GARAGE key import "$ACCESS_KEY" "$SECRET_KEY" --yes -n rewear-dev 2>/dev/null || true
$GARAGE bucket allow --read --write --owner "$BUCKET" --key "$ACCESS_KEY"

echo
echo "Garage ready. Put these in your .env:"
echo "  S3_ACCESS_KEY=$ACCESS_KEY"
echo "  S3_SECRET_KEY=$SECRET_KEY"
echo "  S3_BUCKET=$BUCKET"
