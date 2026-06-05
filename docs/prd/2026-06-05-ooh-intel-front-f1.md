# PRD — uAI-OOH F1 · `intel` + módulo OOH no shell uAI

> **Status:** aprovado (brainstorming 2026-06-05) · **Escopo:** F1 (inteligência de planejamento OOH).
> **Produto:** ferramenta interna de planejamento de mídia OOH (busdoor em ônibus de BH), 1º vertical
> da plataforma uAI. **Define** o front (módulo OOH dentro do shell uAI) + o contrato do `intel` +
> a fronteira de desacoplamento com o comercial. **Revisa** o épico-4 (`epico-4-serving-app.md` 4.5) e
> `arquitetura-servicos.md`. Mantém ADR-003 (sem PostGIS no serving), ADR-004 (referência sem tenant),
> ADR-052 (fronteira dados↔comercial). Honestidade: `proxies-e-premissas.md`.
> **Não cobre** o backend comercial (cms/tenant/campanhas) — Bloco 3.

---

## 1. Contexto & problema

O Épico 3 entregou `core.line_metrics` (303 linhas com score 0–100 + 5 sub-scores + impressões
estimadas + perfil). O material atual do front (épico-4, Tarefa 4.5 "opcional") era raso e assumia o
**cutover do mapa civic `linhas.uaiagencia.com.br`**. Esta rodada de PRD redefine o produto do front e
amarra o contrato do `intel` à UX.

**Duas viradas desta rodada:**
1. O **mapa civic `linhas.uaiagencia.com.br` sai de cena** (legado desligado, sem cutover civic). O
   front F1 é uma **ferramenta interna** de planejamento, não um produto civic público.
2. **Login e shell são da plataforma uAI** (não do OOH). O front OOH nasce como **módulo dentro do
   shell uAI** (SSO `uai-auth`), não como app standalone — pra não jogar fora o login/shell quando o
   comercial chegar. O **backend comercial fica no Bloco 3**.

## 2. Usuário & posicionamento

- **Usuário primário:** planejador/vendedor da uAI montando proposta de mídia OOH para um anunciante.
- **Não é** ferramenta civic/pública nem self-service do cliente (no F1).
- **Job to be done:** *explorar* o inventário de linhas → *montar uma cesta* de linhas → *exportar*
  uma proposta para o cliente.

## 3. Escopo F1

**Dentro:**
- Login SSO (`uai-auth`) + shell uAI com nav de verticais (OOH como 1º item).
- Módulo OOH: lista/ranking de linhas, ficha por linha, cesta, agregação da cesta, export PDF.
- `intel`: endpoints de catálogo/ficha/ranking + **novo** endpoint de agregação, lendo `serving`.

**Fora (diferido):**
- Backend comercial (cms multi-vertical), tenant/RLS, persistência de proposta/campanha, tela "todas
  as campanhas de todos os tipos", cobrança → **Bloco 3**.
- Realtime/verificado (posições, viagens executadas, alcance verificado) → **Bloco 2**.
- Geração de proposta por agent/template → **pós-F1** (ver §10).

## 4. Workflow

```
Explorar (lista + filtros + ficha) → Montar cesta (N linhas) → Exportar proposta (PDF)
```

- A **cesta** é também a superfície de comparação: mostra per-linha + uma linha **COMBINADO**.
- Sem etapa de brief estruturado no F1 — os **filtros** (público-alvo, região, pesos) cumprem o papel.

## 5. Front — módulo OOH no shell uAI

### 5.1 Shell & login
- **Login SSO `uai-auth`** (login único da plataforma; **não** um login do OOH). Reaproveita
  `uai-portal`/shell e `uai-auth`. **⚠️ Gate/dependência:** confirmar estado real de `uai-portal` e
  `uai-auth` (prontos? stack? rota de SSO) — ver §12.
- Shell com **nav de verticais**; hoje só "OOH · Planejamento", criado pronto pra crescer.

### 5.2 Layout master-detail
- **Lista enxuta à esquerda:** `nº · linha (short_name + bairros) · score`. Ordenável por score;
  escaneável de relance. `＋` adiciona à cesta. **Filtros no topo:** público-alvo (AB/DE),
  **região → bairro (dependentes: os bairros carregam conforme a região selecionada)**, pesos do
  score. Semântica do filtro geográfico: linhas que **servem/atravessam** a região/bairro escolhido.
  - *Decisão:* a lista mantém o **score** visível (não "vazia") — sem ele perde-se o ranking de
    relance e vira clica-uma-a-uma. As demais métricas vivem na ficha.
- **Ficha rica à direita ao selecionar** — reaproveita o layout do `docs/simulacao-linha-4107.html`,
  seções:
  1. **Trajeto** · corredor 300m · pontos · POIs (mapa + toggles de categoria).
  2. **Alcance** — residentes no corredor 300m × censo 2022 (ex.: 4107 = 182.566).
  3. **Perfil de renda / classe** — renda média do responsável + %AB/%DE (ex.: 4107 = R$ 7.980, classe
     A, 79% AB / 11% DE).
  4. **Demanda (MCO)** — passageiros/dia útil/sáb/dom (ex.: 4107 = 3.616 / 1.544 / 920).
  5. **Exposição arterial & POIs** — % arterial + POIs por categoria (ex.: 4107 = 48% arterial; POIs:
     comércio 1.720, alimentação 1.069, saúde 782, educação 238; 20.137 total; 781 POI/km).
  6. **Impressões estimadas (back bus)** — útil/sáb/dom + mês, com **box de honestidade**
     (ex.: 4107 = 23.333/dia · ~612 mil/mês).

### 5.3 Componentes interativos (requisito de implementação)
- O **mapa é um componente React/MapLibre renderizado** (reaproveita o approach do `uai-buslines-web`
  congelado, tag `legacy-frozen`), **não** imagem estática.
- Os **charts** (demanda, perfil, sub-scores) são **interativos**, como na simulação.

### 5.4 Cesta
- **Estado no browser** (efêmero no F1; ver §6/§8). Mostra per-linha + linha **COMBINADO**:
  - **Impressões combinadas** = soma (OTS) das linhas.
  - **%AB combinado** = média ponderada por impressões.
  - **Pax combinado** = soma.
- **Honestidade do combinado:** OTS somado (contatos visuais), **não alcance único**; sobreposições
  não descontadas; faixa ±35%.

### 5.5 Export
- **PDF client-side** ("modo proposta" imprimível que o navegador salva como PDF): capa + linhas da
  cesta (ficha resumida) + combinado + disclaimer de honestidade. **Sem backend, sem persistência.**
- **Nota de evolução (pós-F1):** a geração de proposta deve migrar para um **endpoint que chama um
  agent** detentor do **template-padrão de proposta da empresa**, que preenche o template (ou geração
  por código conforme o template). Liga o OOH ao **motor de agentes da uAI** (`marketing-agency`/
  `midia-core`). Ver §10.

## 6. Contrato do `intel` (read-only sobre `serving`)

Serviço Java/Spring (do `uai-ooh-service-template`), lê o schema `serving` no `ooh-postgis` via
`ooh_intel_ro` (SELECT plano por `dataset_version` ACTIVE; **sem `ST_*` em runtime** — ADR-003).
Domínio com IDs como `String` (decisão `decisao-ids-gtfs-text-2026-06-05.md` — IDs GTFS permanecem
texto).

| Endpoint | Uso | Retorno |
|---|---|---|
| `GET /api/lines?regiao=&bairro=` | lista/ranking | id, short_name, long_name, score_total — filtrável por região/bairro (linhas que servem a área) |
| `GET /api/regions` | popular filtros dependentes | regiões + bairros de cada região (taxonomia p/ a cascata região→bairro) |
| `GET /api/lines/{id}` | ficha — catálogo | catálogo + `line_shape` GeoJSON + `line_stop` |
| `GET /api/lines/{id}/metrics` | ficha — métricas | alcance, perfil, demanda, POIs/categoria, arterial, impressões, 5 sub-scores |
| `GET /api/lines/ranking?weights=&publicoAlvo=AB\|DE&regiao=&bairro=` | ranking | re-rank em memória sobre os 5 sub-scores (recalibra `s_perfil` por público-alvo); filtrável por região/bairro |
| **`POST /api/lines/aggregate`** *(novo)* | combinado da cesta | recebe `line_id[]` (+ público-alvo/pesos); devolve **combinado** (impressões OTS somadas, %AB ponderado, pax somado) **+ per-linha**. **Stateless, tenant-agnóstico, sem persistência.** |

- **Auth:** valida token `uai-auth` (introspection). **Sem tenant/RLS** (ADR-004) — autenticação só.
- Re-rank e agregação **em memória**, sem PostGIS.

## 7. Arquitetura & fronteira (anti-acoplamento)

A cesta→proposta é a semente do comercial (que é do cms, Bloco 3). Pra **validar o mínimo agora sem
acoplar**, separa-se em duas pistas no front:

- **Exploração** (lista/ficha/ranking/aggregate) → **`intel` direto** (`:8085`). É dado de
  referência, read-only, estável.
- **Cesta → proposta** → atrás da interface **`ProposalPort`** (porta no front):
  - **F1:** adapter **local** — cesta = estado no browser, export = PDF client-side, **sem
    persistência**.
  - **Bloco 3:** troca por adapter **`cms → agent template`** — persiste proposta (tenant/RLS), gera
    no template-padrão. **Migrar = trocar 1 adapter**, sem reescrever as telas OOH.

**Revisão da regra de plataforma** (`arquitetura-servicos.md`): "front fala só com o cms (BFF)" passa
a valer para **dado comercial** (proposta persistida, campanha — via cms). **Dado de referência**
(read-only, tenant-agnóstico) o front lê **direto do `intel`**. Comercial continua passando pelo cms,
no Bloco 3.

**Repo/home do front:** módulo OOH dentro do shell uAI (estende `uai-portal`/shell), **não** app
standalone com login próprio.

## 8. Fasamento (o que se constrói agora vs depois)

| Camada | Quando | Por quê |
|---|---|---|
| Login SSO (`uai-auth`) + shell/nav | **agora** | fundacional e barato; auth é caro de retrofitar; reusa repos existentes |
| Módulo OOH (explorar→cesta→export) + `intel` | **agora (F1)** | é o produto de dados a validar |
| Tela "todas as campanhas de todos os tipos" | Bloco 3 | exige backend comercial (tenant, campanhas) |
| cms comercial (tenant/RLS, ciclo de vida, cobrança) | Bloco 3 | caro; só depois de validar o produto OOH |
| Realtime/verificado | Bloco 2 | — |

**Princípio:** puxa o **shell+login** (barato, fundacional) pra agora; mantém o **backend comercial**
(caro) no Bloco 3. O módulo OOH nasce **dentro da casa final** (shell), e a `ProposalPort` garante que
chegar na tela de campanhas seja **aditivo, não reescrita**.

## 9. Honestidade (ledger — ver `proxies-e-premissas.md`)

- ✅ **Vendável:** score/ranking relativo 0–100 (e comparação entre linhas).
- ⚠️ **Estimativa ±35% (OTS, não pessoas únicas):** impressões absolutas — coeficientes de mercado não
  calibrados; ilustrativo até estudo de campo.
- **Combinado da cesta** = OTS somado, **nunca alcance único**; sobreposições declaradas.
- Vigências declaradas quando relevante (MCO set/2025, censo 2022, embarque mai/2024, etc.).
- Na UI: o número de impressões aparece **sempre** marcado como estimativa; o score é o número sólido.

## 10. Evolução (pós-F1 / outros blocos)

- **Geração de proposta via agent + template** (pós-F1): endpoint que invoca um agent com o
  template-padrão da empresa → preenche a proposta. Integra OOH ao motor de agentes uAI. A
  `ProposalPort` já prevê essa troca de adapter.
- **Bloco 2 (realtime):** alcance verificado, posições, viagens executadas — entram na ficha/cesta.
- **Bloco 3 (comercial):** cms multi-vertical, tela "todas as campanhas", persistência de proposta
  como campanha `type=ooh`, tenant/RLS. O módulo OOH passa a ser "o que abre numa campanha tipo=OOH".

## 11. Decisões registradas (rationale)

1. **Front interno, não civic** — o produto (ranking de valor publicitário) é pra planejador, não pro
   cidadão; civic `linhas.uaiagencia.com.br` desligado.
2. **Login/shell na plataforma, não no OOH** — login é concern da plataforma; OOH é módulo.
3. **Cesta efêmera + export como artefato; persistência no Bloco 3** — não antecipar tenant/RLS/cms.
4. **Master-detail (lista enxuta + ficha rica)** — reconcilia "melhores infos" (na ficha) com lista
   escaneável (score na lista).
5. **`intel` permanece read-only; agregação é stateless no `intel`** — agregação é matemática sobre
   dados de referência, não comercial; preserva ADR-003/004.
6. **Fronteira `ProposalPort`** — desacopla cesta→proposta do cms; migração = 1 adapter.
7. **Front lê `intel` direto p/ referência; cms só p/ comercial** — revisa a regra BFF.
8. **Shell+login agora, comercial Bloco 3** — separa o barato/fundacional do caro/prematuro.

## 12. Dependências, gates & abertos

- **⚠️ Estado de `uai-portal` e `uai-auth`** — confirmar prontidão, stack e fluxo SSO antes de assumir
  reuso (gate da §5.1).
- **`serving` materializado** (épico-4 T17/T18) + `dataset_version` ACTIVE — pré-req do `intel`.
- **Filtro região→bairro = task nova de regionalização no `core`** (verificado 2026-06-05, pré-req do
  filtro geográfico). **Não existe** regional administrativa nos dados: o `nm_regiao` do censo é a
  **macrorregião IBGE** (= "Sudeste" pra 100% dos 5.167 setores — inútil). O `core` tem bairro
  (`census_sector.nm_bairro`, **476** distintos, 0 nulos) + geom; `core.stop` tem `geom_31983` (GiST)
  mas **sem** bairro/região. **Approach escolhido: espacial** (sem name-matching, consistente com
  ADR-003):
  1. ingerir os **9 polígonos das regionais da PBH** → `raw.pbh__regional` → `core.regional` (nome+geom);
  2. classificar **setor → regional** (∩) e **stop → bairro+regional** (`stop.geom` ∩ setor/regional);
  3. derivar **linha → bairros/regionais** de `line_stop`→`stop`;
  4. projetar pro `serving` + `GET /api/regions` (cascata) + filtros `regiao`/`bairro` nos endpoints.
  - **Encaixe:** task **T8b** (`docs/epicos/bloco1/tarefas/T8b.md`, Épico 2 contexto, repo
    `uai-ooh-pipeline`) — **pendente de execução** em 2026-06-05. A classificação de bairro do legado
    era no app via JTS (ADR-003), não no `core`.
- `uai-ooh-intel` gerado do `uai-ooh-service-template` (T0b/T18/T19).
- Identidade visual OOH (logo/cores) — placeholder no mockup; trocar quando houver.

## 13. Impacto nos docs (atualizar ao consolidar este PRD)

- `arquitetura-servicos.md`: front OOH = módulo no shell uAI; revisar regra BFF (referência vs
  comercial); login SSO; nota de fasamento.
- `epico-4-serving-app.md` (Tarefa 4.5): trocar "cutover civic do bus-lines" por "módulo OOH no shell
  + `ProposalPort`"; somar endpoint `/aggregate` (e à `T19`).
- **Novo ADR:** "Fronteira `ProposalPort` + fasamento shell-agora / comercial-Bloco-3 + regra BFF
  revisada (referência vs comercial)".

## 14. Critérios de pronto (F1)

1. Usuário loga via SSO `uai-auth` no shell uAI e acessa o módulo OOH.
2. Lista/ranking de linhas com filtros (público-alvo, região, pesos), ordenada por score.
3. Ficha por linha com mapa interativo (MapLibre) + charts interativos + box de honestidade.
4. Cesta com combinado correto (OTS somado, %AB ponderado) e disclaimer.
5. Export PDF "modo proposta" client-side.
6. `intel` serve catálogo/ficha/ranking/**aggregate** do `serving` (sem `ST_*`), com auth por
   introspection e sem tenant.
7. Honestidade preservada: impressões sempre marcadas como estimativa; score é o número sólido.
