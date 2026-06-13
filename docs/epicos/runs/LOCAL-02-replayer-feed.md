# LOCAL-02 — replayer do feed (raw local → Kafka local, acelerado) — run (2026-06-11)

> F2 (Bloco 2) · lane **07-local** · segunda task da lane (consome o ambiente do LOCAL-01).
> Card: `docs/epicos/bloco2/f2/07-local/LOCAL-02-replayer-feed.md` · techspec do poller (reusa
> decode/publisher/config). **Status final: DONE.**
>
> Objetivo do card: subcomando **replay** no poller que lê `raw.rt__vehicle_position` do Postgres
> **local** (seedado pelo LOCAL-01), reagrupa por `_feed_timestamp` (1 grupo = 1 ciclo) e re-publica
> os ciclos no Kafka **local** na ordem original, com cadência acelerada — simulação
> **determinística**: o mesmo raw produz sempre o mesmo stream. **Destrava** a parte `e2e` do F2
> (alimenta o consolidador local com dias de operação em minutos).

## Cabeçalho do run

| Campo | Valor |
|-------|-------|
| Repo-alvo | `uai-ooh-realtime-poller` |
| repoPath | `/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-realtime-poller` |
| Branch | `feat/ooh-local-02` |
| Base | poller já em prod (POLL-01..03, F2 `F2-infra-poller-golive.md`) |
| Stack | Python (reusa clients do poller: `psycopg`, `confluent_kafka`) |
| Depende de | LOCAL-01 ✅ (ambiente local: Postgres `ooh` seedado + Kafka local, portas 55432 / 19092) |
| Pipeline | implement → review → test → validate → refute |

## Counts reais vs. esperado

| Item | Esperado (card/techspec) | Real | OK |
|------|--------------------------|------|----|
| Subcomando `python -m poller replay` | subparser com `--speed` / `--from` / `--to` / `--lines` | `replay` via subparsers em `poller/__main__.py`; `--help` e execução real verificados | ✅ |
| Lê `raw.rt__vehicle_position` do Postgres **local** e reagrupa por `_feed_timestamp` | 1 grupo = 1 ciclo, ordem original | `RawReader.cycles()` (`poller/replay.py`) via **server-side cursor** (`name='replay_cycles'`, `itersize=10000`), `ORDER BY _feed_timestamp ASC, vehicle_id ASC`; agrupa linhas adjacentes pelo epoch de `_feed_timestamp` | ✅ |
| Reusa contratos do publisher (não reimplementa) | `key=vehicle_id`, mesmo formato do publisher normal | `VehiclePosition` (tem `current_status`), `KafkaPositionPublisher(bootstrap, topic)` + `.close()`, `PositionPublisher.publish` retorna `int` — todos reusados, sem reimplementação | ✅ |
| **Não** landa de volta no raw (replay = só publish) | raw local já tem o dado | replay apenas publica; sem `COPY`/insert no `raw` | ✅ |
| LOTES == CICLOS (validação 1h, intervalo `2026-06-10 14:00–15:00 UTC`) | `count(DISTINCT _feed_timestamp)` no intervalo == nº de lotes publicados | **136** ciclos distintos no Postgres local == **136** ciclos publicados pelo replay | ✅ |
| LINHAS == MENSAGENS | nº de linhas do intervalo == nº de mensagens no tópico | **156.748** linhas == **156.748** mensagens (offsets das 3 partições do tópico de validação: 54.178 + 49.601 + 52.969) | ✅ |
| Ordem por veículo preservada (offsets por partição monotônicos no tempo do feed) | `feed_timestamp` crescente em cada partição | **0** violações de monotonicidade por partição (consumindo as 156.748 msgs) | ✅ |
| Determinismo (key → partição estável) | mesmo `vehicle_id` sempre na mesma partição | **0** violações de key→partição; replay repetido produz o mesmo stream | ✅ |
| `--speed` | `60` = real/60 (1 dia ≈ 24 min); `0` = sem pausa | implementado; validação rodada com `--speed 0` | ✅ |
| `python -m pytest -q` (suíte) | verde | **78 testes · 76 passed · 0 failed · 2 skipped** (`.venv` do repo) · **18** testes novos (esperado 18) · 1 IT marcado `integration` · lint (ruff) limpo | ✅ |

> **Nota sobre o ambiente de teste.** O `python -m pytest -q` *do system Python* (Homebrew 3.14)
> falhou na coleta (8 erros: sem `confluent_kafka`/`psycopg`). O comando válido é o do repo
> (`.venv/bin/python -m pytest -q`), que roda 78/76✅/0/2 — não é falha do código, é Python errado no
> PATH. Registrado como desvio, não como bloqueio.

## Decisões / desvios

- **Subcomando, não repo novo** (decisão do card): replay mora no `uai-ooh-realtime-poller` e
  **reusa** decode/publisher/config do poller. Nenhum contrato foi reimplementado — `VehiclePosition`,
  `KafkaPositionPublisher(bootstrap, topic)`/`.close()` e `PositionPublisher.publish→int` são os
  mesmos do streaming live.
- **Leitura por server-side cursor** (`name='replay_cycles'`, `itersize=10000`): o intervalo de 1h
  já são ~156k linhas; cursor nomeado evita materializar tudo em memória. `ORDER BY _feed_timestamp
  ASC, vehicle_id ASC` dá a ordem canônica do ciclo e o desempate estável que garante o determinismo.
- **Agrupamento por epoch de `_feed_timestamp`**: linhas adjacentes com o mesmo `_feed_timestamp`
  formam 1 ciclo (= 1 fetch do feed). 136 `_feed_timestamp` distintos no intervalo → 136 lotes.
- **Replay é só publish, não landa no raw** (card): o raw local já está seedado pelo LOCAL-01;
  re-landar duplicaria dado e quebraria a idempotência por `_feed_timestamp`.
- **⚠️ `python -m pytest` do system Python não serve** (Homebrew 3.14 sem `confluent_kafka`/`psycopg`).
  Usar sempre o `.venv` do repo. Desvio ambiental, sem impacto no código.
- **Esquema real do raw**: todas as colunas de dados são `text` (incl. lat/lon/`current_status`);
  o replay respeita isso — reconstrói `VehiclePosition` a partir do `text`, sem assumir tipos
  numéricos no banco. O `_feed_timestamp` é a única coluna de tempo usada pro agrupamento.
- **Pendência a anotar no CONS-03** (já no card): o `TRIP_TIMEOUT_S` do consolidador (timeout de
  viagem, 10min reais) precisa ser configurável por env pra acompanhar o `--speed` do replay no E2E.

## Adversarial (o cético — refute)

O refute **não derrubou**. `refuted: false`. Vetor que ele tentou:

1. **"Critério de pronto não batido — re-verifica cada item do card contra o Postgres local (55432)
   e o Kafka local (19092)."**
   Re-confrontou contagem, ordem e determinismo num tópico **efêmero de 3 partições**, com `--speed 0`,
   no intervalo `2026-06-10 14:00–15:00 UTC`:
   - **Postgres local:** `count(DISTINCT _feed_timestamp)` = **136**; `count(*)` = **156.748** no
     intervalo.
   - **Replay:** reportou exatamente **136 ciclos / 156.748 mensagens** publicados.
   - **Kafka local:** offsets das 3 partições do tópico de validação somam **156.748**
     (54.178 + 49.601 + 52.969) — bate 1:1 com as linhas.
   - **Ordem:** consumindo as 156.748 mensagens, **0** violações de monotonicidade de `feed_timestamp`
     por partição.
   - **Determinismo:** **0** violações de key→partição (mesmo `vehicle_id` sempre na mesma partição);
     replay repetido reproduz o mesmo stream.
   → **sobreviveu.** LOTES == CICLOS e LINHAS == MENSAGENS, confirmados no banco e no broker reais.

Verificação independente (review): veredito **APROVADO** — "LOCAL-02 (replayer raw local → Kafka
local) está completo, conforme à task e à techspec do poller, com reuso correto dos contratos
existentes, testes passando (17 unit + 1 IT marcado `integration`) e lint limpo". O review inspecionou
o diff real, confirmou os contratos reusados (`VehiclePosition.current_status`; `PositionPublisher.publish→int`;
`KafkaPositionPublisher(bootstrap, topic)` + `.close()`) e o esquema real do raw (colunas de dados
em `text`). `validate.ok = true` (sem checks pendentes).

## Estado ao fechar este run

- Subcomando `replay` na branch `feat/ooh-local-02` do `uai-ooh-realtime-poller`: lê o raw **local**,
  reagrupa por `_feed_timestamp` e re-publica os ciclos no Kafka **local**, determinístico. Suíte
  verde (78/76✅/0/2 no `.venv`), 18 testes novos, lint limpo.
- **Não deployado / não em prod** — modo local-first; aponta pro Postgres/Kafka do LOCAL-01
  (55432 / 19092). Sem efeito no banco `ooh` ou no Kafka de produção.
- **Validação real**: 1h de raw (`2026-06-10 14:00–15:00 UTC`) → **136 lotes == 136 ciclos** e
  **156.748 mensagens == 156.748 linhas**; ordem e determinismo confirmados no broker.
- **Pendência aberta**: usar o `.venv` do repo pro pytest (system Python Homebrew 3.14 sem
  `confluent_kafka`/`psycopg`); anotar `TRIP_TIMEOUT_S` configurável no CONS-03.
- **Destrava:** parte **`e2e`** do F2 — o replay alimenta o consolidador local com dias de operação
  em minutos, de forma reproduzível.
