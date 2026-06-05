# EP4-01 — front: scaffold do módulo /ooh (no shell)

> Bloco 1 (uAI-OOH F1) · **Épico EP4** (front) · card 1 do kanban.
> **Depende de:** EP3-01 (shell) + EP3-02 (auth, p/ o Bearer) · **Destrava:** EP4-02..07 (todos usam o intelClient + a ProposalPort) · **Paralelizável:** não (base do EP4)
> **Repo-alvo:** `uai-spark` (→ uai-portal) · **Stack:** React 18 · TS · Vite · @tanstack/react-query
> **Origem:** PRD `docs/prd/2026-06-05-ooh-intel-front-f1.md` §5/§7; consulta ao `uai-spark`.

## Objetivo
Criar a **estrutura do módulo OOH** dentro do shell + o **client do intel** + a **`ProposalPort`** (a costura anti-acoplamento). Base pronta pras telas (EP4-02+).

## Reusa do spark
- Shell/router/guard (EP3-01) · `shadcn/ui` · tema · **`@tanstack/react-query`** (já no projeto).

## Cria
```
src/modules/ooh/
 ├─ pages/            # PlanejamentoPage (skeleton)
 ├─ api/intelClient.ts   # hooks react-query: useLines, useLineMetrics, useRanking, useAggregate, useRegions, useLineGeo
 └─ proposal/ProposalPort.ts  # interface + adapter local (F1)
```
- **intelClient**: base URL via env (`VITE_INTEL_URL`) + **Bearer** do `AuthContext` (EP3-02). Aponta pro **intel direto** (não cms — revisão da regra BFF, PRD §7).
- **`ProposalPort`** (interface TS) + adapter local F1:
  ```ts
  interface ProposalPort { export(cesta: Cesta): Promise<void>; /* F1: PDF client-side; Bloco 3: save→cms */ }
  ```

## Decisão
- **Fetcher: `fetch` + react-query** (decidido — react-query já existe; fetch nativo no fetcher; zero dependência nova). *(Alternativa: axios — interceptors prontos, mas +1 dep, ganho pequeno.)*

## Critério de pronto
- Módulo `/ooh` renderiza uma página "Planejamento OOH" (skeleton) dentro do shell.
- Um hook do intel (ex.: `useLines`) busca e mostra o count (**303**), apontando pro **intel direto** com Bearer.
- `ProposalPort` definida (interface) + adapter local stub.

## Produz
- docs/epicos/runs/EP4-01-scaffold-modulo.md
