# Handoff F2 — espelho legível

> Gerado/atualizado pelo workflow `f2-orchestration` a cada parte. Fonte de verdade: `state.json`.
> Última atualização: lane **gate** em 2026-06-11T01:05:05Z.

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
| jobs | — | INFRA-03 · RECAL-00 |
| cons | — | CONS-01..05 |
| local | — | LOCAL-01..02 |
| e2e | — | RECAL-01 · RECAL-02 (manuais) |
| rt | — | RT-01 · RT-03 · RT-02 |
| web | — | WEB-00..02 |

**e2e validated:** ❌ (ship bloqueado) · **Pendências:** nenhuma registrada ainda.
