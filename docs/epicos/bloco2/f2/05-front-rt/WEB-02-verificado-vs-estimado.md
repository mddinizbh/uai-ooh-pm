# WEB-02 — painel verificado vs estimado (calibração)

> F2 (Bloco 2) · lane **front-rt** · **Repo-alvo:** `uai-portal` *(estende o módulo OOH do F1)* · **Stack:** React · recharts
> **Depende de:** RT-02 (verificado por linha) + a ficha do F1 (EP4-03).

## Objetivo
**Fechar o loop visual** do F2: mostrar, por linha, o **verificado** (viagens/km/velocidade reais) **ao lado do estimado** do F1 — evidenciando a calibração e o estreitamento da ±35%.

## Como executar
- Painel "**verificado vs estimado**" (na ficha do F1 ou tela própria): viagens reais vs frequência GTFS · velocidade real (`v_real`) vs assumida · faixa ±35% estimada vs o medido.
- Reusa **recharts** (do F1). Consome **RT-02**.
- **Honestidade:** o verificado é marcado como **sólido (medido)**; o estimado mantém o selo de estimativa (EP4-08). É o ápice do diferencial "honestidade de medição" — agora com o **medido** ao lado.

## Decisão
- O painel **mostra** a comparação; **não** recalcula o score (isso é o normalizer, ver RT-02).

## Critério de pronto
- Por linha, o planejador vê **verificado ao lado do estimado** + o quanto a ±35% se estreita; verificado marcado como **medido**.

## Produz
- docs/epicos/runs/WEB-02-verificado-vs-estimado.md
