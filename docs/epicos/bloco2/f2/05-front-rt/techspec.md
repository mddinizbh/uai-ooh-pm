# Detalhamento Técnico: lane front-rt (F2 · mapa universal + ao vivo + verificado×estimado)

> Techspec da lane **05-front-rt** (`uai-portal`, módulo OOH). Units = WEB-00..02.
> **Reescrita em 2026-06-10** (replanejamento face-centric + acumulador ao vivo F2-#6).
> Contratos em TypeScript (types, assinaturas de hook/componente — sem implementação).

## Contexto
Constrói o **componente de mapa universal** (camadas plugáveis — base de todo mapa do vertical) e,
sobre ele, o **mapa ao vivo** (carros + impressões subindo, via polling sobre a `PositionFeed`) e o
**painel verificado vs estimado** (selo por métrica, ADR-058). Referência de UX/contrato do mapa:
`uai-ooh-pipeline/docs/design/mapa-alcance-simulacao.html` (Leaflet — espelho visual, não código).

## Decisões Técnicas
- **Mapa universal com camadas declarativas** (WEB-00): telas compõem `layers`, nunca criam mapa próprio. MapLibre (consistência com EP4-04), não Leaflet.
- **Transporte atrás do hook `usePositions(scope)`** (polling no F2; SSE/WS = troca interna do hook). O componente **não sabe** o transporte.
- **Escopo = linha** (a tela passa `lineId`, nunca `vehicleCode`) — espelha o server-derived do back (F2-#2).
- **Selo por métrica (ADR-058) tipado no contrato** — vem do RT-02; o front nunca decide selo, só renderiza. Acumulado ao vivo = sempre ESTIMATIVA.

## Padrões do Projeto a Seguir
- React 18 + TS estrito · shadcn/ui · **recharts** (reuso F1) · MapLibre · `@tanstack/react-query` pro polling.

## Riscos Globais
- **Polling vs always-on:** cadence ~15s no hook (não martelar); erro de fetch não derruba a tela.
- **Selo:** acumulado/reach nunca renderizado como "medido".
- **Convergência do F1:** o mapa do EP4-04 migra pro universal sem big-bang (WEB-01 nasce nele; F1 converge depois).

---

## Unit: WEB-00 — mapa universal (camadas plugáveis)
- **Responsabilidade**: componente único de mapa com camadas declarativas; paridade de UX com a simulação (hex × hora, slider/play).
- **Localização**: `modules/ooh/map/UniversalMap.tsx`, `modules/ooh/map/layers/{H3SurfaceLayer,ShapeLayer,VehiclesLayer,AccumulatorOverlay}.tsx`.
- **Contrato**:
```typescript
type HexMetric = { h3Index: string; valuesByHour: number[] };       // espelha HEXES/METRICS da simulação
type MapLayer =
  | { kind: 'h3-surface'; metrics: HexMetric[]; hour: number; metric: string }
  | { kind: 'shape'; lineId: string; geojson: GeoJSON.Geometry }
  | { kind: 'vehicles'; positions: LivePosition[] }
  | { kind: 'accumulator'; impressoesParciais: number; selo: 'ESTIMATIVA' };

function UniversalMap(props: { layers: MapLayer[]; onHourChange?: (h: number) => void }): JSX.Element;
function useHourPlayback(initial?: number): { hour: number; playing: boolean; toggle(): void }; // slider+play
```
- **Pré-condições**: front F1 (EP4 módulo); endpoints de superfície do intel (serving).
- **Pós-condições**: superfície H3 × hora com slider/play + shape; camadas plugam sem alterar o base.
- **Verificação**: paridade visual com a simulação numa linha conhecida (4107).

## Unit: WEB-01 — mapa ao vivo (veículos + acumulado)
- **Contrato**:
```typescript
type LiveAccumulator = { impressoesParciais: number; hexesVisitados: number; selo: 'ESTIMATIVA' };
type LivePosition = {
  vehicleCode: string; lineId: string; lat: number; lon: number;
  bearing?: number; currentStopSequence?: number; completudeParcial: number;
  acumulado: LiveAccumulator; ts: string;
};
type RealtimeScope = { kind: 'line'; lineId: string };

interface PositionFeed { positionsForScope(scope: RealtimeScope): Promise<LivePosition[]> }
function usePositions(scope: RealtimeScope): { positions: LivePosition[]; loading: boolean; error?: Error };
```
- **Pré-condições**: WEB-00 + RT-01/RT-03.
- **Pós-condições**: carros se movem (~15s) + contador subindo com selo; transporte vive no hook.
- **Decisões locais**: react-query `refetchInterval ~15s`.
- **Verificação**: teste do hook (mock `PositionFeed`); componente depende só do hook.

## Unit: WEB-02 — painel verificado vs estimado
- **Contrato**:
```typescript
type Selo = 'MEDIDO' | 'ESTIMATIVA';
type Metrica = { valor: number; selo: Selo };
type VerifiedVsEstimated = {
  lineId: string;
  viagensDia: Metrica; km: Metrica; velocidadeReal: Metrica;
  reachMedido: Metrica; impressoesMedidas: Metrica;          // selo=ESTIMATIVA (visada cega)
  vsEstimado: { frequenciaGtfs: number; velocidadeAssumida: number;
                reachEstimado: number; impressoesEstimadas: number;
                faixaPctAntes: number; faixaPctDepois: number };
};
function useVerified(lineId: string): { data?: VerifiedVsEstimated; loading: boolean };
function VerifiedPanel(props: { lineId: string }): JSX.Element;
```
- **Pré-condições**: RT-02 + ficha do F1 (EP4-03).
- **Pós-condições**: medido ao lado do estimado + faixa estreitando; selos renderizados por métrica.
- **Verificação**: teste com mock → selos corretos (MEDIDO sólido, ESTIMATIVA rotulada).

## Diagrama de dependências
```
WEB-00 (mapa universal) ──> WEB-01 (veículos + acumulado · RT-01/RT-03)
RT-02 ──────────────────────> WEB-02 (painel verificado vs estimado)
```
