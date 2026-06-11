# Orquestração do F2 — workflow `f2-orchestration` (LOCAL-FIRST)

> Workflow: `.claude/workflows/f2-orchestration.js` · Handoff: [`state.json`](state.json) (espelho [`state.md`](state.md))
> **Estratégia (decisão do dono, 2026-06-10):** construir tudo local → E2E local com **disparos
> manuais dos RECALs** → validar → só então prod. **O ship é bloqueado por código sem
> `lanes.e2e.status=validated` no handoff** — mesmo com confirm.

## Sequência de invocações

| # | Invocação | O que faz | Pré |
|---|---|---|---|
| 1 | `Workflow {name:'f2-orchestration', args:{lane:'gate'}}` | 8 gates read-only (prod via MCP + fs + handoff do F1) | — |
| 2a | `{lane:'jobs'}` | INFRA-03 (tiering) + RECAL-00 (HLLs) no pipeline | gate |
| 2b | `{lane:'cons', confirm:['create-consolidator']}` | **cria o repo** + CONS-01..05 (ITs) | gate |
| 3 | `{lane:'local'}` | LOCAL-01 (compose.local + seed) + LOCAL-02 (replayer) | gate |
| 4 | `{lane:'e2e'}` | consolidador local + replay + **RECAL-01/02 manuais** + reconciliação ⇒ `validated` | cons + local done |
| 5a | `{lane:'rt'}` | RT-01/03/02 contra ambiente local | gate `f1-intel` ≠ RED + CONS-05 |
| 5b | `{lane:'web'}` | WEB-00..02 com intel local | gate `f1-portal` ≠ RED + RT |
| 6 | `{lane:'contract'}` | contrato Redis CONS-05↔RT-01 + REST RT↔portal | rt (e web, ideal) |
| 7 | `{lane:'ship', confirm:['push','deploy']}` | PRs + GHCR + **INFRA-04** (compose+crons+retenção 14d) | **e2e=validated** |
| 8 | `{lane:'verify-prod'}` (D+1 do deploy) | re-checks em prod via MCP + run doc de go-live | ship+merge+Actions |

**Re-run parcial:** `{lane:'cons', only:['CONS-03']}` (corrige uma task sem re-rodar a lane).
**Retomada:** o handoff é a memória — qualquer parte lê o `state.json` no início; re-rodar `gate`
atualiza os semáforos (ex.: `medido-volume` vira GREEN dias após o ship).

## Regras herdadas do f1-orchestration

- 1 parte por invocação; toda parte lê o handoff no início e grava o seu patch no fim (merge, nunca overwrite).
- Cada task = pipeline `implement → review → test → validate → refute` com até `retries` voltas
  (feedback dirigido pro estágio que falhou); run doc em `docs/epicos/runs/` ao final (DONE ou FAILED).
- Ações irreversíveis pausam sem o `confirm` correto: `create-consolidator` (CONS-01), `push`+`deploy` (ship).
- Gates RED **pulam** tasks (pending), não derrubam o workflow.

## Específico do F2

- **Validação local vs prod:** estágios de validação das lanes jobs/local/e2e/rt usam **psql/redis-cli
  no ambiente LOCAL** (envs em `lanes.local.env`; defaults 55432/19092/16379). O MCP `postgres-ooh`
  (PROD) só aparece em `gate` e `verify-prod` — nenhum agente valida contra o banco errado.
- **Lane cons valida por ITs** (Testcontainers): Kafka/Redis da VPS são inacessíveis — o E2E real
  é a parte `e2e`, e o cético é instruído a NÃO refutar por "não validou em prod".
- **Contrato Redis** (CONS-05) é publicado no handoff (`lanes.cons.redis_contract`) e conferido na
  parte `contract` contra o reader do intel.
- Armadilhas embutidas nos prompts: join por `short_name` (CONS-03), selo ADR-058 (RT/WEB),
  `TRIP_TIMEOUT_S` configurável (replay acelerado).
