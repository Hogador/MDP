#!/bin/bash
# Load AI provider keys from .env
set -a
source /home/ekzent/project/MDAOPay/.env
set +a
# Start opencode
exec opencode "$@"
