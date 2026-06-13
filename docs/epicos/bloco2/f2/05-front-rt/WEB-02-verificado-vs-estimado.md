# WEB-02 — painel verificado vs estimado (selo por métrica)

> F2 (Bloco 2) · lane **front-rt** · **Repo-alvo:** `uai-portal` *(módulo OOH)* · **Stack:** React · recharts
> **Depende de:** RT-02 (verificado por linha) + ficha do F1 (EP4-03). · *(Replanejado 2026-06-10 — face-centric/ADR-058)*

## Objetivo
**Fechar o loop visual** do F2: por linha, o **medido** ao lado do **estimado** — trajetória,
frequência e velocidade reais estreitando a faixa de incerteza do modelo face-centric.

## Como executar
- Painel "**verificado vs estimado**" (na ficha do F1 ou tela própria): viagens reais vs frequência
  GTFS · velocidade real (`v_real`) vs assumida · **reach/impressões fonte medida vs estimada**
  (recompute da lane 06) · faixa de incerteza antes/depois.
- **Selo por métrica (ADR-058, vem tipado do RT-02):** viagens/km/velocidade = **MEDIDO** (sólido);
  reach/impressões = **ESTIMATIVA** mesmo recomputados (coeficientes de visada cegos — só Fase C
  calibra). Não inflar o rótulo.
- Reusa **recharts** (F1). Consome **RT-02**.

## Decisão
- O painel **mostra** a comparação; **não** recalcula nada (recompute = lane 06; exposição = RT-02).

## Critério de pronto
- Por linha, o planejador vê medido ao lado do estimado + faixa estreitando; selos por métrica
  corretos (medido sólido, estimativa rotulada).

## Produz
- docs/epicos/runs/WEB-02-verificado-vs-estimado.md
