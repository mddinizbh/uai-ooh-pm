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
| e2e | ✅ validated | RECAL-01=done (recompute --fonte=medida rodou end-to-end no ooh vivo; medida coexiste c/ estimada; gates verdes) · RECAL-02 (manuais) |
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
- `docs/epicos/runs/F2-e2e-hardening-2026-06-11.md`
- `docs/epicos/runs/INFRA-04-automacao-f2.md`

## INFRA-04 (operacionalização) — parte 1 ✅ (2026-06-11)
Consolidador **validado AO VIVO** (local, banco vivo): boot limpo, `vRealSource=core.pattern_stop`,
1.243 `live:vehicle:*` (contrato CONS-05), `medido` criado+gravando. Roda via `docker run --restart
unless-stopped` contra a infra existente (sem cirurgia de volume — pivot do dono). Serviço
`uai-ooh-trip-consolidator` adicionado ao `docker-compose.yml` do uai-infra (+40 linhas, **não
commitado**). **Parte 2 pendente:** crons em `run-pipeline.yml` (archive/retention prontos; recompute
bloqueado por RECAL-01) + ship (merge consolidador → imagem GHCR → commit compose → Actions).

**e2e validated:** ✅ (RECAL-01 resolvido 2026-06-11 tarde — recompute `--fonte=medida` rodou end-to-end no `ooh` vivo; `core.line_reach` medida=13.437, `face_reach` medida=1.773, `pattern_stop_exposure`=4.898; gates da estimada exit=0). **Pendências derivadas:** (1) **bug v_real CORRIGIDO+verificado** (Δfração-snap → distância GPS; mediana 450→26,5 km/h; run `FIX-vreal-velocidade-consolidador.md`); (2) **commitar** os fixes (`line_reach.py` no pipeline + `PostgisTripMetricsCalculator` no consolidador, ambos uncommitted); (3) higiene: re-agregar v_real da RECAL-01 com dado limpo; (4) INFRA-04 p2 crons (desbloqueado) + ship. Ambiente local: consolidador rodando via `docker run`; DB vivo `uai-ooh-db:5432`.
