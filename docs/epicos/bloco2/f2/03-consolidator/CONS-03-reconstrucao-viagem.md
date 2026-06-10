# CONS-03 — reconstrução de viagem + linha-do-RT

> F2 (Bloco 2) · lane **consolidator** · **Repo-alvo:** `uai-ooh-trip-consolidator` · **Stack:** Java
> **Depende de:** CONS-02 + `core.trip_pattern`/`core.line` (✅ F1). · *(Replanejado 2026-06-10)*

## Objetivo
Detectar **início/fim de viagem** e **atribuir a linha** — sempre do **RT, nunca do MCO**.

## Reconstrução
- **Instância de viagem** = sequência **contígua** de `(vehicle_code, trip_id)` no mesmo `service_date`.
- **Condições de fechamento** (qualquer uma fecha):
  1. **mudança de `trip_id`**;
  2. **reset de `current_stop_sequence`** (volta a 0 / sequência menor);
  3. **timeout** sem posição (> **10 min** — varredura periódica);
  4. **último ponto do padrão** (`current_stop_sequence` == último do `trip_pattern`);
  5. **virada de `service_date`**.
- Viagem **parcial** (carro que aparece no meio) **fecha com `completude < 1`** — não descarta.

## Linha-do-RT (nunca MCO)
- `route_id` do feed == `core.line.short_name` (validado no E0: **96,2–96,7%** casam; o `trip_id` do RT não existe no GTFS estático — junção é sempre por rota).
- **Suplementares (S\*)** → `padrao_desconhecido`: `line_id` NULL no fato; fallback geo (shape mais próximo por route+direction) é **opcional** e nunca inventa linha.
- **Divergência de campanha: NÃO existe aqui (F2-#5).** O fato é puro; quem compara linha rodada × linha esperada é o módulo OOH do CMS (Bloco 3), via evento.

## Decisões
- **Timeout de fechamento = 10 min** (ajustável com dado real). As 5 condições acima.
- **Veículo fora do `core.vehicle` não é erro** (frota suplementar/renovação — E0 mediu 14,3%): o fato landa com o `vehicle_code` do feed (F2-#8).

## Critério de pronto (verificável)
- Viagens fecham nas condições certas (4107: nº/dia ~ frequência); linha atribuída do RT; suplementares como `padrao_desconhecido`; parciais com completude < 1. Testes de unidade por `TripCloseReason`.

## Produz
- docs/epicos/runs/CONS-03-reconstrucao-viagem.md
