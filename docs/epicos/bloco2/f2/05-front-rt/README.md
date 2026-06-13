# 05 · front-rt — 🟪 FRONT

> Repo: **`uai-portal`** *(estende o módulo OOH do F1)* · **mapa universal** + mapa ao vivo (carros + impressões subindo) + painel verificado vs estimado.
> **Replanejado 2026-06-10** (face-centric + F2-#6 acumulador ao vivo).
> **Depende de:** front F1 (EP4 — módulo) + RT-01/02/03 (intel-rt).

| Task | O que | Nota |
|---|---|---|
| [WEB-00](WEB-00-mapa-universal.md) | **componente de mapa universal** (camadas plugáveis: superfície H3 × hora, shapes/corredores, veículos, acumulado) — base de WEB-01/02 e do F1/EP4 | referência de UX/contrato: `uai-ooh-pipeline/docs/design/mapa-alcance-simulacao.html` |
| [WEB-01](WEB-01-mapa-ao-vivo.md) | **mapa ao vivo**: carros se movendo **+ contador de impressões subindo** (acumulado parcial, selo estimativa), escopável por linha | camadas do WEB-00 · hook `usePositions(scope)` (polling; swappable) |
| [WEB-02](WEB-02-verificado-vs-estimado.md) | **painel verificado vs estimado**: faixa estreitada, selo por métrica (ADR-058) — fecha o loop do F2 | usa RT-02 |

**Pronto:** o planejador vê os carros de uma linha ao vivo com as impressões acumulando + o quanto o
medido bate com o estimado, com selo honesto por métrica.
