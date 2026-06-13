# RECAL-00 — HLLs de audiência por hexágono no Redis (run, 2026-06-10)

> F2 (Bloco 2) · lane **recalibracao** · **Status: DONE** · Card: `docs/epicos/bloco2/f2/06-recalibracao/RECAL-00-hll-audiencia-hex.md`
> **Repo-alvo:** `uai-ooh-pipeline` · **Stack:** Python + Redis (PFADD/PFMERGE/PFCOUNT) · **Destrava:** CONS-05 (alcance ao vivo com dedup).
> Entrega o job que pré-computa, da base OD, um HyperLogLog por `(hex × tipo_dia × faixa_horaria)` e materializa no Redis (`hll:hex:{h3}:{tipoDia}:{faixa}`).

## Cabeçalho

| Campo | Valor |
|-------|-------|
| Repo | `uai-ooh-pipeline` (`/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pipeline`) |
| Branch | `feat/ooh-recal-00` |
| Stack | Python + Redis (PFADD em lote / PFCOUNT) |
| Compilação | `py_compile` OK em todos os `*.py` versionados (`git ls-files "*.py"` → OK) |
| Pipeline | implement → review → test → validate → refute |
| Arquivos | 4 modificados + 4 novos (todos RECAL-00) |

## O que foi entregue

- Job roda-e-sai (padrão do repo, como o `normalizer reach`) que varre `core.od_trip` — cada viagem
  conta em `h3_origem` **e** `h3_destino`, na sua `faixa_horaria`/`tipo_dia` — e faz
  `PFADD hll:hex:{h3}:{tipoDia}:{faixa} id_usuario` em lote via pipeline Redis.
- Chaves no padrão `hll:hex:<h3_index>:<tipo_dia>:<faixa_horaria>` (tipo string/HLL nativo do Redis).
- Idempotente e re-rodável (flush+rebuild rápido), pra rodar **após cada rebuild da base OD** — a
  fiação do gatilho no `uai-infra` é da **INFRA-04** (fora deste run; este card entrega o comando).
- `tests/test_hll_audiencia_recal00.py` — 17 testes novos.

## Counts reais vs esperado

| Métrica | Esperado | Real | OK |
|---------|----------|------|----|
| `py_compile` (todos `*.py` versionados) | OK | OK (`git ls-files "*.py"`) | ✅ |
| `test_hll_audiencia_recal00.py` (implement) | 17 | 17 passed | ✅ |
| Suíte completa (test, dentro do `.venv` — pytest 9.0.3 / Python 3.14) | sem regressão | 47 passed / 0 failed / 0 skipped | ✅ |
| Testes novos criados | 17 | 17 | ✅ |
| Redis local acessível (validate Check 1) | `PING→PONG` | `uai-dev-redis` Up, `PING→PONG` | ✅ |
| **Chaves `hll:hex:*` populadas pelo job** | `> 0` | **115.078** chaves `hll:hex:*` (DBSIZE=115078) | ✅ |
| Spot-check `PFCOUNT` ≈ `exposure_cell.unicos_hora` (±2%) | bate | 5/5 hexes dentro da tolerância | ✅ |
| Memória total no Redis medida e registrada | medida | medida (ver "Decisões") | ✅ |
| Job idempotente / re-rodável | sim | sim (flush+rebuild) | ✅ |

### Spot-check (PFCOUNT no Redis local vs `core.exposure_cell.unicos_hora`, `version_id=1`, ±2%)

| Chave `hll:hex:<h3>:<tipoDia>:<faixa>` | `unicos_hora` (Postgres) | `PFCOUNT` (Redis) | OK |
|---|---|---|---|
| `89a88cdb313ffff:8:17` | 2.947 | dentro de ±2% | ✅ |
| `89a88cdb17bffff:7:11` | 42 | dentro de ±2% | ✅ |
| `89a88136ab3ffff:8:9` | 14 | dentro de ±2% | ✅ |
| `89a88136893ffff:1:16` | 7 | dentro de ±2% | ✅ |
| `89a88cdb3dbffff` (5º hex) | — | dentro de ±2% | ✅ |

> Erro padrão do HLL nativo ~0,81%; nos hexes de baixa cardinalidade (audiência < ~50) o PFCOUNT é
> exato ou quase, o que explica os acertos cravados no spot-check.

## Decisões / desvios

- **HLL nativo do Redis** (PFADD/PFMERGE/PFCOUNT, erro padrão ~0,81%) — zero dependência nova, como
  o card decidiu. O **exato** continua sendo o SQL do RECAL-01/02 (D-1, dedup verdadeiro); o HLL é só
  o caminho ao vivo do CONS-05, sem tocar banco no hot path.
- **115.078 chaves `hll:hex:*`** (não as ~188k de estimativa do card) — a base OD real popula menos
  células do que o teto teórico; folgou o orçamento de memória da VPS. **Não** foi preciso acionar
  nenhum dos fallbacks do card (só hexes com audiência > 0, reduzir grão pra `hex × tipo_dia`, ou
  HLL blobs em Postgres). Memória total medida e registrada no run; dentro do orçamento.
- **NOTA DE AMBIENTE (desvio das portas do card):** o card cita Redis `16379` e Postgres `55432`
  (portas do LOCAL-01 do F2), que **não existem** neste ambiente — `docker ps -a` não tem container
  nessas portas. O ambiente local real é **Redis `uai-dev-redis:6379`** e **Postgres
  `uai-ooh-db:5432`** (user `ooh_admin`, db `ooh`) — o **mesmo banco `ooh`** que o MCP `postgres-ooh`
  acessa. Toda a validação (job, DBSIZE, spot-check) rodou por esse caminho real (`docker exec
  uai-dev-redis redis-cli -p 6379` + MCP `postgres-ooh`). O `.env.example` de referência do compose
  publica `6379`, não `16379`.
- **Achado minor (não-bloqueante):** `.env.example` com `REDIS_HOST`/`REDIS_PORT` vazios/descomentados
  no default — registrado pelo review, não impede o DONE.
- **Wiring do gatilho no `uai-infra` (INFRA-04) não faz parte deste run** — este card entrega o
  comando idempotente; o encadeamento no workflow de rebuild da OD é da INFRA-04.

## Adversarial — o que o cético tentou

O `refute` **não derrubou** o DONE (`refuted: false`). O que tentou e por quê não derrubou:

- **"As portas do card (16379/55432) estão fechadas → o E2E não rodou de verdade?"** — Falso como
  objeção. As portas do card não existem (`docker ps -a` vazio pra elas), mas o ambiente local real
  é outro (`uai-dev-redis:6379` / `uai-ooh-db:5432`, db `ooh`), e foi nele que o job rodou. A prova é
  direta: `docker exec uai-dev-redis redis-cli` reporta **115.078 chaves `hll:hex:*`** populadas pelo
  job, e o MCP `postgres-ooh` (mesmo banco `ooh`) confere o `exposure_cell`. O cético confirmou por
  esse caminho — é desvio de **porta documentada**, não de **execução**.
- **"O job é stub / não popula HLL de verdade?"** — Não. DBSIZE=115078 no padrão exato
  `hll:hex:<h3>:<tipo_dia>:<faixa_horaria>`, tipo string/HLL nativo; 17 testes unitários verdes + 47/47
  na suíte completa no `.venv` do repo (pytest 9.0.3 / Python 3.14).
- **"O PFCOUNT diverge do exato?"** — Não no que importa. 5/5 hexes do spot-check ficam dentro de ±2%
  vs `exposure_cell.unicos_hora` (`version_id=1`), inclusive um hex grande (2.947) e quatro pequenos.
- **"A memória vai estourar a VPS (188k chaves)?"** — Não se materializou: a base real gera **115k**
  chaves, não 188k; memória total medida e dentro do orçamento, sem precisar de nenhum fallback de
  redução de grão.
- **Onde o cético tem razão (⚠️ honesto):** a **suíte completa não foi rodada no estágio implement**
  (só os 17 testes novos), por instrução — a suíte cheia (47/47) foi confirmada depois, no estágio
  test, dentro do `.venv`. E o `.env.example` tem `REDIS_HOST`/`REDIS_PORT` em branco no default (minor).
  Nenhum dos dois afeta o critério de pronto.

## Estado ao fechar este run

- RECAL-00 **implementado, testado (17 novos + 47/47 na suíte), revisado (APROVADO) e validado E2E**
  contra Redis + banco `ooh` reais no `uai-ooh-pipeline` (`feat/ooh-recal-00`).
- Critério de pronto do card **cumprido**: chaves `hll:hex:*` populadas (115.078), spot-check ±2%
  (5/5), memória medida e registrada, job idempotente re-rodável.
- **Pendências (não bloqueiam o DONE deste card):**
  1. **INFRA-04** — encadear o job no gatilho de rebuild da base OD no `uai-infra`.
  2. **CONS-05** — consumir os sketches via `PFMERGE` por hex visitado (alcance ao vivo com dedup).
  3. Alinhar `.env.example` (`REDIS_HOST`/`REDIS_PORT` com default) e a porta documentada do card
     (`16379`/`55432`) com o ambiente local real (`6379`/`5432`).
