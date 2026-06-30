# MDAOPay Runbook

## Purpose
This file defines how to turn external findings, code analysis, and product changes into swarm work.

## When starting a new task
1. Read AGENTS.md.
2. Read docs/orchestration/PROJECT_INDEX.md.
3. Read docs/orchestration/inbox.md if it exists.
4. Decide whether the task is:
   - bug fix
   - optimization
   - architecture change
   - product-flow change
   - research / investigation

## When external analysis arrives
1. Save the findings in docs/orchestration/inbox.md.
2. Write a short summary of the issue.
3. Add suspected modules and files.
4. Convert it through the swarm-triage skill.
5. Spawn scanner, coder, reviewer, and tester tasks.

## When code needs optimization
1. Run scanner on the smallest relevant scope.
2. Run coder on the minimal patch.
3. Run reviewer independently.
4. Run tester for product behavior if flows changed.
5. Use glm-5.2 only for final synthesis if the result is unclear.

## When product behavior changes
1. Compare changes against PRD and TDD.
2. Validate against TDD/test-scenarios-v5-final.md.
3. Validate against docs/e2e-test-plan.md.
4. Record the result in docs/orchestration/inbox.md or a changelog note.

## Parallelism policy
- Scan app, backend, relay, and contracts in parallel when the task spans modules.
- Review code independently from implementation.
- Test independently from review whenever product behavior is affected.

## Ponytail policy
- Always consult ponytail/AGENTS.md and ponytail/opencode.json before code changes.
- Prefer ponytail skills and commands where they improve consistency, optimization, or reuse.

## Output policy
- Report exact files changed.
- Summarize risks and follow-up work.
- Keep the final result short and actionable.
