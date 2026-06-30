# MDAOPay Swarm Workflow

planner: mimo-v2.5-free
scanner: north-mini-code-free
coder: deepseek-v4-flash-free
reviewer: big-pickle
tester: nemotron-3-ultra-free
final: glm-5.2

Rules:
- Read ponytail/AGENTS.md and ponytail/opencode.json first.
- Use ponytail skills and commands before writing code.
- Prefer project-wide optimizations from ponytail.
- Run scanner in parallel across app/backend/contracts/relay.
- Use glm-5.2 only for final synthesis.
