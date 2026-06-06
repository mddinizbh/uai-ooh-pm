# Run — EP1-03 (pontos das paradas no serving: geometria GeoJSON) · 2026-06-06 · ❌ FAILED / BLOQUEADA

> Execução no `uai-ooh-pipeline` (branch `feat/ooh-ep1-03`), normalizer projetando a geometria das
> paradas (`core.stop.geom_31983` → GeoJSON 4326) na 4ª camada que o EP2-08 precisa servir. Estado
> **verificado no banco `ooh`** via MCP `postgres-ooh` em 2026-06-06, contra a versão de serving
> **ACTIVE (v5)**. Task do mapa F1: `docs/epicos/bloco1/f1/01-pipeline-dados/EP1-03-pontos-serving.md`.
>
> **Veredito: FAILED / BLOQUEADA.** Código escrito, compilado, **review aprovado** e **20/20 testes
> passando** (9 novos) — mas **nada chegou ao banco**. O critério de pronto de uma task de dados EP1 é
> contra o `ooh` vivo, e lá a coluna `serving.line_stop.geom_geojson` **não existe**: a migração/materialize
> **não foi aplicada** na v5 ACTIVE. As lanes implement/review/test ficaram verdes no worktree; o
> **gate de db-validate derrubou** o done. Sem geom no serving → mapa do front continua **sem marcadores
> de parada** (o gap que originou esta task no EP2-08 segue aberto).

## Counts reais vs. esperado (banco `ooh`, MCP `postgres-ooh`, serving v5 ACTIVE)

| Gate (critério de pronto) | Esperado | Medido no banco AGORA | Veredito |
|---|---|---|---|
| Coluna `serving.line_stop.geom_geojson` (Tarefa 1 — DDL) | existe (`jsonb`), via `ADD COLUMN IF NOT EXISTS` | **não existe** — `line_stop` segue com 7 colunas (sem geom); DDL não aplicado | ❌ |
| `geom_geojson` populado (Point GeoJSON 4326) | 84.992 rows / 9.192 paradas distintas (= 303 linhas) na v5 | **0** — não executável: coluna ausente, materialize não rodou | ❌ |
| `0` null onde `core.stop` tem geometria | 0 nulls (todas as 9.192 paradas servidas têm `geom_31983` em `core.stop` → 0 esperado) | não aferível (sem coluna) | ❌ |
| `0` tipo ≠ `Point` / `0` com `crs` | 0 / 0 | não aferível (sem coluna) | ❌ |
| `serving.dataset_version` | exatamente 1 ACTIVE | v5 ACTIVE + v4 ARCHIVED (= 1 ACTIVE) — **intocada** por esta task | ⚠️ |

Baselines reconferidos no banco (o que **deveria** ter sido populado): `serving.line_stop` v5 ACTIVE =
**84.992 rows / 9.192 stop_id distintos / 303 line_id**; e **9.192/9.192** dessas paradas têm
`geom_31983` em `core.stop` (0 ausentes) — ou seja, a materialização cobriria 100% sem null. O alvo
existe e é limpo; só **não foi escrito**.

## Build / testes (escopo declarado — todos verdes, mas em lane que NÃO é o gate de record)

- **Compilação:** `py_compile` em todos os `.py` rastreados (`git ls-files`) = `PY_COMPILE_OK`. venv do
  repo com `psycopg 3.3.4` / Python 3.14.5.
- **Suite:** `.venv/bin/python -m pytest -q` (fallback do repo — o `python -m pytest` do sistema falha:
  Homebrew Python 3.14 sem `psycopg`/`ooh_pipeline`). **20/20 passou** (0 falhas, 0 skips), incluindo os
  **9 testes novos** esperados (9/9). A **suite completa do repo NÃO foi rodada** (escopo do estágio Test).
- **Review:** aprovado — o reviewer atestou que DDL (`CREATE` + `ALTER … ADD COLUMN IF NOT EXISTS` dentro
  do bloco DDL único, antes do `GRANT`) cobre DB novo e DB já materializado pelo T17 sem geom (caso da v5),
  e que o materialize segue o **mesmo padrão do EP1-02/`line_shape`**: `INSERT…SELECT` server-side com
  `ST_AsGeoJSON(ST_Transform(st.geom_31983,4326))::jsonb` + `LEFT JOIN` por `stop_id`, carimbado com a
  versão do serving; validate traz os 3 asserts do critério.
- **Limite que invalida tudo acima como prova de done:** review e testes rodaram contra o **código no
  worktree**, não contra o `ooh` vivo. Para uma task de dados EP1 o gate de record é o **banco** — e lá a
  mudança **não existe**. Verde em implement/review/test ≠ done.

## Decisões / desvios

- **A migração/materialize nunca foi aplicada na v5 ACTIVE.** Causa-raiz da reprovação: o pipeline rodou as
  lanes implement→review→test no repo, mas o passo que **aplica a DDL + roda o materialize contra o `ooh`**
  e confere os counts (db-validate) encontrou a coluna ausente. Não houve execução do runner de migração
  apontando para o banco vivo (ou rodou contra alvo diferente). Sem isso, `feat/ooh-ep1-03` é só código em
  branch — o serving segue idêntico ao deixado pelo T17.
- **O `ADD COLUMN IF NOT EXISTS` é idempotente, mas idempotência não materializa sozinha.** O DDL foi
  desenhado certo para o caso da v5 (materializada pelo T17 **sem** geom) — só que ninguém o executou. A
  defesa de idempotência protege contra re-rodar, não contra **nunca rodar**.
- **Sem branch mergeada / sem ALTER aplicado, EP2-08 não destrava.** A 4ª camada do `/geo` (pontos)
  continua impossível de servir sem `ST_*` em runtime, que é justamente o que o ADR-003 proíbe ao intel.
  O gap que a validação adversarial do EP2-08 (2026-06-06) levantou **permanece aberto**.

## Estado do `dataset_version`

- `serving.dataset_version`: **v5 ACTIVE** + **v4 ARCHIVED** (= exatamente 1 ACTIVE) — **inalterado** por
  esta task. Diferente do EP1-02 (que carimbou `line_corridor`/`line_poi` em `version_id=5`), aqui **nada
  foi carimbado** porque nada foi escrito. Não há versão órfã nem divergência a reconciliar — há **ausência**.
- Quando a task for desbloqueada, o `geom_geojson` deve nascer carimbado em `version_id=5` (a ACTIVE
  corrente), no mesmo padrão version-stamped do EP1-02.

## Adversarial — o gate DERRUBOU o done (refuted: true)

- **Atacou o critério #1 (DDL aplicada) e DERRUBOU.** `SELECT EXISTS(… column_name='geom_geojson')` =
  **false**, reconferido AGORA via MCP. A coluna não existe; `line_stop` continua com as 7 colunas do T17.
  O `ADD COLUMN IF NOT EXISTS` está no código mas não no banco. Gate de dados: **caiu na primeira asserção**.
- **Atacou o critério #2 (materialização) e DERRUBOU por construção.** A contagem alvo
  (`… WHERE version_id=5 AND geom_geojson IS NOT NULL`) é **não-executável** — a coluna não existe. Baseline
  esperado (84.992 rows / 9.192 paradas, todas com geom em `core.stop`) confirma que havia o que popular;
  **zero** foi populado. Não é "0 nulls porque está limpo", é "0 porque não rodou".
- **Onde o EP1-02 sobreviveu, o EP1-03 não chega a ser testado.** No EP1-02 o cético tentou derrubar 3
  critérios e falhou (counts batiam no banco). Aqui o estágio **refute nem precisou rodar** (`refute=null`):
  o db-validate já reprovou antes, porque não há estado materializado para o cético atacar. Falha **a
  montante** do refute.
- **O que NÃO é a causa (para não culpar o lugar errado):** não é bug de código, não é teste flaky, não é
  versão órfã (pecado da T8b), não é falta de geom na origem — `core.stop` tem **9.192/9.192** com
  `geom_31983`. É **falha de execução do passo de aplicação no banco**. O conserto é operacional (rodar a
  migração + materialize contra o `ooh` na v5 ACTIVE e reconferir os counts), não uma reescrita.

## Desbloqueio (próximos passos)

1. **Aplicar a DDL** `ALTER TABLE serving.line_stop ADD COLUMN IF NOT EXISTS geom_geojson jsonb` no `ooh`
   (v5 ACTIVE) e **rodar o materialize** (`INSERT…SELECT`/`UPDATE…FROM` server-side com
   `ST_AsGeoJSON(ST_Transform(geom_31983,4326))::jsonb`, join por `stop_id`, carimbado em `version_id=5`).
2. **Reconferir no banco** e re-rodar este run: alvo = **84.992 rows com `geom_geojson` não-null** (0 null,
   0 tipo ≠ `Point`, 0 com `crs`), 9.192 paradas distintas, 303 linhas.
3. Só então marcar EP1-03 **DONE** e destravar **EP2-08** (4ª camada do `/geo`) → **EP4-04** (marcadores de
   parada no mapa).

## Tracking

- Run **canônico** gravado aqui no hub de PM
  (`uai-ooh-pm/docs/epicos/runs/EP1-03-pontos-serving.md`), registrando o estado **FAILED/BLOQUEADA**
  verificado no banco `ooh` em 2026-06-06.
- Código fica parado em `feat/ooh-ep1-03` (não mergeado, não aplicado). Bloqueia: **EP2-08** → **EP4-04**.
