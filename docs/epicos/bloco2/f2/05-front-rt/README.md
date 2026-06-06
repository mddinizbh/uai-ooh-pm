# 05 · front-rt — 🟪 FRONT

> Repo: **`uai-portal`** *(estende o módulo OOH do F1)* · mapa ao vivo + painel verificado vs estimado.
> **Depende de:** front F1 (EP4 — módulo + componente de mapa EP4-04) + RT-01/02/03 (intel-rt).

| Task | O que | Nota |
|---|---|---|
| [WEB-01](WEB-01-mapa-ao-vivo.md) | **mapa ao vivo**: estende o componente de mapa do F1 (EP4-04) com **carros se movendo**, **escopável por linha** | via hook `usePositions(scope)` sobre a `PositionFeed` (polling no F2; swappable) · reusa MapLibre do F1 |
| [WEB-02](WEB-02-verificado-vs-estimado.md) | **painel verificado vs estimado** (a calibração visual): faixa estreitada, velocidade real, viagens reais — fecha o loop do F2 | usa RT-02 |

**Pronto:** o planejador vê os carros de uma linha ao vivo + o quanto o verificado bate com o estimado do F1.
