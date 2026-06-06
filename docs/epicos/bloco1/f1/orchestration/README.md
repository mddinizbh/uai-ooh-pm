# F1 — Orquestração por agentes (partes por lane + handoff explícito)

> Harness do Workflow tool que executa o F1 (Bloco 1 uAI-OOH). Script: `.claude/workflows/f1-orchestration.js`.
> **Cada invocação roda UMA parte** (`args.lane`) que **lê** o handoff (`state.json`) no início e **grava** o seu no fim.
> O código de app NÃO nasce aqui — nasce nos repos-alvo (`uai-ooh-pipeline`, `uai-ooh-intel`, `uai-portal`).

## Modelo

- **Parte = `args.lane`** ∈ `gate · data · back · shell · module · contract · ship`.
- **Contexto compartilhado = `state.json`** (handoff explícito). Merge, nunca sobrescreve; `decisions/runDocs/pending`
  fazem append. Espelho legível em `state.md`.
- **Cada task** roda como pipeline `implement → review → test → db-validate → refute` (loop até `retries`). Só vira
  `done` se sobreviver à **camada adversarial** (refute). Tasks sob `uai-auth=RED` viram `partial` (stub), não `done`.
- **Paralelismo real = entre partes/repos**: `data`, `back`, `shell` são repos distintos → podem ser disparados
  concorrentes. Dentro de uma parte as tasks **serializam** (working tree único).
- **Validação**: counts no banco `ooh` via MCP `postgres-ooh` (estágio db-validate + adversarial) e contract-check
  OpenAPI(intel) ↔ hooks(intelClient) na parte `contract`.

## Ordem de dispatch

```
1. {lane:'gate'}                              → readiness matrix GREEN/RED no handoff
2. {lane:'data'}                              ┐ repos distintos —
3. {lane:'back',  confirm:['create-intel']}   ├ podem rodar concorrentes
4. {lane:'shell', confirm:['fork-portal']}    ┘
5. {lane:'module'}                            → exige back.endpoints + shell EP3-01 no handoff
6. {lane:'contract'}                          → divergências viram pending (reabrem nas tasks culpadas)
7. {lane:'ship',  confirm:['push','deploy']}  → PRs, GHCR, uai-infra (nginx/VPS intocados)
```

## Tokens de confirm (fronteiras irreversíveis)

| Token | Onde | O que destrava |
|---|---|---|
| `create-intel` | parte `back`, task EP2-01 | cria `uai-ooh-intel` do template |
| `fork-portal` | parte `shell`, task EP3-01 | forka `uai-spark` → `uai-portal` |
| `push` | parte `ship` | `git push` / abre PRs |
| `deploy` | parte `ship` | imagem GHCR + entrada `uai-infra` |

## Args

`lane` (obrigatório de fato; default `gate`) · `confirm[]` · `retries` (default 2) · `only[]` (subset de tasks p/ re-run).

## Como retomar

O handoff é a memória. Re-rode `{lane:'gate'}` a qualquer momento (re-apura banco/fs — fonte da verdade) e depois a
parte que faltou. Tasks `skipped/blocked/failed/partial` ficam em `state.json.pending` com o motivo. Quando um gate
vira GREEN (ex.: `uai-auth`), re-rode a lane afetada com `only:['EP3-02', ...]` pra reconciliar (stub → real).

## Degradação `uai-auth` RED

EP3-01/EP3-03 normais; **EP3-02 e EP2-07 rodam em modo stub** (AuthContext + interceptor Bearer reais; SSO/introspection
real deferido). A camada adversarial mantém essas tasks em `partial`. EP4 não é bloqueada — roda sobre o stub.
