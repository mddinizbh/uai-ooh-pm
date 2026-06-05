# Épico 6 — Comercial (F3): cms multi-vertical + uai-ooh-commercial

> Bloco 3 do roadmap. **Workstream de PLATAFORMA** (toca o `uai-cms`) + backend OOH-comercial.
> Registrar **ADR próprio no vault** (revisão do cms). Depende de: Épico 5 (consolidado/verificado).

## Contexto da decisão
O `uai-cms` é o **gerenciador de campanhas da EMPRESA** — campanha de OOH **também** é campanha da
empresa. Em vez de criar um CRM paralelo (ou bypassar o cms), **revisa-se o cms p/ ser multi-vertical**:
o genérico (cliente/campanha/cobrança) fica no cms; o **específico por tipo** é delegado ao vertical.
Assim o cms cresce sem inchar. Os specifics OOH vão num backend próprio `uai-ooh-commercial`.

## Tarefa 6.1 — Revisão do `uai-cms` para multi-vertical (PLATAFORMA · ADR próprio)
- Generalizar **campanha** com `campaign.type` (`marketing` | `ooh` | …): lifecycle comum
  (cliente, campanha, período, orçamento, status, cobrança) no cms; execução/dados específicos
  **delegados por tipo** (`uai-core` p/ marketing; `uai-ooh-commercial`/`uai-ooh-intel` p/ OOH).
- O cms continua o **edge/BFF** do frontend e delega auth ao `uai-auth` (introspection); chamadas aos
  backends por `/internal/**` + `X-UAI-Internal-Key`.
- **ADR no vault** documentando a revisão (campaign types, fronteira genérico↔específico, delegação).
- **Pronto:** cms cria/gerencia uma campanha `type=ooh` reusando o lifecycle comum, delegando o OOH.

## Tarefa 6.2 — `uai-ooh-commercial` (Java/Spring, tenant)
- Specifics comerciais OOH (tenant_id + RLS):
  - **carros vendidos por campanha** (campaign_vehicle: vehicle_code, line_esperada, período, status).
  - **ativação/"start" + registro de carros ativos** (PENDING→ACTIVE→ENDED) → publica
    `ooh.vehicle.activated/deactivated` (consome o registro mínimo do Épico 5.0, agora completo).
  - **relatório de entrega**: cruza `core.trip_executed`/alcance verificado (do Épico 5) com a campanha
    → veiculação (viagens/km/horas), alcance verificado vs estimado, divergências de linha.
    **Por composição via `intel`** (agregação por `campaign_id`/conjunto de `vehicle_id`), **sem cross-DB
    join** — correlação por `vehicle_code` (ADR-052).
- Orquestrado pelo cms (não fala direto com o frontend).
- **Pronto:** ativar carros de uma campanha, rastrear, e gerar relatório de entrega por campanha/tenant.

## Tarefa 6.3 — Contrato de API p/ o frontend separado
- Definir o contrato que o **frontend (repo separado)** consome **via cms**:
  - Planejamento (A1–A5): catálogo, ficha de linha (score, perfil, alcance, POIs), ranking por pesos
    e público-alvo — proxied do `uai-ooh-intel`.
  - Campanha: CRUD de campanha OOH, carros vendidos, **dar start**, status.
  - Tempo real: mapa ao vivo dos carros ativos da campanha + alcance verificado — proxied do `intel`.
  - Relatório de entrega.
- OpenAPI no cms (+ nos backends). Tenant via auth → cms → backends.
- **Pronto:** contrato versionado; o frontend pode ser construído contra ele (mock/real).

## Tarefa 6.4 — Assinatura / recorrência
- Modelo de assinatura ancorado na **metrificação em tempo real** (o diferencial que justifica
  recorrência): planos/tiers (só planejamento vs planejamento+tempo real), faturamento por
  campanha/carro ativo. Vive no cms (cobrança genérica) + dados de uso do OOH.
- **Pronto:** uma campanha gera cobrança recorrente derivada dos carros ativos + acesso por tier.

## Critério de pronto do Épico 6 (= F3)
- cms gerencia campanhas OOH como tipo (multi-vertical, ADR registrado); `uai-ooh-commercial` faz
  os specifics (carros/ativação/entrega) com tenant+RLS; frontend separado consome via cms (contrato
  OpenAPI); assinatura recorrente ancorada na metrificação. Isolamento por tenant testado.
