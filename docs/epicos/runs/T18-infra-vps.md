# Run — T18 (Infra: ooh-postgis no VPS) — diagnóstico do gate · 2026-06-05

> Diagnóstico **read-only** via MCP Hostinger (VPS) + MCP `postgres-ooh`. Cobre o **GATE bloqueante**
> do T18 (go/no-go: alpine unhealthy + baseline RAM/disco). Parcial — o T18 completo (backup +
> paridade 4107 formal) segue pendente.

## Veredito do gate: 🟢 GO

### Sizing / baseline — KVM4 (VM 1446714, Ubuntu 24.04)
| Recurso | Total | Uso | Pico (8d) | Folga |
|---|---|---|---|---|
| RAM | 16 GiB | ~2,7 GiB (17%) | ~3,8 GiB (24%) | ampla |
| Disco | 200 GiB | ~17 GiB (9%) | ~19 GiB | ampla |
| CPU | 4 vCPU | ~14% médio | 100% pontual (= normalizer) | ampla |

### `ooh-postgis` — já provisionado ✅
Container `uai-postgres-ooh` (`postgis/postgis:16-3.4`) **Up (healthy)** em `127.0.0.1:5433`, init OK,
e o `uai-ooh-pipeline` rodou containerizado no VPS. O núcleo do T18 já foi executado (sessão
`infra-ooh-ativacao-pipeline`).

### Alpine `uai-postgres` "unhealthy" — diagnosticado e RESOLVIDO
- **Causa raiz:** o healthcheck usava `pg_isready -U $${PG_USER}` — mas o container só tem
  `POSTGRES_USER` no environment (não `PG_USER`). A variável expandia vazio → conexão como `root` →
  `FATAL: role "root" does not exist` → healthcheck reprovava a cada 30s. **O banco sempre esteve são**
  (ready to accept connections; servindo `uai_cms`/`uai_tokenmetrics`/`uai_buslines`).
- **Prova:** o `ooh-postgis`, no mesmo compose, usa `$${POSTGRES_USER}` (variável que existe) e fica healthy.
- **Fix:** `uai-infra/docker-compose.yml` — `PG_USER` → `POSTGRES_USER` no healthcheck do postgres.
  Subido pelo dono em 2026-06-05; deploy aplicado → `uai-postgres` agora **`Up (healthy)`** (confirmado via MCP).

## Pendências do T18 (não-gate)
- Backup `pg_dump -Fc` por schema (core+serving separado do raw).
- Paridade 4107 formal contra o VPS documentada.
- `ooh-intel` ainda NÃO toca o nginx (correto nesta fase).
