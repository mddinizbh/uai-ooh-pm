# Convergência do mapa OOH + heatmap — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. **Nesta entrega a execução é via Workflow** (decisão do usuário).

**Goal:** Unificar os 3 mapas MapLibre do vertical OOH numa casca compartilhada e adicionar a camada de heatmap de audiência (Audiência/Renda/Densidade) na ficha F1 e na cesta, alimentada por um endpoint read-only novo no intel.

**Architecture:** Front — extrair init/chips/canvas comuns (`useOohMap` + `OohMapChips` + `MapCanvas`); os 3 mapas viram wrappers finos que plugam seu adaptador de camadas; heatmap é um adaptador novo (`heatmapLayers`) plugado só em ficha/cesta. Backend — fatia hexagonal `/api/exposure/cells` sobre `serving.exposure_cell` (ACTIVE server-side, sem PostGIS).

**Tech Stack:** React 18 + Vite + TypeScript + MapLibre GL + @tanstack/react-query + Vitest/RTL (portal) · Java 21 + Spring Boot 3.3.6 + JdbcTemplate + JUnit5/Mockito (intel).

**Spec:** `docs/superpowers/specs/2026-06-11-convergencia-mapa-ooh-design.md`

**Branches:** `feat/f2-realtime` nos dois repos (já checked out).

---

## Grafo de dependências (p/ o workflow)

```
Backend (intel) — independente do front:
  B1 (model+ports) → B2 (jdbc repo) → B3 (usecase) → B4 (controller)

Frontend (portal):
  F1 useOohMap ┐
  F2 OohMapChips ├─→ F4 OohMap        (refactor RealTime)
  F3 MapCanvas ┘   ├─→ F5 CorridorMap (refactor ficha)
                   └─→ F6 CestaMap    (refactor cesta)
  F7 heatmapLayers ┐
  F8 types+useExposureCells ┴─→ F9 wire heatmap (em F5+F6)
```

F1–F3 são paralelos entre si. F4/F5/F6 dependem de F1–F3. F7/F8 paralelos a tudo. F9 depende de F5,F6,F7,F8. Backend B1–B4 sequencial mas paralelo ao front inteiro. **Convenção:** cada task termina com `vitest run` (portal) ou `mvn -o test` com **JAVA_HOME do ms-21.0.10** (intel) verdes + commit.

---

## Pré-requisitos de ambiente

- **Java 21 obrigatório no intel:** `export JAVA_HOME=/Users/marleydiniz/Library/Java/JavaVirtualMachines/ms-21.0.10/Contents/Home` antes de qualquer `mvn`. (O `mvn` do host pega JDK 26 → Mockito falha ao instrumentar `JdbcTemplate`.)
- Portal: `npm` na raiz do `uai-portal`. Test runner: `npx vitest run <file>`. Typecheck: `npx tsc --noEmit`.

---

# BACKEND (uai-ooh-intel)

### Task B1: Modelo + portas do exposure

**Files:**
- Create: `src/main/java/com/uai/ooh/intel/domain/model/ExposureLayer.java`
- Create: `src/main/java/com/uai/ooh/intel/domain/model/ExposureCell.java`
- Create: `src/main/java/com/uai/ooh/intel/domain/model/ExposureSurface.java`
- Create: `src/main/java/com/uai/ooh/intel/domain/port/out/ExposureSurfaceRepository.java`
- Create: `src/main/java/com/uai/ooh/intel/domain/port/in/QueryExposureUseCase.java`

- [ ] **Step 1: `ExposureLayer` enum (allow-list → coluna SQL).** Enum de rótulo (memória do usuário: tipo finito de rótulo = enum). Mapeia o param público à coluna física, evitando SQL dinâmico aberto.

```java
package com.uai.ooh.intel.domain.model;

/** Camada de heatmap pedível e a coluna de `serving.exposure_cell` que vira `value`. */
public enum ExposureLayer {
    AUDIENCIA("audiencia"),
    RENDA("renda"),
    POP("pop"),
    FOOTFALL("footfall");

    private final String column;
    ExposureLayer(String column) { this.column = column; }
    public String column() { return column; }

    /** Resolve o rótulo público (case-insensitive); lança IllegalArgumentException se inválido. */
    public static ExposureLayer fromLabel(String label) {
        if (label == null) throw new IllegalArgumentException("layer is required");
        return ExposureLayer.valueOf(label.trim().toUpperCase());
    }
}
```

- [ ] **Step 2: records `ExposureCell` + `ExposureSurface`.** O `geomGeojson` é o JSON cru (String) da coluna jsonb — o adapter web o injeta como `geometry` sem re-serializar.

```java
package com.uai.ooh.intel.domain.model;

/** Uma célula H3 da superfície: a métrica pedida (`value`) + as demais p/ tooltip + geometria crua. */
public record ExposureCell(
        String h3, double value,
        double audiencia, double renda, double pop, double footfall,
        String classePredom,
        String geomGeojson) {}
```

```java
package com.uai.ooh.intel.domain.model;

import java.util.List;

/** A superfície servida: a camada pedida, a versão ACTIVE resolvida e as células. */
public record ExposureSurface(ExposureLayer layer, int versionId, List<ExposureCell> cells) {}
```

- [ ] **Step 3: portas.**

```java
package com.uai.ooh.intel.domain.port.out;

import com.uai.ooh.intel.domain.model.ExposureLayer;
import com.uai.ooh.intel.domain.model.ExposureSurface;

/** Lê a superfície de exposição (serving ACTIVE) — read-only, sem PostGIS. */
public interface ExposureSurfaceRepository {
    /** @param hour 0–23 ou {@code null} = dia todo (linhas com hora IS NULL). */
    ExposureSurface load(ExposureLayer layer, Integer hour);
}
```

```java
package com.uai.ooh.intel.domain.port.in;

import com.uai.ooh.intel.domain.model.ExposureLayer;
import com.uai.ooh.intel.domain.model.ExposureSurface;

public interface QueryExposureUseCase {
    ExposureSurface surface(ExposureLayer layer, Integer hour);
}
```

- [ ] **Step 4: compila.** Run: `export JAVA_HOME=/Users/marleydiniz/Library/Java/JavaVirtualMachines/ms-21.0.10/Contents/Home && mvn -o -q compile` · Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit.**
```bash
git add src/main/java/com/uai/ooh/intel/domain/
git commit -m "feat(exposure): modelo + portas da superfície de exposição"
```

---

### Task B2: JdbcExposureSurfaceRepository

**Files:**
- Create: `src/main/java/com/uai/ooh/intel/adapter/out/persistence/JdbcExposureSurfaceRepository.java`
- Test: `src/test/java/com/uai/ooh/intel/adapter/out/persistence/JdbcExposureSurfaceRepositoryTest.java`

- [ ] **Step 1: Write the failing test.** Espelha o estilo do `JdbcNetworkQueryRepositoryTest` (mock do `JdbcTemplate`). Verifica: resolve ACTIVE, mapeia layer→value, e o filtro de hora NULL.

```java
package com.uai.ooh.intel.adapter.out.persistence;

import com.uai.ooh.intel.domain.model.ExposureLayer;
import com.uai.ooh.intel.domain.model.ExposureSurface;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcExposureSurfaceRepositoryTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final JdbcExposureSurfaceRepository repo = new JdbcExposureSurfaceRepository(jdbc);

    @Test
    void loadResolvesActiveVersionAndMapsCells() {
        when(jdbc.queryForObject(eq(JdbcExposureSurfaceRepository.SQL_ACTIVE_VERSION), eq(Integer.class)))
                .thenReturn(8);
        var cell = new com.uai.ooh.intel.domain.model.ExposureCell(
                "89a88cdb383ffff", 4553, 32504, 4553, 1304, 0, "B", "{\"type\":\"MultiPolygon\"}");
        when(jdbc.query(any(String.class), any(RowMapper.class), eq(8))).thenReturn(List.of(cell));

        ExposureSurface s = repo.load(ExposureLayer.RENDA, null);

        assertThat(s.versionId()).isEqualTo(8);
        assertThat(s.layer()).isEqualTo(ExposureLayer.RENDA);
        assertThat(s.cells()).hasSize(1);
        assertThat(s.cells().get(0).value()).isEqualTo(4553);
    }
}
```

- [ ] **Step 2: Run test — verify FAIL.** Run: `export JAVA_HOME=…/ms-21.0.10/Contents/Home && mvn -o -q -Dtest=JdbcExposureSurfaceRepositoryTest test` · Expected: FAIL (classe não existe).

- [ ] **Step 3: Implement.** A coluna do layer é interpolada só a partir do `enum.column()` (allow-list) — nunca do input cru. `hora` filtrada por `(:h IS NULL AND hora IS NULL) OR hora = :h`; como o param vira binding, usar dois branches de SQL ou COALESCE. Mais simples: branch no Java.

```java
package com.uai.ooh.intel.adapter.out.persistence;

import com.uai.ooh.intel.domain.model.ExposureCell;
import com.uai.ooh.intel.domain.model.ExposureLayer;
import com.uai.ooh.intel.domain.model.ExposureSurface;
import com.uai.ooh.intel.domain.port.out.ExposureSurfaceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcExposureSurfaceRepository implements ExposureSurfaceRepository {

    static final String SQL_ACTIVE_VERSION =
            "SELECT version_id FROM serving.dataset_version WHERE status = 'ACTIVE' LIMIT 1";

    private final JdbcTemplate jdbc;

    public JdbcExposureSurfaceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public ExposureSurface load(ExposureLayer layer, Integer hour) {
        Integer version = jdbc.queryForObject(SQL_ACTIVE_VERSION, Integer.class);
        if (version == null) {
            return new ExposureSurface(layer, -1, List.of());
        }
        String col = layer.column(); // allow-list (enum) — seguro interpolar
        String hourPred = (hour == null) ? "hora IS NULL" : "hora = " + hour.intValue();
        String sql = "SELECT h3_index, " + col + " AS value, audiencia, renda, pop, footfall, "
                + "classe_predom, geom_geojson::text AS geom "
                + "FROM serving.exposure_cell WHERE version_id = ? AND " + hourPred
                + " AND " + col + " IS NOT NULL";
        List<ExposureCell> cells = jdbc.query(sql, CELL_MAPPER, version);
        return new ExposureSurface(layer, version, cells);
    }

    private static final RowMapper<ExposureCell> CELL_MAPPER = (rs, i) -> new ExposureCell(
            rs.getString("h3_index"),
            rs.getDouble("value"),
            rs.getDouble("audiencia"),
            rs.getDouble("renda"),
            rs.getDouble("pop"),
            rs.getDouble("footfall"),
            rs.getString("classe_predom"),
            rs.getString("geom"));
}
```
> Nota: o `hour` só entra na SQL via `Integer.intValue()` (numérico, não string) — sem risco de injeção. `geom_geojson::text` traz o JSON cru.

- [ ] **Step 4: Run test — verify PASS.** Run: `… mvn -o -q -Dtest=JdbcExposureSurfaceRepositoryTest test` · Expected: PASS.

- [ ] **Step 5: Commit.**
```bash
git add src/main/java/com/uai/ooh/intel/adapter/out/persistence/JdbcExposureSurfaceRepository.java src/test/java/com/uai/ooh/intel/adapter/out/persistence/JdbcExposureSurfaceRepositoryTest.java
git commit -m "feat(exposure): JdbcExposureSurfaceRepository (serving ACTIVE, sem PostGIS)"
```

---

### Task B3: ExposureQueryService (use case)

**Files:**
- Create: `src/main/java/com/uai/ooh/intel/application/usecase/ExposureQueryService.java`
- Test: `src/test/java/com/uai/ooh/intel/application/usecase/ExposureQueryServiceTest.java`

- [ ] **Step 1: Write the failing test.**

```java
package com.uai.ooh.intel.application.usecase;

import com.uai.ooh.intel.domain.model.ExposureLayer;
import com.uai.ooh.intel.domain.model.ExposureSurface;
import com.uai.ooh.intel.domain.port.out.ExposureSurfaceRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExposureQueryServiceTest {
    @Test
    void delegatesToRepository() {
        ExposureSurfaceRepository repo = mock(ExposureSurfaceRepository.class);
        var expected = new ExposureSurface(ExposureLayer.AUDIENCIA, 8, List.of());
        when(repo.load(eq(ExposureLayer.AUDIENCIA), isNull())).thenReturn(expected);

        ExposureSurface out = new ExposureQueryService(repo).surface(ExposureLayer.AUDIENCIA, null);

        assertThat(out).isSameAs(expected);
    }
}
```

- [ ] **Step 2: Run — verify FAIL.** Run: `… -Dtest=ExposureQueryServiceTest test` · Expected: FAIL.

- [ ] **Step 3: Implement.**
```java
package com.uai.ooh.intel.application.usecase;

import com.uai.ooh.intel.domain.model.ExposureLayer;
import com.uai.ooh.intel.domain.model.ExposureSurface;
import com.uai.ooh.intel.domain.port.in.QueryExposureUseCase;
import com.uai.ooh.intel.domain.port.out.ExposureSurfaceRepository;
import org.springframework.stereotype.Service;

@Service
public class ExposureQueryService implements QueryExposureUseCase {
    private final ExposureSurfaceRepository repo;
    public ExposureQueryService(ExposureSurfaceRepository repo) { this.repo = repo; }

    @Override
    public ExposureSurface surface(ExposureLayer layer, Integer hour) {
        return repo.load(layer, hour);
    }
}
```

- [ ] **Step 4: Run — verify PASS.** Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(exposure): ExposureQueryService"`

---

### Task B4: ExposureController (`GET /api/exposure/cells`)

**Files:**
- Create: `src/main/java/com/uai/ooh/intel/adapter/in/web/ExposureController.java`
- Test: `src/test/java/com/uai/ooh/intel/adapter/in/web/ExposureControllerTest.java`

O controller monta o `FeatureCollection` à mão (o `geometry` é o JSON cru via Jackson
`RawValue`/`@JsonRawValue`, ou montando um `Map`/String). Para evitar re-serialização do
jsonb, usar um DTO com `@JsonRawValue` no campo geometry.

- [ ] **Step 1: DTOs internos (no próprio controller ou records dedicados).**

```java
// dentro de ExposureController (ou records package-private):
record FeaturePropsDTO(String h3, double value, double audiencia, double renda,
                       double pop, double footfall, String classePredom) {}
```
Para a geometry crua, usar um wrapper:
```java
record GeometryRaw(@com.fasterxml.jackson.annotation.JsonValue @com.fasterxml.jackson.annotation.JsonRawValue String json) {}
```

- [ ] **Step 2: Write the failing test** (MockMvc com a security desabilitada? Seguir o padrão dos *ControllerTest existentes — checar como RealtimeControllerTest monta o contexto). Verifica 200 + `type=FeatureCollection` + `layer` + 400 em layer inválido.

```java
package com.uai.ooh.intel.adapter.in.web;

import com.uai.ooh.intel.domain.model.ExposureCell;
import com.uai.ooh.intel.domain.model.ExposureLayer;
import com.uai.ooh.intel.domain.model.ExposureSurface;
import com.uai.ooh.intel.domain.port.in.QueryExposureUseCase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExposureControllerTest {

    private final QueryExposureUseCase useCase = mock(QueryExposureUseCase.class);
    private final ExposureController controller = new ExposureController(useCase);

    @Test
    void cellsReturnsFeatureCollection() {
        var cell = new ExposureCell("89a", 4553, 32504, 4553, 1304, 0, "B",
                "{\"type\":\"MultiPolygon\",\"coordinates\":[]}");
        when(useCase.surface(any(ExposureLayer.class), isNull()))
                .thenReturn(new ExposureSurface(ExposureLayer.RENDA, 8, List.of(cell)));

        var fc = controller.cells("renda", null);

        assertThat(fc.type()).isEqualTo("FeatureCollection");
        assertThat(fc.layer()).isEqualTo("renda");
        assertThat(fc.versionId()).isEqualTo(8);
        assertThat(fc.features()).hasSize(1);
        assertThat(fc.features().get(0).properties().value()).isEqualTo(4553);
    }

    @Test
    void invalidLayerThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.cells("xpto", null));
    }
}
```

- [ ] **Step 3: Run — verify FAIL.** Run: `… -Dtest=ExposureControllerTest test` · Expected: FAIL.

- [ ] **Step 4: Implement the controller.** Path `@RequestMapping("/api")` + `@GetMapping("/exposure/cells")`. `layer` resolvido por `ExposureLayer.fromLabel` (lança IllegalArgumentException → 400 via `ApiExceptionHandler`; **verificar** que o handler já mapeia IllegalArgumentException p/ 400, senão adicionar).

```java
package com.uai.ooh.intel.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.uai.ooh.intel.domain.model.ExposureCell;
import com.uai.ooh.intel.domain.model.ExposureLayer;
import com.uai.ooh.intel.domain.model.ExposureSurface;
import com.uai.ooh.intel.domain.port.in.QueryExposureUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ExposureController {

    private final QueryExposureUseCase useCase;
    public ExposureController(QueryExposureUseCase useCase) { this.useCase = useCase; }

    @GetMapping("/exposure/cells")
    public FeatureCollectionDTO cells(@RequestParam String layer,
                                      @RequestParam(required = false) Integer hour) {
        ExposureSurface s = useCase.surface(ExposureLayer.fromLabel(layer), hour);
        List<FeatureDTO> feats = s.cells().stream().map(ExposureController::toFeature).toList();
        return new FeatureCollectionDTO("FeatureCollection", layer.toLowerCase(), s.versionId(), feats);
    }

    private static FeatureDTO toFeature(ExposureCell c) {
        return new FeatureDTO("Feature",
                new PropsDTO(c.h3(), c.value(), c.audiencia(), c.renda(), c.pop(), c.footfall(), c.classePredom()),
                c.geomGeojson());
    }

    public record FeatureCollectionDTO(String type, String layer, int versionId, List<FeatureDTO> features) {}
    public record FeatureDTO(String type, PropsDTO properties, @JsonRawValue String geometry) {}
    public record PropsDTO(String h3, double value, double audiencia, double renda,
                           double pop, double footfall, String classePredom) {}
}
```

- [ ] **Step 5: Run — verify PASS.** Expected: PASS (os 2 testes).

- [ ] **Step 6: Full suite (Java 21) + commit.** Run: `export JAVA_HOME=…/ms-21.0.10/Contents/Home && mvn -o -q test` · Expected: BUILD SUCCESS, 0 failures.
```bash
git add src/main/java/com/uai/ooh/intel/adapter/in/web/ExposureController.java src/test/java/com/uai/ooh/intel/adapter/in/web/ExposureControllerTest.java
git commit -m "feat(exposure): GET /api/exposure/cells (GeoJSON da superfície ACTIVE)"
```

---

# FRONTEND (uai-portal)

> Os arquivos atuais a refatorar: `src/modules/ooh/components/{OohMap,CorridorMap,CestaMap}.tsx`.
> Adaptadores existentes a **não** alterar: `src/modules/ooh/map/{realtimeMapLayers,corridorLayers,cestaLayers}.ts`.

### Task F1: hook `useOohMap`

**Files:**
- Create: `src/modules/ooh/map/useOohMap.ts`
- Test: `src/modules/ooh/map/useOohMap.test.ts`

- [ ] **Step 1: Write the failing test.** Smoke: monta o hook num componente de teste, espera `containerRef` definido e o mapa criado (mockar `maplibre-gl` como os testes de mapa existentes já fazem — **verificar** o mock em `CorridorMap.test.tsx` e reusar o mesmo padrão de mock).

```ts
import { renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useOohMap } from "./useOohMap";

vi.mock("maplibre-gl", () => {
  const Map = vi.fn(() => ({
    addControl: vi.fn(), on: vi.fn((ev: string, cb: () => void) => ev === "load" && cb()),
    remove: vi.fn(),
  }));
  return { default: { Map, NavigationControl: vi.fn() } };
});

describe("useOohMap", () => {
  it("expõe containerRef e mapLoaded", () => {
    const { result } = renderHook(() => useOohMap({}));
    expect(result.current.containerRef).toBeDefined();
    expect(result.current).toHaveProperty("mapLoaded");
  });
});
```

- [ ] **Step 2: Run — verify FAIL.** Run: `npx vitest run src/modules/ooh/map/useOohMap.test.ts` · Expected: FAIL (módulo não existe).

- [ ] **Step 3: Implement.** Extrair o init/cleanup idêntico dos 3 mapas. `preserveDrawingBuffer` opcional (cesta).

```ts
import { useEffect, useRef, useState } from "react";
import maplibregl from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import { BH_CENTER, BH_ZOOM, DEFAULT_MAP_STYLE } from "@/modules/ooh/map/corridorLayers";

export interface UseOohMapOptions {
  mapStyle?: string;
  preserveDrawingBuffer?: boolean;
}

export interface UseOohMapResult {
  containerRef: React.RefObject<HTMLDivElement>;
  mapRef: React.MutableRefObject<maplibregl.Map | null>;
  map: maplibregl.Map | null;
  mapLoaded: boolean;
}

/** Init/cleanup compartilhado do MapLibre dos mapas OOH (casca). */
export function useOohMap({ mapStyle = DEFAULT_MAP_STYLE, preserveDrawingBuffer = false }: UseOohMapOptions): UseOohMapResult {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const [mapLoaded, setMapLoaded] = useState(false);

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;
    const map = new maplibregl.Map({
      container: containerRef.current,
      style: mapStyle,
      center: BH_CENTER,
      zoom: BH_ZOOM,
      preserveDrawingBuffer,
    });
    mapRef.current = map;
    map.addControl(new maplibregl.NavigationControl({ showCompass: false }), "top-right");
    map.on("load", () => setMapLoaded(true));
    return () => {
      map.remove();
      mapRef.current = null;
      setMapLoaded(false);
    };
  }, [mapStyle, preserveDrawingBuffer]);

  return { containerRef, mapRef, map: mapRef.current, mapLoaded };
}
```

- [ ] **Step 4: Run — verify PASS.** Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(map): hook useOohMap (init/cleanup compartilhado)"`

---

### Task F2: `OohMapChips`

**Files:**
- Create: `src/modules/ooh/components/OohMapChips.tsx`
- Test: `src/modules/ooh/components/OohMapChips.test.tsx`

- [ ] **Step 1: Write the failing test.** Renderiza 2 chips, clica num, espera o `onToggle` com a key; testids `${prefix}-toggles` + `${prefix}-toggle-${key}`.

```tsx
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import OohMapChips from "./OohMapChips";

describe("OohMapChips", () => {
  it("renderiza chips e dispara onToggle", () => {
    const onToggle = vi.fn();
    render(
      <OohMapChips
        testIdPrefix="ooh-map"
        chips={[{ key: "corredor", label: "Corredor", color: "#3b82f6" }, { key: "pontos", label: "Pontos", color: "#94a3b8", count: 12 }]}
        vis={{ corredor: true, pontos: false }}
        onToggle={onToggle}
      />,
    );
    expect(screen.getByTestId("ooh-map-toggles")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("ooh-map-toggle-pontos"));
    expect(onToggle).toHaveBeenCalledWith("pontos");
    expect(screen.getByText("12")).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run — verify FAIL.** Run: `npx vitest run src/modules/ooh/components/OohMapChips.test.tsx` · Expected: FAIL.

- [ ] **Step 3: Implement.** Extrair o markup de chip idêntico do `OohMap`/`CorridorMap` (mesmas classes). `data-k` mantido p/ compat.

```tsx
import { cn } from "@/lib/utils";

export interface MapChip {
  key: string;
  label: string;
  color: string;
  count?: number | null;
}

export interface OohMapChipsProps {
  chips: MapChip[];
  vis: Record<string, boolean>;
  onToggle: (key: string) => void;
  testIdPrefix?: string;
}

export default function OohMapChips({ chips, vis, onToggle, testIdPrefix = "ooh-map" }: OohMapChipsProps) {
  return (
    <div className="flex flex-wrap gap-2" data-testid={`${testIdPrefix}-toggles`}>
      {chips.map((chip) => {
        const on = vis[chip.key] !== false;
        return (
          <button
            key={chip.key}
            type="button"
            data-k={chip.key}
            data-testid={`${testIdPrefix}-toggle-${chip.key}`}
            aria-pressed={on}
            onClick={() => onToggle(chip.key)}
            className={cn(
              "inline-flex items-center gap-2 rounded-full border border-border bg-card px-3 py-1 text-xs text-foreground transition-opacity",
              !on && "opacity-40",
            )}
          >
            <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: chip.color }} />
            <span className={cn(!on && "line-through")}>{chip.label}</span>
            {chip.count != null ? (
              <span className="font-mono font-semibold text-muted-foreground">{chip.count}</span>
            ) : null}
          </button>
        );
      })}
    </div>
  );
}
```

- [ ] **Step 4: Run — verify PASS.** Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(map): componente OohMapChips (toggles compartilhados)"`

---

### Task F3: `MapCanvas`

**Files:**
- Create: `src/modules/ooh/components/MapCanvas.tsx`
- Test: `src/modules/ooh/components/MapCanvas.test.tsx`

- [ ] **Step 1: Write the failing test.**

```tsx
import { render, screen } from "@testing-library/react";
import { createRef } from "react";
import { describe, expect, it } from "vitest";
import MapCanvas from "./MapCanvas";

describe("MapCanvas", () => {
  it("renderiza o container com testid e aria-label", () => {
    render(<MapCanvas ref={createRef<HTMLDivElement>()} testId="ooh-map-canvas" ariaLabel="Mapa OOH" heightClass="h-[520px]" />);
    const el = screen.getByTestId("ooh-map-canvas");
    expect(el).toHaveAttribute("aria-label", "Mapa OOH");
  });
});
```

- [ ] **Step 2: Run — verify FAIL.** Expected: FAIL.

- [ ] **Step 3: Implement** (forwardRef — o hook passa o `containerRef`).

```tsx
import { forwardRef } from "react";
import { cn } from "@/lib/utils";

export interface MapCanvasProps {
  testId: string;
  ariaLabel: string;
  heightClass?: string;
}

const MapCanvas = forwardRef<HTMLDivElement, MapCanvasProps>(
  ({ testId, ariaLabel, heightClass = "h-[460px]" }, ref) => (
    <div
      ref={ref}
      role="region"
      aria-label={ariaLabel}
      data-testid={testId}
      className={cn("w-full overflow-hidden rounded-lg border border-border bg-muted", heightClass)}
    />
  ),
);
MapCanvas.displayName = "MapCanvas";
export default MapCanvas;
```

- [ ] **Step 4: Run — verify PASS.** Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(map): componente MapCanvas (moldura compartilhada)"`

---

### Task F4: refatorar `OohMap` (RealTime) p/ a casca

**Files:**
- Modify: `src/modules/ooh/components/OohMap.tsx`
- Test (existente): `src/modules/ooh/components/OohMap.test.tsx` (se existir) — deve continuar verde.

- [ ] **Step 1:** Substituir o bloco de `useRef/useState/useEffect(init)` pelo `useOohMap`, e o markup dos chips + container pelo `OohMapChips` + `MapCanvas`. **Manter** toda a lógica de `realtimeMapLayers` (vehicles, diff de line-geo, visibilidade) e o `live`. Os chips: `corredor`/`pontos`/(`vehicles` se `live`). `heightClass="h-[520px]"`, `testIdPrefix="ooh-map"`, `ariaLabel="Mapa OOH"`.

- [ ] **Step 2: Typecheck.** Run: `npx tsc --noEmit` · Expected: 0 erros.
- [ ] **Step 3: Test.** Run: `npx vitest run src/modules/ooh/components/OohMap.test.tsx` (se houver) + qualquer teste da `RealTimePage` · Expected: PASS.
- [ ] **Step 4: Commit.** `git commit -m "refactor(map): OohMap usa a casca compartilhada"`

---

### Task F5: refatorar `CorridorMap` (ficha) p/ a casca

**Files:**
- Modify: `src/modules/ooh/components/CorridorMap.tsx`
- Test (existente): `src/modules/ooh/components/CorridorMap.test.tsx` — deve continuar verde (testids preservados).

- [ ] **Step 1:** Trocar init pelo `useOohMap`; chips pelo `OohMapChips` (passando os chips de `corridorLayers` — trajeto/corredor/pontos/POIs/Outros, com `count`); container pelo `MapCanvas` (`heightClass="h-[420px]"`, `testIdPrefix="ooh-map"`, `ariaLabel="Mapa do corredor da linha"`). **Manter** `splitFeatures`/`addCorridorLayers`/`applyVisibility` e o efeito de visibilidade. **Não** adicionar heatmap aqui ainda (Task F9).

- [ ] **Step 2: Typecheck.** Run: `npx tsc --noEmit` · Expected: 0 erros.
- [ ] **Step 3: Test.** Run: `npx vitest run src/modules/ooh/components/CorridorMap.test.tsx src/modules/ooh/components/CorridorMapSlot.test.tsx` · Expected: PASS.
- [ ] **Step 4: Commit.** `git commit -m "refactor(map): CorridorMap usa a casca compartilhada"`

---

### Task F6: refatorar `CestaMap` (cesta) p/ a casca

**Files:**
- Modify: `src/modules/ooh/components/CestaMap.tsx`
- Test (existente): `src/modules/ooh/components/CestaMap.test.tsx` — verde (testids `ooh-cesta-map-*` preservados).

- [ ] **Step 1:** Trocar init por `useOohMap({ preserveDrawingBuffer: true })`; container por `MapCanvas` (`testId="ooh-cesta-map-canvas"`, `ariaLabel="Mapa do alcance combinado da cesta"`, `heightClass="h-[460px]"`). **Manter intactos**: legenda, botão de snapshot (`handleSnapshot`/`onSnapshot`), banner de honestidade, toggle "Corredores 300m", e `cestaLayers`. (O toggle 300m pode continuar como botão bespoke OU virar um `OohMapChips` de 1 chip — manter bespoke p/ não mexer no testid `ooh-cesta-map-toggle-corredor`.) **Não** adicionar heatmap aqui ainda (Task F9).

- [ ] **Step 2: Typecheck.** Run: `npx tsc --noEmit` · Expected: 0 erros.
- [ ] **Step 3: Test.** Run: `npx vitest run src/modules/ooh/components/CestaMap.test.tsx src/modules/ooh/components/CestaMapSlot.test.tsx` · Expected: PASS.
- [ ] **Step 4: Commit.** `git commit -m "refactor(map): CestaMap usa a casca (snapshot/legenda/honestidade intactos)"`

---

### Task F7: `heatmapLayers.ts`

**Files:**
- Create: `src/modules/ooh/map/heatmapLayers.ts`
- Test: `src/modules/ooh/map/heatmapLayers.test.ts`

- [ ] **Step 1: Write the failing test.** Mock do `map` (objeto com `getSource/addSource/addLayer/removeLayer/removeSource/setLayoutProperty/setPaintProperty`). Verifica add cria source+layer; remove limpa; setHeatmapLayer troca os dados.

```ts
import { describe, expect, it, vi } from "vitest";
import { addHeatmap, removeHeatmap, HEATMAP_LAYER, HEATMAP_SOURCE } from "./heatmapLayers";

function fakeMap() {
  const sources = new Set<string>();
  const layers = new Set<string>();
  return {
    getSource: (id: string) => (sources.has(id) ? {} : undefined),
    addSource: vi.fn((id: string) => sources.add(id)),
    addLayer: vi.fn((l: { id: string }) => layers.add(l.id)),
    getLayer: (id: string) => (layers.has(id) ? {} : undefined),
    removeLayer: vi.fn((id: string) => layers.delete(id)),
    removeSource: vi.fn((id: string) => sources.delete(id)),
    setPaintProperty: vi.fn(),
  } as unknown as maplibregl.Map;
}

const FC = { type: "FeatureCollection", features: [{ type: "Feature", properties: { value: 10 }, geometry: { type: "MultiPolygon", coordinates: [] } }] } as GeoJSON.FeatureCollection;

describe("heatmapLayers", () => {
  it("add cria source+layer; remove limpa", () => {
    const map = fakeMap();
    addHeatmap(map, FC, "audiencia");
    expect(map.getSource(HEATMAP_SOURCE)).toBeDefined();
    expect(map.getLayer(HEATMAP_LAYER)).toBeDefined();
    removeHeatmap(map);
    expect(map.getSource(HEATMAP_SOURCE)).toBeUndefined();
  });
});
```

- [ ] **Step 2: Run — verify FAIL.** Run: `npx vitest run src/modules/ooh/map/heatmapLayers.test.ts` · Expected: FAIL.

- [ ] **Step 3: Implement.** Fill colorido por `value` com `interpolate` (rampa normalizada pelo min/max do payload). Inserir a layer **abaixo** das demais (primeira no z-order) — sem `beforeId` por simplicidade (o efeito a adiciona antes das outras no fluxo do componente, Task F9).

```ts
import type maplibregl from "maplibre-gl";

export const HEATMAP_SOURCE = "ooh-heatmap";
export const HEATMAP_LAYER = "ooh-heatmap-fill";

export type HeatmapLayerKey = "audiencia" | "renda" | "pop" | "footfall";

const RAMP: Record<HeatmapLayerKey, [string, string]> = {
  audiencia: ["#fee5d9", "#a50f15"],
  renda: ["#edf8e9", "#006d2c"],
  pop: ["#eff3ff", "#08519c"],
  footfall: ["#feedde", "#a63603"],
};

function maxValue(fc: GeoJSON.FeatureCollection): number {
  let m = 1;
  for (const f of fc.features ?? []) {
    const v = Number((f.properties as { value?: number })?.value ?? 0);
    if (v > m) m = v;
  }
  return m;
}

export function addHeatmap(map: maplibregl.Map, fc: GeoJSON.FeatureCollection, layer: HeatmapLayerKey): void {
  if (map.getSource(HEATMAP_SOURCE)) {
    setHeatmap(map, fc, layer);
    return;
  }
  map.addSource(HEATMAP_SOURCE, { type: "geojson", data: fc });
  const [lo, hi] = RAMP[layer];
  const max = maxValue(fc);
  map.addLayer({
    id: HEATMAP_LAYER,
    type: "fill",
    source: HEATMAP_SOURCE,
    paint: {
      "fill-color": ["interpolate", ["linear"], ["get", "value"], 0, lo, max, hi],
      "fill-opacity": 0.55,
    },
  });
}

export function setHeatmap(map: maplibregl.Map, fc: GeoJSON.FeatureCollection, layer: HeatmapLayerKey): void {
  const src = map.getSource(HEATMAP_SOURCE) as maplibregl.GeoJSONSource | undefined;
  if (src) src.setData(fc);
  if (map.getLayer(HEATMAP_LAYER)) {
    const [lo, hi] = RAMP[layer];
    const max = maxValue(fc);
    map.setPaintProperty(HEATMAP_LAYER, "fill-color", ["interpolate", ["linear"], ["get", "value"], 0, lo, max, hi]);
  }
}

export function removeHeatmap(map: maplibregl.Map): void {
  if (map.getLayer(HEATMAP_LAYER)) map.removeLayer(HEATMAP_LAYER);
  if (map.getSource(HEATMAP_SOURCE)) map.removeSource(HEATMAP_SOURCE);
}
```

- [ ] **Step 4: Run — verify PASS.** Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(map): heatmapLayers (fill por hexágono, rampa por camada)"`

---

### Task F8: tipos + hook `useExposureCells`

**Files:**
- Modify: `src/modules/ooh/api/types.ts` (adicionar tipos do exposure)
- Modify: `src/modules/ooh/api/intelClient.ts` (hook + entry em oohKeys)
- Test: `src/modules/ooh/api/intelClient.exposure.test.ts` (ou estender o existente)

- [ ] **Step 1: Tipos** (em `types.ts`):

```ts
export interface ExposureCellProps {
  h3: string;
  value: number;
  audiencia: number;
  renda: number;
  pop: number;
  footfall: number;
  classePredom: string;
}
export interface ExposureFeatureCollection {
  type: "FeatureCollection";
  layer: string;
  versionId: number;
  features: { type: "Feature"; properties: ExposureCellProps; geometry: GeoJSON.MultiPolygon }[];
}
```

- [ ] **Step 2: Write the failing test** (mockando o fetch client do intel — **seguir o padrão** dos testes existentes de `intelClient`; checar como `useLineGeo` é testado e espelhar).

```ts
import { describe, expect, it } from "vitest";
import { exposureCellsKey } from "./intelClient";

describe("exposureCellsKey", () => {
  it("inclui layer e hour na key", () => {
    expect(exposureCellsKey("audiencia", null)).toEqual(["ooh", "exposure", "audiencia", null]);
    expect(exposureCellsKey("renda", 8)).toEqual(["ooh", "exposure", "renda", 8]);
  });
});
```

- [ ] **Step 3: Run — verify FAIL.** Run: `npx vitest run src/modules/ooh/api/intelClient.exposure.test.ts` · Expected: FAIL.

- [ ] **Step 4: Implement** o hook + key (espelhando `useLineGeo`/`useVehiclePositions`). `enabled` controla o lazy-load (só busca quando o heatmap está ligado).

```ts
// em intelClient.ts (usar o mesmo fetcher/baseURL/headers dos outros hooks):
export function exposureCellsKey(layer: string, hour: number | null) {
  return ["ooh", "exposure", layer, hour] as const;
}

export function useExposureCells(layer: HeatmapLayerKey | null, hour: number | null = null) {
  return useQuery({
    queryKey: exposureCellsKey(layer ?? "none", hour),
    queryFn: () => getJson<ExposureFeatureCollection>(
      `/api/exposure/cells?layer=${layer}${hour != null ? `&hour=${hour}` : ""}`),
    enabled: layer != null,
    staleTime: 5 * 60_000,
  });
}
```
> `getJson`/`useQuery`/imports: usar exatamente os mesmos do arquivo (não inventar). Importar `HeatmapLayerKey` de `../map/heatmapLayers` e os tipos de `./types`.

- [ ] **Step 5: Run — verify PASS** + `npx tsc --noEmit`. Expected: PASS, 0 erros TS.
- [ ] **Step 6: Commit.** `git commit -m "feat(api): tipos + hook useExposureCells (GET /api/exposure/cells)"`

---

### Task F9: plugar heatmap em `CorridorMap` + `CestaMap`

**Files:**
- Modify: `src/modules/ooh/components/CorridorMap.tsx`
- Modify: `src/modules/ooh/components/CestaMap.tsx`
- Test: estender `CorridorMap.test.tsx` (toggle de heatmap aparece + radio behavior)

- [ ] **Step 1: Write the failing test** (no `CorridorMap.test.tsx`): ligar "Audiência" chama o fetch/mostra a layer; clicar de novo desliga; ligar "Renda" troca. Mockar `useExposureCells` p/ devolver um FC fixo. Verifica que **no máximo um** chip de heatmap fica `aria-pressed=true`.

```tsx
// adicionar ao CorridorMap.test.tsx (mock do hook no topo):
vi.mock("@/modules/ooh/api/intelClient", async (orig) => ({
  ...(await orig()),
  useExposureCells: () => ({ data: { type: "FeatureCollection", layer: "audiencia", versionId: 8, features: [] }, isLoading: false }),
}));
// it("liga/desliga heatmap como radio", () => { ... fireEvent nos chips ooh-map-toggle-heat-audiencia / -renda / -pop ... })
```

- [ ] **Step 2: Run — verify FAIL.** Run: `npx vitest run src/modules/ooh/components/CorridorMap.test.tsx` · Expected: FAIL.

- [ ] **Step 3: Implement (CorridorMap).** Estado `heatLayer: HeatmapLayerKey | null` (default null). 3 chips extras com key `heat-audiencia|heat-renda|heat-pop` (cores da legenda do heatmap, label "Audiência"/"Renda"/"Densidade"). `onToggle` desses chips faz radio: clicar no ativo → null; clicar noutro → set. `useExposureCells(heatLayer)` busca; um `useEffect([mapLoaded, heatLayer, data])` chama `addHeatmap`/`setHeatmap`/`removeHeatmap`. Hover: `useEffect` que registra `map.on("mousemove", HEATMAP_LAYER, …)` → `maplibregl.Popup` com `audiência·renda·pop`; cleanup remove os handlers. O `OohMapChips` recebe os chips de corredor **+** os 3 de heat (mantendo `vis` combinado).

- [ ] **Step 4: Implement (CestaMap).** Mesma mecânica de heatmap (estado `heatLayer`, hook, efeito add/remove, hover). Como a cesta não usa `OohMapChips` p/ o toggle 300m (bespoke), adicionar um pequeno grupo de 3 chips de heat (reusando `OohMapChips` com `testIdPrefix="ooh-cesta-map-heat"`) ao lado do toggle 300m. **Não** mexer no snapshot/legenda/honestidade.

- [ ] **Step 5: Run — verify PASS** (CorridorMap + CestaMap tests) + `npx tsc --noEmit`. Expected: PASS, 0 TS.

- [ ] **Step 6: Suite cheia do módulo OOH + commit.** Run: `npx vitest run src/modules/ooh` · Expected: todos verdes.
```bash
git add src/modules/ooh/components/CorridorMap.tsx src/modules/ooh/components/CestaMap.tsx src/modules/ooh/components/CorridorMap.test.tsx
git commit -m "feat(map): heatmap de audiência (Audiência/Renda/Densidade + hover) na ficha e na cesta"
```

---

# INTEGRAÇÃO FINAL

### Task INT: E2E local + verificação

- [ ] **Step 1:** Intel já roda em :8087 (mvn). Após o backend, reiniciar o intel (Java 21) e validar o endpoint:
  Run: `curl -s -H "Authorization: Bearer stub-token" "http://localhost:8087/api/exposure/cells?layer=renda" | python3 -c "import sys,json;d=json.load(sys.stdin);print('type',d['type'],'layer',d['layer'],'version',d['versionId'],'feats',len(d['features']))"`
  Expected: `type FeatureCollection layer renda version 8 feats >0`.
- [ ] **Step 2:** Portal (Vite :8080) — abrir ficha F1: ligar "Audiência" → heatmap pinta; trocar Renda/Densidade; hover mostra tooltip; desligar limpa. Abrir cesta: idem. Abrir RealTime: **sem** chip de heatmap.
- [ ] **Step 3:** Suites finais: `cd uai-ooh-intel && export JAVA_HOME=…/ms-21.0.10/Contents/Home && mvn -o -q test` (BUILD SUCCESS) · `cd uai-portal && npx vitest run src/modules/ooh && npx tsc --noEmit` (verdes).

---

## Self-Review (cobertura do spec)

- §4.1 casca (useOohMap/OohMapChips/MapCanvas) → F1/F2/F3 ✓
- §4.2 wrappers por contexto → F4/F5/F6 ✓
- §5.1 heatmap front (fill + toggle radio + hover) → F7/F9 ✓
- §5.2 endpoint intel → B1–B4 ✓
- §6 invariantes (não tocar layers/snapshot/slots; testids; read-only; Java 21) → notas em F5/F6/F9 + pré-requisitos ✓
- §7 testes → cada task + Task INT ✓
- Tipos consistentes: `HeatmapLayerKey` (F7) reusado em F8/F9; `ExposureFeatureCollection` (F8) bate com o shape do `FeatureCollectionDTO` (B4: type/layer/versionId/features → properties{h3,value,…}/geometry) ✓
