# Épico 0 — Lacunas de dados (pré-requisito da normalização)

> **Único épico executável agora.** Os Épicos 1–4 (normalização `core`) só serão detalhados
> depois que este fechar — o que renda e radar revelarem pode mudar o desenho.
> Ver o plano completo em `docs/plano-normalizacao-core.md`.

**Objetivo:** trazer para o schema `raw` as duas fontes que faltam e que **bloqueiam** o produto:
1. **Renda do censo por setor** — sem ela não há perfil socioeconômico / classe social (features A3/A5).
2. **Contagem volumétrica de radar** — sem ela não há componente de trânsito do score (20% do A4);
   o trânsito **tem** de ser volume real (sem fallback de velocidade 2019).

**Convenção (igual à Fase 1B):** tudo `TEXT`, header original em COMMENT, metadados
`_arquivo_origem/_carregado_em/_linha_num`, sem PK/FK/índice. Reusar `.data/scripts/` (venv
`.data/venv`, `load_csv_generic.py` para CSV; novo decoder p/ o webservice se não for CSV puro).
Banco: `ooh`, schema `raw`.

---

## Tarefa 0.1 — Renda do censo (rendimento do responsável) por setor → `raw.censo__renda_responsavel_bh`

**Fonte / endpoint (confirmado):**
- FTP IBGE, diretório `https://ftp.ibge.gov.br/Censos/Censo_Demografico_2022/Agregados_por_Setores_Censitarios_Rendimento_do_Responsavel/`
- Dados: `Agregados_por_setores_renda_responsavel_BR_20260508_csv.zip` (nacional)
- Dicionário: `dicionario_de_dados_renda_responsavel_20260508.xlsx` (mesmo diretório)
- É **divulgação separada** (Nota metodológica 02/2025, 30/04/2025) — **não** está no zip dos
  agregados básicos.
- Baixar com User-Agent de browser (FTP IBGE via HTTPS aceita).

**Passos:**
1. Baixar o zip nacional + o dicionário xlsx para `.data/downloads/censo/`.
2. Abrir o dicionário local (openpyxl no venv) e mapear as variáveis de renda (ex.: faixas de
   salário mínimo do responsável, valor médio/mediano). **Registrar o de-para variável→significado**
   neste épico antes de carregar.
3. Inspecionar o CSV: encoding (provável latin-1, `;`), header, 3–5 linhas, e a coluna de
   identificação do setor (`CD_SETOR`/`cd_setor`).
4. Filtrar **somente BH**: linhas com `cd_setor LIKE '3106200%'` (ou `CD_MUN='3106200'` se houver
   a coluna), carregando em streaming (não jogar o nacional inteiro no banco).
5. Carregar via `load_csv_generic.py` (ou filtro Python + COPY) → `raw.censo__renda_responsavel_bh`,
   tudo TEXT, com os metadados padrão.

**Chave a validar:** `cd_setor` da renda **casa com a malha** `raw.censo__setores_bh.cd_setor`
(e com `raw.censo__agregados_basico_bh.cd_setor`).
```sql
-- esperado: ~5.166 setores cobertos, 0 (ou pouquíssimos) órfãos
SELECT
  (SELECT count(*) FROM raw.censo__renda_responsavel_bh)                       AS renda_linhas,
  (SELECT count(DISTINCT cd_setor) FROM raw.censo__renda_responsavel_bh)       AS renda_setores,
  (SELECT count(*) FROM raw.censo__setores_bh m
     LEFT JOIN raw.censo__renda_responsavel_bh r ON r.cd_setor = m.cd_setor
   WHERE r.cd_setor IS NULL)                                                   AS malha_sem_renda;
```

**Critério de pronto:**
- Tabela `raw.censo__renda_responsavel_bh` criada e populada só com setores de BH.
- De-para das variáveis de renda documentado (qual coluna é renda média/mediana/faixas).
- `malha_sem_renda` ≈ 0 (todo setor da malha tem renda; reportar exceções).
- Relatado: nº de setores, nº de colunas, variável de renda escolhida como âncora, anomalias.

---

## Tarefa 0.2 — Contagem volumétrica de radar → `raw.pbh__contagem_volumetrica`

**Fonte / endpoint (confirmado):**
- Webservice BHTrans: `http://servicosbhtrans.pbh.gov.br/Bhtrans/webservice/`
- Arquivos **mensais grandes** (~150MB a ~1,5GB). **Pegar só UM mês representativo recente.**
  **Carregar em chunks — nunca o arquivo inteiro na RAM.**
- Dicionário leve no mesmo diretório: `Dicionário de dados - V1 - Contagem volumétrica de radares.xlsx`.
- **Caminho alternativo (subconjunto, se o webservice for inviável):** dataset CKAN
  `contagens-volumetricas-de-radares` (`package_show?id=contagens-volumetricas-de-radares`).

**Passos:**
1. Listar o diretório do webservice (curl com UA de browser) e identificar o mês mais recente
   disponível + o dicionário. Baixar o dicionário e **um** mês.
2. Pelo dicionário, mapear os campos: identificador do radar (casar com
   `fiscalizacao_eletronica`), faixa/sentido, **volume por tipo de veículo** (carro/moto/ônibus),
   faixa horária, data.
3. Inspecionar formato (CSV `;` latin-1 ou JSON) e tamanho real; definir leitura **streaming**
   (csv.reader linha-a-linha ou ijson p/ JSON), COPY incremental.
4. Carregar em chunks → `raw.pbh__contagem_volumetrica`, tudo TEXT, metadados padrão. Não
   deduplicar, não tipar (fidelidade Fase 1B).

**Chave a validar:** o identificador do radar na contagem **casa com**
`raw.pbh__fiscalizacao_eletronica` (provável `num_serie_atual`/`cod_secundaria`/`id_fiscalizacao`).
```sql
-- substituir <col_id_contagem> e <col_id_fisc> conforme dicionário
SELECT
  (SELECT count(DISTINCT <col_id_contagem>) FROM raw.pbh__contagem_volumetrica)              AS radares_contagem,
  (SELECT count(*) FROM raw.pbh__fiscalizacao_eletronica)                                     AS radares_fisc,
  (SELECT count(DISTINCT c.<col_id_contagem>) FROM raw.pbh__contagem_volumetrica c
     JOIN raw.pbh__fiscalizacao_eletronica f ON f.<col_id_fisc> = c.<col_id_contagem>)        AS casaram;
```

**Critério de pronto:**
- Tabela `raw.pbh__contagem_volumetrica` criada, populada com **um mês representativo** carregado
  em chunks (sem estourar memória).
- Campo de volume identificado (por tipo de veículo e faixa horária) e documentado.
- Chave do radar **casa** com `fiscalizacao_eletronica` (reportar % de match e radares sem
  contagem — esperado parcial: nem todo radar tem contagem por vigência/furto).
- Relatado: nº de linhas, mês escolhido, colunas de volume, % de match com radares, anomalias.

---

---

## Resultado da execução (2026-05-31)

### Tarefa 0.1 — Renda do censo ✅ CONCLUÍDA
Tabela `raw.censo__renda_responsavel_bh` criada (5.137 setores de BH, 7 colunas).
De-para das variáveis (do dicionário oficial):
| Var | Significado |
|---|---|
| V06001 | Pessoas responsáveis em DPP ocupados |
| V06002 | Moradores em DPP ocupados |
| V06003 | Variância do nº de moradores |
| **V06004** | **Rendimento nominal MÉDIO mensal do responsável (R$)** ← âncora de renda |
| V06005 | Variância do rendimento |
| **V06006** | **Rendimento nominal MEDIANO mensal (R$)** |

Formato: `;`, decimal com **ponto**, sigilo IBGE = `X` (tratar como NULL). Validação:
- `renda_sem_malha = 0` (toda renda casa com `censo__setores_bh.cd_setor`).
- `malha_sem_renda = 29` (setores sem responsável com rendimento — esperado).
- `renda_sigilo_X = 24`.
- Renda média do responsável em BH = **R$ 4.682** (min R$ 886, máx R$ 170.418) — plausível.

### Tarefa 0.2 — Contagem volumétrica → RECLASSIFICADA PARA v2 (fonte indisponível)
A fonte publicada **não é mais acessível**:
- CKAN `contagens-volumetricas-de-radares` tem 1 recurso apontando para `http://servicosbhtrans.pbh.gov.br/Bhtrans/webservice/` (ponteiro genérico, formato "JSON").
- Esse host **redireciona** para `https://portalsumob.pbh.gov.br` — que é o **portal de e-serviços** da SUMOB (consulta de linha, CEP, permissionários…), uma SPA Angular + backend Laravel. **Não há feature/endpoint de contagem volumétrica** ali (main.js só expõe rotas auth/usuário; nenhum termo contagem/volumétrica/radar nos bundles; backend responde 404 nos endpoints de dados; reCAPTCHA presente).
- Busca ampla no CKAN por "volumétrica/contagem" retorna só esse dataset com ponteiro morto. Não há arquivo mensal baixável na origem anunciada.

**Conclusão honesta:** o link oficial está defasado; o dado de contagem volumétrica não foi obtido. Não foi usado proxy de velocidade (proibido pela regra do plano).

**Fontes adicionais do dono também checadas (dead-end):** `dados.pbh.gov.br/organization/bhtrans` tem 24 datasets — o único de contagem é o mesmo `contagens-volumetricas-de-radares` (1 recurso JSON → webservice morto). A página `prefeitura.pbh.gov.br/bhtrans/informacoes/dados/dados-abertos` só remete de volta à org do CKAN, sem links de arquivo. Confirmado: o dado não está publicado de forma baixável atualmente.

**Recomendação:** não bloquear toda a normalização nisso — só o **sub-score de trânsito (20%)** depende do volume. Seguir os Épicos 1–4 com `volume_transito_dia` marcado **pendente** (radares já têm localização via `fiscalizacao_eletronica`), score computado com o componente de trânsito sinalizado como ausente, e abrir uma pendência para obter o dado junto à BHTrans (pedido formal/SIC) — quando chegar, recalcula. Decisão do dono.

**✅ DECISÃO TOMADA (dono):** componente de trânsito de F1 = **exposição arterial** (qualidade de
via do trajeto), com base no `trecho_logradouro` (tipo+largura → arterial/coletora/local — proxy
de hierarquia, dado **atual e completo**) + refinamento pelas 35 Vias de Referência/velocidade do
`velocidade_corredores`. **NÃO é volume nem congestionamento — é ONDE a linha roda.** Mantém peso
respeitável no score (não rebaixado). A **contagem volumétrica de radar fica reclassificada para
v2** (coletor próprio de congestionamento via API de tráfego — ver `docs/plano-normalizacao-core.md`,
seção "v2"); não bloqueia F1. Logo, **a Tarefa 0.2 não é mais pré-requisito** — vira backlog v2.

**Opções para destravar (decisão do dono):**
1. Localizar a fonte atual no navegador (portal SUMOB/BHTrans pode exigir login/reCAPTCHA) e me passar a **URL real do arquivo** ou baixar o mês localmente → eu carrego.
2. Confirmar se o dataset foi descontinuado/migrado (procurar nova publicação em dados.pbh.gov.br / nova org).
3. Relaxar temporariamente a regra: seguir a normalização com **localização** de radares (`fiscalizacao_eletronica`, 287 pts) e deixar `volume_transito_dia` como pendente/placeholder — assumindo explicitamente que o score sai sem o componente de trânsito real até a fonte aparecer.

## Definição de pronto do Épico 0 — ✅ FECHADO
- **0.1 renda:** `raw.censo__renda_responsavel_bh` carregada, de-para documentado, validação de
  chave OK (0 órfãos). ✔
- **0.2 radar:** fonte indisponível → **reclassificada para v2** (não bloqueia F1; trânsito de F1
  resolvido por exposição arterial com dado já no `raw`). ✔ (como decisão, não como carga)
- **Surpresa que muda o desenho dos Épicos 1–4:** o trânsito de F1 deixou de depender de volume de
  radar e passou a ser **exposição arterial** (`trecho_logradouro` + Vias de Referência); o volume
  real vira o **coletor próprio de congestionamento (v2)**.
- **Próximo passo:** detalhar os Épicos 1–4 (normalização `core`) — pode começar.
