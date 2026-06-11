# Handoff F2 — espelho legível

> Gerado/atualizado pelo workflow `f2-orchestration` a cada parte. Fonte de verdade: `state.json`.
> Última atualização: lane **e2e** em 2026-06-11T08:30:02Z.

## Gates

| Gate | Status | Resumo |
|---|---|---|
| raw-history | 🟡 YELLOW | Só 1 dia de histórico raw (2026-06-10, ~12h, 1,88M posições); poller acumulando — vira GREEN em ~1 dia |
| poller-live | 🟢 GREEN | Poller de prod streaming: 143 ciclos/h (~1 a cada 25s), 105.940 linhas na última hora, feed fresco |
| medido-volume | 🔴 RED | `medido.viagem` não existe em prod — esperado antes do ship do consolidador; re-verificar pós-deploy |
| repos | 🟡 YELLOW | Estado esperado pré-F2: todos os repos presentes; `uai-ooh-trip-consolidator` ausente (CONS-01 cria) |
| local-env | 🔴 RED | Compose local existe mas postgres-ooh nunca subiu (só kafka up); psql ausente no host — esperado antes do LOCAL-01 |
| f1-intel | 🟢 GREEN | EP2-03=done, EP2-07=partial (auth stub), EP2-08=done — lane rt liberada |
| f1-portal | 🟢 GREEN | Shell + módulo + mapa do F1 done (EP3-01; EP4-01/03/04) — lane web NÃO precisa ficar skip |
| prereqs-core | ⚪ UNKNOWN | Agente não retornou — re-verificar |

## Lanes

| Lane | Status | Tasks |
|---|---|---|
| jobs | ✅ complete | INFRA-03=done · RECAL-00=done |
| cons | ✅ complete | CONS-01=done · CONS-02=done · CONS-03=done · CONS-04=done · CONS-05=done |
| local | ✅ complete | LOCAL-01=done · LOCAL-02=done · env: pg `:55432` · kafka `:19092` · redis `:16379` |
| e2e | ❌ failed | RECAL-01=failed (recompute local falhou) · RECAL-02 (manuais) |
| rt | — | RT-01 · RT-03 · RT-02 |
| web | — | WEB-00..02 |

## Contrato Redis (CONS-05 — consolidador é o ÚNICO escritor; RT-01 só lê)

`live:vehicle:{code}` HASH(lat,lon,bearing,lineId,tripId,currentStopSequence,completudeParcial,impressoesParciais,alcanceParcial,hexesVisitados,ts) · `live:line:{lineId}` SET · `live:reach:trip|day:{code}` HLL · `hll:hex:{h3}:{tipoDia}:{faixa}` HLL (read-only). TTL `live:vehicle` 5min (`ooh.consolidator.redis.state-ttl-s`, default 300s).

## Run docs

- `docs/epicos/runs/INFRA-03-arquivamento-raw-minio.md`
- `docs/epicos/runs/RECAL-00-hll-audiencia-hex.md`
- `docs/epicos/runs/CONS-01-scaffold.md`
- `docs/epicos/runs/CONS-02-consumo-estado.md`
- `docs/epicos/runs/CONS-05-acumulador-ao-vivo.md`
- `docs/epicos/runs/CONS-03-reconstrucao-viagem.md`
- `docs/epicos/runs/CONS-04-fechamento-medido.md`
- `docs/epicos/runs/LOCAL-01-ambiente-local.md`
- `docs/epicos/runs/LOCAL-02-replayer-feed.md`

**e2e validated:** ❌ (lane e2e **failed** — RECAL-01 falhou no recompute local; ship bloqueado sem e2e=validated) · **Pendências:** RECAL-01=failed (recompute local falhou). Ambiente local: pg `postgresql://ooh:ooh@localhost:55432/ooh` · kafka `localhost:19092` · redis `localhost:16379`.
