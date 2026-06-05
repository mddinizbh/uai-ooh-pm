# T0 — Fundação dos repos (run 2026-06-04) — ✅ CONCLUÍDO

Executado via workflow `ooh-t0-fundacao` (2 trilhas paralelas + verificação independente com secret-scan).

## Entregue

### `uai-ooh-pipeline` (Python) — commit `00126d7` · CI ✅
- Pacote `ooh_pipeline/`: `db.py` (conexão **env-based**), `ingestor/` (loaders), `normalizer/` (gen_ficha_4107), `__main__.py` (CLI roda-e-sai).
- Loaders movidos de `.data/scripts/` (load_csv_generic, load_multi, load_gtfs_static, load_gtfs_rt, load_osm → ingestor/; gen_ficha_4107 → normalizer/), imports adaptados para `ooh_pipeline.db`.
- `requirements.txt`, `Dockerfile` (multi-stage python:3.12-slim, non-root, config 100% por env), `.gitignore` (.env/.env.*/.data/__pycache__/venv), `.env.example`, `pyproject.toml`, `.github/workflows/ci.yml`.
- CI: job test (ruff + pytest) → build-push GHCR `ghcr.io/mddinizbh/uai-ooh-pipeline` (`:latest`+`:<sha>`), deploy delegado ao `uai-infra`. **Verde** (test + build-push, imagem publicada).
- Correção pós-1ª-falha: `ruff format` + `--fix` nos scripts legados (I001/UP015/E702) → commit `00126d7`.

### `uai-ooh-service-template` (Java) — commit `48f199f` · CI ✅
- Esqueleto Spring Boot 3.3.6 hexagonal `com.uai.ooh.template` (domain/{model,port/in,port/out}, application/usecase, adapter/{in/web,out/persistence}).
- Exemplo end-to-end Ping (record + UseCase + Controller `GET /api/ping`); `HealthState` sealed sem default.
- `InternalApiKeyFilter` protege `/internal/**` via `X-UAI-Internal-Key` (env `UAI_INTERNAL_API_KEY`); `OpenApiConfig` (`/api/docs`); actuator health.
- `pom.xml`: api.version=1.44 (surefire+failsafe), Testcontainers **1.21.3**, JaCoCo 0.8.12 check ≥80%, Java 21. `Dockerfile` multi-stage. `deploy.yml` (test mvn verify + build-push GHCR `ghcr.io/${{github.repository}}`).
- Marcado como **GitHub Template** (`isTemplate=true`). `mvn -DskipTests compile` EXIT=0. CI **verde** (mvn verify com Testcontainers no runner).

## Secret
- `db.py` 100% `os.environ` (DB_HOST/PORT/NAME/USER/PASSWORD); **secret-scan independente limpo** (working tree + histórico git completo). A senha `ooh_admin` (linha 13 do `db.py` original) **NÃO** foi para nenhum repo.
- ⚠️ **PENDENTE (operacional, fora do workflow):** rotação efetiva da senha `ooh_admin` no banco dev local — `ALTER ROLE ooh_admin PASSWORD '<nova>'` + atualizar o `.env` local. Deixado de fora de propósito (muda o ambiente dev ativo).

## Decisões / adaptações
- `ingestor`+`normalizer` no MESMO repo `uai-ooh-pipeline` (decisão consolidada verificada).
- Template usa prefixo `/internal/` (não `/api/internal/` do bus-lines); `deploy.yml` usa `ghcr.io/${{github.repository}}` → serviço gerado publica na imagem certa sem edição manual. Flyway removido (template genérico; migrations no serviço concreto).
- CLI `__main__` expõe gtfs-static/gtfs-rt/osm/ficha-4107; load_csv_generic/load_multi rodam via `python -m ooh_pipeline.ingestor.<modulo>` (argv complexo).

## Pendências que não bloqueiam (anotar para T1+)
- **Paridade da ficha 4107 NÃO validada** (precisa do banco `ooh` local + env DB_*). Validar ao rodar T1/T2.
- `geopandas`/`pyogrio` no requirements mas ainda não usados (loaders vetoriais futuros).
- Warnings de deprecation Node.js 20 nas Actions (checkout@v4 etc.) — atualizar quando conveniente.

## Critério de pronto do T0 — atendido
Repos `uai-ooh-pipeline` e `uai-ooh-service-template` no GHCR com **CI verde**; `db.py` sem DSN; template marcado; secret não vazou. (Rotação efetiva do secret = passo operacional ainda pendente.)

**Próximo:** T1 — baseline 4107 recomputada + resolução do modelo `trip_pattern` (`docs/epicos/bloco1/tarefas/T1.md`).
