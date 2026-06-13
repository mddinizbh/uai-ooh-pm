# F2 — preview manual do alcance MEDIDO dos carros-fixture (run exploratório, 2026-06-10)

> Pergunta do dono: "o alcance desses carros com base no dia de hoje, só com a base pronta".
> Feito **à mão em SQL** (MCP `postgres-ooh`, read-only) — é o RECAL-01 manual: posições reais do
> `raw` (poller, dia 2026-06-10, faixas 10h–17h local) → join espacial com `core.h3_cell` →
> audiência OD (`core.od_trip`, tipo_dia=8, dedup por `id_usuario`, origem OU destino no hex×faixa).
> O `20736` (fixture) não apareceu no feed hoje — fora desta análise.

## Resultado

| Carro | Linha(s) real(is) hoje | Hexes medidos (só ping) | Hexes do corredor real 10–17h | Cobertura ping | **Reach MEDIDO hoje** | Reach ESTIMADO (mesmas faixas) | Inflação |
|---|---|---|---|---|---|---|---|
| 11198 | 9501 (9 trips) | 55 | 58 | **95%** | **21.327** | 175.114 | **8,2×** |
| 30835 | 2101 + 2150 | 39 | 51 | 76% | **9.409** | 110.598 | **11,8×** |
| 40705 | 9801 | 53 | 55 | **96%** | **16.225** | 152.662 | **9,4×** |
| 40806 | 3054 | 53 | 51 | 104%* | **21.069** | 124.734 | **5,9×** |

\* mediu hexes fora do corredor (desvio/deslocamento de garagem) — exatamente o caso do limiar de
interpolação do CONS-04.

## O achado (calibração que o F2 existe pra fazer)

**A trajetória estimada espalha cada carro por 5–14 linhas; na realidade ele roda 1–2.**
O schedule GTFS não amarra viagem a carro físico, então a estimativa (T4, `metodo='estimada'`)
distribuiu cada veículo por muitas linhas — o `face_reach` estimado do 11198 soma corredores de
**14 linhas** (419 hexes) quando o carro de verdade rodou **só a 9501** (58 hexes). Resultado:
**o reach estimado por face está inflado ~6–12×** na comparação like-for-like (mesmas faixas).

Segunda notícia, boa: **a cobertura por ping do corredor real é 76–104% mesmo SEM interpolação** —
o dado do poller é suficiente pra trajetória medida ser sólida; a interpolação do CONS-04 fecha o
resto (o 30835, 76%, tinha menos posições e trocou de linha).

## Caveats (honestidade)

- Dia parcial (poller subiu ~10h local; janela 10h–17h) e 1 dia só — não é o grão `tipo_dia` cheio.
- Sem interpolação pelo shape (só hexes com ping) e sem dwell — régua simplificada.
- Reach de ambos os lados **sem coeficiente de visada** (comparáveis entre si; selo BAIXO/estimativa
  dos dois jeitos — ADR-058).

## Implicações registradas

1. Reforça a decisão F2-#3..#7: o recompute com `medido.viagem_hex` corrige a inflação na fonte
   (cobertura real, não schedule).
2. O **estimado por FACE deve ser lido com desconfiança** até o RECAL rodar — o estimado por LINHA
   (corredor) não sofre desse problema (o corredor existe independente de qual carro o roda).
3. ~~Candidato a melhoria pós-RECAL: re-derivar o `face_reach` estimado limitando o carro às linhas
   em que ele é REALMENTE visto no RT (prior do medido sobre o schedule).~~ **Superseded no mesmo
   dia** pela decisão de produto dos 3 tempos (notas B3 §4b): o `face_reach` estimado **sai do
   caminho de produto** (cesta estima por LINHA via `line_reach`; relatório usa o medido da face) —
   corrigir o T4 ficou desnecessário; artefato marcado interno/deprecado.
