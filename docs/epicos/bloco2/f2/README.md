# F2 — Tempo real / verificado (motor de avaliação, sem comercial)

> **Entrega 2** do vertical uAI-OOH: o **motor verificado** que transforma o "estimado" do F1 em **medido**.
> Poller GTFS-RT → Kafka → consolidador de viagens → `intel` realtime + mapa ao vivo.
> **Origem:** `docs/epicos/epico-5-realtime-consolidado.md` + `docs/arquitetura-servicos.md` (Bloco 2).
> **Decisão de escopo (F2-#1, 2026-06-05): SÓ O MOTOR, ZERO COMERCIAL.** Funcionalidades de **avaliar/validar**,
> não de vender por campanha. A ativação/alcance-por-campanha é **Bloco 3**.

## O que o F2 entrega (sem nenhuma campanha)
- **Viagens verificadas por linha** (`core.trip_executed`): nº de viagens, km, completude.
- **Velocidade real por ponto** (`v_real`) → alimenta `f_vel`.
- **Frequência real** (vs GTFS estático).
- **→ Calibra o F1:** velocidade/frequência/viagens reais **estreitam a faixa ±35%** e validam o score. **Esse é o produto central do F2** (o loop verificado→estimado).
- **Mapa ao vivo** dos carros, **escopável por linha** (ex.: "carros da 4107").

## Lanes (= repos)

| Lane | Tipo | Repo | Tasks |
|---|---|---|---|
| [`01-infra/`](01-infra/) | 🟫 INFRA | `uai-infra` | INFRA-01..02 (Kafka KRaft + Redis + `raw` particionado) |
| [`02-poller/`](02-poller/) | 🟨 DATA | `uai-ooh-realtime-poller` *(novo, Python)* | POLL-01..03 |
| [`03-consolidator/`](03-consolidator/) | 🟦 BACK | `uai-ooh-trip-consolidator` *(novo, Java)* | CONS-01..04 |
| [`04-intel-rt/`](04-intel-rt/) | 🟦 BACK | `uai-ooh-intel` *(estende F1)* | RT-01..03 |
| [`05-front-rt/`](05-front-rt/) | 🟪 FRONT | `uai-portal` *(estende F1)* | WEB-01..02 |

## Primitivo tenant-agnóstico (o que mantém sem comercial)
O intel-RT serve **"posição/progresso de um conjunto de `vehicle_id`"**. No F2 o conjunto vem de um **filtro por LINHA** (avaliação); no Bloco 3 viria de uma **campanha**. Mesmo primitivo, quem chama é diferente → **sem acoplamento comercial agora, sem retrabalho depois**.

## Decisões & gates
- **F2-#1:** motor "frota inteira", sem comercial (✅ decidido). **CONS** roda sem filtro de ativos; a flag de **divergência-de-campanha fica latente** (sem "linha esperada" → Bloco 3).
- **Transporte (F2-#2, decidido 2026-06-06): polling atrás de uma `PositionFeed` port** (~15s, stateless — sem conexão always-on, casa com o cadence do GTFS-RT); SSE/WS ficam a **um adapter de distância**. **Regra anti-vazamento:** o conjunto de `vehicle_id` é **sempre derivado no servidor** do escopo autenticado (linha no F2; campanha+tenant no Bloco 3) — o cliente **nunca** pede IDs arbitrários.
- **✅ Gate INFRA resolvido (2026-06-06):** **Kafka (KRaft) + Redis já de pé** no `uai-infra` → INFRA-01 = só criar o tópico `ooh.rt.position`; resta INFRA-02 (`raw` particionado).
- **Depende de:** `core` (`trip_pattern`/`line_shape` — ✅ F1) · intel-RT estende o **intel F1** (lane 04 espera o EP2 do F1).
- **Sequenciamento:** infra ∥ poller ∥ consolidator podem rodar **em paralelo ao F1**; intel-RT e front-RT entram quando o intel/front do F1 existirem.
- **Convenção de criação de repo (POLL-01/CONS-01):** sempre via **`gh repo create`** + push de uma **`main` vazia** (baseline) **antes** de qualquer código → só então regerar do template. *(Convenção uAI — vale pra todo repo novo.)*

## Futuro (pós-F2) — acumulador de alcance ao vivo
Feature desejada (não no F2): o **alcance subindo ao vivo** conforme o carro anda — por **posição**, calcular a contribuição **incremental** de alcance (`v_real` local × corredor/embarque cacheado × coef) e **acumular por viagem no Redis**; no fim, **total = soma dos incrementos**. **Módulo de stream SEPARADO** (não o consolidador — que fica só com fatos —, não o hot-path do intel). Usa a **mesma fórmula** do RT-02 (agregado), então **reconcilia** (soma incremental ≈ agregado). **Continua estimativa** (coeficientes não calibrados), só que ao vivo. Dá a "sensação de ver o alcance em tempo real".

## Validação
Na linha **4107**: viagens/dia ~ frequência, km ~ extensão×viagens, **alcance verificado comparável ao estimado** (o loop). Counts no banco `ooh` (`core.trip_executed`) via MCP.
