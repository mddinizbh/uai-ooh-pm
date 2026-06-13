# INFRA-04 — automação do F2 no `uai-infra` (run, 2026-06-11)

> Operacionalização do consolidador F2. **Repo-alvo:** `uai-infra`. Estratégia ajustada em execução:
> a "cirurgia completa de compose" foi **substituída por validação leve local + serviço committável
> pro VPS**, por decisão do dono ("é local, não precisamos sofrer; o que importa é tá certo pra subir
> na VPS"). O consolidador foi **validado AO VIVO** contra o feed real do poller.

## Decisão de rota (pivot)
- **Plano inicial:** cirurgia completa — reapontar `postgres-ooh` do compose pro volume vivo
  (`banco_uai_ooh_pgdata`, 12GB), matar standalone DB+poller, subir tudo sob um compose.
- **Pivot do dono:** alto risco desnecessário numa máquina local. O valor é (a) **provar o motor ao
  vivo** e (b) deixar o **compose certo pro VPS** (onde o polling roda 24/7 em produção).
- **Rota executada:** consolidador via `docker run` leve contra a infra existente (zero parada, zero
  remap) + serviço `uai-ooh-trip-consolidator` adicionado ao `docker-compose.yml` (committável, VPS).

## O 12,3GB do banco `ooh` (pergunta do dono)
Banco = **12GB**. O feed RT ao vivo é a **menor** parte:
| Schema | Tamanho | Destaque |
|---|---|---|
| core | 11GB | `vehicle_trajectory` 6GB/26,7M linhas + `od_trip` 684MB |
| raw | 3,7GB | `gtfs__stop_times` 923MB, `pbh__matriz_od` 864MB — **RT vivo só ~845MB** |
| serving | 2,4GB | `line_poi` 1,8GB (POI/linha, nichos) |

RT do poller é **particionado por dia** (`raw.rt__vehicle_position_p2026MMDD`, ~426MB/dia, ~1,6M
linhas/dia). Polling 24/7 **não infla** o banco — adiciona ~426MB/dia que o **archive (INFRA-03) +
retention** podam. O peso é a base estática (trajectory/GTFS/OD/POI).

## Validação AO VIVO do consolidador (local, banco vivo)
`docker run uai-ooh-trip-consolidator:local` na rede `uai-infra_uai-dev`:
`KAFKA_BOOTSTRAP=kafka:29092` · `REDIS_HOST=uai-dev-redis` (mantém os 115k HLLs do RECAL-00) ·
`DB_HOST=host.docker.internal:5432` (standalone `uai-ooh-db`, `ooh_admin`). `--restart unless-stopped`.

- **Boot limpo (7,8s, 0 erros):** Flyway criou schema `medido` (v1) · Kafka subscrito em
  `ooh.rt.position`, offset reset p/ **latest** · `snap calculator ready: vRealSource=core.pattern_stop`.
- **Estado ao vivo (Redis):** **1.243 `live:vehicle:*`** (contrato CONS-05 completo — lat/lon, lineId,
  tripId, currentStopSequence, completude, impressões, alcance, hexes, trackPointCount) + `live:track:*`,
  `live:reach:trip|day:*`, 273 `live:line:*`.
- **`medido` gravando:** schema `medido` criado (viagem, viagem_hex, viagem_track, parada_velocidade).
  Primeiras viagens fecharam por `SEQUENCE_RESET`/`TRIP_ID_CHANGED` — **esperado num start a frio em
  stream latest**: o consolidador pega veículos no meio da viagem e fecha os fragmentos parciais
  (km/completude baixos). Viagens reais completas (km alto, completude ~0,9, v_real) precisam de
  ~dezenas de min observando do começo ao fim — e a matemática já está **replay-provada** (run
  `F2-e2e-hardening`: 40806 km=29,45 completude=0,915). O consolidador segue rodando e acumulando.

## Entregas
- **`docker-compose.yml`** (committável, **ainda não commitado**): serviço `uai-ooh-trip-consolidator`
  — imagem GHCR, `depends_on` postgres-ooh(healthy)+redis(healthy)+kafka-init, env (kafka:9092,
  redis:6379, DB postgres-ooh/ooh_admin via `.env`), `JAVA_TOOL_OPTIONS=-Xmx384m`, healthcheck
  actuator, mem 512M, `restart: unless-stopped`. Diff = +40 linhas, só o serviço.
- Imagem `uai-ooh-trip-consolidator:local` buildada do Dockerfile (multi-stage maven→jre, 396MB).

## Pendências (parte 2 — crons + ship)
1. **Crons no `.github/workflows/run-pipeline.yml`** (mecanismo = Actions+SSH, não crontab):
   - `ingestor rt-raw-archive` (INFRA-03) — diário `0 4 * * *` ✅ pronto
   - `ingestor rt-raw-retention` — diário (fallback do archive) ✅ pronto
   - `normalizer reach --fonte=medida` (RECAL-01 recompute) — diário `30 4 * * *` ⛔ **bloqueado por
     RECAL-01** (falta coluna `metodo` no serving + executar o recompute)
   - `recal hll-audiencia-hex` (RECAL-00) — passo pós-rebuild da OD (não periódico)
2. **Ship do consolidador pro VPS:** merge `feat/ooh-f2-consolidator` → CI publica imagem GHCR →
   commit do `docker-compose.yml` → Actions deploya. (compose config já pronto; falta a imagem.)
3. **Hardening do consolidador** (não bloqueia operação latest): clamp de velocidade (outliers
   95–211 km/h → ~70) + timeout sweep replay-safe (bug #6, usar `feedTs`).

## Estado do ambiente local ao fim
- `uai-ooh-trip-consolidator` rodando (`docker run`, `--restart unless-stopped`) consumindo o feed vivo.
- Standalone `uai-ooh-db` + poller + `uai-dev-kafka`/`uai-dev-redis` **intactos** (nada parado/remapeado).
- `medido` agora **existe e cresce** no banco vivo (reversível: `DROP SCHEMA medido CASCADE`).
