# Bloco 3 — pré-planejamento: campanhas OOH = módulo OOH no CMS

> **Notas de design** do brainstorm de 2026-06-10 (replanejamento do F2 — decisões F2-#3..#10 em
> `../bloco2/f2/README.md`). **Não são tasks** — viram lanes/cards quando o Bloco 3 abrir.
> Decisão central registrada: **F2-#9 — campanhas OOH são um MÓDULO do `uai-cms`, não um serviço novo.**

## 1. Decisão: módulo no CMS (revisa a ADR-052 do `uai-ooh-pipeline`)

A ADR-052 apontava um repo `uai-ooh-commercial` separado. Revisado em 2026-06-10:

- **Campanha já é conceito agnóstico de vertical no CMS** (`campaign` + `campaign_channel` com
  `channel_type`, tenant/client/briefing/budget). uAI é multi-marketing: o cliente X pode ter
  social + OOH **na mesma campanha** — 1 linha em `campaign`, N canais. Relatório cross-canal sai
  do CMS naturalmente.
- **O módulo já nasceu lá:** `ai.uai.cms.ooh` (V5 `ooh_active_vehicle`, `OohVehicleService`,
  `VehicleStatusPublisher` → tópico `ooh.vehicle.status`). Mesma filosofia do front
  (`uai-portal/src/modules/ooh/`).
- **Regra BFF decide o tráfego:** portal só fala com o CMS — um serviço separado ficaria atrás do
  CMS de qualquer jeito (mais um salto, mesma origem). Tenant/auth (`uai-auth`) já resolvidos no CMS.
- **Gatilho de extração** (se um dia valer o serviço próprio): operação comercial OOH com ciclo
  próprio pesado — inventário/reserva/instalação/billing por face — ou time dedicado. Proteção até
  lá: módulo autocontido (pacote `ooh`, tabelas `ooh_*`, conversa com o resto só por `campaign_id`).

## 2. Fronteira com o F2 (o que o motor garante pro comercial)

```
fato:      consolidador → medido.* (puro, SEM campaign_id — F2-#5)
contrato:  ooh.trip.completed ENRIQUECIDO (viagem, vehicle_code, linha, período, km,
           completude, resumo de cobertura) — consumidor NÃO acessa medido/core (F2-#10)
audiência: intel (serving face_reach/line_reach; reach de campanha = UNIÃO sob demanda
           via CampaignScope — reach NUNCA soma entre faces/dias)
```

Teste decisório das camadas (F2-#3): *reconstruível de raw+GTFS → `medido`/`core`; precisa de
contrato/tenant → banco do CMS.*

## 3. Modelo de dados (banco do CMS — evolução)

### `channel_type` ganha OOH
`OOH_BUS` agora; `OOH_BACKSEAT` quando a face interna existir (ver §5). OOH entra como canal da
campanha (`campaign_channel`), igual aos canais sociais.

### `ooh_active_vehicle` → `ooh_placement` (a mudança estrutural)
A V5 atual (`UNIQUE(vehicle_code)` + boolean) só representa "quem está ativo agora" — **sem
histórico, sem vigência, sem 2 faces no mesmo carro**. Não atribui viagem passada após troca de
campanha e bloqueia traseira+backseat simultâneos.

```sql
ooh_placement (                      -- contrato de ocupação de uma face por uma campanha
  id uuid PK,
  campaign_id uuid FK,
  vehicle_code text NOT NULL,        -- ref ao mundo (sem FK cross-db; junção ADR-052/057)
  face_type text NOT NULL,           -- TRASEIRA | BACKSEAT | ...
  status text NOT NULL,              -- RESERVED → ACTIVE → ENDED
  activated_at timestamptz, deactivated_at timestamptz,   -- vigência
  expected_line text,                -- pra flag de divergência (§4)
  install_meta jsonb                 -- foto, instalador, ...
)
-- regra: sem sobreposição de vigência ATIVA por (vehicle_code, face_type)
-- (EXCLUDE USING gist com tstzrange, ou validação de app)
```
`ooh_active_vehicle` vira **view** dos ativos atuais (o `VehicleStatusPublisher` existente continua
funcionando sem mudança).

### `ooh_delivery_trip` (atribuição da entrega)
```sql
ooh_delivery_trip (
  viagem_ref text UNIQUE,            -- viagemId do evento (medido.viagem)
  placement_id uuid FK, campaign_id uuid FK,
  vehicle_code text, service_date date, line_id text,
  km numeric, completude numeric,
  divergencia boolean                -- line_id ≠ expected_line do placement
)
```
Preenchida pelo **consumer do módulo OOH** em `ooh.trip.completed`: casa `vehicle_code` + janela da
viagem contra a **vigência** do placement. **A divergência mora AQUI** (o consolidador não sabe o
que era esperado — F2-#5).

### Métricas de audiência
Padrão existente `campaign_channel_metrics` (canal × dia): snapshot diário de impressões/alcance
**buscado do intel**. ⚠️ **Reach não soma** (união com dedup, nem entre faces nem entre dias): o
diário é informativo; o número do **período** da campanha o CMS pede ao intel na hora do relatório
(`CampaignScope` — variante sealed prevista no RT-03).

## 4. Fluxos

```
ativação:  CMS (módulo ooh) cria/ativa placement ──ooh.vehicle.status──► quem precisar
entrega:   consolidador ──ooh.trip.completed──► CMS atribui → ooh_delivery_trip (+divergência)
relatório: portal → CMS: campanha + canais sociais + entrega OOH (local)
                       + audiência via intel server-to-server (CampaignScope, união do período)
```

## 4b. Decisão de produto — os 3 tempos: cesta por LINHA, campanha por contrato, relatório pela FACE medida (2026-06-10)

Contexto (achado do preview de alcance, run `F2-preview-alcance-medido-fixture.md`): o operador
troca o carro de linha a qualquer momento; **a plotagem segue o carro**, não a linha. O sistema de
bordo atualiza a rota → o RT enxerga a troca; o contrato comercial ("vai rodar na linha X") não.
Modelo definido pelo dono:

1. **Montar a CESTA (pré-venda):** vende-se "carro rodando na linha X" e o **alcance estimado vem
   da ROTA da linha** = `line_reach` (corredor — são, não tem a inflação do estimado por face).
   Regra da tríade na cesta: **alcance** da face ≈ alcance do corredor da linha (2 carros na mesma
   linha NÃO dobram alcance — mesma audiência); **impressões** ∝ participação do carro nas viagens
   da linha (essas dobram).
2. **CAMPANHA ativa (CMS):** o contrato registra a linha (`expected_line` do placement) — pro
   cliente, o carro "está na linha X". **Mapa ao vivo mostra SÓ CARROS ANDANDO — nunca traceja
   rota/linha.**
3. **RELATÓRIO (entrega):** medido da face — km, viagens, **cobertura real** (hexes visitados),
   **alcance medido** (UNIÃO dedup de `id_usuario` — alcance NUNCA soma entre hexes/dias) e
   **impressões** (somam). **Divergência (contratada × rodada)** fica INTERNA em
   `ooh_delivery_trip.divergencia` (gestão com o operador; expor ao cliente é escolha de
   apresentação).

**Consequência técnica:** o `face_reach` **estimado** (T4/vlh — espalha o carro por 5–14 linhas,
inflação 6–12× medida no preview) **sai do caminho de produto** (nenhum dos 3 tempos o consome).
Não corrigir o T4 agora (correção via prior-RT descartada por desnecessária); marcar o artefato
como interno/deprecado. Estimativa de face = corredor da linha contratada; medido assume na entrega.

## 5. OOH-BACKSEAT (face interna) — o que falta pra medir

Audiência do backseat = **embarcados** (quem está dentro), não a rua. O modelo face-centric aguenta
(`face_type` com função de audiência própria):

| Ingrediente | Estado |
|---|---|
| Demanda/ocupação por linha × hora (`line_demand_hourly`/`line_turnover`/`line_load_segment`, Onda 1) | ✅ |
| Que veículo rodou que linha/hora/quantas viagens (`medido.viagem`, F2) | ✅ pós-F2 |
| **Únicos embarcados** por linha/veículo (reach dedup) | ⚠️ **gap**: `od_trip` sem `route_id` — bilhetagem com atribuição de linha/veículo é o dado a buscar |

**Impressões backseat** viáveis pós-F2 (embarcados ÷ viagens da linha × viagens do carro — denominador
real). **Alcance único** fica com selo baixo até o dado chegar. Dwell altíssimo (a viagem inteira) →
coeficientes próprios por `face_type`.

## 6. Pendência herdada do F2 (registro)

- **trip-updates (F2-#10b):** feed irmão 1:1 com positions (`departure.delay` + próxima parada) —
  âncora de calibração do consolidador + proxy de congestionamento. Capturar = +1 fetch no poller.
