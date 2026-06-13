# Convergência do mapa OOH (casca compartilhada) + camada de heatmap — design

> Spec de design. Vertical OOH BH Bus Mídia · entrega `feat/f2-realtime`.
> Status: aprovado p/ implementação (2026-06-11).
> Repos tocados: **uai-portal** (front) · **uai-ooh-intel** (1 endpoint).
> Relacionado: [`feature-mapa-calor/README.md`](../../feature-mapa-calor/README.md) (master da feature de heatmap).

## 1. Problema

O vertical tem hoje **três mapas MapLibre** que duplicam o esqueleto (init do `Map`,
chips de toggle, container) mas divergem nas camadas e nos extras:

- `OohMap` (RealTime, F2) — trajeto + pontos + **ônibus ao vivo**. `realtimeMapLayers`.
- `CorridorMap` (ficha F1, EP4-04) — trajeto + corredor 300m + pontos + **POIs por categoria**. `corridorLayers`.
- `CestaMap` (cesta, EP4-09) — N linhas coloridas + 300m + **legenda + snapshot→PDF + honestidade**. `cestaLayers`.

Dois objetivos:

1. **Coesão** — o planejador deve sentir "é o mesmo mapa em toda parte"; manutenção num lugar só.
2. **Heatmap de audiência** — as telas de **ficha F1** e **cesta** devem poder sobrepor o
   **mapa de calor** da superfície de exposição (Audiência / Renda / Densidade), pra o
   planejador ver *que público* a(s) linha(s) cobre(m). **NÃO** aparece no RealTime.

## 2. Escopo

**Entra (este ship):**

- **Casca compartilhada** dos 3 mapas (mata a duplicação de init/chips/container).
- **Camada de heatmap** (fill por hexágono H3, colorido pela métrica) com **toggle de
  camada** (Audiência / Renda / Densidade) e **tooltip de hover** (audiência·renda·pop).
  Disponível em **ficha F1 + cesta**; ausente no RealTime. Default **OFF** (mapa abre limpo).
- **Endpoint intel** `GET /api/exposure/cells` → GeoJSON pronto de `serving.exposure_cell`.

**Fica pra depois (defer — F2 da feature de heatmap, não bloqueia este ship):**

- Painel-resumo de audiência do corredor (`/api/lines/{id}/audience`: alcance único,
  impressões/mês, frequência, perfil de renda, **selo de confiança**).
- Slider de hora (recolorir por faixa horária) e "Simular passada".
- Servir por `bbox`/zoom + tiling + cache Redis (otimização; o subset "dia todo" ~2,6k
  hexes ≈ 1MB é servível inteiro pro pitch).

## 3. Estado da base (verificado 2026-06-11)

- `serving.exposure_cell` **materializada**: 51.451 linhas na versão **ACTIVE = 8**;
  colunas `h3_index, hora, audiencia, pop, renda, classe_predom, footfall, geom_geojson
  (jsonb), lon, lat, version_id`.
- `geom_geojson` é **MultiPolygon GeoJSON pronto** (sem PostGIS no runtime).
- Agregação "dia todo" = linhas com **`hora IS NULL`** (audiência até ~32k); as 24 faixas
  horárias (`hora` 0–23) ficam pra o slider F2.
- ACTIVE resolvido por `serving.dataset_version.status = 'ACTIVE'`.
- O README da feature dizia "bloqueado por exposure_cell"; **está desatualizado** — a lane
  de Dados está pronta.

## 4. Arquitetura — casca compartilhada

Extrair o que os 3 mapas têm em comum em **3 unidades novas**, e reduzir cada mapa a um
**wrapper fino** que pluga seu próprio adaptador de camadas + extras. Props externas e
comportamento de cada mapa ficam **inalterados** → `*Slot.tsx`, páginas e cesta não mudam.

### 4.1 Unidades compartilhadas (novas)

| Unidade | Arquivo | Responsabilidade |
|---|---|---|
| `useOohMap` | `modules/ooh/map/useOohMap.ts` | Hook: init/cleanup do `maplibregl.Map` (container ref, `center=BH_CENTER`, `zoom=BH_ZOOM`, `style`, `NavigationControl`, `on("load")`, `map.remove()`). Aceita `{ mapStyle, preserveDrawingBuffer }`. Retorna `{ containerRef, mapRef, map, mapLoaded }`. |
| `OohMapChips` | `modules/ooh/components/OohMapChips.tsx` | UI presentacional dos chips de toggle (lista `rounded-full border…`, cor, label, `count?`, `aria-pressed`, `line-through` off). Props: `chips[]`, `vis`, `onToggle`, `testIdPrefix`. |
| `MapCanvas` | `modules/ooh/components/MapCanvas.tsx` | O `<div ref>` do mapa com styling padrão (`rounded-lg border border-border bg-muted`) + `height` + `ariaLabel` + `testId`. |

**Contrato dos chips** (preserva os `data-testid`/`data-k` que os testes já checam):

```ts
interface MapChip { key: string; label: string; color: string; count?: number | null; }
interface OohMapChipsProps {
  chips: MapChip[];
  vis: Record<string, boolean>;
  onToggle: (key: string) => void;
  testIdPrefix?: string;   // ex.: "ooh-map" → ooh-map-toggles / ooh-map-toggle-${key}
}
```

### 4.2 Camadas por contexto (o que cada wrapper pluga)

| Contexto (componente) | Adaptador de camadas | Camadas / chips | Extras |
|---|---|---|---|
| **RealTime** (`OohMap`) | `realtimeMapLayers` | Corredor · Pontos · **Ônibus ao vivo** | — |
| **Ficha F1** (`CorridorMap`) | `corridorLayers` (+ heatmap) | Trajeto · Corredor · Pontos · POIs/categoria · **Heatmap** | — |
| **Cesta** (`CestaMap`) | `cestaLayers` (+ heatmap) | N corredores · 300m · **Heatmap** | Legenda · Snapshot→PDF · Honestidade |

`live` (ônibus ao vivo) existe **só** no RealTime; heatmap existe **só** em ficha/cesta —
por construção (cada wrapper só pluga o que lhe cabe), não por flag global.

## 5. Arquitetura — camada de heatmap

### 5.1 Front (`modules/ooh/map/heatmapLayers.ts` + hook de dados)

- **`heatmapLayers.ts`** (irmão de `corridorLayers`/`cestaLayers`): `addHeatmap(map, fc, layer)`,
  `removeHeatmap(map)`, `setHeatmapLayer(map, fc, layer)`, `setHeatmapVisibility(map, on)`.
  Uma `fill` layer (source geojson das células) colorida por `value` com **rampa de cor
  normalizada por camada** (`interpolate` no min/max do payload), opacidade ~0.55, abaixo
  das camadas de trajeto/veículos (fica de **contexto atrás**).
- **Tooltip de hover**: `map.on("mousemove", layer, …)` → `maplibregl.Popup` com
  `audiência · renda · pop` (lê as props do feature); `mouseleave` fecha.
- **Toggle de camada (radio, não toggles independentes)**: 3 chips **mutuamente exclusivos**
  — Audiência (`audiencia`) · Renda (`renda`) · **Densidade (`pop`)**. No máximo **um** ativo
  por vez (o heatmap mostra uma métrica só). Clicar num chip inativo liga o heatmap naquela
  camada (troca `layer` → re-fetch + re-seta `value`/rampa); clicar no chip **já ativo**
  desliga o heatmap. Default: **nenhum** ativo (heatmap OFF) — coerente com o "Corredores
  300m" da cesta. (footfall existe no endpoint mas **não** vira chip no MVP.)
- **Fetch**: hook `useExposureCells(layer)` (react-query) → `GET /api/exposure/cells?layer=`.
  `enabled` só quando o heatmap está ligado (lazy — não baixa 1MB à toa).

### 5.2 Backend (intel — endpoint novo)

`GET /api/exposure/cells` — read-only sobre `serving.exposure_cell` (ACTIVE server-side).

| Param | Efeito |
|---|---|
| `layer` (obrigatório) | `audiencia\|renda\|pop\|footfall` → qual coluna vira `value` |
| `hour` (opcional) | `0–23`; ausente ⇒ `hora IS NULL` (dia todo) |

Resposta — `FeatureCollection` (4326), `geometry` = o `geom_geojson` da linha:

```jsonc
{ "type":"FeatureCollection", "layer":"renda", "versionId":8,
  "features":[ { "type":"Feature",
    "properties":{ "h3":"89a88cdb383ffff", "value":4553,
                   "audiencia":32504, "renda":4553, "pop":1304, "footfall":0,
                   "classePredom":"B" },
    "geometry":{ "type":"MultiPolygon", "coordinates":[...] } } ] }
```

Hexagonal (segue o intel): `ExposureController` → `QueryExposureUseCase` →
`ExposureSurfaceRepository` (porta out) → `JdbcExposureSurfaceRepository`. SQL: resolve
ACTIVE (`serving.dataset_version`), `SELECT h3_index, <layer> AS value, audiencia, renda,
pop, footfall, classe_predom, geom_geojson FROM serving.exposure_cell WHERE version_id=:v
AND (hora = :h OR (:h IS NULL AND hora IS NULL))`. **Sem PostGIS/ST_*** — `geom_geojson` já
é jsonb; o adapter o injeta cru no campo `geometry`. `layer` validado contra allow-list
(evita SQL dinâmico aberto). Auth: Bearer (igual aos demais `/api/**`).

## 6. Invariantes / controle de risco

- **Não tocar** em `realtimeMapLayers` / `corridorLayers` / `cestaLayers` (lógica de camadas
  existente), no fluxo do **snapshot** (EP4-07), nem nos `CorridorMapSlot` / `CestaMapSlot`.
- **Preservar todos os `data-testid`** existentes: `ooh-map`, `ooh-map-toggles`,
  `ooh-map-canvas`, `ooh-map-toggle-${k}`, `ooh-cesta-map-*`.
- A refatoração da casca é **presentacional pura** — props externas de `OohMap`/`CorridorMap`/
  `CestaMap` inalteradas.
- O endpoint é **read-only** e **aditivo** (nenhuma query/serving existente muda).
- **Java 21 obrigatório** no build/test do intel (o `mvn` do host pega JDK 26 do Homebrew →
  Mockito não instrumenta `JdbcTemplate`; usar `JAVA_HOME` do ms-21).

## 7. Testes

- **Front**: os 3 test files de mapa + `CorridorMapSlot`/`CestaMapSlot` continuam verdes
  (testids preservados). Novos: `OohMapChips.test`, `useOohMap` (smoke), `heatmapLayers.test`
  (add/remove/recolor), e um teste do toggle de heatmap + hover popup. `tsc` limpo.
- **Backend**: teste do `JdbcExposureSurfaceRepository` (ACTIVE + layer→value + hora NULL),
  do `ExposureController` (200 + shape do FeatureCollection; 400 em `layer` inválido; 401 sem
  token). Rodar com Java 21.
- **E2E local**: subir intel (:8087) + portal (:8080), abrir ficha F1 e cesta, ligar o
  heatmap, trocar Audiência↔Renda↔Densidade, conferir hover; confirmar que RealTime **não**
  tem o toggle de heatmap.

## 8. Arquivos (mapa de mudança)

**uai-portal** (branch `feat/f2-realtime`):

- `+ modules/ooh/map/useOohMap.ts`
- `+ modules/ooh/components/OohMapChips.tsx`, `+ MapCanvas.tsx`
- `+ modules/ooh/map/heatmapLayers.ts`
- `+ modules/ooh/api/` — tipos `ExposureCell`/`ExposureFeatureCollection` + hook `useExposureCells`
- `~ modules/ooh/components/OohMap.tsx` — usa casca; sem heatmap
- `~ modules/ooh/components/CorridorMap.tsx` — usa casca + pluga heatmap
- `~ modules/ooh/components/CestaMap.tsx` — usa casca + pluga heatmap (mantém legenda/snapshot/honestidade)
- `+` testes correspondentes

**uai-ooh-intel** (branch `feat/f2-realtime`):

- `+ adapter/in/web/ExposureController.java`
- `+ domain/port/in/QueryExposureUseCase.java`, `+ application/usecase/ExposureQueryService.java`
- `+ domain/port/out/ExposureSurfaceRepository.java`, `+ adapter/out/persistence/JdbcExposureSurfaceRepository.java`
- `+ domain/model/ExposureLayer.java` (enum allow-list) + `ExposureCell`/`ExposureSurface` records
- `+` testes correspondentes

## 9. Fora de escopo (explícito)

Painel-resumo + selo de confiança, `/api/lines/{id}/audience`, slider de hora, "simular
passada", bbox/tiling/cache, prospecção de nichos (reuso futuro do heatmap).
