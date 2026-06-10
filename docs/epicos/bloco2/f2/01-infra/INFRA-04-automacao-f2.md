# INFRA-04 — automação do F2 no `uai-infra` (compose + crons)

> F2 (Bloco 2) · lane **infra** · **Repo-alvo:** `uai-infra` · **Stack:** Docker Compose / cron / GitHub Actions
> **Criada em 2026-06-10** (gap apontado pelo dono: os cards CONS/RECAL/INFRA-03 descrevem os jobs,
> mas ninguém era dono da fiação que os faz rodar **automático**). Dono do compose/cron = `uai-infra`;
> deploy só por Actions (princípio uAI — nunca tocar a VPS direto).

## Objetivo
Tudo do F2 rodando **sem mão humana**: consolidador como serviço contínuo + jobs agendados do
pipeline. Espelha o que foi feito pro poller no go-live (compose + kafka-init + envs).

## Entregas

### 1. Serviço contínuo: `uai-ooh-trip-consolidator` no compose
- Serviço com imagem GHCR (CI do repo do consolidador publica — vem do template, CONS-01),
  `depends_on: postgres-ooh, kafka-init, redis`, restart `unless-stopped`, healthcheck do worker.
- Envs/secrets: Kafka bootstrap, Redis, datasource `ooh` (schema `medido` via Flyway do app),
  limites de heap enxutos (VPS pequena — `JAVA_TOOL_OPTIONS=-Xmx384m` como ponto de partida, medir).
- **Quando:** junto do fim da lane CONS (CONS-04 pronto ⇒ deploy; CONS-05 atualiza a mesma imagem).

### 2. Crons dos jobs do pipeline (padrão da tabela de crons existente no README do `uai-infra`)
| Job | Cadência | Cron (proposta) | Card de origem |
|---|---|---|---|
| `ingestor rt-raw-archive` | diário | `0 4 * * *` | INFRA-03 (tiering raw→MinIO) |
| `normalizer reach --fonte=medida` (recompute) | diário (D-1) | `30 4 * * *` (após o archive) | RECAL-01 |
| `recal hll-audiencia-hex` | pós-rebuild da base OD (não periódico) | passo no fim do workflow do `normalizer reach` | RECAL-00 |
| `ingestor rt-raw-retention` | diário (vira fallback do archive) | mantém o atual | INFRA-02 ✅ |

- Ordem importa: archive (4h) → recompute (4h30) — o recompute lê `medido`, não o `raw`, mas
  manter janela única de madrugada simplifica o diagnóstico.
- `OOH_RT_RAW_RETENTION_DAYS=14` **já** (alívio imediato da INFRA-03); cai pra 7 quando o archive rodar.

### 3. Observabilidade mínima
- Healthcheck do consolidador no compose; logs dos crons com exit code visível (`docker compose logs`);
  o run de cada job continua sendo registrado no PM quando relevante (convenção do repo).

## Critério de pronto (verificável)
- VPS reiniciada → poller + consolidador voltam sozinhos e o `medido` segue crescendo.
- Crons disparam nos horários (verificar 1 ciclo completo de madrugada): partição arquivada,
  recompute D-1 com `face_reach` fonte medida atualizado.
- Nenhum passo do F2 depende de comando manual.

## Produz
- docs/epicos/runs/INFRA-04-automacao-f2.md
