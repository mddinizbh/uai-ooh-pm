# INFRA-03 — tiering do `raw` RT: arquivar no MinIO antes de dropar (run, 2026-06-10)

> F2 (Bloco 2) · lane **infra** · **Status: DONE** · Card: `docs/epicos/bloco2/f2/01-infra/INFRA-03-arquivamento-raw-minio.md`
> **Repo-alvo:** `uai-ooh-pipeline` (job) — cron/env no `uai-infra` fica pendente (fora deste run).
> Evolui o `rt-raw-retention` (que só **dropava**) para **arquivar → verificar → dropar**.

## Cabeçalho

| Campo | Valor |
|-------|-------|
| Repo | `uai-ooh-pipeline` (`/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pipeline`) |
| Branch | `feat/ooh-infra-03` |
| Stack | Python + DuckDB (parquet/zstd) + MinIO (S3) |
| Compilação | `py_compile` OK em todos os `*.py` versionados (`PY_COMPILE_OK`) |
| Pipeline | implement → review → test → validate → refute |

## O que foi entregue

- `ooh_pipeline/ingestor/rt_raw_archive.py` — job novo `rt-raw-archive` (roda-e-sai, como o `rt-raw-retention`).
  Por partição-filha `rt__vehicle_position_pYYYYMMDD` mais velha que `OOH_RT_RAW_HOT_DAYS` (default **7**):
  exporta a partição via **DuckDB** (`ATTACH postgres` → `COPY ... TO '<dest>/.../pYYYYMMDD.parquet' (FORMAT PARQUET, COMPRESSION ZSTD)`),
  **verifica** `count(parquet) == count(partição)` e só então **DROP**. Idempotente: objeto já existe + count bate ⇒ pula export, vai direto pro drop.
- `ooh_pipeline/ingestor/rt_raw_retention.py` — evoluído como **backstop**: só dropa partição > `OOH_RT_RAW_RETENTION_DAYS` **se já arquivada**; `OOH_RT_RAW_RETENTION_FORCE=1` = circuit breaker (dropa sem arquivo, perde replay). Mesmo path do archive.
- `ooh_pipeline/common/storage.py` — helper `s3_object_exists(uri)` (verificação de idempotência no destino S3).
- Wiring CLI completo em `ooh_pipeline/__main__.py` (`ingestor rt-raw-archive` / `rt-raw-retention`).
- Destino frio configurável: `OOH_RT_RAW_ARCHIVE_DEST` (default `s3://uai-ooh/rt-archive`) aceita **`s3://`** ou **diretório local** (validação sem MinIO). Path: `rt__vehicle_position/YYYY/MM/pYYYYMMDD.parquet`.
- `.env.example` — 4 envs novas documentadas (`OOH_RT_RAW_HOT_DAYS`, `OOH_RT_RAW_ARCHIVE_DEST`, `OOH_RT_RAW_RETENTION_DAYS`, `OOH_RT_RAW_RETENTION_FORCE`).
- `README.md` do job — **restore documentado** (DuckDB `read_parquet` para consulta in-place, ou `COPY raw.rt__vehicle_position FROM <parquet>` para restaurar a partição).
- `tests/test_rt_raw_infra03.py` — 22 testes (20 originais + 2 de regressão).

## Counts reais vs esperado

| Métrica | Esperado | Real | OK |
|---------|----------|------|----|
| `py_compile` (todos `*.py` versionados) | OK | `PY_COMPILE_OK` | ✅ |
| `test_rt_raw_infra03.py` (implement) | 22 | 22 passed (0.09s) | ✅ |
| Suíte completa (review/test, dentro do `.venv`) | sem regressão | 52 passed / 0 failed / 0 skipped | ✅ |
| Testes novos criados | 22 | 22 | ✅ |
| Envs novas no `.env.example` | 4 | 4 | ✅ |
| Helper `s3_object_exists` | presente | presente em `common/storage.py` | ✅ |
| CLI `rt-raw-archive` / `rt-raw-retention` | ambos | ambos registrados em `__main__.py` | ✅ |
| Restore documentado no README | sim | sim (DuckDB + `COPY`) | ✅ |
| **E2E pleno (arquivar 1 partição do raw LOCAL → count bate → drop)** | executado | **NÃO** — bloqueado por ambiente | ⚠️ |
| **Restore testado (download → COPY → counts batem)** | executado | **NÃO** — depende do E2E acima | ⚠️ |

## Decisões / desvios

- **Critério de pronto verificável do card é E2E** (arquivar partição real, count bater, drop; restore download→COPY→count). Esse caminho **não rodou**: o ambiente LOCAL do F2 (`LOCAL-01`) não está de pé — Postgres LOCAL na `55432`, Redis `16379` e Kafka `19092` todos **CLOSED** (connection refused). Os únicos containers up são o dev antigo (`uai-ooh-db:5432`, `uai-dev-kafka:9092`), que **não** é o LOCAL do F2. `state.json` do F2: gate `local-env=RED`, lane `local={}` vazia.
- **Fallback adotado** (previsto pelo próprio card e pela estratégia local-first do F2): validar dry-run + unidade + sem-regressão e **anotar pendência de validação plena**. O DONE deste run cobre **código implementado, testado em unidade e revisado**, não o E2E contra banco real.
- **Pendência registrada:** rodar o E2E pleno + restore quando `LOCAL-01` subir (gate local-env GREEN), antes do cron no `uai-infra`. Sem isso, o "disco do raw estabiliza em ~10 GB" do card permanece **não-verificado em campo**.
- **Cron/env no `uai-infra` não fazem parte deste run** (Repo-alvo secundário). O alívio imediato sem código (`OOH_RT_RAW_RETENTION_DAYS` 30→14 no compose) também segue como ação separada do dono.
- **Retry #2:** dos 3 checks do `validate`, o único `pass:false` dependente de código era o "modo teste pleno" — confirmado **bloqueado por ambiente, não por defeito**.

## Adversarial — o que o cético tentou

O `refute` **não derrubou** o DONE (`refuted: false`). O que tentou e por quê não derrubou:

- **"O código não arquiva de verdade / é stub?"** — Não. `rt_raw_archive.py` faz o ciclo completo arquiva→verifica→dropa, idempotente, DuckDB parquet/zstd, destino `s3://` **ou** dir local. Verificável por leitura e pelos 22 testes unitários.
- **"A retenção dropa às cegas e perde replay?"** — Não. O backstop só dropa **se já arquivado**; `FORCE` é circuit breaker explícito, mesmo path do archive. É uma rede de segurança, não um buraco.
- **"Faltou wiring / helper / docs?"** — Não. CLI `rt-raw-archive`+`rt-raw-retention` registrados, `s3_object_exists` presente, `.env.example` com as 4 envs, README com restore (DuckDB `read_parquet` + `COPY`).
- **Onde o cético **tem** razão (⚠️ honesto):** o **Critério de pronto VERIFICÁVEL do card é E2E** e o E2E **não rodou** — ambiente LOCAL ausente. Logo o DONE é de **implementação + testes unitários verdes + review aprovado**, com a **validação E2E/restore pendente** até `LOCAL-01` subir. Isso é desvio do critério literal do card, assumido conscientemente via fallback local-first; não é regressão nem defeito de código.

## Estado ao fechar este run

- INFRA-03 **implementado e revisado** no `uai-ooh-pipeline` (`feat/ooh-infra-03`); 52/52 na suíte, sem regressão.
- **Pendências (não bloqueiam o DONE de implementação, bloqueiam o "em produção"):**
  1. E2E pleno + restore contra o banco LOCAL quando `LOCAL-01` subir (gate local-env GREEN).
  2. Cron diário + envs no `uai-infra`.
  3. (Opcional, alívio imediato) baixar `OOH_RT_RAW_RETENTION_DAYS` 30→14 no compose até o archive estar em produção.
