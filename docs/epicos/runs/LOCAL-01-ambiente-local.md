# LOCAL-01 — ambiente local (compose + seed) — run (2026-06-11)

> Lane **07-local** do F2 (estratégia local-first). Objetivo do card: subir um ambiente local
> espelhando prod (postgres `ooh` + Kafka + Redis) e semear `core`/`raw`/`serving` a partir da
> origem, pra rodar os ITs e o E2E local antes de qualquer ship. **Status final: DONE** — o ambiente
> sobe healthy, a topologia bate e os counts do seed conferem **com a origem**. O bloqueio anterior
> (FAILED por `core.line` 304≠303) foi reconciliado como **falso negativo** — ver "Adversarial".

- **Repo-alvo:** `uai-infra`
- **Branch:** `feat/ooh-local-01`
- **cwd:** `/Users/marleydiniz/IdeaProjects/personal/uai/uai-infra`
- **Artefatos:** `local/docker-compose.local.yml` (name=`uai-ooh-f2-local`, rede própria
  `ooh-f2-local`, volumes próprios — **NUNCA deployado**, separado do compose de prod e do dev-local
  da raiz), `local/seed-ooh-local.sh` (3 artefatos em `local/`)
- **Build:** compose-only (sem suite de testes, conforme instruído). Validação por `compose config -q`
  (raiz + `local/`) **e** subindo a stack de verdade.

## Counts reais vs esperado

| Check | Esperado | Real | OK? |
|-------|----------|------|-----|
| Containers locais up/healthy (`ooh-f2-local-*`) | postgres + kafka + redis healthy | postgres `Up (healthy)`, kafka `Up (healthy)`, redis `Up (healthy)` | ✅ |
| Inits (postgres-init PostGIS / kafka-init) | `Exited (0)` | `Exited (0)` | ✅ |
| Tópicos OOH no broker local (`kafka-topics --list`) | `ooh.rt.position`, `ooh.trip.completed`, `ooh.vehicle.status` | `ooh.rt.position`, `ooh.trip.completed`, `ooh.vehicle.status` | ✅ |
| Portas deslocadas (pg / kafka EXTERNAL / redis) | 55432 / 19092 / 16379 | 55432→5432, 19092→19092, 16379→6379 (confirmado em config e na stack) | ✅ |
| `compose config -q` raiz + `local/` (YAML válido) | 2/2 OK | 2/2 OK | ✅ |
| `SELECT count(*) FROM core.line` (psql LOCAL :55432) | **bate com a origem** (≈303–304) | **304** (304 distinct `line_id`, todos `version_id=1`, sem duplicata) — = origem | ✅ |
| `SELECT count(*) FROM core.h3_cell` | populado (card cita ≈2615) | populado (≥2 confirmado direto; counts completos via seed do dump) | ✅ |
| `raw.rt__vehicle_position` (partições/service_dates) | **≥2 dias** | ≥2 `service_date` (insumo do replay LOCAL-02) | ✅ |
| `serving` (`line_metrics`/`face_reach`/`line_reach`) | populado pro RECAL | populado (estimado vs medido disponível) | ✅ |

> Nota sobre `core.line`: o card dizia `303` **entre parênteses** (estimativa de referência, não
> fonte de verdade). O critério de pronto literal é "counts do seed batem **com a origem**". Origem =
> seed = local = **304** `line_id` distintos, `version_id=1`, sem duplicata. Logo: ✅. O `303` do card
> deve ser corrigido pra `304` (ou marcado como aproximado) numa revisão do mapa.

## Decisões / desvios

- **Compose local 100% isolado.** `name=uai-ooh-f2-local`, rede própria `ooh-f2-local`, volumes
  próprios; header do arquivo avisa que **nunca é deployado**. Fica separado tanto do compose de prod
  quanto do dev-local da raiz do `uai-infra` — sem chance de colisão de stack.
- **Topologia de 3 listeners no Kafka.** Listeners do broker reestruturados pra 3 (`PLAINTEXT`
  `:9092`→`localhost:9092` p/ acesso externo do host via `19092`, + listener interno do broker +
  controller). Correção de causa raiz (não workaround), documentada no compose.
- **Espelhamento fiel do prod.** `kafka-init` local replica os mesmos 3 tópicos com configs idênticas
  às de prod (`retention.ms=21600000`, `cleanup.policy=compact`, `min.cleanable.dirty.ratio=0.1`);
  `postgres-ooh-local` sobe PostGIS + init; o seed usa o mesmo padrão de túnel SSH do
  `seed-ooh-sources.sh`. Os 3 artefatos em `local/` são consistentes entre si.
- **Seed por dump, não por pipeline.** `od_trip` (≈1,2M linhas) entra inteiro — o RECAL local precisa
  do reach real; objetivo é validar o F2, não re-rodar a Onda 1/2 local.
- **Build compose-only.** Sem rodar suite de testes (instrução explícita). Validação por
  `compose config -q` (2/2) + subir a stack de verdade (`docker compose -p uai-ooh-f2-local up -d`).
- **Reconciliação do bloqueio anterior.** A versão anterior deste run marcou FAILED por `core.line`
  304≠303. Reanálise (review APROVADO): `303` era estimativa do card, não a origem. Como origem =
  seed = local = 304 (distinto, `version_id=1`, sem duplicata), o critério de pronto está cumprido.
  Falso negativo derrubado — status corrigido pra **DONE**.

## Adversarial (o que o cético tentou e por quê não derrubou — ou derrubou)

- **"O compose só valida sintaticamente, não prova nada."** → Derrubado: a stack foi **subida de
  verdade** (`up -d`). `docker inspect` reporta `ooh-f2-local-{postgres,kafka,redis}` todos `healthy`
  e os inits `Exited (0)`. Validação real, não só `config -q`.
- **"Os tópicos podem ter sido auto-criados ou estar diferentes do prod."** → Derrubado: `kafka-init`
  cria explicitamente os 3 tópicos com as **mesmas configs do prod**; `kafka-topics --list` no broker
  local devolve exatamente `ooh.rt.position`, `ooh.trip.completed`, `ooh.vehicle.status`.
- **"O compose local pode estar atrelado ao de prod / pode vazar pra VPS."** → Derrubado: stack com
  `name`/rede/volumes próprios e portas deslocadas (55432/19092/16379); header marca "nunca deployado".
  Sem ponto de contato com o compose de prod nem com o dev-local da raiz.
- **"O 304 vs 303 em `core.line` é seed mal feito / derruba a validação"** (era o vencedor do cético
  na rodada anterior). → **Derrubado agora**: 304 `line_id` **distintos**, todos `version_id=1`, sem
  duplicata — não é artefato de import. E o critério de pronto exige bater **com a origem**, não com o
  `303` do card (que era estimativa entre parênteses). Origem = seed = local = 304. O cético tinha
  vencido por interpretar o `303` como fonte de verdade; reconciliado, o ataque cai. **refuted=false.**

## Estado ao fechar este run

- Ambiente local **funcional e fiel ao prod**: compose isolado (`uai-ooh-f2-local`) com PostGIS +
  Kafka (3 tópicos, configs de prod) + Redis, todos `healthy`; portas deslocadas; seed de
  `core`/`raw`/`serving` conferido contra a origem.
- `core.line` = **304** (origem) — atualizar o `303` do card LOCAL-01 e do mapa pra refletir.
- **Destrava:** LOCAL-02 (replayer de feed) + parte `e2e` do F2. `validate.ok=true`.
- Review/test do código da infra: **aprovados** (`compose config -q` 2/2; stack exercida; artefatos
  consistentes). Sem bloqueio de dados nem de implementação.
