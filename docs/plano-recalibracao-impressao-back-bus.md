# Plano de recalibração — impressão/alcance do back bus (RASCUNHO)

> **Status:** rascunho, 2026-06-11. A **estrutura** está pronta; a **fonte de footfall por área** fica em
> aberto até fechar a rodada 2 do scout (Task `w0u8mngir`) — é só encaixar o vencedor no slot marcado.
> Base: [`proxies-e-premissas.md`](proxies-e-premissas.md), [`epico-3-metricas-score.md`](epicos/bloco1/_arquivo/epico-3-metricas-score.md),
> `core.model_params`, [`F2-preview-alcance-medido-fixture.md`](epicos/runs/F2-preview-alcance-medido-fixture.md).

## 1. Como é hoje (F1, Tarefa 3.5)

```
E_pop(d) = pop_corredor_pond × f_exposicao(0.15) × freq_norm(d)   # externo: gente no corredor
E_pax(d) = pax_por_dia(d)     × p_vista_traseira(0.6)             # interno: passageiro vê a traseira
Impressoes(d) = E_pop(d) + E_pax(d)                              # E_traf (trânsito veicular) = 0 → v2
× f_luz(0.76)                                                    # só a fração diurna conta
```

| Termo | O que é | Fonte hoje | Honestidade |
|---|---|---|---|
| `pop_corredor_pond` | pop **residente** do corredor (buffer 300 m, área-ponderada) | Censo 2022 | **medido, mas é residente** |
| `f_exposicao = 0.15` | fração da pop exposta/dia | — | **chute de mercado** |
| `p_vista_traseira = 0.6` | fração dos passageiros que vê a traseira | — | **chute de mercado** |
| `f_luz = 0.76` | fração diurna (share de embarque 6–17 h) | `line_boarding_profile` | **observado** (único fator não-chute) |
| `E_traf` | trânsito veicular que vê o ônibus | — (=0) | **v2, não computado** |

Saída: `Impressoes` alimenta `s_alcance` (35 % do `score_total`). **Ranking é vendável; o absoluto é
ilustrativo (±35 %)** porque pende de `f_exposicao`/`p_vista_traseira` não calibrados.

## 2. Os dois furos (o que a pesquisa veio resolver)

1. **Residente ≠ footfall.** `E_pop` usa quem **mora** no corredor. No centro, residente ≈ 0 mas a
   circulação é máxima (sonda Banca Glória: 222 moradores vs 1.801 POIs em 150 m). O modelo **subestima
   exatamente os corredores centrais** — onde o ônibus mais roda e a impressão é maior.
2. **Coeficientes globais.** `0.15` e `0.6` são iguais pra toda linha, hora e lado de via. Na realidade a
   chance de ver depende de **lado da via, velocidade/dwell (ônibus parado no trânsito = mais visto),
   tamanho do painel, dia/noite**. Um número só não captura isso.

## 3. Estrutura-alvo: hierarquia MRC (o "funil")

Em vez de uma soma de termos heterogêneos, **camadas multiplicativas auditáveis** (padrão MRC/Geopath):

```
Location Traffic   → quanta gente CIRCULA pela área   (pedestres + veículos + passageiros)
   ↓ × VAI (visibility adjustment, por atributo)
OTS / Viewable     → quantos estão POSICIONADOS pra ver o painel
   ↓ × LTS (eye-tracking, futuro)
Impressões         → contatos visuais (o que se vende)
```

Cada degrau multiplica o anterior por um fator **com fonte declarada**. Hoje o produto pula do topo pro
fundo com dois chutes; o plano é **explicitar o funil e medir cada degrau**.

## 4. Migração (antes → depois)

| Termo atual | Vira, na estrutura MRC | Fonte nova | Rodada |
|---|---|---|---|
| `pop_corredor_pond` (residente) | **Location Traffic — pedestres/diurno**: pop por zona × hora | **derivado**: Censo (residente, no banco) × O-D telefonia VIVO RMBH (aberta) + O-D bilhetagem (`od_trip`) | ✅ footfall |
| `E_traf = 0` | **Location Traffic — veículos**: fluxo veicular do corredor × hora | Waze via OpenWeb Ninja (~US$25/mês) | 1 ✅ |
| `pax_por_dia × 0.6` | **Location Traffic — internos**: já ok; `0.6` vira fator com selo | `line_demand` + estudo | 1 (metodologia) |
| `f_exposicao 0.15` / `p_vista_traseira 0.6` | **VAI**: tabela de fatores por atributo (lado/dwell/tamanho/luz) | Geopath VAI + Route (forma funcional) | 1 ✅ |
| `f_luz 0.76` | já é um fator VAI (iluminação) — **manter e generalizar** | `line_boarding_profile` | — |

## 5. Mudanças de schema (mínimas — o slot já existe)

- `core.exposure_cell.footfall` — **trocar o proxy constante (`82`) pela pop diurna derivada** (Censo ×
  matrizes O-D telefonia VIVO RMBH + bilhetagem). Coluna já existe; é repovoar via novo job do pipeline.
  Upgrade futuro: Unacast (H3×hora nativo, **validar cobertura BH**) ou Claro Geodata (caro, projeto B2B).
  `unicos_hora` (passageiro O-D) fica **separado**.
- `core.model_params` — `f_exposicao`/`p_vista_traseira` deixam de ser escalares globais e viram **tabela
  VAI** (fator por atributo: `lado_via`, `faixa_velocidade/dwell`, `tamanho_painel`, `faixa_horaria`).
- `core.line_exposure_hourly` (`exposicao_rel`, `pax_hora`) e `core.line_reach` (`reach_od`) — já existem;
  passam a ler o Location Traffic medido em vez do proxy.

## 6. Fases (ordem de menor→maior esforço; cada uma destrava sozinha)

- **F-a — reestruturar a fórmula em camadas** (sem fonte nova): separar Location Traffic / OTS na fórmula e
  no `model_params`. Ganho imediato de honestidade; o ranking não muda, mas a fórmula vira auditável.
- **F-b — plugar footfall medido** (rodada 2) no Location Traffic-pedestres → mata o `82`-constante e o viés
  residente. **Maior ganho de qualidade.**
- **F-c — VAI por atributo** (Geopath/Route) substituindo `0.15`/`0.6` → estreita o ±35 %.
- **F-d — E_traf** (Waze) → fecha o Location Traffic-veículos.

## 6.1 — Método de normalização e cruzamento (F-b detalhado, validado 2026-06-12)

A telefonia (viagens/zona/faixa) e o `footfall` (índice/H3) estão em unidades incompatíveis — **não se somam,
decompõem-se em NÍVEL × FORMA**:

1. **Normalizar por duração da faixa:** `viagens ÷ horas` → viagens/hora (a madrugada tem 7h; sem isso ela
   parece grande). Revela o pico/vale real de **11,2×** em BH (madrugada ~12 vs pico tarde ~129, base 82).
2. **Perfil adimensional:** cada faixa ÷ média → índice (média=100) → multiplicador sem unidade.
3. **Aplicar (unidade = H3, não setor):** `footfall(hex, hora) = pop(hex) × perfil_norm(zona_do_hex, hora)`.
   O `pop` **já está em `core.exposure_cell` / `core.h3_cell` por hexágono** — o `82` constante vira esse nível
   redistribuído pela curva. A F-b é literalmente um `UPDATE core.exposure_cell SET footfall = ...`.

**Cruzamento espacial (165 zonas O-D × 2.615 hexágonos H3 res ~9):** cada hex herda o perfil da zona OD que
contém seu centroide (`ST_Contains`, feito 1×). Validado em 4 zonas-âncora — pop por hex bate com pop por setor
(ex. Savassi 15,3k vs 15,6k) — e **o perfil varia muito por zona**: Praça Sete = comuter (6h=222, 19h=34);
Pampulha = pico tarde (15h=164). **Uma curva única de BH seria errada.** As **5 faixas** da telefonia expandem
pras **24 faixas** do `exposure_cell` (cada hora herda o índice da sua faixa).

**Por que H3 e não setor:** `exposure_cell.footfall` já é por H3×hora (gravar = UPDATE), o `pop` já está no hex,
o `line_reach` já consome H3, e o hex é **uniforme** (~0,113 km²). O setor (5.166, tamanho variável) foi só
ilustração do nível fino.

**Ressalvas:** zona OD ~20× mais grossa que o hex (forma temporal por zona, nível por hex); validação usou bbox —
produção usa a geometria real da zona importada via `shp2pgsql` no `uai-ooh-pipeline`. Base = **nov/2019**
(pré-pandemia; mai/2021 deprimido). Telefonia é VIAGENS (chegadas) → presença é derivação. Dados em `/tmp/odrmbh`.

## 7. O que NÃO muda (honestidade preservada)

- **Ranking continua sendo o produto vendável**; o absoluto só vira "preciso" após calibração local (o que
  o MRC/VAC exige formalmente — estudo de campo periódico documentado).
- Todo número segue **carimbado** com selo de confiança e `metodo_versao`; medido vs estimado separados.
- `exposure_cell.unicos_hora` (reach único de passageiro, O-D) **não se mistura** com footfall externo.
