cl# Feature: Prospecção por Nichos (tela, sem agente)

> **Master da feature** (hub PM). Specs por repo:
> - Dados: [`uai-ooh-pipeline/docs/feature-nichos/data-layer.md`](../../../uai-ooh-pipeline/docs/feature-nichos/data-layer.md) (+ design completo em `uai-ooh-pipeline/docs/design/base-prospeccao-geral.md`)
> - Backend: [`uai-ooh-intel/docs/feature-nichos/api-rest.md`](../../../uai-ooh-intel/docs/feature-nichos/api-rest.md)
> - Frontend: este doc (lane front; migra pro `uai-ooh-web` quando o repo for criado)
>
> Status: proposta (pré-implementação). Vertical OOH BH Bus Mídia.

## 1. Problema e objetivo

Hoje, prospectar estabelecimentos no banco `ooh` exige um **pipeline manual** (filtrar grupo ruidoso → reenriquecer no `raw` → recortar BH por geometria → deduplicar → classificar) — feito por um agente/dev a cada vez. Queremos que o **time comercial** faça isso sozinho numa **tela**, com filtros e exportação, **sem agente no loop** e sem saber SQL/PostGIS.

**Resultado-alvo:** abrir a tela → escolher "Educação infantil" + "Centro-Sul" + "só com telefone" → ver 431 estabelecimentos classificados (público/particular, porte) → exportar xlsx. O que fiz na mão nesta sessão vira um clique.

## 2. A fronteira: agente vs tela

| | Agente (LLM) | Tela (UI) |
|---|---|---|
| Cobre | nicho **novo/fuzzy** em linguagem natural | nichos **salvos** + filtros estruturados |
| Custo/velocidade | segundos, gasta token | instantâneo, custo zero |
| Quem usa | dev explorando | comercial sozinho |
| Limite | precisa do agente toda vez | nicho inédito precisa ser **criado 1×** |

**Chave:** o agente (ou um form de admin) define o nicho **uma vez** → vira linha em `analise.nicho_regra` → aparece pra sempre no dropdown da tela. O agente fica **opcional** (ajuda a sugerir os "excludes"), não obrigatório.

## 3. Arquitetura (3 lanes)

```
core/raw (PostGIS) ──rebuild──> analise (classifica+geo+dedup+nicho[]) ──swap──> serving.lead_prospeccao
   [uai-ooh-pipeline — LANE DADOS]                                          (plano, ooh_intel_ro, ACTIVE)
                                                                                  │ (sem PostGIS/regex)
                                                          uai-ooh-intel  REST :8085  [LANE BACKEND]
                                                                                  │
                                                          Tela "Prospecção" (React/MapLibre) [LANE FRONT]
                                                                  (uai-ooh-web a criar; ou via uai-cms)
```

A tela **nunca** toca core/analise/PostGIS — só lê `serving` via o `intel`. Classificação, dedup, recorte de BH, público/porte já vêm prontos do rebuild. Depende da **base de prospecção** (lane dados) estar materializada — ver design completo no pipeline.

## 4. Lane DADOS (resumo — detalhe no pipeline)

Entrega o que a API lê:
- `analise.poi_prospeccao` (1:1 com core.poi: `nome_norm`+trgm, geo `regional`/`bairro`/`dentro_municipio`, classificação `nicho[]`, `publico_privado`/`porte`/`eh_rede`, scores, `dedup_key`/`dedup_is_primary`/`local_key`).
- `analise.nicho` (catálogo p/ o dropdown) + `analise.nicho_regra` (regras versionadas).
- `serving.lead_prospeccao` (espelho plano, grão lead × nicho, `geom_geojson` 4326, GRANT `ooh_intel_ro`).
- Alcance por linha com **2 métricas** (`anunciantes_distintos`/`destinos_distintos`) em `serving.line_metrics`.

## 5. Lane BACKEND (resumo — detalhe no intel)

`uai-ooh-intel` (Java/Spring, :8085, read-only sobre `serving` ACTIVE) expõe:
- `GET /nichos` → catálogo (dropdown).
- `GET /prospeccao?nicho=&regional=&bairro=&rede=&porte=&com_contato=&q=&dedup=&page=&size=` → linhas + total.
- `GET /prospeccao/export.xlsx?...` → exportação (o que gerei na mão).
- `GET /alcance?nicho=&linha=` → 2 métricas (anunciantes/destinos).

Busca por nome (`q`) usa índice GIN trigram do `nome_norm` (fuzzy, sem regex no front).

## 6. Lane FRONT (spec — migra pro `uai-ooh-web`)

### 6.1 Tela "Prospecção"

```
┌─ Prospecção ────────────────────────────────────────────────┐
│ Nicho:  [Educação infantil ▾]   🔎 nome: [______]           │
│ Regional:[Todas ▾] Bairro:[__]  Rede:[Todas ▾] Porte:[▾]    │
│ ☑ só com telefone   ☑ deduplicar (telefone)                 │
│ ───────────────────────────────────────────────────────────│
│  431 estabelecimentos · 192 c/ telefone      [Exportar xlsx]│
│ ┌─────────────────────────────┬──────────────┬──────┬─────┐ │
│ │ Estabelecimento             │ Telefone     │ Tipo │Porte│ │
│ │ Creche Caminhos do Saber    │ (31) 3457... │Públic│Pequ.│ │
│ │ Colégio Bernoulli           │ (31) 3029... │Partic│Grand│ │
│ └─────────────────────────────┴──────────────┴──────┴─────┘ │
│ [ 🗺️ ver no mapa ]   [ 📊 alcance por linha de ônibus ]      │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 Componentes

- **Filtros** (sidebar): `nicho` (dropdown de `GET /nichos`), `regional`, `bairro`, `rede`, `porte`, toggle `com_contato`, toggle `dedup`, campo `q` (nome, debounce → trigram).
- **Tabela** paginada: Estabelecimento · Telefone · Tipo (público/particular) · Porte · Regional. Ordenação por `score_lead` desc (default) ou nome. Header com contagem (total + com telefone).
- **Mapa** (MapLibre/Leaflet): pinta `geom_geojson` (4326) dos resultados filtrados; clique no pin abre ficha.
- **Exportar**: botão → `GET /prospeccao/export.xlsx` com os filtros atuais.
- **Alcance por linha**: aba que chama `GET /alcance` e mostra as 2 métricas (anunciantes vs destinos) por linha.

### 6.3 Tela admin "Criar nicho" (tira o agente do loop recorrente)

Form: nome do nicho · grupo(s) base · palavras-chave **incluir** · palavras-chave **excluir** · atributos (público/porte on/off) → grava regras (`analise.nicho_regra`) → dispara rebuild → nicho aparece no dropdown. Opcional: botão "sugerir excludes (IA)" que chama o agente só pra rascunhar — confirmação humana antes de salvar. **MVP pode ser só leitura de nichos** (criação fica no agente/dev); a tela admin é F3.

### 6.4 Não-funcionais

- Sem PostGIS/SQL no front; tudo via REST do `intel`.
- Auth/edge: seguir o padrão da plataforma (front OOH via `uai-cms` revisado / `uai-auth`; ver `docs/arquitetura-servicos.md`).
- Paginação server-side; debounce na busca; estados de loading/vazio.

## 7. Roadmap / tasks (encaixe no Bloco 1)

| Lane | Task | Repo | Depende de |
|---|---|---|---|
| Dados | materializar `analise.poi_prospeccao` + `serving.lead_prospeccao` + `analise.nicho` (educação + automotivo) | pipeline | base de prospecção (F1) |
| Dados | fix core mislabel + bug de versão | pipeline | — |
| Backend | endpoints `/nichos`, `/prospeccao`, `/export`, `/alcance` | intel | serving acima |
| Front | tela Prospecção (filtros+tabela+mapa+export) | uai-ooh-web | intel acima |
| Front | tela admin "Criar nicho" | uai-ooh-web | nicho_regra |

Sequência: **F1** entrega lane dados (base) → **F2** lane backend + tela de leitura → **F3** tela admin "criar nicho" + overlay de alcance. (Alinhar com `docs/epicos/bloco1/f1/`.)

## 8. Dependências e decisões

- **Depende** da base de prospecção (lane dados) — sem `serving.lead_prospeccao` + `analise.nicho`, não há tela.
- **Nicho = dado** (não código): adicionar nicho não precisa de deploy de front/back — só regras + rebuild.
- **Repo de front**: `uai-ooh-web` ainda **a criar** (ver mapa de repos no README do PM). Até lá, a lane front mora aqui. Confirmar se o front OOH é repo próprio (`uai-ooh-web`) ou agregado via `uai-cms` (há divergência entre `README` e `arquitetura-servicos.md` a resolver).
- **Agente continua útil** para nichos inéditos/exploração e pra sugerir excludes — mas deixa de ser obrigatório no uso recorrente.
