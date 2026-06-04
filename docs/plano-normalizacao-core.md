# Plano de Normalização — camada `core` (uAI OOH, fase F1)

> **Status:** documento de referência. **Não executar** a partir daqui.
> A execução começa pelo **Épico 0** (ver `docs/epicos/epico-0-lacunas-dados.md`); os Épicos 1–4
> (normalização) só serão detalhados depois que o Épico 0 fechar, porque o que a renda e o radar
> revelarem pode mudar o desenho.

## Contexto

A base crua está completa: **27 tabelas no schema `raw`** do banco `ooh` (GTFS estático+RT,
ponto/estação de ônibus, embarque, MCO, matriz O-D, pesquisas, trecho de logradouro, velocidade,
radares, OSM, censo BH). É fiel mas inutilizável para o produto — tudo `TEXT`, sem chaves, 3 CRS
diferentes, IDs incompatíveis entre fontes.

O produto (uAI OOH, caso de partida **back bus**) precisa, na fase **F1**, da inteligência de
planejamento: **A1** catálogo de linhas com itinerário, **A2** estimativa de alcance por linha,
**A3** perfil socioeconômico do trajeto, **A4** score 0–100, **A5** score por público-alvo,
**A7** POIs no trajeto. Isso exige uma camada **`core`** normalizada e cruzada espacialmente.

Esta análise validou via MCP/PostGIS que os cruzamentos necessários funcionam (ver "Fatos
validados"). Este documento define **o que normalizar**, **como** (pipeline PostGIS → tabelas
planas), o **modelo de impressões/score** e a **camada de classe social**.

## Decisões de arquitetura (tomadas pelo dono)

1. **Híbrida (C):** PostGIS faz o cálculo espacial pesado num **pipeline** no banco `ooh` (tem
   PostGIS 3.4.3) e **materializa tabelas planas por linha**; o app Java de produção
   (`uai_buslines`, sem PostGIS) lê com `SELECT` puro. Respeita ADR-003 e espelha o padrão atual
   do app (precompute JTS → `line_neighborhood`).
2. **Ingestão Python (reusa `.data/scripts/`) + serving Java.** Coleta recorrente não é
   necessária agora; não bloqueia a normalização.
3. **CRS unificado = EPSG:31983** (métrico) para cálculo; 4326 (lat/lon) para web.
4. **Alcance back bus = IMPRESSÕES / contatos visuais** = exposição externa (corredor + trânsito)
   **+ passageiros**. Pessoas únicas não são mensuráveis pela natureza da mídia.
5. **Classe social é requisito** (A3/A5). Censo 2022 **TEM renda por setor** (rendimento do
   responsável pelo domicílio, universo) → renda **medida** é a âncora da classe social.

### Respostas a duas perguntas de arquitetura
- **Scripts Python viram ingestor?** Sim, o miolo (descoberta CKAN+UA, auto-encoding, decode
  protobuf RT, geo→WKT, COPY). O que muda p/ serviço: `DROP+CREATE`→upsert idempotente,
  watermark/ETag, dedup, retry/observabilidade, agendamento. **Recomendação: Python na
  ingestão/pipeline, Java no serving** (híbrido coerente com a decisão 1).
- **Webservices relevantes?** **Contagem volumétrica de radar = sim e é PRÉ-REQUISITO** (ver
  Épico 0). RT trip-updates = fase tempo real (B). RT alerts = vazio. `tempo_real_onibus`
  (SUMOB) = duplica vehicle-positions, só fallback.

## Fatos validados (MCP/PostGIS)
- **Linha:** `route_short_name` casa ~99% com MCO/embarque. 304 linhas; `SC*`=circular centro,
  `S*`=suplementar.
- **Ponto (resolve o atrito):** GTFS `stop_id` ↔ `ponto_onibus` por **geo** — mediana 0m, p90
  3,9m, **99% ≤50m**. `SIU`↔`stop_id` direto = 0 (universos de ID distintos), mas KNN ≤50m
  reconcilia e dá a ponte pro embarque.
- **Itinerário:** shape reconstruível (`ST_MakeLine` ord. por `shape_pt_sequence`); 4107 = 14
  shapes, 2 sentidos, 13,9 km.
- **Perfil:** corredor `ST_Buffer(shape,300)` ∩ censo (join malha+agregado por `cd_setor`) =
  4107 → **471 setores / ~182.566 residentes**.
- **Veículo:** MCO `veiculo` ↔ RT `vehicle_id` ~81%.
- **Armadilha:** geometrias PBH no `raw` têm **SRID=0** → sempre `ST_SetSRID(...,31983)`;
  GTFS/OSM em 4326 e censo em 4674 → `ST_Transform(...,31983)`.
- **Temporal:** GTFS/RT/OD mai/2026; MCO set/2025 (volume); embarque mai/2024 (**só
  distribuição**, não volume); censo 2022; pesquisa 2012 (**descartar** como volume).
- **Reaproveitar do app:** `dataset_version` (swap atômico ACTIVE/IMPORTING/ARCHIVED), `line`,
  `stop`, `line_shape`, `line_neighborhood`, domínio hexagonal (records puros, sealed
  `LineRelation`). `LineDetail.stops` está vazio (TODO) — resolvido por `core.line_stop`.

## ⚠️ Premissas e limites de honestidade (LER ANTES DE VENDER NÚMEROS)

- **O que é vendável hoje:** a **comparação/ranking entre linhas** e o **score 0–100**. Os
  sub-scores e a ordenação são robustos (baseados em dados medidos: população do corredor, renda
  por setor, POIs, e **exposição arterial** do trajeto — % do percurso em vias de alto fluxo,
  de dado viário atual e completo).
- **O que NÃO é vendável como número confiável hoje:** o **número absoluto de impressões**. Os
  coeficientes `f_exposicao`, `k_traf`, `ocupacao_media`, `p_vista_traseira` são **chutes de
  mercado não calibrados**. Enquanto não houver **calibração por estudo de campo**, o valor
  absoluto de impressões é ilustrativo, não métrica. Vender o absoluto agora é prometer precisão
  falsa.
- **Margem ±35% = "faixa indicativa", NÃO margem estatística.** É otimista dado que combina
  coeficientes chutados + trânsito modelado como **exposição arterial** (onde a linha roda), não
  volume de veículos medido. Rotular como tal em qualquer saída.
- **Mistura temporal de fontes** declarada (mai/2026 GTFS/OD; set/2025 MCO; mai/2024 embarque-
  distribuição; 2022 censo).
- **Sobreposição não descontada** entre população do corredor e passageiros (OTS, não pessoas
  únicas) — explícito ao cliente.
- **Renda** é do **responsável pelo domicílio** (não renda domiciliar total), universo 2022 — mas
  é **dado medido**, não proxy.

## Schema `core` (no banco `ooh`)

Convenção: `geom_31983 geometry(...,31983)` p/ cálculo + `geom_geojson jsonb` (4326) p/ web;
`dataset_version` FK onde versiona por release GTFS, `valid_from/valid_to/is_current` p/ fontes
externas (censo/POI/viário).

| Tabela core | Propósito | Colunas-chave | Alimentada por (raw) | Reconciliação |
|---|---|---|---|---|
| `core.line` | catálogo (A1) | short_name, long_name, gtfs_route_id, categoria, is_circular_sc, is_suplementar, extensao_m | `gtfs__routes` | short_name=route_short_name |
| `core.stop` | pontos | gtfs_stop_id, **siu**, match_dist_m, name, geom_31983, lat, lon | `gtfs__stops` (+`ponto_onibus`/`embarque`) | **KNN ≤50m** |
| `core.line_shape` | trajeto/direção/**tipo de dia** (A1) | line_id, **service_type**(util/sab/dom), direction, geom_31983(LineString), geom_geojson, comprimento_m | `gtfs__shapes`+`trips`+`routes`+`calendar` | shape mais longo por (service_type, direction) — **itinerário varia por dia** |
| `core.line_stop` | sequência ponto×linha×dia | line_id, **service_type**, stop_id, direction, stop_sequence, is_terminal | `gtfs__trips`+`stop_times`+`calendar` | trip representativo por serviço |
| `core.vehicle` (+`vehicle_line_history`) | frota (auxiliar) | veiculo_code, rt_vehicle_id, empresa / linha, tipo_dia, n_viagens | `mco_consolidado`+`rt__vehicle_position` | veiculo=vehicle_id (~81%) |
| `core.census_sector` | setor + pop + **renda/classe** (A3) | cd_setor, populacao, domicilios, densidade_hab_km2, **renda_media_resp**, **renda_mediana_resp**, **classe_renda(A–E)**, geom_31983 | `censo__setores_bh`+`agregados_basico_bh`+**(Épico 0)** `renda_responsavel` | cd_setor; geom 4674→31983 |
| `core.poi` | POIs (A7) | osm_type/id, categoria, name, geom_31983 | `osm__poi` | 4326→31983 |
| `core.road_segment` | malha viária c/ classe (trânsito F1) | id_trecho, tipo_logradouro, largura, **classe_arterial** (arterial/coletora/local), is_via_referencia, geom_31983(LineString) | `trecho_logradouro` (tipo+largura) + `velocidade_corredores` (35 VRs + velocidade onde houver) | tipo+largura→classe; VR por nome/geo. Radar/volume = **v2** |
| `core.line_demand_daily` | volume MCO | line_short_name, tipo_dia(1/7/8), n_viagens, total_usuarios, usuarios_por_viagem | `mco_consolidado` | linha→short_name (volume set/2025) |
| `core.line_boarding_profile` | curva horária | line_short_name, faixa_horaria(0–23), **share_embarque** | `embarque_ped_sublinha` (`c_0..c_23`) | siu→linha (**distribuição** mai/2024) |
| **`core.line_metrics`** [→app] | saída p/ produto (A2/A4) | pop_corredor(_ponderada), renda_media_pop, classe_predom, pct_pop_classe_ab/de, n_poi_*, **exposicao_arterial_pct**, classe_via_predom, passageiros_{util,sab,dom}, **impressoes_{util,sab,dom}** (+ componentes), faixa_indicativa_pct, **score_total** + 5 sub-scores | pipeline | — |
| **`core.line_profile_demografico`** [→app] | distribuição A–E por linha (A3/A5) | line_id, classe_renda, pop_na_classe, pct_pop | pipeline | — |

DDL completo (tipos/PK/FK/CHECK) detalhado no design da sessão; seguir na implementação dos
Épicos 1–4.

## Pipeline PostGIS (precompute → materializa)
Todos no banco `ooh`. Regra de ouro: `ST_SetSRID(geom,31983)` nas PBH (SRID=0); `ST_Transform`
nas 4326/4674.
> **Índices na `raw`:** cada passo cria, ao rodar, os índices que precisa na raw — geo via temp-table
> GiST; chaves de junção via `CREATE INDEX IF NOT EXISTS` + `ANALYZE` (deixar). Persistência `INSERT…SELECT`
> server-side / `COPY`; volume processado no Python → reusar a **classe de persistência em chunks** do
> pipeline. Convenção e tabela por tarefa em `arquitetura-servicos.md` §"Índices na `raw`".
1. **KNN ponto↔ponto_onibus** (`<->`, `ST_DWithin(...,50)`) → `core.stop.siu`+`match_dist_m`.
2. **Shape/direção** (`ST_MakeLine` ord.) + `core.line_stop` via stop_times.
3. **Corredor `ST_Buffer(300)` ∩ censo** → pop ponderada por área + renda média ponderada por pop
   + distribuição A–E → `line_profile_demografico` + perfil em `line_metrics`.
4. **POIs no corredor** por categoria → `n_poi_*`.
5. **Exposição arterial do trajeto** → classificar trechos de `trecho_logradouro` (tipo de
   logradouro + largura → arterial/coletora/local; **não é classificação funcional oficial, é
   proxy de hierarquia** — AVENIDA/RODOVIA/VIA + largura alta = arterial) e medir
   `exposicao_arterial_pct` = % do comprimento do shape em via de alto fluxo. Refinar com as 35
   **Vias de Referência** + velocidade real onde houver cobertura (`velocidade_corredores`,
   2019–20). **Não é volume nem congestionamento — é ONDE a linha roda.**
6. **Demanda:** MCO→`line_demand_daily` (volume); embarque→`line_boarding_profile` (distribuição
   normalizada `c_h/total_geral`).
7. **Impressões+score** → `line_metrics`.
8. **Export** `line_metrics`/`line_profile_demografico` (sem geom) p/ `uai_buslines`, swap atômico
   via `dataset_version`.

## Modelo de impressões (back bus) e score
**Impressões(tipo_dia d)** = `E_pop + E_traf + E_pax`, todas como contatos visuais:
- `E_pop = pop_corredor_ponderada × f_exposicao × freq_norm(d)` (f_exposicao default 0,15 — **não
  calibrado**)
- `E_traf` (exposição externa em via) = **adiado para v2**. Em F1 não há volume nem
  congestionamento medido, então **não entra no número absoluto de impressões** (que já é
  ilustrativo). O sinal de trânsito de F1 vive no **score** (sub-componente exposição arterial),
  não nas impressões. *(v2: `E_traf` passa a usar intensidade de congestionamento do coletor
  próprio como proxy de dwell time — ver seção v2.)*
- `E_pax = total_usuarios(d) × p_vista_traseira` (0,6 — **não calibrado**)
- **faixa indicativa ±35%** (não é margem estatística — ver premissas).

> Reforço: por causa dos coeficientes não calibrados, **só o ranking relativo e o score são
> vendáveis**; o valor absoluto de impressões é ilustrativo até estudo de campo.

**Score 0–100** = `100×(0,35·alcance + 0,15·densidade + 0,20·perfil + 0,20·exposição_arterial + 0,10·POI)`,
pesos parametrizáveis. O componente de trânsito = **exposição arterial** (`exposicao_arterial_pct`:
qualidade de via do trajeto, de dado viário **atual e completo**) — mantém peso respeitável (não é
"elo fraco datado"). **Não é volume nem congestionamento — é onde a linha roda** (visibilidade do
back bus em via de alto fluxo). `s_perfil` é onde A5 (público-alvo) recalibra (favorece A/B por
padrão, invertível por campanha). Sub-scores min-max entre linhas, pré-calculados → re-rank por
pesos em memória no app (sem PostGIS).

## Classe social (A3/A5) — renda medida por setor
Censo 2022 **tem renda por setor** (rendimento do responsável pelo domicílio, universo). Âncora =
renda; classe A–E por **quintis (ou faixas em salário mínimo) de BH**:
- `renda_media_resp`/`renda_mediana_resp` por setor; classificar em A–E.
- Opcional: índice composto leve combinando renda (peso alto) + infraestrutura domiciliar.
- Por linha: renda média **ponderada por população do corredor** + % pop por classe + rótulo
  predominante → "linha X passa por região mais rica/pobre".

## Consumo pelo app Java (aditivo, sem quebrar)
- Migração `V2__create_metrics_tables.sql` em `uai_buslines`: `line_metrics`,
  `line_profile_demografico` (sem geom), `line_stop`.
- Domínio: novos records `LineMetrics`, `LineImpressions`, `DemographicProfile`, sealed
  `ClasseRenda`. Não alterar `LineDetail` — usar novo método/record.
- Ports: `QueryNetworkUseCase.lineMetrics(id)`, `rankLines(weights)`;
  `NetworkQueryRepository.findMetricsByLineId(...)`. Aditivos.
- Persistence: `LineMetricsEntity` + repo + mapper. SELECT plano por `(line_id, dataset_version
  ACTIVE)`.
- Web: `GET /api/lines/{id}/metrics`, `GET /api/lines/ranking?weights=...`. `LineController`
  intacto.

## Épicos
- **Épico 0 — Lacunas de dados:** (0.1) renda do censo por setor — **✅ concluída** (`raw.censo__renda_responsavel_bh`).
  (0.2) contagem volumétrica de radar — **fonte indisponível → reclassificada para v2**; em F1 o
  trânsito é coberto por **exposição arterial** (dado viário atual, já no `raw`), então **o Épico 0
  não bloqueia mais por trânsito**. Detalhe em `docs/epicos/epico-0-lacunas-dados.md`.
- **Épicos 1–4 — Normalização `core`** (detalhados, incorporando os aprendizados da simulação 4107):
  - **[Épico 1 — Identidades](epicos/epico-1-identidades.md)**: line/stop(KNN)/line_shape(repr.)/line_stop/vehicle.
  - **[Épico 2 — Contexto](epicos/epico-2-contexto.md)**: census_sector(renda+classe), poi, road_segment(arterial).
  - **[Épico 3 — Métricas & score](epicos/epico-3-metricas-score.md)**: corredor×camadas, impressões, score 0–100 (304 linhas).
  - **[Épico 4 — Serving](epicos/epico-4-serving-app.md)**: export→`uai_buslines` + V2 + endpoints Java (sem PostGIS).
- **v2 — Sinal de trânsito próprio (NÃO bloqueia F1):** coletor de congestionamento (ver seção v2).

## Verificação (quando os Épicos 1–4 rodarem)
- **Reconciliação ponto:** `% de core.stop com siu não-nulo` + histograma `match_dist_m` (~99% ≤50m).
- **Cobertura linha↔shape↔demanda:** toda `core.line` tem ≥1 `line_shape`; `% com
  line_demand_daily` (~99% via short_name); listar não-casados (esperado: suplementares).
- **Perfil:** recomputar 4107 e conferir ~471 setores / ~182k.
- **Classe de renda:** distribuição A–E ≈ quintis; bairros notoriamente ricos/pobres caem em A/E.
- **Exposição arterial:** `% de linhas com exposicao_arterial_pct` calculado; sanity: linhas em
  grandes avenidas (Contorno/Amazonas/Antônio Carlos) têm exposição alta vs linhas de bairro.
- **line_metrics:** score 0–100 sem nulos; ranquear top/bottom 10 e validar com mercado; valor
  absoluto de impressões rotulado como ilustrativo.
- **App:** `GET /api/lines/{id}/metrics` sem tocar PostGIS; `mvn verify` (Testcontainers
  postgres:16) passa com a V2.

## v2 — Sinal de trânsito próprio (evolução; NÃO bloqueia F1)
Evolução planejada do componente de trânsito, no mesmo padrão arquitetural do
`uai-ooh-realtime-poller`.
- **O que:** serviço de polling que consulta uma API de tráfego ao vivo (ex.: Waze via
  OpenWeb Ninja/Zyla, ~US$25–50/mês, focado nos corredores das linhas vendidas) a cada ~15min e
  **persiste os snapshots** no Postgres → histórico próprio de **congestionamento por corredor ×
  horário × dia-da-semana**.
- **Por que próprio:** as APIs só dão dado ao vivo (sem histórico). Persistindo ~2 meses,
  construímos o dataset histórico que não existe pronto → **ativo proprietário** (ninguém em BH
  tem).
- **Por que serve ao back bus:** o dado é **intensidade de congestionamento** (não volume) — e
  para back bus é até melhor: **carro parado no congestionamento vê MAIS o anúncio** (mais dwell
  time = mais exposição). Vira um **segundo** sinal de trânsito, distinto e complementar:
  - **Exposição arterial** (F1, `trecho_logradouro`) = **ONDE** a linha roda.
  - **Intensidade de congestionamento** (v2, coletor próprio) = **QUÃO parado** está o trânsito ali.
- **Modelo quando maduro:** `E_traf` das impressões passa a **combinar os dois**, recalculado
  usando congestionamento como **proxy de dwell time** (NÃO contagem de veículos); o peso do
  conjunto "trânsito" no score pode subir.
- **Cronograma:** o coletor pode **começar a acumular histórico já, em paralelo**, sem bloquear
  F1; o componente evolui quando houver massa de dados (~2 meses mínimo).

## Arquivos críticos
- `git show legacy-frozen:src/main/resources/db/migration/V1__create_tables.sql` (referência do schema do app legado — código removido do HEAD, vive na tag) → futura `V2__create_metrics_tables.sql`
- `.data/scripts/load_csv_generic.py`, `load_osm.py` (reuso p/ Épico 0)
- `git show legacy-frozen:src/main/java/com/uai/buslines/domain/port/in/QueryNetworkUseCase.java`
- `git show legacy-frozen:src/main/java/com/uai/buslines/adapter/out/persistence/JpaNetworkQueryRepository.java`
- Novos scripts de pipeline PostGIS em `.data/scripts/` (raw→core no banco `ooh`)