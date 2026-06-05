# 03 · front — 🟪 FRONT

> Repo: **`uai-spark`** → promovido a **`uai-portal`** (React 18 · TS · Vite · Tailwind · shadcn/ui · react-query · recharts).
> Base = bootstrap Lovable (shell + login mock + UI kit já existem). `shell/` = plataforma (EP3); `modulo-ooh/` = o produto OOH (EP4).
> **Reconcilia:** o antigo **T20b** (uai-ooh-web SPA) virou este módulo no shell.

## `shell/` — EP3 · plataforma
| # | Task | Entrega | Decisão |
|---|---|---|---|
| 1 | [EP3-01](shell/EP3-01-shell-nav.md) | shell + nav de verticais (adapta o spark) | **spark → uai-portal** |
| 2 | [EP3-02](shell/EP3-02-login-sso.md) | login SSO (troca o auth mock) | ⚠️ **gate uai-auth** · SSO redirect |
| 3 | [EP3-03](shell/EP3-03-deploy-shell.md) | deploy do shell (GHCR + uai-infra) | reusa Dockerfile/nginx do spark |

## `modulo-ooh/` — EP4 · produto OOH
| # | Task | Entrega | Decisão |
|---|---|---|---|
| 1 | [EP4-01](modulo-ooh/EP4-01-scaffold-modulo.md) | módulo /ooh + intelClient + ProposalPort | **fetch + react-query** · intel direto |
| 2 | [EP4-02](modulo-ooh/EP4-02-lista-filtros.md) | lista + filtros (ranking) + ＋cesta na linha | lista usa /ranking · pesos sliders |
| 3 | [EP4-03](modulo-ooh/EP4-03-ficha.md) | ficha master-detail (lista slim + ficha ~75%) | /metrics na hora, /geo lazy |
| 4 | [EP4-04](modulo-ooh/EP4-04-mapa-interativo.md) | mapa (reusa legado) + toggles | ← EP2-08 (/geo) |
| 5 | [EP4-05](modulo-ooh/EP4-05-charts.md) | charts interativos (recharts) | sub-scores **radar** |
| 6 | [EP4-06](modulo-ooh/EP4-06-cesta.md) | cesta + combinado | barra (combinado sempre visível) + drawer |
| 7 | [EP4-07](modulo-ooh/EP4-07-export-pdf.md) | export PDF (ProposalPort) | print-mode (→ agent+template pós-F1) |
| 8 | [EP4-08](modulo-ooh/EP4-08-honestidade-ui.md) | honestidade na UI (cross-cutting) | badges inline + modal metodologia |
| 9 | [EP4-09](modulo-ooh/EP4-09-mapa-cesta.md) | mapa da cesta (alcance combinado) + snapshot pro export | trajetos + **corredores com toggle** |

> O deploy do front **não** é card próprio — sobe junto com o shell (EP3-03), é o mesmo app.
