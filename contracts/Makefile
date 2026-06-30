# MDAOPay Contracts — Makefile
# Usage:
#   make test      — Run tests
#   make deploy    — Deploy to Sepolia
#   make snapshot  — Gas snapshot

.PHONY: test deploy snapshot

test:
	forge test -vvv

deploy:
	@bash scripts/deploy-sepolia.sh

snapshot:
	forge snapshot
