# Feature: Mapa de Calor da Audiência — tela de análise de linhas (ativáveis)

> **Master da feature** (hub PM). Vertical OOH BH Bus Mídia · Status: proposta (pré-implementação).
> **Especificação VISUAL pronta e rodável:** [`uai-ooh-pipeline/docs/design/mapa-alcance-simulacao.html`](../../../uai-ooh-pipeline/docs/design/mapa-alcance-simulacao.html) — protótipo interativo com dados reais (2.615 hexágonos H3, alternador Audiência/Renda/População, ônibus simulando alcance×impressões×frequência). **O front deve reproduzir esse comportamento.**
> Modelo de dados por trás: [`modelo-alcance-analise.md §0.6`](../../../uai-ooh-pipeline/docs/design/modelo-alcance-analise.md) (superfície de exposição `exposure_cell`).
> Lanes: **Dados** = `uai-ooh-pipeline` (materializa `serving.exposure_cell`) · **API** = `uai-ooh-intel` · **Front** = este doc.

## 1. Problema e objetivo

Na **tela de análise de uma linha** (decidir se ativa/vende), o planejador comercial precisa *ver* **que público a linha cobre** — não só números. Hoje o front é line-centric e textual. Queremos sobrepor a **linha** ao **mapa de calor da superfície de exposição** (a "camada invisível" de dados sob a cidade), com camadas alternáveis (audiência única / renda / população / footfall), pro planejador entender o alcance e o perfil **de relance** e bater o martelo de ativar.

**Resultado-alvo:** abrir a análise da linha 4107 → ver o corredor dela traçado sobre o heat-map → trocar pra "Renda" e ver que ela cruza eixo nobre → ler o resumo (alcance único, impressões, frequência, % classe A/B) com selo de confiança → **Ativar**.

## 2. Onde encaixa

Módulo OOH do front (estende o `EP4-04-mapa-interativo`). A tela de **linhas ativáveis** ganha, ao selecionar uma linha, um **painel de mapa de calor** + resumo de audiência. Componente de heat-map é **reutilizável** (serve também pra prospecção de nichos e, no futuro, pra visão de campanha/face).

## 3. UI (mockup)

```
┌─ Análise da linha 4107 ───────────────────────────────────────────────┐
│ [Audiência ▸][Renda][População][POI]      🕐 hora: [todas ▾]  [Ativar]│
│ ┌───────────────────────────────────┐  ┌──────────────────────────┐  │
│ │                                   │  │ ALCANCE (corredor)       │  │
│ │      🗺️  heat-map H3              │  │   274.357 únicos  ⓘ MÉDIO│  │
│ │      + corredor da linha (overlay)│  │ IMPRESSÕES  611.760/mês  │  │
│ │      🚌 (simular passada)         │  │ FREQUÊNCIA  2,2×         │  │
│ │                                   │  │ ── perfil de renda ──    │  │
│ │   hover hex → audiência·renda·pop │  │ A/B ████ 41%  C ███ 33%  │  │
│ └───────────────────────────────────┘  │ D/E ██ 26%               │  │
│ legenda: ▮▮▮▮ audiência única          │ ⚠ audiência do CORREDOR, │  │
│                                         │   não da face (selo)     │  │
└───────────────────────────────────────────────────────────────────────┘
```

### Comportamento
- **Alternador de camadas:** Audiência única · Renda · População · POI/footfall (cada uma com sua escala de cor). *(igual ao protótipo)*
- **Overlay do corredor** da linha selecionada por cima dos hexágonos.
- **Hover no hexágono** → tooltip com o vetor (`audiência · renda · pop`).
- **(F2) Slider de hora 0–23** → recolore pela curva horária (a superfície muda ao longo do dia).
- **(F2) Botão "Simular passada"** → anima o ônibus acumulando alcance/impressões/frequência (educa o cliente sobre a tríade).
- **Painel-resumo** da linha: alcance único do corredor, impressões/mês, frequência, distribuição por classe de renda dos hexágonos cobertos — **cada métrica com `selo_confiança` visível** (contrato de rótulo, ver §6).
- **Ativar** = ação da tela de linhas ativáveis (fora do escopo deste doc; só o gancho).

## 4. Componentes (React/MapLibre — contratos)

```ts
// Heat-map reutilizável (núcleo da feature)
interface HeatMapAudienciaProps {
  layer: 'audiencia' | 'renda' | 'pop' | 'footfall';
  hour?: number | null;            // 0–23 ou null = dia todo (F2)
  lineId?: string;                 // overlay do corredor desta linha
  bbox?: [number, number, number, number]; // [w,s,e,n] p/ servir por viewport
  onHexHover?: (hex: HexProps) => void;
}
interface HexProps { h3: string; audiencia: number; renda: number; pop: number; footfall: number; }

interface LineAudienceSummary {
  lineId: string; shortName: string;
  alcanceCorredor: number; impressoesMes: number; frequencia: number;
  perfilRenda: { classe: 'A'|'B'|'C'|'D'|'E'; pct: number }[];
  selo: SeloConfianca;             // ver §6
}
```
Subcomponentes: `LayerToggle`, `HourSlider` (F2), `LineCorridorOverlay`, `LineSummaryPanel`, `SeloBadge`.
Stack: **MapLibre GL** (ou Leaflet, como o protótipo) · basemap claro (CARTO/OSM) · sem PostGIS/ST_* no front — o front **só pinta** o GeoJSON que recebe pronto.

## 5. Contrato de dados / API (o que destrava o front)

**Read-only via `uai-ooh-intel`** (sobre `serving` ACTIVE; sem regex/PostGIS em runtime). Depende da materialização de `serving.exposure_cell` (Fase 2/3 do roadmap do pipeline).

### `GET /api/v1/exposure/cells`
A superfície, como GeoJSON pronto pra pintar.
| Param | Efeito |
|---|---|
| `layer` | `audiencia\|renda\|pop\|footfall` → qual propriedade vem em `value` |
| `hour` (opc, F2) | `0–23`; ausente = dia todo |
| `bbox` (opc) | `w,s,e,n` (4326) → só hexágonos no viewport (servir por zoom) |
```jsonc
// FeatureCollection (4326). value = a métrica do layer pedido; o resto vem junto p/ tooltip.
{ "type":"FeatureCollection", "layer":"renda", "version_id":7,
  "features":[ { "type":"Feature",
    "properties":{ "h3":"89a88cdb403ffff", "value":7980,
                   "audiencia":2055, "renda":7980, "pop":982, "footfall":63 },
    "geometry":{ "type":"Polygon", "coordinates":[...] } } ] }
```

### `GET /api/v1/lines/{id}/audience`
Corredor + resumo da linha (alimenta o overlay e o painel).
```jsonc
{ "lineId":"562137", "shortName":"4107",
  "corridor": { "type":"MultiLineString", "coordinates":[...] },   // geojson 4326
  "alcanceCorredor":274357, "impressoesMes":611760, "frequencia":2.2,
  "perfilRenda":[{"classe":"A","pct":22},{"classe":"B","pct":19},...],
  "selo": { "nivel":"MEDIO", "rotulo":"audiência geográfica do corredor", "metodo":"F2-REACH" } }
```

### (F2) `GET /api/v1/lines/{id}/exposure/sequence`
Sequência ordenada de hexágonos do corredor (p/ a animação "Simular passada"): `[{ h3, audiencia, pos }]`.

## 6. Contrato de rótulo / selo (OBRIGATÓRIO — D4)

```ts
interface SeloConfianca { nivel: 'ALTO'|'MEDIO'|'BAIXO'; rotulo: string; metodo: string; }
```
- **Todo número** de alcance/impressão chega **acompanhado do selo** e o front **renderiza o selo junto** — nunca o número pelado.
- `reach`/`audiência` é rotulado **"audiência do corredor"**, NUNCA "alcance da face X". A UI exibe o `rotulo` no tooltip/legenda.
- (Recomendado) teste de UI que falha se um número de audiência for renderizado sem `SeloBadge`.

## 7. Não-funcionais

- Sem PostGIS/ST_* no front. O GeoJSON dos 2.615 hexágonos (~1MB completo) deve ser servido **por bbox/zoom** ou simplificado/tiled; cache no intel (Redis) por `(layer,hour,version_id)`.
- Estados de loading/vazio; debounce ao trocar camada/hora; versão ACTIVE resolvida server-side.
- Auth/edge conforme a plataforma (via `uai-cms`/`uai-auth`).

## 8. Dependências e faseamento

- **Bloqueado por:** `serving.exposure_cell` materializada (pipeline, Fase 2/3) + endpoints no intel. **Até lá**, o protótipo (`mapa-alcance-simulacao.html`) é a referência rodável e pode demoar com dados estáticos.
- **F1 (MVP):** camada **Audiência única** + overlay do corredor + painel-resumo com selo. (É o que o protótipo já mostra.)
- **F2:** camadas Renda/Pop/Footfall + slider de hora + "Simular passada".
- **F3:** reuso do componente na visão de **campanha/face** (heat-map da cobertura das faces compradas).

## 9. Roadmap / tasks

| Lane | Task | Repo | Depende de |
|---|---|---|---|
| Dados | materializar `serving.exposure_cell` (hex GeoJSON + métricas) | pipeline | `core.exposure_cell` (Fase 2) |
| Backend | `GET /exposure/cells`, `GET /lines/{id}/audience` (+ F2 sequence) | intel | serving acima |
| Front | `HeatMapAudiencia` + overlay + painel + `SeloBadge` (F1) | uai-ooh-web | intel acima |
| Front | camadas extras + hora + "simular passada" (F2) | uai-ooh-web | F1 |
