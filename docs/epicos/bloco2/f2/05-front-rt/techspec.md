# Detalhamento Técnico: lane front-rt (F2 · mapa ao vivo + verificado×estimado)

> Techspec da lane **05-front-rt** (`uai-portal`, estende o módulo OOH do F1). Units = WEB-01..02.
> Contratos em TypeScript (types, assinaturas de hook/componente — sem implementação).

## Contexto
Estende o front do F1 com o **mapa ao vivo** (carros se movendo, escopável por linha, via polling sobre a `PositionFeed`) e o **painel verificado vs estimado** (a calibração, com o alcance recalculado do RT-02). Reusa o mapa (EP4-04) e recharts (EP4-05).

## Decisões Técnicas
- **Transporte atrás do hook `usePositions(scope)`** (polling no F2; SSE/WS = troca interna do hook). O componente **não sabe** o transporte.
- **Escopo = linha** (a tela passa `lineId`, nunca `vehicleId`) — espelha o server-derived do back.
- **Honestidade:** verificado = **sólido (medido)**; alcance recalculado mantém o selo **estimativa** (EP4-08), pois `aindaEstimativa=true`.

## Padrões do Projeto a Seguir
- React 18 + TS estrito · shadcn/ui · **recharts** (reuso do F1) · MapLibre (camada nova sobre EP4-04) · `@tanstack/react-query` pro polling.

## Riscos Globais
- **Polling vs always-on:** cadence ~15s; o hook controla o intervalo (não martelar). Erro de fetch não derruba a tela (estado de erro).
- **Selo de estimativa** no alcance recalculado — não deixar parecer "medido".

---

## Unit: WEB-01 — mapa ao vivo (camada de veículos)
- **Responsabilidade**: camada de carros se movendo no mapa do F1, escopável por linha, via `usePositions`.
- **Localização**: `modules/ooh/realtime/LiveVehiclesLayer.tsx`, `modules/ooh/realtime/usePositions.ts`, `modules/ooh/realtime/positionFeed.ts`.
- **Contrato**:
```typescript
type LivePosition = {
  vehicleId: string; lineId: string; lat: number; lon: number;
  bearing?: number; currentStopSequence?: number; completudeParcial: number; ts: string;
};
type RealtimeScope = { kind: 'line'; lineId: string };

interface PositionFeed { positionsForScope(scope: RealtimeScope): Promise<LivePosition[]> } // adapter polling no F2

function usePositions(scope: RealtimeScope): { positions: LivePosition[]; loading: boolean; error?: Error };

function LiveVehiclesLayer(props: { lineId: string }): JSX.Element; // camada sobre o mapa EP4-04
```
- **Pré-condições**: front F1 (EP4-04 mapa) + RT-01/RT-03.
- **Pós-condições**: seleciona linha → carros dela se movem no mapa (~15s); o transporte vive no hook (swap sem tocar a UI).
- **Decisões locais**: `usePositions` usa react-query com `refetchInterval ~15s`.
- **Verificação**: teste do hook (mock `PositionFeed`) → atualiza; o componente depende só do hook.

## Unit: WEB-02 — painel verificado vs estimado
- **Responsabilidade**: mostrar, por linha, o verificado ao lado do estimado (a calibração) + o alcance recalculado.
- **Localização**: `modules/ooh/verified/VerifiedPanel.tsx`, `modules/ooh/verified/useVerified.ts`.
- **Contrato**:
```typescript
type VerifiedVsEstimated = {
  lineId: string; viagensDia: number; km: number; velocidadeReal: number;
  frequenciaGtfs: number; faixaPct: number;
  alcanceRecalculado: number; aindaEstimativa: boolean; // true no F2
};
function useVerified(lineId: string): { data?: VerifiedVsEstimated; loading: boolean };
function VerifiedPanel(props: { lineId: string }): JSX.Element;
```
- **Pré-condições**: RT-02 (`/api/lines/{id}/verified`) + ficha do F1 (EP4-03).
- **Pós-condições**: o planejador vê verificado (sólido) ao lado do estimado + quanto a ±35% se estreita; alcance recalculado com **selo de estimativa**.
- **Decisões locais**: reusa recharts; o selo de estimativa segue EP4-08.
- **Verificação**: teste do componente com dados mock → mostra ambos + o selo quando `aindaEstimativa`.

## Diagrama de dependências
```
RT-03/RT-01 ──> WEB-01 (mapa ao vivo, hook usePositions)
RT-02 ───────> WEB-02 (painel verificado vs estimado)
```
