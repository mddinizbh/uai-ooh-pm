# Proxies, coeficientes e premissas (ledger de honestidade)

> Fonte única de verdade do que é **medido** vs **aproximado** nas fórmulas do produto OOH — estático
> (planejamento) e tempo real (verificado). Base do diferencial **"honestidade de medição"**: nenhum
> número é apresentado como mais preciso do que sua fonte permite.

## Regra de ouro (o que é vendável)
- ✅ **Vendável hoje:** o **ranking/score relativo entre linhas** (0–100) e a **comparação**; e, no
  tempo real, os **fatos operacionais verificados** (veiculação, km, itinerário rodado, pontos servidos).
- ⚠️ **NÃO vendável como número exato:** o **alcance/impressões absoluto** — depende de coeficientes
  de mercado não calibrados. Reportar como **estimativa com faixa indicativa (±~35%)**, ilustrativa,
  até calibração por estudo de campo.
- **Impressões = OTS (contatos visuais), não pessoas únicas.** Sobreposições são assumidas (declaradas).

## Chaves de junção (não são proxies, mas têm cobertura parcial)
| Chave | Liga | Cobertura | Tratar o resto |
|---|---|---|---|
| `route_short_name` | GTFS ↔ MCO ↔ embarque | ~99% | suplementares S* fora do estático |
| `stop_id` ↔ `SIU` (geo KNN ≤50m) | GTFS ↔ PBH (ponto/embarque) | mediana 0m, 99% ≤50m | ID direto = 0; só geo reconcilia |
| `trip_id` → `trip_pattern` | RT ↔ estático | 96,7% | 3,3% S* → `padrao_desconhecido` (route+dir+geo) |
| `vehicle_id` ↔ `veiculo` | RT ↔ MCO | 81% | defasado; **no realtime usar a LINHA do RT, não o carro** |

## Proxies — estático / planejamento
| Item | Aproxima | Fonte / vigência | Limitação | Calibrado? |
|---|---|---|---|---|
| **embarque por PED** (E_pontos) | presença de pessoas no ponto | `pbh__embarque_ped_sublinha` · mai/2024 | só embarque (não desembarque/espera/pedestre); série cessou; usar como **distribuição**, não volume | não (distribuição) |
| **E_pop_frontagem** (residentes ~30m, área-ponderada) | pedestres/moradores na testada | censo 2022 | overlap com E_pontos (OTS); faixa fina → fração do setor | não · coef pequeno |
| **classe arterial** (tipo+largura) | hierarquia viária (onde a linha roda) | `trecho_logradouro` (atual) | **não é classificação funcional oficial** — heurística AVENIDA/RODOVIA/largura≥20 | heurística |
| **renda do responsável** | renda domiciliar / classe social | censo 2022 universo | é do **responsável** (não domiciliar total); sigilo `X` em alguns setores | **dado medido** |
| **classe A–E** (quintis de BH) | nível socioeconômico | derivado da renda | relativo a BH | derivado |
| **velocidade_corredores** | velocidade da via (planejamento) | carro-sonda **2019–20**, 35 arteriais, 1 dia/corredor | datado, pré-pandemia, cobertura parcial | refinamento (não espinha) |
| **frequência do estático** | frequência real de viagens | GTFS `trips`/`calendar` | substituível pela **verificada** (RT) | até o RT verificar |
| **MCO `total_usuarios`** | volume de passageiros por linha | MCO · set/2025 | ~8 meses defasado; carros mudaram de linha | medido (defasado) |
| **pesquisa sobe/desce** | ocupação ao longo do trajeto | 2012 (pré-MOVE) | **descartar como volume** | descartado |
| **300m residencial/POI** | catchment de **perfil + prospecção** | censo/OSM | **NÃO usar p/ alcance** (só perfil A3/A5 e leads) | — |
| **matriz O-D** (`id_usuario`, H3) | pessoas únicas (quando dedup) | bilhetagem · mai/2026 | só quando alcance único for necessário | medido |
| **score 0–100** (min-max entre linhas) | adequação relativa | derivado | só comparativo; precisa das 304 linhas | relativo |

### Coeficientes de mercado — NÃO calibrados (parametrizáveis)
`f_exposicao`, `coef_ext`, `coef_frontagem`, `k_traf`, `ocupacao_media`, `p_vista_traseira`, `f_vel`.
São chutes de mercado → **só o ranking/score é defensável; o absoluto é ilustrativo**. Calibrar com 1
estudo de campo (estreita a faixa ±35%). `margem ±35%` = **faixa indicativa, não margem estatística**.

## Proxies — tempo real / verificado
| Item | Aproxima | Fonte | Limitação |
|---|---|---|---|
| **km por snap no shape** | distância percorrida real | RT + `trip_pattern` | vs odômetro; depende do shape do padrão |
| **v_real de passagem no ponto** | velocidade instantânea (alimenta `f_vel`) | RT (posições ~15–20s) | granularidade do polling |
| **completude** | % do itinerário percorrido | snap | — |
| **alcance verificado** | exposição externa real | `trip_executed` + `v_real` | **ainda usa `coef_ext`/`f_vel`** (só `v_s` vira real) — coeficientes seguem não calibrados |
| **occupancy (RT)** | passageiros a bordo | RT | **ausente** (veio vazio) → passageiro fica do MCO; opção: `viagens_verificadas × pax/viagem MCO` |
| **linha do carro (realtime)** | linha que o carro está rodando | RT `trip_id`→`route_id` | **sempre do RT, nunca MCO** (carros mudam de linha) → flag de divergência |

## Premissas transversais
- **Mistura temporal** (colagem de épocas): GTFS/RT/O-D mai/2026; MCO set/2025; embarque mai/2024;
  velocidade 2019–20; censo 2022. Declarar a vigência de cada número ao cliente quando relevante.
- **OTS, não pessoas únicas**; overlaps (pop×pontos, pop×passageiros) não descontados (impressões).
- **CRS:** cálculo em EPSG:31983; web em 4326. Geom PBH no `raw` têm SRID 0 (sempre `ST_SetSRID`).
- **O verificado (RT) é o chão sólido** dos fatos operacionais; o estimado (planejamento) é cenário —
  separar sempre "estimado" de "verificado" nas saídas (princípio do produto).
