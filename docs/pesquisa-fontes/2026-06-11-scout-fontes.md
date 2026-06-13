# Scout de fontes de dados OOH — relatório (rodada 1) + footfall por área (rodada 2)

> Gerado pelo workflow `ooh-data-scout` em 2026-06-11. Varredura **verificada** (3 votos adversariais por
> claim; cota mínima por ângulo) de fontes pras lacunas do uAI-OOH. **Motivação desta leva:** investigar
> dados de **população em trânsito por área** pra calibrar **alcance e impressão do back bus** (mídia
> traseira de ônibus) — e, de quebra, enriquecer a frente de bancas (ver [README](../../README.md) §Frentes).

## Achado do banco que motivou a busca

O `core.exposure_cell.footfall` (188.280 células H3 × faixa horária) está **populado, mas é proxy
estático**: `footfall = 82` **constante nas 24 faixas horárias** — não varia da madrugada ao rush ⇒ não é
medição de pessoas em trânsito, é densidade estrutural replicada. O `unicos_hora` varia, mas é
**passageiro de ônibus** (O-D de bilhetagem), não footfall externo. A impressão do back bus pendura em
`f_exposicao = 0.15` e `p_vista_traseira = 0.6`, ambos coeficientes **"ilustrativos"** (ver
[`proxies-e-premissas.md`](../proxies-e-premissas.md)). Logo: uma camada de **footfall medido por área ×
hora** é o que estreita o ±35% declarado e dá lastro ao `f_exposicao`.

**Fixture de teste** (mídia fixa / banca, mas serve de sonda de footfall): Banca Glória — POI `164899`,
Av. Afonso Pena 726, `-19.919615, -43.938639`, 12 m da Praça Sete. Footfall extremo: **222 moradores vs
1.801 POIs em 150 m** — prova que população residente do censo subestima o ponto de maior circulação de BH.

---

## Rodada 1 — relatório (ângulos: tráfego, telemetria, demanda, POI, metodologia)

> 34 achados confirmados, 5/5 ângulos com cota batida, 0 lacunas. Cada fonte classificada **SUBSTITUI** /
> **COMPLEMENTA** / **NÃO VALE** o que o produto já usa.

### Maiores ganhos pra alcance/impressão do ônibus

1. **Metodologia (2× SUBSTITUI):** adotar a hierarquia **MRC** (Location Traffic → Gross → OTS → LTS) e o
   **VAI do Geopath** (tabela de fatores de visibilidade por atributo físico — tamanho, lado, distância,
   iluminação — **não um coef global**), com o fator de visibilidade do **Route** (eye-tracking, cone 120°).
   É o que troca o `footfall=82`-constante por uma camada **Location Traffic** e os coef-chute por **VAI**.
2. **Tráfego — Waze via OpenWeb Ninja (~US$25/mês):** fluxo veicular por corredor × hora = quem, em outros
   veículos, vê a traseira do ônibus. TomTom/HERE **caem** por termos que proíbem persistir histórico.
3. **Telemetria — campo `VL` do feed bruto `temporeal.pbh.gov.br` (SUBSTITUI):** velocidade já populada na
   fonte, elimina a derivação por diff de GPS que causou o bug `v_real` (mediana 450 km/h).

### Tabela por ângulo

**Tráfego (6):** Waze for Cities (NÃO VALE — só órgão público) · OpenWeb Ninja/Waze (COMPLEMENTA, coletor
viável ~US$25/mês) · TomTom Flow (COMPLEMENTA p/ live, **NÃO VALE** p/ histórico — termos) · HERE
(COMPLEMENTA fraco) · INRIX (NÃO VALE — enterprise).

**Telemetria (8):** `temporeal.pbh.gov.br` campo **VL** (SUBSTITUI) · campo **DG**/heading (COMPLEMENTA) ·
layout `EV;HR;LT;LG;NV;VL;NL;DG;SV;DT` (COMPLEMENTA) · GTFS-RT mobilibus atual (NÃO VALE — já consumido,
occupancy 100% NULL) · feed Suplementar S* (COMPLEMENTA — cobre os 3,3% `padrao_desconhecido`) ·
Transitland / Mobility Database (NÃO VALE — só estático) · **ocupação/APC aberta: lacuna persiste**.

**Demanda (7):** Matriz O-D bilhetagem (COMPLEMENTA — mensal, fresco mai/2026) · MCO (COMPLEMENTA —
defasado ~8-9 meses) · GTFS estático (COMPLEMENTA — vivo) · GTFS-RT oficial (SUBSTITUI 2/3 — hipótese) ·
embarque-PED (NÃO VALE — morto mai/2024) · pesquisa sobe/desce (NÃO VALE — 2012) · SUMOB (NÃO VALE).

**POI (6):** **Overture Maps** (SUBSTITUI — base materializável, US$0, licença aberta, mensal) · Foursquare
OS Places (COMPLEMENTA — exige filtragem pesada fora dos EUA) · Google Places (COMPLEMENTA — só
enriquecimento on-the-fly, proíbe cache). Requisito: propagar `source` por POI + NOTICE da Foursquare.

**Metodologia (7):** MRC 4 níveis (SUBSTITUI) · Route eye-tracking (SUBSTITUI) · Route detecção via painel
GPS (COMPLEMENTA — uAI já tem shapes+O-D) · Route amostra/governança (COMPLEMENTA — benchmark) · MRC/VAC
(COMPLEMENTA — exige calibração local periódica) · Geopath VAI (COMPLEMENTA — circulação de
ridership+DOT+GPS+O-D, VAI por atributo) · OTS genérico (NÃO VALE — já adotado).

### Lacunas honestas (sem solução nos achados)

- **Ocupação/APC em tempo real:** nenhuma fonte aberta — fallback MCO×viagens-verificadas (defasado).
- **Histórico de congestionamento:** só via coletor próprio (OpenWeb Ninja); TomTom/HERE proíbem persistir.
- **Calibração de visibilidade local:** MRC exige estudo de campo periódico — não há painel/auditoria hoje.
- **Footfall de pedestre por área (telemetria de celular/mobility):** o ângulo "telemetria" desta rodada
  virou GPS de ônibus; footfall geográfico **não foi varrido** → é o objeto da **rodada 2** (abaixo).

---

## Rodada 2 — footfall por área (Task `w7zw7d6ut`, 33 achados)

> Ângulos: `mobility_celular`, `geomarketing_br`, `footfall_global`, `populacao_flutuante`, `mobility_aberto`.
> ⚠️ **Verificação turbulenta:** muitos votos falharam por **limite de sessão**; foram refeitos no reforço.
> Achados-chave têm fonte primária; revalidar os marcados **2/3**. (Rodadas anteriores 2 e 3 saíram
> redundantes por bug de passagem de `args` — corrigido editando `DEFAULT_ANGLES` no script.)

**Conclusão: não existe footfall por face × hora pronto e barato. O dado pra DERIVAR existe e é local/grátis.**

### 🟢 Caminho gratuito — arquitetura derivada (recomendado)
- **Matriz O-D RMBH por telefonia (VIVO, 2019/2021)** — Agência RMBH. Toda a população 18+ da RMBH, **por
  faixa horária** e **por zona** (shapefile), **aberta/grátis**. Captura carro+a pé+tudo, não só ônibus.
  Fonte: `agenciarmbh.mg.gov.br/pesquisa-od`. (3/3)
- **Censo 2022 setor censitário** (já no banco) — baseline residente noturno; IBGE só dá residente ("de jure"),
  população presente **tem de ser derivada**. (3/3)
- **O-D bilhetagem PBH** (já é o `od_trip`) — mensal/viva, por faixa horária, só passageiro. ⚠️ **2/3 — revalidar.**
- Receita: **pop diurna por zona×hora = Censo × matrizes O-D** → preenche `exposure_cell.footfall`. Custo zero.

### 🟡 Upgrade pago (futuro)
- **Unacast Area Visitors** — **H3 × hora nativo** (bate o esquema exato), US$1/chamada + sample grátis.
  **Cobertura de BH NÃO confirmada → validar via sample antes de adotar.** (SUBSTITUI, 3/3)
- **Claro Geodata** / **Vivo Smart Steps** — já entregam alcance/frequência/GRP OOH; projeto B2B caro
  (~centenas de milhares de R$). **Claro fechou parceria com a Eletromídia (ago/2025) → concorrente direto.**

### 🔴 Descartados
SafeGraph/Advan e Placer.ai (só EUA) · Veraset (US$40k) · Inloco/Incognia (saiu do mercado, 2020) ·
Geofusion/Cortex (fluxo modelado semanal, sem preço público) · Meta Movement (tiles 3×3 km, grosso demais) ·
WorldPop/HRSL/GHS-POP (residencial estático — só normalizadores).

## Validação das fontes (2026-06-12)

**O-D telefonia RMBH — baixada e medida (✅ serve, com ressalva).**
- Matriz por faixa horária: **686.228 pares O-D**, CSV `ue_or, ue_ds, faixa_horaria, periodo, vol_universo`.
- Zoneamento: **393 zonas na RMBH, 165 em BH** (shapefile `id, munic_ibge, munic_nome, populacao` + geometria).
- **5 faixas horárias** (23–6 / 6–10 / 10–15 / 15–19 / 19–23) × **2 períodos** (nov/2019, mai/2021).
- Links: matriz horária = Google Sheets id `1cXo9Y6dR2KXuPHAafjfJweRdIIN-Mc1y` (export `?format=csv`); zoneamento
  = `agenciarmbh.mg.gov.br/wp-content/uploads/2021/08/shp_rmbh_v3.zip` (**baixar com `User-Agent`** — sem ele dá 93 bytes).
- **Ressalva:** zona OD ~30× mais grossa que setor censitário (165 vs 5.166 em BH); é matriz de VIAGENS
  (derivar presença por zona×faixa = chegadas+partidas) e estática (2019/21). **Uso:** telefonia dá a FORMA da
  curva horária por zona; Censo + POI (já no banco) desagregam no espaço fino → footfall por setor/H3 × hora.

**Unacast — Brasil confirmado; BH + sample dependem de cadastro.**
- Busca confirma cobertura do **Brasil** (lat/long, cidade, estado, CEP) — melhor que o "não confirmado" do scout.
- Cidades específicas (BH) não públicas; sample exige cadastro (`go.unacast.com` / Datarade "request sample") —
  ação do dono. Como a O-D telefonia já cobre o eixo horário de graça, a Unacast fica **opcional** (só p/ H3 fino).

## Contratos Claro Geodata + fontes grátis adicionais (2026-06-12)

**Custo Claro Geodata — benchmark real (2 agentes de garimpo):**
- **CPTM × Claro = R$ 810 mil** (pregão PE22524, set/2025) — estadual SP, matriz O-D + API + histórico desde 2023.
  É o **único valor primário** e representa o **teto** (projeto grande). [teletime](https://teletime.com.br/09/09/2025/cptm-e-claro-firmam-parceria-para-analisar-fluxo-de-passageiros/)
- **SMTUR Rio** (Embratel/Claro Geodata turismo) — sem valor público; está no Contas Rio (consulta manual/LAI).
- **Faixa:** ~R$ 50 mil–810 mil/projeto, por abrangência × período × API. Escopo BH/banca → dezenas a baixas
  centenas de milhares. Sem tabela pública; cotação por projeto.

**Fontes grátis NOVAS pra BH (não estavam na rodada 1):**
- 🟢 **CNEFE — IBGE 2022** (game-changer pra banca): todos os endereços de BH georreferenciados (lat/long) com
  espécie (domicílio/**estabelecimento**) → **densidade comercial fina por ponto = footfall diurno na
  granularidade que a banca precisa, de graça**. [ibge](https://www.ibge.gov.br/estatisticas/sociais/populacao/38734-cadastro-nacional-de-enderecos-para-fins-estatisticos.html)
- **Embarque/desembarque por PONTO DE PARADA** (BHTrans, CC-BY, CSV, mensal, por parada) — footfall na parada.
  [ckan.pbh](https://ckan.pbh.gov.br/dataset/estimativa-de-embarque-nos-pontos-de-parada) (cruzar c/ dataset "Ponto de Ônibus" p/ geo).
- **Grade Estatística 200 m** (IBGE 2022) — pop/domicílios em células 200m; melhor que setor p/ heatmap.
- **CEMPRE** (IBGE) — empregos por município/CNAE (proxy footfall diurno, só municipal). **OSM Overpass** —
  densidade de amenities on-demand (marginal vs Overture).
- 🟡 **Strava Metro** — fluxo pedestre/bike por segmento, grátis p/ planejadores, **mas cobertura BH incerta +
  viés (esportistas)**. Candidatura só pra testar.
- 🔴 Descartados: Tembici (sem GBFS em BH), câmeras CFTV (sem dado aberto), contador de ciclistas (1 ponto),
  Wi-Fi público (só localização), Kaggle "Mobility RMBH" (= é a própria O-D telefonia, redundante).

**Revisão da estratégia da banca:** granularidade fina **não exige mais pagar**. Footfall estrutural grátis =
`CNEFE (onde fino) × perfil horário telefonia VIVO (quando) + Grade 200m + embarque/parada`. Claro/Unacast viram
calibração/validação com receita, não pré-requisito.

## Validação: embarque por parada + grade 200m (2026-06-12)

**Embarque por parada — JÁ temos o bruto, mas não normalizado como footfall.** `raw.pbh__embarque_ped_sublinha`
= **8.444 pontos geolocalizados** (lat/long), embarque **por hora** (`c_0..c_23`) + `total_geral`, 290 linhas. No
`core` virou só `line_boarding_profile` (`linha × faixa × share_embarque`) — **descartou ponto, volume e coords**.
Pra usar como footfall por parada é uma re-normalização raw→core (o dado fino já está lá). Vigência mai/2024 (série
cessou); dataset vigente em `ckan.pbh.gov.br/dataset/estimativa-de-embarque-nos-pontos-de-parada` pode reabastecer.

**Grade Estatística 200m IBGE — baixada e cruzada (válida).** Célula **221 m**, SIRGAS 2000 (graus), quadrícula
`grade_id36` cobre BH (`geoftp.ibge.gov.br/.../grade_estatistica/censo_2022/grade_estatistica/`). Granularidade BH:
setores 5.166 / hex H3 2.615 (~340m) / **grade ~12 mil células de 221m uniforme**. Pop concorda com nossos dados
(mesma fonte Censo 2022) na mesma área. **Achado:** a grade cobre a **RMBH inteira** — nosso banco só tem BH
município; expandir OOH p/ Contagem/Betim já tem densidade grátis (como a telefonia O-D). Pra banca, 221m ainda é
grosso (CNEFE por endereço é mais fino). Dados em `/tmp/odrmbh/g36`.

**Strava Metro — descartado.** Sem download aberto; acesso só por candidatura ao programa (provável inelegível como
privada, igual Waze for Cities) + viés (esportistas). Reavaliar só se houver parceria com órgão público.
