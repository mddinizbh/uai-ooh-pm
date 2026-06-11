# RECAL-01 — recompute por fonte/método (estimada vs medida) — run **FAILED/BLOQUEADA** (2026-06-11)

> Pipeline `implement → review → test → db-validate → refute`. Os 4 primeiros estágios passaram (código
> compila, review aprovou, 82 testes verdes), mas o **db-validate reprovou (`ok:false`)**: a marca de
> fonte/método não está materializada no banco `ooh`. Sem evidência no banco, a task **não fecha**.

## Repo / branch

- **Repo-alvo:** `uai-ooh-pipeline` (`/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pipeline`)
- **Branch:** `feat/ooh-recal-01`
- **Estágio de parada:** `db-validate` (4º de 5) — `refute` não chegou a rodar.

## Counts reais vs. esperado (banco `ooh`, 2026-06-11)

| # | Check | Esperado | Real | OK? |
|---|-------|----------|------|-----|
| 1 | `serving.face_reach` tem coluna `fonte`/`metodo` | `true` (marca de fonte presente) | `false` — colunas: `vehicle_code, periodo, reach, impressoes, frequencia_media, n_linhas, selo_confianca, version_id` | ❌ |
| 2 | `serving.line_reach` tem coluna `fonte`/`metodo` | `true` | `false` — sem coluna de fonte | ❌ |
| 3 | Linhas `fonte=medida` em `serving.face_reach` (237 linhas com medido) | ≥ 237 linhas marcadas `medida` | **0** — impossível, coluna não existe | ❌ |
| 4 | `core.line_reach` tem coluna `metodo` | `true` (marca no grão de linha) | `false` — coluna inexistente | ❌ |
| 5 | `core.face_reach.metodo` distribui estimada+medida | mix com `medida` populado | só `estimada` = **10.612**, `medida` = **0** | ❌ |
| 6 | `version_id` do serving consta em `core.dataset_version` | sim (build rastreada) | `serving` em `version_id` **7 e 8**; `dataset_version` só tem **1 (BUILDING)** | ❌ |

Resumo: o serving vivo (`face_reach` v7/v8 = 10.612 linhas; `line_reach` v7/v8 = 20.349 linhas) é a
**build ESTIMADA legada**. O recompute por fonte do RECAL-01 não tocou esse estado.

## Evidência por estágio (o que cada um reportou)

- **implement** — `status: complete`, branch `feat/ooh-recal-01`. Diz ter corrigido o MAJOR da tentativa
  anterior: serving (`materialize.py`) deixaria de copiar `core.line_reach`/`face_reach` sem filtro de
  método, via `_filtro_metodo_serving(cur, tabela)` que injeta `metodo='estimada'` nas 3 cópias
  core→serving (robusto a tabela pré-RECAL-01).
- **review** — `APROVADO`. Confirma os 3 itens do checklist no diff real e (alegadamente) no banco;
  sem issues critical/major; observações minor/informativas.
- **test** — `82 passed / 0 failed / 0 skipped` no `.venv` do projeto
  (`/Users/.../uai-ooh-pipeline/.venv/bin/python -m pytest -q`). 35 testes novos criados (esperado 35),
  incluindo `tests/test_fonte_medida_recal01.py`. Suíte completa **não** rodada no estágio Test.
- **db-validate** — **`ok:false`**. Checks SQL contra `ooh` mostram que nenhuma marca de fonte/método
  existe no serving e que `medida` é zero em todo o pipeline (tabela acima).
- **refute** — `null` (não executado; o pipeline parou no db-validate).

## Por que falhou (diagnóstico)

A discrepância é **review/test verdes vs. banco vazio de "medida"**. Causas que o validate expõe:

1. **A marca de método não existe no grão onde precisa.** `core.face_reach` tem `metodo` (mas 100%
   `estimada`); `core.line_reach` **não tem** `metodo`. O `_filtro_metodo_serving` injeta a constante
   `'estimada'` na cópia — ou seja, mesmo se rodasse, marcaria tudo como estimada. Não há caminho para
   `medida` chegar no serving.
2. **As colunas `fonte`/`metodo` não foram adicionadas ao serving.** `serving.face_reach`/`line_reach`
   não têm coluna de fonte. A migration/DDL que o card pressupõe não foi aplicada no banco.
3. **O recompute não rodou contra este banco.** O serving vivo está em `version_id` 7/8, builds
   estimadas legadas que **nem constam em `core.dataset_version`** (só `version_id 1`, `BUILDING`). Os
   testes verdes validam a *lógica em fixtures*, não a materialização no `ooh`.

Os testes passam porque exercitam o código de cópia/filtro isoladamente; eles **não** asseguram que a
DDL de fonte foi aplicada nem que existe linha `medida` no core para propagar. Gap clássico de
"unit-green, integration-red".

## Adversarial (o cético tentou derrubar o FAILED — e não conseguiu)

- **"As 237 linhas medidas estão em outra version_id / outra tabela?"** Não. `core.face_reach` inteiro é
  `estimada` (10.612). `core.line_reach` não tem nem coluna `metodo`. Não há `medida` em lugar nenhum do
  core/serving. Procurei a fonte da marca e ela não existe no banco.
- **"O serving talvez use coluna com outro nome (ex.: `selo_confianca` carrega a fonte)?"** Não.
  `selo_confianca` é selo de confiança (texto), pré-existente às duas builds. Nenhuma coluna de
  `serving.*` separa estimada de medida — listei o schema completo das duas tabelas.
- **"Review aprovou olhando o banco — quem está certo?"** O banco. O review afirma ter confirmado "no
  banco `ooh`", mas a inspeção direta (2026-06-11) contradiz: as colunas alegadas não existem. O review
  validou o diff e provavelmente um banco de teste/fixture, não o `ooh` de verdade. Evidência > alegação.
- **"82 testes verdes não bastam?"** Não para esta task. O critério de aceite do RECAL-01 é a marca de
  fonte fluindo `core → serving` **no banco**, não a lógica em fixtures. Os testes não cobrem a DDL
  aplicada nem a existência de linha `medida` real — então passam mesmo com o objetivo não atingido.

Conclusão: o cético **não derrubou** o veredito. A falha do db-validate é real e bem fundamentada.

## Decisões / desvios

- **Status final: FAILED/BLOQUEADA.** Não promover `feat/ooh-recal-01` enquanto o banco não mostrar a
  marca de fonte materializada.
- **Não confiar no "review aprovado" como prova de estado de banco** — a validate desmente. O sinal de
  verdade do repo de PM é o banco `ooh` (convenção do `CLAUDE.md` deste repo), não o verdict do reviewer.

## Próximos passos (para destravar)

1. **DDL primeiro:** adicionar coluna de fonte/método em `core.line_reach` e em
   `serving.face_reach`/`serving.line_reach` (migration aplicada no `ooh`, não só em fixture).
2. **Origem de `medida`:** definir de onde saem as 237 linhas medidas (qual job/consolidator escreve
   `metodo='medida'` no core) — sem fonte de `medida`, o `_filtro_metodo_serving` só propaga `estimada`.
3. **Rebuild rastreado:** rodar o materialize gerando `version_id` registrado em `core.dataset_version`
   (o serving atual v7/v8 é órfão) e **re-rodar o db-validate** contra o `ooh`.
4. Só então liberar o estágio `refute` e fechar a task.

---

**Arquivo gravado:** `/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pm/docs/epicos/runs/RECAL-01-recompute-fonte-medida.md`
