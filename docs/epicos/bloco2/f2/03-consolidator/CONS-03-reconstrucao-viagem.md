# CONS-03 — reconstrução de viagem + linha-do-RT

> F2 (Bloco 2) · lane **consolidator** · **Repo-alvo:** `uai-ooh-trip-consolidator` · **Stack:** Java
> **Depende de:** CONS-02 + `core.trip_pattern`/`core.line` (✅ F1).

## Objetivo
Detectar **início/fim de viagem** e **atribuir a linha** — sempre do **RT, nunca do MCO**.

## Reconstrução
- **Instância de viagem** = sequência **contígua** de `(vehicle_id, trip_id)` no mesmo `service_date`.
- **Condições de fechamento** (qualquer uma fecha a viagem em andamento):
  1. **mudança de `trip_id`**;
  2. **reset de `current_stop_sequence`** (volta a 0 / sequência menor);
  3. **timeout** sem posição (sem update por **> 10 min**);
  4. **último ponto do padrão** (`current_stop_sequence` == último do `trip_pattern`);
  5. **virada de `service_date`**.
- Viagem **parcial** (carro que aparece no meio do trajeto) **fecha com `completude < 100%`** — não descarta.

## Linha-do-RT (nunca MCO)
- `trip_id` → `route_id` (do feed) → `core.line` / `core.trip_pattern`. **96,7%** casam direto.
- **3,3% suplementares** (S*) → `padrao_desconhecido`: casar por **route + direction + geo** (shape mais próximo).
- **Divergência de campanha:** sem comercial no F2 → **fica latente** (não há "linha esperada"); o campo existe, reativa no Bloco 3.

## Decisão
- **Timeout de fechamento = 10 min** sem update (ajustável). As 5 condições acima.

## Critério de pronto (verificável)
- Viagens fecham nas condições certas (validar na 4107: nº/dia ~ frequência); linha atribuída **do RT**; 3,3% supl tratados como `padrao_desconhecido`; parciais fechadas com completude < 100%.

## Produz
- docs/epicos/runs/CONS-03-reconstrucao-viagem.md
