# Épico 2 — Camadas de contexto (censo+renda · POIs · malha arterial)

> Normalização F1, parte 2. Normaliza as camadas geográficas que serão cruzadas com o corredor de
> cada linha no Épico 3. Banco `ooh`, schema `core`, PostGIS.
> **Depende de:** Épico 1 (precisa de `core.line_shape` só no Épico 3; aqui as camadas são
> independentes da linha e podem rodar em paralelo ao Épico 1).

Mesmas regras de SRID/cast do Épico 1. Estas tabelas versionam por validade
(`valid_from/valid_to/is_current`), não por release GTFS (censo/POI/viário mudam noutro ritmo).

**Índices na `raw` (criar ao rodar):** **2.1 census** = btree `(cd_setor)` nas 3 tabelas censo + temp-GiST da geom dos setores; **2.2 poi** = temp-GiST se precisar de cruzamento espacial; **2.3 road** = temp-GiST da geom de `pbh__trecho_logradouro`. Padrão (temp-GiST p/ geo; btree `IF NOT EXISTS`+`ANALYZE`, deixar) e detalhe em `arquitetura-servicos.md` §"Índices na `raw`". Persistência server-side; volume no Python → reusar a **classe de persistência em chunks** do pipeline.

---

## Tarefa 2.1 — `core.census_sector` (setor + população + renda + classe)
- DDL: `id, cd_setor unique, nm_bairro, populacao int, domicilios int, area_km2 numeric, densidade_hab_km2 numeric, renda_media_resp numeric, renda_mediana_resp numeric, classe_renda char(1) check in (A..E), geom_31983 geometry(MultiPolygon,31983), geom_geojson jsonb`. Índice GiST.
- Fontes (join por `cd_setor`): `raw.censo__setores_bh` (geom 4674→31983; `area_km2`),
  `raw.censo__agregados_basico_bh` (`v0001`=população, `v0002`=domicílios),
  `raw.censo__renda_responsavel_bh` (`v06004`=renda média, `v06006`=mediana). Tratar `X`/`''` como NULL.
- **Classe A–E por quintis de BH** sobre `renda_media_resp` (percentile_cont 0.2/0.4/0.6/0.8 sobre
  todos os setores com renda numérica). A = quintil ↑, E = quintil ↓. Setor sem renda → classe NULL
  (reportar; 29 setores na sessão).
- `densidade_hab_km2 = populacao/area_km2`.
- **Validação:** 5.166 setores; 0 órfãos vs renda já validado; distribuição A–E ≈ 20% cada;
  renda média BH ≈ R$ 4.682; bairros nobres (Serra/Lourdes/Belvedere) caem em A, periferia em E.
- **Pronto:** setores com população, renda e classe; geom indexada.

## Tarefa 2.2 — `core.poi` (POIs MULTI-FONTE: Overture + Foursquare + OSM) — enriquecido

> Reescrita 2026-06-04 (workflow adversarial `ooh-poi-enriquecido`). Antes era só OSM (~2.478, campos
> pobres). Agora **multi-fonte com nome, endereço, contato e nível de faturamento ESTIMADO**, para
> PERFIL do trajeto (A3/A5/A7) e PROSPECÇÃO de leads (cada estabelecimento no trajeto = potencial anunciante).
> Já no `raw` (BH): `raw.overture__poi` 113.814 · `raw.fsq__poi` 132.061 · `raw.osm__poi` 2.478.

### ⚠️ Decisão central — NÃO deduplicar por proximidade (verificado empiricamente no banco)
A verificação adversarial mediu o `raw` real e **refutou** o dedup espacial (que parecia óbvio):
- 15,2% dos POIs FSQ vivem em clusters de 3+ no MESMO ponto (até **61** num ponto = shopping/galeria/
  edifício comercial com dezenas de CNPJs distintos); mediana 4 vizinhos a 30m, p90=26.
- Das colisões espaciais Overture×FSQ, **só ~29% compartilham o nome** → a maioria são estabelecimentos
  DIFERENTES no mesmo ponto. `similarity≥0.6` funde distintos ("12ª Vara" × "10ª Vara"; redes homônimas).
- **Em BH anunciantes se empilham na mesma coordenada.** Dedup por proximidade+nome **destrói a base de
  leads** (funde donos distintos, cola contato de A em B) e contamina o perfil. Falso-merge ≫ falso-split.

**Regra:** `core.poi` guarda **1 registro por POI por fonte** (identidade = `(fonte, fonte_id)`; cada loja =
1 lead). SEM colapso espacial. Cross-source vira **link opcional só com SINAL FORTE** (telefone igual OU
domínio de website igual OU endereço normalizado igual), nunca por proximidade/nome.

### DDL (achatado para o serving Java; cálculo em 31983)
```sql
CREATE TABLE core.poi (
  id            bigserial PRIMARY KEY,
  fonte         text NOT NULL CHECK (fonte IN ('fsq','overture','osm')),
  fonte_id      text NOT NULL,                 -- fsq_place_id | overture id | osm_type:osm_id
  nome          text,
  endereco text, bairro text, cep text,
  telefone text, website text, email text, redes_sociais jsonb,
  tem_contato boolean GENERATED ALWAYS AS (telefone IS NOT NULL OR website IS NOT NULL OR email IS NOT NULL) STORED,
  grupo_ooh     text NOT NULL DEFAULT 'outros',-- 9 grupos da taxonomia uAI
  subgrupo      text,                          -- granularidade fina (herda da fonte)
  categoria_raw text,                          -- código original da fonte (auditoria)
  marca         text,                          -- brand (Overture); rede/franquia
  relevancia_prospeccao smallint NOT NULL DEFAULT 2 CHECK (relevancia_prospeccao BETWEEN 0 AND 3),
  -- porte/faturamento: ADIADO p/ v2 (decisão do dono 2026-06-04). Sem coluna de porte em F1.
  confidence    numeric,                       -- da Overture quando houver
  link_grupo_id bigint,                         -- cross-source link por sinal forte (NULL se sem par)
  geom_31983    geometry(Point,31983) NOT NULL,
  geom_geojson  jsonb NOT NULL,
  valid_from date NOT NULL DEFAULT current_date, valid_to date, is_current boolean NOT NULL DEFAULT true,
  UNIQUE (fonte, fonte_id)
);
-- GiST em geom_31983; btree em grupo_ooh e em (relevancia_prospeccao, tem_contato) p/ prospecção.
```

### Taxonomia (2 níveis, em dados — não enum no código)
- `core.poi_taxonomy(subgrupo PK, grupo_ooh, rotulo_pt, relevancia_prospeccao, porte_proxy)` — 9 grupos:
  `alimentacao, comercio, saude, educacao, servicos, lazer_cultura, automotivo, religioso_comunitario, outros`.
- `core.poi_category_map(fonte, match_tipo[exato|prefixo], codigo_origem, subgrupo, grupo_ooh_fallback, release_origem, valid_*)`
  — de-para versionável. FSQ casa por **prefixo** do caminho hierárquico (os 10 L1 dão fallback 100%);
  Overture/OSM por código exato. Não-mapeado → `outros` + relatório de não-mapeados por volume.
- `relevancia_prospeccao=0` p/ ruído não-prospectável (estradas 2.465, linhas de ônibus 1.246, prédios
  residenciais 5.386, estruturas 3.013) — **mantido para PERFIL, filtrado da PROSPECÇÃO**.

### Nível de faturamento — ADIADO PARA v2 (decisão do dono, 2026-06-04)
- **NÃO entra em F1.** O dono optou por não incluir porte/faturamento estimado agora — nenhuma fonte traz
  receita e o proxy por categoria seria fraco demais para arriscar a honestidade do produto. `core.poi`
  de F1 **não tem coluna de porte**.
- **v2 (quando houver dado real):** cruzar POI ↔ Receita/CNPJ (porte oficial ME/EPP + CNAE) numa coluna
  **medida** separada; só então, opcionalmente, derivar classe de porte. Registrar no ledger quando entrar.

### Pipeline (raw → core.poi)
1. **Estender `load_poi_overture_fsq.py`** (única decisão que passou na verificação) p/ trazer ao `raw`,
   além de nome/categoria/lat/lon: endereço (locality/region/postcode), contato (tel/website/email/socials),
   `brand`+`confidence` (Overture), mantendo `*_json` do registro original.
2. Normalizar cada fonte → `core.poi` (1 linha por POI/fonte), reprojetando 4326→31983
   (`ST_Transform(ST_SetSRID(ST_MakePoint(lon,lat),4326),31983)`; OSM lat/lon TEXT → cast com guarda).
3. Categoria via `core.poi_category_map`; estimar porte; classificar relevância.
4. (Opcional) `link_grupo_id` p/ registros de fontes diferentes com **sinal forte** igual — link, não fusão.
5. Índices (GiST + btree de prospecção); materializar `geom_geojson`.

### Validação
- `core.poi` = **UNIÃO marcada por fonte** (~248k; mesmo estabelecimento pode aparecer em 2 fontes —
  rotular: NÃO é "estabelecimentos únicos"). Contagens por `fonte` batem com o raw.
- `tem_contato` e `porte_estimado` populados; distribuição por `grupo_ooh` plausível; ruído cai em
  `relevancia_prospeccao=0`. Honestidade carimbada: porte = proxy (COMMENT + ledger); `fonte` explícita por POI.

### Decisões (aceitas pelo dono — 2026-06-04)
- ✅ **União marcada por fonte, SEM dedup** — `core.poi` = 1 registro por POI/fonte (~248k), cross-source
  só por sinal forte. (Dedup por proximidade refutado empiricamente — ver decisão central acima.)
- ✅ **Porte/faturamento ADIADO para v2** — não entra em F1 (ver seção acima).
- ✅ **LGPD = interesse legítimo** (dado público de estabelecimento) + mecanismo de **opt-out** na
  ferramenta de prospecção. O contato (tel/website/email/socials) entra no `core.poi`.

## Tarefa 2.3 — `core.road_segment` (malha viária + classe arterial) — componente de trânsito
- DDL: `id, id_trecho, id_logradouro, nome_logradouro, tipo_logradouro, largura_m numeric, classe_arterial text check in ('arterial','coletora','local'), is_via_referencia bool, geom_31983 geometry(LineString,31983)`. Índice GiST.
- Fonte: `raw.pbh__trecho_logradouro` (geom LineString SRID0→31983; `largura` vírgula→ponto, guardar regex).
- **Classificação (proxy de hierarquia — NÃO é classificação funcional oficial, declarar):**
  - `arterial` se `desc_sigla_tipo_lograd` ∈ {AVENIDA, RODOVIA, VIA, VIADUTO, TREVO, ELEVADO, TUNEL, TRINCHEIRA} **ou** `largura_m >= 20`;
  - `coletora` se largura 10–20m e tipo RUA/ALAMEDA/ESTRADA;
  - `local` caso contrário (RUA estreita, BECO, TRAVESSA, VIA DE PEDESTRE).
- **Refinamento (Vias de Referência):** marcar `is_via_referencia=true` nos trechos que coincidem
  geograficamente com os 35 corredores VR de `raw.pbh__velocidade_corredores` (match por nome de
  via + proximidade). Opcional em F1; agrega "corredor oficial BHTrans".
- **Validação:** ~55k trechos; distribuição arterial/coletora/local plausível (AVENIDA 4.246 +
  RODOVIA 372 etc. → arterial); inspeção visual: Contorno/Amazonas/Antônio Carlos = arterial.
- **Pronto:** malha classificada com geom indexada; pronta para medir % arterial por trajeto (Épico 3).

---

## Critério de pronto do Épico 2
- `core.census_sector` (renda+classe), `core.poi`, `core.road_segment` (classe arterial) — todas
  com geom 31983 indexada (GiST), validadas contra os números da sessão.
- **Honestidade registrada nas tabelas/COMMENT:** renda = do responsável (medida, não domiciliar
  total); classe = quintil de BH; classe arterial = proxy tipo+largura (não funcional oficial).
- **Habilita o Épico 3** (cruzamento corredor × estas 3 camadas → métricas por linha).
