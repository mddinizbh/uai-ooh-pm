# INFRA-03 — tiering do `raw`: arquivar no MinIO antes de dropar

> F2 (Bloco 2) · lane **infra** · **Repo-alvo:** `uai-ooh-pipeline` (job) + `uai-infra` (cron/env) · **Stack:** Python + DuckDB + MinIO (S3)
> **Criada em 2026-06-10** (preocupação do dono: VPS pequena não guarda a frota inteira por hora em Postgres).
> **Evolui o `rt-raw-retention` existente:** hoje ele só **dropa**; passa a **arquivar → verificar → dropar**.

## Objetivo
Janela **quente curta** do `raw` no Postgres (7 dias, ~10 GB constante) + histórico **frio comprimido
no MinIO** (bucket `uai-ooh`, já de pé no `uai-infra`) — sem perder a reprodutibilidade (o `raw` é a
única fonte de replay; feed em tempo real não tem rewind).

## Números (medidos 2026-06-10)
- ~147 bytes/linha · ~4.100 ciclos/dia · frota 600–1.800/ciclo → **~4-5M linhas/dia ≈ 1,2-1,5 GB/dia** com índices.
- Comprimido (parquet/zstd, TEXT repetitivo ~10-15×): **~100-150 MB/dia ≈ 3-4,5 GB/mês** no MinIO.

## Como executar
- **Job `rt-raw-archive`** (novo comando do `ingestor`, padrão roda-e-sai como o `rt-raw-retention`):
  1. Para cada partição `rt__vehicle_position_pYYYYMMDD` mais velha que `OOH_RT_RAW_HOT_DAYS` (default **7**):
     exporta pra **parquet/zstd** via **DuckDB** (dep já existente no pipeline; mantém o histórico
     consultável in-place) — fallback aceitável: `COPY ... TO STDOUT CSV | gzip`.
  2. Upload pro MinIO: `uai-ooh/rt-archive/rt__vehicle_position/YYYY/MM/pYYYYMMDD.parquet`
     (creds via env, mesmo padrão do `ooh-gtfs-sync`).
  3. **Verifica** (count do parquet == count da partição) → só então **DROP** da partição.
     Idempotente: partição já arquivada (objeto existe + count bate) → só dropa.
- **Cron no `uai-infra`**: diário, junto dos jobs existentes do pipeline. O `rt-raw-retention` atual
  vira o fallback de segurança (dropa > N dias **só se já arquivado**, ou mantém como circuit breaker).
- **Restore documentado** no README do job: baixar parquet → `COPY` de volta na partição (ou consultar
  direto via DuckDB sem restaurar).

## Decisões
- **Quente = 7 dias** (consolidador/debug não olham mais que isso; ajustável por env).
- **Parquet/zstd, particionado por dia, agrupado por mês no path** (o "de mês em mês" vira organização
  de bucket; arquivar diariamente a partição D-7 é mais suave que um job mensal gigante).
- **Mesmo padrão pro `medido` depois** (decisão local do CONS-01): `viagem_hex`/`viagem_track` em
  partições mensais, quente ~90d, arquiva no MinIO; `medido.viagem` (1 linha/viagem) fica pra sempre.
- **Alívio imediato sem código:** baixar `OOH_RT_RAW_RETENTION_DAYS` 30 → 14 no compose enquanto este
  card não roda (perde replay > 14d — aceitável até o arquivamento existir).
- Lifecycle do bucket (expirar frio > 12-24 meses): decisão do dono, fora deste card.

## Critério de pronto (verificável)
- Partições > 7d arquivadas no MinIO (counts batem), dropadas do Postgres; disco do `raw` estabiliza
  em ~10 GB; restore testado (1 partição: download → COPY → counts batem).

## Produz
- docs/epicos/runs/INFRA-03-arquivamento-raw-minio.md
