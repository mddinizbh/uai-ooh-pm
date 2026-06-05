# 02 · intel — backend — 🟦 BACK

> Repo: **`uai-ooh-intel`** (Java 21 / Spring Boot) · API **read-only** sobre o `serving` (sem PostGIS/`ST_*`,
> ADR-003; sem tenant, ADR-004). Gerado do `uai-ooh-service-template`. Ordem por dependência.

| # | Task | Entrega | Decisão-chave |
|---|---|---|---|
| 1 | [EP2-01](EP2-01-scaffold.md) | scaffold + conexão ao serving (version ACTIVE) | regerar do template · dev local |
| 2 | [EP2-02](EP2-02-dominio-portas.md) | domínio + portas (records, sealed/enum, ScoreBreakdown) | **enum** p/ rótulos · IDs String |
| 3 | [EP2-03](EP2-03-catalogo-ficha.md) | `/api/lines` · `/{id}` · `/{id}/metrics` | **JdbcTemplate** |
| 4 | [EP2-04](EP2-04-ranking.md) | `/ranking` (pesos + público-alvo) | público **substitui** s_perfil · BH-relativo |
| 5 | [EP2-05](EP2-05-agregacao.md) | `POST /aggregate` (combinado da cesta) | **OTS** somado, não soma pop |
| 6 | [EP2-06](EP2-06-regioes-filtros.md) | `/regions` + filtros regiao/bairro | ⚠️ **gate EP1** · BH-relativo |
| 7 | [EP2-07](EP2-07-auth.md) | auth (introspection uai-auth) | **introspection** · sem tenant |
| 8 | [EP2-08](EP2-08-camadas-geojson.md) | `/{id}/geo` (camadas GeoJSON do corredor) | lê o que **EP1-02** materializa |
| 9 | [EP2-09](EP2-09-testes-deploy.md) | ITs (Testcontainers) + deploy GHCR/uai-infra | **sem tocar o nginx** (closer) |

**Reconcilia:** o antigo **T19** (domínio+endpoints) e **T20a** (ITs) viraram EP2-02..04/09.
