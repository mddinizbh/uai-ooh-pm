# PRD — Mapa de Densidade Populacional & Renda por Bairro (BH Bus Lines)

> **Status:** rascunho para planejamento · **Autor:** Marley Diniz · **Data:** 2026-05-27
> **Produto:** BH Bus Lines — Neighborhood Bus Explorer (`com.uai.buslines`)
> **Branch de trabalho:** `claude/population-density-mapping-i1sjT`

---

## 1. Visão / Resumo

Hoje o BH Bus Lines mostra **quais linhas servem cada bairro** (relação linha↔bairro:
`PASSES_THROUGH` / `DEPARTS_FROM` / `ARRIVES_AT`). Este PRD propõe uma nova camada de
**inteligência socioterritorial**: cruzar a rede de ônibus com **densidade populacional**
e **renda (classes A/B/C/D)** por bairro.

**Objetivo central:** identificar **bairros de alta demanda** (alta densidade populacional)
e usar isso como **filtro** para descobrir **quais linhas os atendem**, e em seguida
sobrepor a dimensão de **renda por classe** — permitindo analisar densidade × renda ×
cobertura de transporte.

### Perguntas que o produto passa a responder
- Quais bairros têm maior densidade populacional? Onde está a maior demanda potencial?
- Quais linhas de ônibus passam pelos bairros mais densos?
- Bairros de classe C/D de alta densidade estão bem ou mal servidos por linhas?
- Onde há descompasso entre demanda (densidade) e oferta (nº de linhas)?

---

## 2. Decisões já tomadas

| Tema | Decisão |
|------|---------|
| Granularidade | **Bairro** (reusa os 487 polígonos e a relação linha↔bairro já existentes). Setor censitário fica como evolução futura. |
| Ingestão de dados | **Gateway por URL** (espelhando o `BoundaryGateway` atual), com fonte configurável por env var. |
| Métrica primária | **Densidade populacional** (hab/km²) — Censo IBGE 2022 via PBH. |
| Métrica secundária | **Classe de renda A/B/C/D** por bairro. |
| Storage | A definir no planejamento: colunas em `neighborhood` **ou** tabela `neighborhood_demographics` 1:1 (ver §6). |

---

## 3. Estado atual do sistema (o que já dá pra reaproveitar)

- **Arquitetura:** hexagonal, Java 21 / Spring Boot 3.3.6 / Maven, porta 8085.
- **Banco:** Postgres 16 (sem PostGIS). Geometria de bairro guardada como **GeoJSON em `jsonb`**
  (ADR-003). Sem `tenant_id` (ADR-004).
- **Tabela `neighborhood`:** `id`, `name`, `boundary_geojson` (Polygon/MultiPolygon WGS84),
  `dataset_version`. **Não há população, área nem renda.**
- **Tabela `line_neighborhood`:** classificação precomputada linha↔bairro (JTS no import).
  **É o coração do filtro "linhas que servem bairros X".**
- **Geometria/CRS:** já existe pipeline JTS (`jts-core` 1.19.0) + reprojeção
  (`proj4j` 1.3.0) usada no import — dá pra **calcular área** reprojetando para
  EPSG:31983 (SIRGAS 2000 / UTM 23S, recomendado pela PBH).
- **Frontend:** React 19 + TypeScript + **MapLibre GL 5.x**, já renderiza contorno de bairro
  (`MapView.tsx` → `addBoundaryLayer`) e listas de linhas. Painel lateral 320px (design tokens
  em `web/src/App.css`).
- **Ingestão análoga existente:** `BoundaryGateway` baixa GeoJSON de bairros da PBH CKAN —
  o `DemographicsGateway` deve seguir o mesmo padrão.

---

## 4. Fontes de dados (pesquisa realizada em 2026-05-27)

### 4.1 População por bairro → densidade

**Recomendado — PBH Dados Abertos: "População e Domicílio por Bairro 2022"**
(fonte: Censo IBGE 2022, processado pela Prodabel).
- Formato **CSV**, com **habitantes e domicílios por bairro**, chaveável por `CODIGO`/`NOME`.
- **Mesma origem** do boundary já usado (dataset "Bairro Oficial" da PBH) → nomes/códigos batem.
- **Área para densidade:** calculada da própria geometria (JTS + proj4j → EPSG:31983).
- Landing: `https://ckan.pbh.gov.br/pt_BR/dataset/populacao-e-domicilio-por-bairro-2022`
- Alternativa: registro GeoNetwork PBH "População e Domicílios por Bairro" pode ser
  **shapefile/GeoJSON com população já anexada à geometria** (avaliar — pode unificar boundary+pop).
- Descoberta da URL exata do CSV via API CKAN (com UA de browser):
  `GET https://dados.pbh.gov.br/api/3/action/package_show?id=populacao-e-domicilio-por-bairro-2022`

### 4.2 Renda por bairro → classes A/B/C/D

> O Censo IBGE 2022 **não publica renda no nível de bairro** diretamente (só por *setor censitário*).
> Opções já no nível de bairro:

| Fonte | Conteúdo | Base | Observação |
|-------|----------|------|------------|
| **IPEAD/UFMG — "Classificação dos Bairros de BH"** | 4 classes (Popular / Médio / Alto / Luxo) por renda do chefe em salários mínimos | — | Mapeia ~1:1 para A/B/C/D. Tabela pronta (PDF). **Recomendado para v1.** |
| **Rede Nossa BH — "Mapa da Desigualdade"** | Renda média dos 487 bairros, formato aberto | Censo 2010 | Renda contínua; base mais antiga. |
| **IBGE — Agregados por Setores Censitários (Censo 2022)** | Rendimento por setor | 2022 | Mais atual e granular, mas exige agregar setor→bairro. Evolução futura. |

**Conversão renda → A/B/C/D:** faixas em salários mínimos (ou Critério Brasil/ABEP).

### 4.3 Restrições técnicas das fontes
- **WAF da PBH:** `ckan.pbh.gov.br` / `dados.pbh.gov.br` retornam **403 sem User-Agent de
  browser** (confirmado; já documentado no CLAUDE.md para o boundary). O gateway precisa enviar UA.
- **CRS:** dados de bairro da PBH usam **EPSG:31983** (SIRGAS 2000 / UTM 23S). O reprojetor
  atual aceita códigos EPSG genéricos.
- **Sem internet no ambiente de dev (Claude on the web):** o container de desenvolvimento
  **não tem rede de saída** — a ingestão real só roda em deploy com rede; nos testes usa-se mock.

---

## 5. Escopo

### 5.1 Dentro do escopo (v1)
- Ingestão de **população por bairro** (gateway por URL) + cálculo de **densidade** (área da geometria).
- Ingestão/seed de **classe de renda A/B/C/D** por bairro (IPEAD).
- Persistência das métricas no modelo de bairro.
- API: expor densidade + classe no `GET /api/neighborhoods`; filtro de linhas por
  densidade mínima e/ou classe de renda.
- Frontend: **choropleth** no mapa (cor por densidade), **toggle** densidade ↔ renda,
  filtros (slider de densidade mínima + chips A/B/C/D), lista de "linhas nos bairros filtrados".

### 5.2 Fora do escopo (futuro)
- Granularidade por setor censitário.
- Renda contínua/atualizada via agregação IBGE 2022.
- Visualização combinada densidade+renda numa só camada (hachura/borda).
- Indicadores extras (raça, gênero, frequência de ônibus do Mapa da Desigualdade).

---

## 6. Decisões em aberto (para o planejamento de amanhã)

1. **Modelagem de storage:**
   - (A) Colunas em `neighborhood` (`population`, `area_km2`, `income_class`, `avg_income`) — mais simples, alinhado ao schema atual.
   - (B) Tabela `neighborhood_demographics` 1:1 — mais limpo se vierem mais indicadores.
   - *Recomendação:* (A) para v1.
2. **Join key população↔bairro:** guardar o `codigo` do bairro na migration V2 (join robusto) **ou** join por nome normalizado (acentos/caixa). *Recomendação:* guardar `codigo`.
3. **Fonte de renda v1:** IPEAD (4 classes) como seed vs. Nossa BH (renda contínua 2010). *Recomendação:* IPEAD.
4. **Mapeamento renda → A/B/C/D:** definir faixas (salários mínimos / Critério Brasil).
5. **UX do detalhe do bairro:** popup no mapa vs. painel lateral.
6. **Camadas:** toggle (uma de cada vez) vs. combinadas.
7. **Origem dos dados em deploy:** URL única (GeoJSON com pop) vs. boundary + CSV de população separados.

---

## 7. Esboço técnico (sujeito ao planejamento)

- **Migration V2** (`db/migration/V2__*.sql`): adicionar métricas demográficas a `neighborhood`
  (+ `codigo` para join). Manter `hibernate.ddl-auto=validate`.
- **Domínio:** estender `Neighborhood` (record) com population/area/incomeClass; `NeighborhoodSummary`
  passa a expor densidade/classe.
- **Porta out + adapter:** `DemographicsGateway` (HTTP, UA de browser) + parser CSV; join por código.
- **Pipeline de import:** popular métricas após classificação JTS; densidade derivada de
  população/área (área via reprojeção EPSG:31983).
- **API:** `GET /api/neighborhoods` com densidade/classe; `GET /api/lines?minDensity=&incomeClass=`
  reusando `line_neighborhood`.
- **Frontend:** camada `fill` MapLibre com expressão de cor por faixa; legenda; toggle; filtros;
  lista de linhas filtradas.
- **Testes:** Testcontainers Postgres 16; gateway com mock (sem rede em CI/dev).

---

## 8. Métricas de sucesso (proposta)
- Choropleth de densidade renderiza para os 487 bairros sem buracos de dado (cobertura ≥ 95%).
- Filtro "linhas em bairros de alta densidade" retorna resultados coerentes com `line_neighborhood`.
- Import demográfico idempotente e versionado (atomic swap, igual ao import atual).

---

## 9. Referências (fontes)
- PBH Dados Abertos — População e Domicílio por Bairro 2022: https://ckan.pbh.gov.br/pt_BR/dataset/populacao-e-domicilio-por-bairro-2022
- PBH — Bairro Oficial: https://dados.pbh.gov.br/dataset/bairro-oficial/resource/a3b2591d-4904-4859-a11f-aae680821c75
- GeoNetwork PBH — População e Domicílios por Bairro: https://geonetwork.pbh.gov.br/geonetwork/srv/api/records/9759294b-d385-4dfc-b65a-08a5c2e14476
- IPEAD/UFMG — Classificação dos Bairros de BH: https://site.ipead.face.ufmg.br/wp-content/uploads/2023/10/Classes_Bairros_BH_com_mapa.pdf
- Rede Nossa BH — Mapa da Desigualdade BH/RMBH: https://nossabh.org.br/2021/06/mapa-da-desigualdade-de-belo-horizonte-rmbh/
- IBGE — Agregados por Setores Censitários (Censo 2022): https://www.ibge.gov.br/estatisticas/sociais/trabalho/22827-censo-demografico-2022.html
- IBGE Cidades — Panorama BH (Censo 2022): https://cidades.ibge.gov.br/brasil/mg/belo-horizonte/pesquisa/10101/0

---

## 10. Anexos
- **Mockup da tela:** `density-preview.svg` (gerado por `gen_mockup.py`) — preview do choropleth,
  filtros e lista de linhas. Números/bairros são ilustrativos (placeholder).
