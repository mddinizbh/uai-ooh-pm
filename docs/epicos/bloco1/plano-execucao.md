# uAI-OOH — Bloco 1 (F1 / dados): Plano de Execução
> Gerado pelo workflow adversarial `ooh-bloco1-arquitetura` em 2026-06-04 (72 agentes, 3 céticos por decisão). 6 decisões confirmadas, 16 desafiadas, 21 tarefas.
> Decisões do dono já tomadas: alvo=Bloco 1 dados/F1 · PostGIS=container dedicado · auth=só no Bloco 3.

## ✅ Decisões do DONO — RESOLVIDAS (2026-06-04)
1. **Serving:** o `intel` lê o schema `serving` (tabelas planas, sem geometry) **no próprio `ooh-postgis`** (usuário read-only). SEM cross-DB, SEM export pro `uai_buslines`. → **o épico-4 será reescrito** nessa linha (afeta T17/T19).
2. **Intel = repo NOVO, do zero** a partir do `uai-ooh-service-template`; o `uai-bus-lines-map` fica **CONGELADO** (não é fork nem rename). → **Implicação:** como o bus-lines não é mais o backend do intel, o schema `serving` precisa conter **o catálogo completo** (linhas, shapes GeoJSON, pontos) **além** das métricas — não só `line_metrics`. O cutover de `linhas.uaiagencia.com.br` (SPA → intel novo) é **pós-F1**. Afeta T17 (materializar catálogo+métricas) e T19 (intel reimplementa os endpoints de catálogo que o bus-lines tinha).
3. **trip_pattern** entra no **Bloco 1 (T3)** como unidade canônica; `line_shape`/`line_stop` derivam dele.
4. **Jobs Python** disparados por **GitHub Actions** (schedule/workflow_dispatch no `uai-infra`), não n8n. Afeta T0/T18.

> Demais decisões técnicas (adotadas conforme recomendação verificada do workflow): ingestor+normalizer no mesmo `uai-ooh-pipeline`; migrations via sqitch; seed via MinIO; backup core/serving separado do raw. Pré-requisito de infra: diagnosticar o `uai-postgres` unhealthy antes do T18.

## ✅ Decisões CONFIRMADAS (sobreviveram à verificação adversarial)

### [repos-topologia] Polyrepo desde já — 3 repos novos (uai-ooh-pipeline, uai-ooh-intel) + reuso do uai-bus-lines-map como base do intel — NÃO um monorepo uai-ooh  _(votos 3/3)_
Criar AGORA dois repos greenfield e reaproveitar um existente, alinhados 1:1 com a unidade de deploy da plataforma (1 container = 1 imagem GHCR = 1 entry no deploy_apps do uai-infra): (a) uai-ooh-pipeline (Python) — ÚNICO repo Python que abriga as DUAS funções static-ingestor (raw landing) E normalizer (raw→core PostGIS + materializa flat), como dois entrypoints/jobs no MESMO repo, não dois repos; (b) uai-ooh-intel (Java/Spring) — o serving F1, que NASCE do uai-bus-lines-map (fork/rename, não repo do zero); (c) o uai-bus-lines-map atual continua live em linhas.uaiagencia.com.br até o intel assumir. NÃO criar um monorepo uai-ooh com tudo dentro: a plataforma já é polyrepo (cms/core/tokenmetrics separados), o deploy é por imagem, e juntar Python+Java+Web num repo quebraria o padrão de CI por-imagem e o cache de build. Decisão central de fronteira: static-ingestor e normalizer COMEÇAM colados num repo (uai-ooh-pipeline) porque compartilham 100% do toolchain Python, o db.py, o DSN e o ciclo de vida do dado; só viram repos separados se/quando o ingestor precisar de cadência própria (Bloco 2 já força isso ao nascer o realtime-poller).

### [repos-topologia] Criar um molde de scaffolding versionado (uai-ooh-service-template para Java + estrutura padrão no uai-ooh-pipeline para Python) em vez de copiar-colar serviço a serviço  _(votos 2/3)_
Materializar o padrão hexagonal single-module Java num GitHub Template Repository (uai-ooh-service-template) derivado do esqueleto do intel JÁ LIMPO de domínio específico: estrutura de pacotes domain/{model,port/in,port/out} + application/usecase + adapter/{in/web,out/persistence}, pom.xml com parent Spring Boot 3.3.6 + os fixes (api.version=1.44, Testcontainers 1.21.3, JaCoCo 80%, Java 21), Dockerfile multi-stage (Maven temurin-21 build → jre-alpine runtime, non-root) idêntico ao atual, workflow .github/workflows/deploy.yml (test→build-push GHCR, deploy delegado ao uai-infra), filtro X-UAI-Internal-Key para /internal/**, OpenApiConfig, application.yml com env vars padrão (DB_HOST/PORT/NAME/USER/PASSWORD) e actuator/health. Para o Python, o molde é a CONVENÇÃO de layout dentro do uai-ooh-pipeline (db.py compartilhado, um módulo por stage, entrypoints `python -m ...`, Dockerfile próprio por job) documentada como README/CONTRIBUTING — não precisa de template repo separado porque por ora só há um repo Python. NÃO investir agora numa shared library Java publicada (BOM/parent POM corporativo no GHCR Maven) — é o passo seguinte, não o do Bloco 1.

### [postgis-prod] intel le tabelas FLAT no PROPRIO ooh-postgis (schema serving), NAO cross-DB e NAO exporta para uai_buslines  _(votos 3/3)_
O `uai-ooh-intel` (Java/Spring) conecta no banco `ooh` e le um schema dedicado `serving` (ou `flat`) de tabelas PLANAS materializadas pelo normalizer (line_metrics, line_profile_demografico, line_stop, line_shape com geom_geojson jsonb, line catalogo) — SEM nenhuma chamada `ST_*` em runtime, exatamente como o ADR-003 manda. Ou seja: o intel le do MESMO Postgres onde o PostGIS vive, mas de tabelas que NAO usam tipos geometry no caminho de leitura (apenas numeric/text/jsonb-GeoJSON). NAO fazer cross-DB (sem dblink/postgres_fdw) e NAO exportar para o `uai_buslines` do alpine. O `uai_buslines` atual e do app legado `uai-bus-lines-map` e fica como esta (327 linhas live); o intel e o sucessor que serve OOH a partir do ooh-postgis. Separacao logica: schema `raw` (landing) + schema `core` (PostGIS, geometry, dono=normalizer) + schema `serving` (flat, sem geometry, lido pelo intel) — tudo no banco ooh. O normalizer escreve em core e materializa serving; o intel so tem GRANT SELECT em serving (usuario `ooh_intel_ro` read-only, sem acesso a core/raw).

### [postgis-prod] Seed inicial via MinIO como staging de fontes + dump do dev so como fallback; backup do core/serving independente do raw  _(votos 2/3)_
Operacionalizar o seed em 2 trilhos: (TRILHO PRIMARIO) static-ingestor baixa as fontes F1 (D4), persiste os arquivos brutos no MinIO existente (bucket `ooh-raw-landing`), e faz COPY para raw; depois o normalizer roda core+serving (D3). (FALLBACK) um pg_dump -Fc do schema `raw` do dev local (so as tabelas F1, ~1GB comprimido provavelmente <300MB) guardado no MinIO `ooh-backups/seed-dev-raw.dump`, restauravel com pg_restore se uma fonte cair no primeiro seed. Backups recorrentes (D1) cobrem core+serving SEPARADAMENTE do raw: como o raw e reproduzivel das fontes, o backup critico e o core/serving (resultado do pipeline + parametros do modelo); um pg_dump por schema (`-n core -n serving`) reduz o tamanho e o tempo de restore vs dumpar o raw gigante todo dia. Versionar tambem o estado do MinIO (fontes brutas) = reproducibilidade total do dataset que gerou um score.

### [local-vs-service] uai-ooh-normalizer nasce como repo+container AO FINAL do Bloco 1, não antes — empacota o que já validou, não constrói abstração antecipada  _(votos 3/3)_
Criar o repo uai-ooh-normalizer (greenfield) como ÚLTIMO passo do Bloco 1, depois que os Épicos 1-3 rodaram e validaram local. Conteúdo do repo: (a) as migrations do schema core que já vinham sendo escritas (D1); (b) os scripts de pipeline raw->core promovidos do .data/scripts/ com a única mudança de DROP+CREATE -> idempotente por dataset_version (que o Épico 1.0 já prevê); (c) Dockerfile de job Python; (d) entry que roda o pipeline e faz o swap ACTIVE. NÃO transformar em ingestor recorrente, NÃO adicionar watermark/ETag/retry/observabilidade agora — isso é explicitamente 'o que muda pra virar serviço' descrito no plano, e é trabalho do uai-ooh-static-ingestor, que é OUTRO serviço e pode vir depois/no Bloco 2.

### [cicd-deploy] Cada job Python (ingestor, normalizer) = imagem própria no GHCR, com ENTRYPOINT que roda-e-sai; nunca um container long-running com sleep  _(votos 2/3)_
Criar 2 repos greenfield (uai-ooh-static-ingestor, uai-ooh-normalizer), cada um com seu Dockerfile multi-stage Python (base python:3.12-slim + GDAL/proj só onde precisa) e um ENTRYPOINT que é o CLI do job (ex.: `python -m ooh_ingestor run --source all` / `python -m ooh_normalizer build --version next`). O processo termina com exit 0/!=0 — a imagem NÃO sobe servidor nem fica em loop. CI idêntico ao deploy.yml atual do bus-lines: jobs `test` (pytest + lint) → `build-push` (push GHCR `:latest` + `:<sha>`) só em push pra main, usando GITHUB_TOKEN (sem secret de VPS no repo). Imagens: ghcr.io/mddinizbh/uai-ooh-static-ingestor e .../uai-ooh-normalizer. O DSN hardcoded de DEV do db.py vira 100% env (DB_HOST/PORT/NAME/USER/PASSWORD), igual o Dockerfile Java atual — RULE-OPS-01, nada de secret na imagem.

## ⚠️ Decisões que precisam de DECISÃO DO DONO
1. FRONTEIRA DE SERVING (a mais importante — conflita com o épico-4 escrito): o intel deve LER o schema 'serving' no próprio ooh-postgis (usuário read-only, tabelas planas sem geometry — minha recomendação, elimina ETL cross-DB, dois donos de Flyway e a disputa de dataset_version no banco live) OU manter o export cross-DB core(ooh)→uai_buslines(alpine) como o épico-4.2 está escrito? Se for a recomendação, é preciso reescrever o épico-4.1/4.2 e confirmar que o intel é o SUCESSOR do bus-lines (não um plugin no app legado).
2. IDENTIDADE DO INTEL: confirmo que o intel evolui IN-PLACE do uai-bus-lines-map (sem rename agora), o pipeline Python SAI para o uai-ooh-pipeline, e a SPA + rename de repo/imagem ficam para tarefa posterior fora do caminho crítico. O repo intel continua PÚBLICO ao evoluir (recomendo sim — dado cívico, sem tenant, ADR-004)? Decidir antes de T0.
3. MODELO trip_pattern vs line_shape (bloqueante do Épico 1): os épicos têm cabeçalho pattern-centric mas corpo shape-centric, e nenhuma tarefa cria trip_pattern. Confirmo inserir a fundação trip_pattern/pattern_stop/pattern_service + pattern_stop_exposure no Bloco 1 e tratar line_shape/line_stop como derivações? Sem isso o Bloco 2 (trip-consolidator) vira retrofit de schema.
4. dataset_version do GTFS (app live, 327 linhas, status ACTIVE/IMPORTING/ARCHIVED/FAILED, com cron noturno de import) vs dataset_version das métricas: são tabelas/eixos independentes ou a mesma? Como o serving passa a viver no ooh (recomendação), o conflito desaparece, MAS enquanto o bus-lines live coexistir é preciso decidir o dono único do swap. Recomendo desligar o import agendado do bus-lines (scheduler.enabled=false) antes do cutover.
5. FRONTEIRA ingestor↔normalizer: aceita que static-ingestor e normalizer fiquem no MESMO repo (uai-ooh-pipeline) com dois entrypoints até o realtime-poller forçar a separação no Bloco 2? (recomendado).
6. TEMPLATE Java: criar o uai-ooh-service-template já no Bloco 1 (logo após o esqueleto do intel, capturando os fixes frescos) ou só quando nascer o segundo serviço Java (consolidator, Bloco 2)? Recomendo no Bloco 1.
7. DISPARO DE JOBS (n8n derrubado pela verificação): aceita usar GitHub Actions schedule/workflow_dispatch no uai-infra (DAG versionado, sem socket Docker num container internet-facing) em vez de n8n executando docker compose run? (fortemente recomendado).
8. FERRAMENTA DE MIGRATION do core (Python): sqitch nativo (sem JVM no container) ou Flyway CLI (consistência com o intel Java) ou Alembic/yoyo? Recomendo sqitch para não embutir JRE no container de pipeline.
9. SEED DO RAW NO VPS: artefatos-fonte versionados no MinIO (reprodutível, auditável, não desalinha 4107 — recomendado) vs pg_dump/restore do raw local (fallback) vs re-rodar das fontes via static-ingestor (caminho crítico refém de CKAN/IBGE/Overpass no ar — NÃO recomendado para o seed F1).
10. SECRET: a credencial ooh_admin está em claro no .data/scripts/db.py (working tree). Confirmo rotação imediata + parametrização por env em T0, tratando-a como potencialmente vazada antes de qualquer commit em repo público.
11. PRÉ-REQUISITO DE INFRA (bloqueante de T18): o postgres alpine está UNHEALTHY há 8 dias. Diagnosticar a causa (healthcheck vs degradação real, RAM/disco no KVM4 16GB/14 containers) é gate go/no-go ANTES de somar o ooh-postgis. Quem é o dono desse ticket?
12. RETENÇÃO DE BACKUP: quantas versões de dataset/fontes manter no MinIO e por quanto tempo o pg_dump diário de core+serving? Definir custo de storage aceitável no KVM4.
13. TÓPICOS KAFKA ooh.* não afetam F1, mas decidir cedo se a regra dos '5 tópicos genéricos' bloqueia os dedicados (ooh.dataset.refreshed etc.) para o Bloco 2 não travar.

## 🔴 Decisões DESAFIADAS pelos céticos (revisar — maioria achou falha)

### [repos-topologia] O intel evolui IN-PLACE do uai-bus-lines-map via rename do repo + refactor de pacote em PR único, mantendo o app live durante a transição
- **Falha apontada:** O escopo do rename está incompleto e perpetua um acoplamento que a arquitetura-alvo proíbe. O repo uai-bus-lines-map NÃO é um serviço — são DOIS artefatos acoplados no mesmo CI: ghcr.io/.../uai-buslines (API Java) E ghcr.io/.../uai-buslines-web (SPA React/MapLibre — container uai-buslines-web, build…
- **Alternativa sugerida:** Manter o in-place (a direção está certa — greenfield seria semanas de retrabalho), mas REDEFINIR o recorte do PR mecânico para refletir que são dois artefatos com destinos divergentes, e resolver o schema antes: (1) No mesmo PR mecânico, se

### [repos-topologia] Banco ooh com PostGIS é um serviço de INFRA no uai-infra (container dedicado), não um repo de aplicação — e a fronteira local→VPS do pipeline é uma config de DSN, não retrabalho de código
- **Falha apontada:** A afirmação-âncora da decisão — "rodar o pipeline contra o ooh local ou do VPS é literalmente trocar o DSN, zero mudança de código; materializar o db.py para ler DSN de env é a ÚNICA mudança de fronteira" — é FALSA como escrita, e é justamente o que dissolveria a tensão local-first vs service-first.…
- **Alternativa sugerida:** Refinar (não descartar) a decisão separando o "pipeline" em DUAS fronteiras com naturezas diferentes, e corrigir a afirmação de "só DSN":

1. NORMALIZER (raw→core, SQL PostGIS): AQUI sim a decisão vale — db.py lê DSN de env (DB_HOST/PORT/NA

### [postgis-prod] Container ooh-postgis dedicado no uai-infra (imagem postgis/postgis:16-3.4, mesma rede, bind 127.0.0.1, volume nomeado, backup proprio)
- **Falha apontada:** A decisão de criar o container `ooh-postgis` dedicado (imagem oficial, mesma rede, bind 127.0.0.1:5433, volume `ooh_pgdata`, pg_dump -Fc) RESISTE no seu escopo literal — é o que ADR-003 recomenda, isola a extensão espacial, não toca os 5 vizinhos do alpine, e a infra tem folga (RAM real do VPS ~3,9G…
- **Alternativa sugerida:** Manter o container `ooh-postgis` EXATAMENTE como decidido (a escolha está certa), mas FECHAR duas lacunas de expansão agora, baratas no F1: (a) Decidir e documentar JÁ o contrato de leitura de realtime do Bloco 2 — o `intel` lerá `core.trip

### [postgis-prod] normalizer e dono do schema core+serving via migrations versionadas (Flyway/sqitch), pipeline Python roda DENTRO dele como job
- **Falha apontada:** A decisao define "normalizer e dono do schema core+SERVING via migrations" — mas isso contradiz a fonte de verdade ja detalhada (epico-4-serving-app.md) e cria um conflito de ownership de DDL que ENCARECE o Bloco 2 (o criterio-mestre "facil de expandir").

Fato 1 — contradicao com os docs: o epico-4…
- **Alternativa sugerida:** Manter quase tudo da decisao (migration versionada idempotente, sem DROP+CREATE; pipeline DML em Python reusando .data/scripts; flip atomico via core.dataset_version BUILDING->ACTIVE; rodar como job por GitHub Actions). Corrigir SO o owners

### [postgis-prod] static-ingestor faz a recarga total do raw em producao a partir das FONTES (CKAN/PBH), NAO migrar dump do raw do dev
- **Falha apontada:** A decisão afirma que o static-ingestor "RECARREGA o raw direto das fontes (CKAN/PBH/IBGE/OSM)" só "reusando os loaders DROP+CREATE+COPY" e que isso "já é o mesmo trabalho de D3". Isso é factualmente impreciso e esconde o custo principal. Auditei .data/scripts/: os loaders NÃO baixam das fontes. load…
- **Alternativa sugerida:** Separar explicitamente as duas peças que a decisão mistura: AQUISIÇÃO (fetch das fontes → MinIO) e LANDING (MinIO → COPY no raw via loaders). (1) Seed inicial de F1: subir os artefatos brutos que JÁ existem em .data/downloads (subconjunto F

### [local-vs-service] Local-first PARA VALIDAR O MODELO, service-first PARA O SCHEMA — não é binário, é faseado dentro do Bloco 1
- **Falha apontada:** A decisão protege o ARTEFATO certo (schema core = migration versionada) com o racional certo (separar cálculo de contrato). Mas ela promete "Bloco 2 só acrescenta V_n, zero retrabalho de schema" apoiada numa premissa que os próprios épicos contradizem: o schema F1 que vai virar migration NÃO é o sch…
- **Alternativa sugerida:** Manter a decisão (local-first p/ cálculo + service-first p/ schema) — é sólida — MAS corrigir o conteúdo antes de congelar: (1) Adicionar uma tarefa "Épico 1.2.5 — trip_pattern/pattern_stop/pattern_service como modelo canônico" e reescrever

### [local-vs-service] PostGIS dedicado no VPS entra SÓ no fim do Bloco 1, como destino do primeiro run containerizado — não no começo
- **Falha apontada:** Custo escondido / redundância no caminho crítico do Bloco 1, não no eixo de expansão.

A sequência produz o `core` DUAS vezes para um banco que, no Bloco 1, não serve nada em produção:
- Passos (1)-(2): Épicos 1-3 rodam contra o ooh LOCAL e VALIDAM a 4107 + as 304 local. Ao fim do passo (2) o `core`…
- **Alternativa sugerida:** Diferir o container postgis para a ENTRADA do Bloco 2 e, no Bloco 1, exportar core LOCAL → uai_buslines(VPS) diretamente.

Sequência alternativa:
- Bloco 1: Épicos 1-3 rodam local (igual), validam 4107 + 304 local; Épico 4.2 exporta core lo

### [local-vs-service] Granularidade de sessão: 1 Épico = 1 a 2 sessões auto-suficientes; cada tarefa carrega seu pré-req, sua DDL e seu gate de validação 4107 — e atualiza um run-log versionado
- **Falha apontada:** O mapeamento de sessões S1-S5 é local-first disfarçado e viola o critério-mestre do dono ("vale gastar mais tempo tendo arquitetura fácil de expandir"). Três defeitos concretos:

(1) S4 esconde um PORT não-gated, não um "re-run". S1-S3 rodam o pipeline inteiro em `.data/scripts/` contra o banco `ooh…
- **Alternativa sugerida:** Inverter a costela do sequenciamento para service-first parcial, alinhado ao que o dono explicitamente aceitou ("gastar mais tempo por arquitetura fácil de expandir"):

- S0 dedicada = INFRA: subir o container PostGIS no VPS via uai-infra/d

### [cicd-deploy] uai-ooh-intel = fork do esqueleto do uai-bus-lines-map (Java :8085), entra no GHCR/compose como serviço novo; o bus-lines atual continua vivo até o cutover
- **Falha apontada:** A decisão valida o risco ERRADO e ignora o de verdade. Ela analisa `ddl-auto=validate` (que é benigno — Hibernate só valida entidades mapeadas, ignora tabelas extras, confirmado) mas não trata o conflito de FLYWAY com dois serviços no MESMO banco.

CENÁRIO DE QUEBRA (concreto, F1, banco compartilhad…
- **Alternativa sugerida:** Manter o "novo serviço, não rename in-place" e o reuso de Dockerfile/CI (isso é bom), mas mudar DUAS coisas:

1) OWNERSHIP DE FLYWAY EXPLÍCITO. Apenas UM serviço aplica migrations no `uai_buslines`. Como a V2 é aditiva e o intel é quem prec

### [cicd-deploy] Disparo dos jobs batch = n8n (que já roda) chamando `docker compose run --rm` via execução no host, NÃO cron de SO nem container-com-cron
- **Falha apontada:** A decisão se autocontradiz e abre risco escondido sério, falhando no critério-mestre (GitOps / fácil de expandir).

1) CONTRADIÇÃO COM A PRÓPRIA JUSTIFICATIVA. Ela rejeita cron-de-SO por "violar 'nunca tocar o VPS direto' e ficar invisível ao repo", mas a alternativa que escolhe (n8n rodando `docker…
- **Alternativa sugerida:** Disparo agendado = GitHub Actions `schedule:` (cron) + um ÚNICO entrypoint versionado no repo uai-infra que executa `docker compose run --rm static-ingestor ...` e, no sucesso, encadeia `docker compose run --rm normalizer build` e o export 

### [cicd-deploy] Dono do schema = quem grava: normalizer é dono do schema `core` no banco `ooh` (migrations Python próprias); o intel (Java/Flyway) é dono APENAS da V2 plana no banco `uai_buslines`. Bancos e migrators separados.
- **Falha apontada:** A decisao acopla o ciclo de vida do trip-consolidator (Bloco 2, worker Java 24/7) ao do normalizer (batch Python agendado), porque coloca o DDL de core.trip_executed/trip_executed_track na migration do normalizer ("dono do core = normalizer"), enquanto quem grava essas tabelas e o consolidator. Tres…
- **Alternativa sugerida:** Manter a separacao de BANCOS (ooh PostGIS vs uai_buslines plano — correto, habilita o container dedicado e respeita ADR-003), mas trocar o eixo de ownership de "1 dono por schema" para "1 dono por agrupamento de tabelas, alinhado ao servico

### [cicd-deploy] Adicionar ao uai-infra sem quebrar o que roda: intel entra no deploy_apps + nginx (upstream via variável/resolver); jobs Python entram no compose mas FORA do deploy_apps (não têm proxy)
- **Falha apontada:** A decisão modela `intel` como um CONTAINER NOVO paralelo (`ooh-intel:8085`, server block próprio `uai-ooh-intel.uaiagencia.com.br`, rollout "3) subir intel; 4) trocar upstream do nginx pro intel"). Isso contradiz a fonte-de-verdade do repo: (1) arquitetura-servicos.md diz que intel "EVOLUI do bus-li…
- **Alternativa sugerida:** Manter toda a mecânica de deploy proposta (deploy_apps + nginx resolver/variável; ingestor/normalizer com profiles:[jobs]/restart:no fora do deploy_apps; postgres-ooh dedicado 127.0.0.1-only; ordem de rollout reversível; curar o postgres un

### [task-breakdown] Tarefa 0 fundadora: criar os 3 repos greenfield + scaffolding mínimo ANTES de qualquer tarefa de pipeline (service-first no ESQUELETO, local-first na EXECUÇÃO)
- **Falha apontada:** A decisão resolve a tensão errada e cria um acoplamento escondido que a própria Tarefa 0 não enxerga. Três problemas, em ordem de gravidade:

FALHA 1 (séria) — A "casa" do normalizer nasce com 3 fronteiras de versionamento desalinhadas, e a Tarefa 0 trata "herdar o miolo de .data/scripts/" como cópi…
- **Alternativa sugerida:** Quebrar a Tarefa 0 em DUAS tarefas auto-suficientes, e tornar o versionamento do motor explícito:

TAREFA 0a — "Greenfield dos repos PYTHON de dados (ingestor + normalizer)": cria SÓ uai-ooh-static-ingestor e uai-ooh-normalizer. Critério de

### [task-breakdown] Mapa de tarefas = épicos já decompostos, com Épico 2 rodando 100% em PARALELO ao Épico 1 (camadas de contexto são independentes da linha)
- **Falha apontada:** A decisão se contradiz exatamente no critério-mestre (fácil de expandir). Ela faz duas coisas incompatíveis:

(1) "Manter a granularidade que os épicos já têm (1.0-1.5, 3.1-3.7...) como o conjunto de tarefas-sessão" — ou seja, adota LITERALMENTE as tarefas concretas dos épicos.

(2) "Expande bem por…
- **Alternativa sugerida:** Manter quase toda a decisão (o paralelismo Ep1//Ep2, a barreira 3.6 como gate de count=304, 1.5/vehicle como adiável, 4.x sequencial) — esses pontos são sólidos e bem argumentados — mas com DUAS correções no grafo de tarefas antes de congel

### [task-breakdown] Cada tarefa-sessão carrega um cabeçalho-contrato fixo + produz um APONTAMENTO de saída padronizado em docs/epicos/runs/ — esse é o mecanismo de auto-suficiência
- **Falha apontada:** O mecanismo (cabeçalho-contrato + apontamento em docs/epicos/runs/) é bom e expande bem. A falha séria está no item (5) do contrato: "as queries de validação 4107 copiadas LITERALMENTE com o NÚMERO esperado" como critério de pronto binário. Esses números vêm do snapshot da simulação (gen_ficha_4107.…
- **Alternativa sugerida:** Manter o mecanismo (contrato + apontamento em runs/), mas mudar o item (5) de "número literal igualdade exata" para "invariante metodológica + faixa de sanidade + recomputo da referência". Três ajustes:\n\n(A) Separar o critério em duas cla

### [task-breakdown] Fronteira de banco explícita por tarefa: Épicos 1-3 escrevem SÓ no ooh (PostGIS); Épico 4 lê ooh e escreve no uai_buslines — a tarefa 4.2 (export) é a única que toca os dois e é o ponto de respeito ao ADR-003
- **Falha apontada:** A fronteira (4.2) define o MECANISMO (abrir 2 conexoes, drop de geom, flip por dataset_version) mas nao o CONTRATO da coerencia entre core.line_metrics e a tabela plana line_metrics. Hoje a coerencia e mantida por convencao humana ("espelha sem geom") + dois schemas com dois donos (normalizer Python…
- **Alternativa sugerida:** Manter a decisao, mas elevar a fronteira de "convencao" para "contrato declarativo": (1) tornar EXPLICITO no doc que a 4.2 e um passo do uai-ooh-normalizer (dono unico do core E do export flat), nao um exporter orfao - assim no Bloco 2 o ga

## Plano (síntese do arquiteto-chefe)
# Plano de Execução — uAI-OOH Bloco 1 (F1 / Dados)

> Arquiteto-chefe. Plano consolidado após verificação adversarial das 6 decisões que sobreviveram e revisão das que caíram. Objetivo do Bloco 1: catálogo + perfil + score 0-100 + ficha das 304 linhas (mídia back bus), sem tempo real, sem comercial, sem auth.

## 0. Princípios herdados (não rediscutir — decisões do dono)

1. Alvo = Bloco 1 (dados/F1).
2. PostGIS em produção = **container dedicado** no VPS (banco `ooh` separado COM PostGIS). O serving Java lê tabelas PLANAS, sem PostGIS (ADR-003).
3. `uai-auth` só no Bloco 3.
4. `intel` herda ADR-003 (sem PostGIS no serving) e ADR-004 (referência sem `tenant_id`).
5. `normalizer` é dono do schema `core` (migrations próprias). Pipeline em Python; serving em Java. Hexagonal single-module por serviço; sealed sem `default`.
6. Deploy: container → GHCR → `uai-infra` compose. Nunca tocar o VPS direto.
7. Critério-mestre do dono: **vale gastar mais tempo por uma arquitetura fácil de expandir** (Blocos 2-3 trazem realtime-poller, trip-consolidator, cms multi-vertical, commercial).
8. Cada tarefa roda em **sessão própria, auto-suficiente** (pré-reqs explícitos, contexto em doc, critério de pronto verificável).

## 1. Arquitetura consolidada do Bloco 1

### 1.1 Topologia de repositórios (Decisão repos-topologia, 3/3 e 2/3)

Polyrepo, alinhado 1:1 com a unidade de deploy (1 container = 1 imagem GHCR = 1 entry no `deploy_apps`). **NÃO** monorepo `uai-ooh`.

| Repo | Stack | Conteúdo | Estado |
|---|---|---|---|
| `uai-ooh-pipeline` | Python + PostGIS | DOIS entrypoints no MESMO repo: `static-ingestor` (landing raw) e `normalizer` (raw→core + materializa flat, dono do schema `core`). Compartilham `db.py`, DSN, toolchain. | greenfield |
| `uai-ooh-intel` | Java/Spring | Serving F1. **Evolui in-place do `uai-bus-lines-map`** (não greenfield). | evolução do existente |
| `uai-ooh-service-template` | Java | GitHub Template Repository com o esqueleto hexagonal limpo + os fixes do `pom.xml` (api.version=1.44, Testcontainers 1.21.3, JaCoCo 80%, Java 21) + Dockerfile multi-stage + deploy.yml + filtro `X-UAI-Internal-Key`. | greenfield, derivado do intel |

**Fronteira ingestor↔normalizer (fraca, deliberada):** começam colados no `uai-ooh-pipeline` porque compartilham 100% do toolchain. Só viram repos separados quando o realtime-poller nascer (Bloco 2). **Pendência de confirmação do dono** (ver decisões).

**Identidade do intel (REVISADO — o desafio derrubou o "rename in-place tratado como 1 serviço").** O `uai-bus-lines-map` NÃO é um serviço único: ele empacota (a) API Java `uai-buslines`, (b) SPA `uai-buslines-web` (container/imagem próprios), e (c) `.data/scripts/` (pipeline Python, hoje gitignored). Portanto:

- O **intel evolui in-place** do `uai-bus-lines-map` (renomear daria semanas de retrabalho e não destrava expansão — consolidator/commercial acoplam por HTTP/Kafka, não por nome de pacote).
- O **pipeline Python sai do repo do intel** e vai para o `uai-ooh-pipeline` greenfield (resolve o acoplamento que a arquitetura-alvo proíbe e tira o DSN com senha do repo público).
- O **rename de repo/imagem GHCR e a SPA** ficam para tarefa POSTERIOR, fora do caminho crítico de F1, com subdomínio novo se desejado (zero risco no link público live).

### 1.2 PostGIS dedicado (Decisão postgis-prod, 3/3)

- Novo serviço `ooh-postgis` no compose do `uai-infra`, imagem oficial `postgis/postgis:16-3.4` (casa com 3.4.3 do dev), **NÃO** mexer no `postgres` alpine compartilhado.
- Bind `127.0.0.1:5433:5432` (5433 evita colidir com o alpine), rede `uai_uai-net`, volume nomeado `ooh_pgdata`, healthcheck `pg_isready`, init `01-init-ooh.sql` com `CREATE EXTENSION postgis`.
- Três schemas no banco `ooh`: `raw` (landing) + `core` (PostGIS, geometry, dono = normalizer) + `serving`/`flat` (tabelas planas, sem geometry, lido pelo intel).
- **REVISADO — fronteira de serving:** o intel lê o schema `serving` DENTRO do próprio `ooh-postgis` via usuário `ooh_intel_ro` (GRANT SELECT só em `serving`), com tabelas planas sem tipos geometry no caminho de leitura. **Não** cross-DB, **não** export para `uai_buslines`. O `uai_buslines` legado fica como está (app live, 327 linhas) até o intel assumir. Isso elimina o ETL cross-DB do épico-4.2 e o problema de dois donos de Flyway / dois `dataset_version` no mesmo banco. **(Conflita com a redação atual do épico-4 — pendência do dono, ver §1.6 e decisões.)**

### 1.3 Sequência local-first / service-first (Decisões local-vs-service)

Resolvida em dois eixos (a tensão é faseamento, não binário):

- **ESQUELETO = service-first.** Os 2 repos (`uai-ooh-pipeline`, `uai-ooh-service-template`) e o esqueleto do intel nascem como serviço desde já: repo + Dockerfile + CI GHCR (build-only, sem deploy) + schema `core` como **migrations versionadas** (não `CREATE` solto, não `DROP+CREATE` ad-hoc).
- **EXECUÇÃO = local-first.** Os Épicos 1-3 (pipeline PostGIS pesado) rodam contra o banco `ooh` LOCAL do dono, validando os números da 4107, usando os scripts JÁ versionados dentro do `uai-ooh-pipeline`. "Extrair pro serviço" não é reescrita — é empacotar scripts que nasceram organizados.
- **VERSIONAR O MOTOR, NÃO SÓ O SCHEMA (REVISADO).** O desafio mostrou que versionar só o DDL deixa o cálculo espacial (o artefato caro, validado contra a 4107) órfão num diretório gitignored. Logo: o miolo de `.data/scripts/` + `gen_ficha_4107.py` é **movido** para o `uai-ooh-pipeline` e versionado junto, com os coeficientes/pesos em `core.model_params` (DDL via migration + seed UPSERT idempotente recarregável, para calibração não virar migration nova a cada ajuste).
- **PROVISIONAMENTO DO VPS = tarefa de infra separada**, depois do core validado local, **gateada** pelo diagnóstico do postgres alpine UNHEALTHY (8 dias) e auditoria de RAM/disco do KVM4.

### 1.4 Fronteira local→VPS (REVISADO — não é "só DSN")

O desafio derrubou "trocar o DSN = zero código". Verdadeiro só para o **normalizer** (raw→core é SQL dentro do banco). Para o **ingestor** há filesystem local + rede de saída + 1.8 GB de downloads + secret em claro no `db.py`. Portanto:

- **Normalizer:** `db.py` parametrizado por env (DB_HOST/PORT/NAME/USER/PASSWORD); rodar contra ooh local e depois contra ooh do VPS é troca de DSN. OK.
- **Ingestor no Bloco 1:** NÃO re-rodar das fontes (coleta recorrente não é requisito agora). Seed do raw no VPS via **artefatos-fonte versionados no MinIO** (bucket `ooh-raw-landing`, com manifest SHA256 + origem + data) — promove os arquivos brutos JÁ validados que produziram os números da 4107. Isso dá reprodutibilidade/auditabilidade SEM colocar a descoberta multi-fonte no caminho crítico e SEM desalinhar as validações 4107 (mesma versão GTFS). `pg_dump -Fc` do raw local fica como fallback no `ooh-backups/`.
- A aquisição automatizada (CKAN/IBGE/Overpass → MinIO) é tarefa do `static-ingestor` entregue no Bloco 1 mas **fora do caminho crítico** (e amadurece de vez no Bloco 2 com o realtime-poller).
- **Secret:** remover o DSN+senha em claro do `db.py` e **rotacionar** a credencial `ooh_admin` (já exposta no working tree; tratar como vazada por precaução antes de qualquer commit em repo público).

### 1.5 CI/CD e disparo de jobs (Decisões cicd-deploy)

- **Imagens:** cada job Python = imagem própria no GHCR com ENTRYPOINT roda-e-sai (`python -m ooh_ingestor run` / `python -m ooh_normalizer build`), exit 0/!=0, NUNCA long-running com sleep. CI = `test` (pytest+lint) → `build-push` (GHCR `:latest`+`:<sha>`) só em push pra main, GITHUB_TOKEN.
- **Compose:** ingestor/normalizer entram no compose com `restart: no`, disparados explicitamente; intel entra no `deploy_apps` + bloco nginx (`resolver 127.0.0.11; set $up http://...; proxy_pass $up;`). O `ooh-postgis` entra como infra (não em deploy_apps, sem proxy).
- **Disparo dos jobs (REVISADO — n8n derrubado).** O desafio mostrou que n8n disparando `docker compose run` exige socket Docker (root no host) num container internet-facing, e tira o DAG do GitOps. **Usar GitHub Actions `schedule:`/`workflow_dispatch`** no `uai-infra` com o MESMO canal SSH que o deploy já usa: cron + entrypoint versionado que encadeia ingestor→normalizer→ativar-versão, com `concurrency: group` (serializa sem advisory lock improvisado), retry e notificação nativos. DAG fica em YAML versionado = o "pipeline declarativo" que a plataforma quer. n8n, no máximo, observa/notifica — não executa.
- **Ownership de schema (REVISADO — um dono por banco/escritor):** normalizer = dono de `core` (migrations Python, ferramenta a definir — sqitch nativo evita JVM no container Python). Intel = dono APENAS do que ELE valida no boot (se mantida a leitura do schema `serving` no ooh, o intel não migra o ooh; valida via teste de contrato). No Bloco 2, tabelas gravadas em runtime pelo consolidator (`trip_executed`) terão DDL versionado pelo consolidator (Flyway Java nativo — Flyway roda PostGIS sem problema, é só runner SQL). Regra: **dono do DDL = quem grava em runtime**.

### 1.6 Tensão de modelo de dados a resolver ANTES do Épico 1 (REVISADO — bloqueante)

O cabeçalho dos Épicos 1 e 3 já promoveu `core.trip_pattern` + `core.pattern_stop` + `pattern_service` a **unidade canônica do itinerário** (e `core.pattern_stop_exposure` para o alcance refinado), rebaixando `line_shape`/`line_stop` a materializações de apresentação. Mas o **corpo** das tarefas 1.3/1.4/3.5 ainda é shape-centric e **nenhuma tarefa cria `trip_pattern`**. O Bloco 2 (trip-consolidator faz snap em `trip_pattern`) depende disso. Sem resolver, congela-se um modelo inconsistente e o Bloco 2 vira retrofit de schema no caminho crítico — o oposto de "fácil de expandir". **Resolução:** inserir tarefa de fundação (`trip_pattern`/`pattern_stop`/`pattern_service` + `pattern_stop_exposure`) antes de 1.3/1.4, e reposicionar `line_shape`/`line_stop` como derivações. Pendência de confirmação do dono sobre materializar AMBOS vs só pattern.

### 1.7 Mecanismo de auto-suficiência de sessão

Cada doc de tarefa carrega 6 blocos fixos: (1) Objetivo + qual A1-A7 atende; (2) Pré-requisitos verificáveis por query; (3) Ler antes (lista fechada de docs+seções); (4) Passos (script Python + SQL); (5) **Critério de pronto TIPADO** (REVISADO): invariantes estruturais = igualdade exata (count=304, 5.166 setores, ~9,6k stops, índices GiST); grandezas derivadas/calibráveis = direção + faixa + método declarado (pop ponderada < 182k bruta; %AB ∈ faixa; impressões rotuladas ilustrativas) — **nunca igualdade literal com número de snapshot que o próprio plano manda corrigir** (ex.: 182k bruta, 23,7 km máx vs 21,4 km representativo). A Tarefa 0/baseline **recomputa** os alvos 4107 com a metodologia vigente; (6) Apontamento de saída em `docs/epicos/runs/<tarefa>-<data>.md` (tabelas+counts reais, obtido vs esperado, decisões de generalização, desvios, estado do dataset_version). A próxima sessão lê SÓ o apontamento + o doc da tarefa.

## 2. Sequência de tarefas

Eixos paralelizáveis: Épico 2 roda 100% em paralelo ao Épico 1 (camadas de contexto independem de `core.line`). Barreira de sincronização: 3.6 (score min-max precisa das 304). Épico 4 sequencial.

- **T0a** — Greenfield `uai-ooh-pipeline` (Python) + mover scripts + rotacionar secret (bloqueante das levas de dados). ∥ T0b.
- **T0b** — `uai-ooh-service-template` (Java) + esqueleto do `uai-ooh-intel` (bloqueante da leva intel). ∥ T0a.
- **T1** — Baseline 4107 recomputada + resolução do modelo trip_pattern (bloqueante do Épico 1).
- **T2** — Schema `core` como migrations + `model_params` (Tarefa 1.0 absorvida).
- **T3** — `core.line` + `trip_pattern`/`pattern_stop`/`pattern_service` (fundação canônica).
- **T4** — `core.stop` (KNN). | **T5** — `line_shape` (derivado). | **T6** — `line_stop` (derivado). | **T7** — `vehicle` (auxiliar, adiável).
- **T8/T9/T10** — Épico 2 em paralelo: census_sector, poi, road_segment.
- **T11/T12/T13** — Épico 3: corredor×censo, POIs no corredor, exposição arterial (paralelos após Ep1+Ep2).
- **T14** — demanda (MCO+embarque). | **T15** — pattern_stop_exposure + impressões.
- **T16** — score 0-100 (barreira, precisa das 304). | **T17** — materializar serving + ativar versão.
- **T18** — Infra: subir `ooh-postgis` no VPS + seed raw via MinIO + re-run normalizer containerizado (gateado pelo diagnóstico do alpine).
- **T19** — Épico 4: domínio + endpoints Java no `intel` (repo NOVO do `uai-ooh-service-template`; bus-lines congelado).
- **T20a** — `intel`: ITs (Testcontainers) + `mvn verify` (cobertura F1).
- **T20b** — `uai-ooh-web` (novo): SPA de ficha + ranking consumindo o `intel` (opcional em F1).

Detalhe de cada tarefa na lista estruturada.


## Índice de tarefas
| ID | Título | Épico | Depende | ∥ |
|---|---|---|---|---|
| [T0a](tarefas/T0a.md) | Greenfield uai-ooh-pipeline (Python) + mover scripts + rotacionar secret | Arquitetura / repos-topo | — | sim (∥ T0b) |
| [T0b](tarefas/T0b.md) | uai-ooh-service-template (Java) + esqueleto do uai-ooh-intel | Arquitetura / repos-topo | — | sim (∥ T0a) |
| [T1](tarefas/T1.md) | Baseline 4107 recomputada (metodologia vigente) + resolução do modelo  | Fundação / task-breakdow | T0a | não |
| [T2](tarefas/T2.md) | Schema core como migrations versionadas + core.model_params (absorve T | Épico 1 (Tarefa 1.0) | T1 | não |
| [T3](tarefas/T3.md) | core.line + trip_pattern/pattern_stop/pattern_service (fundação canôni | Épico 1 (1.1 + fundação) | T2 | não |
| [T4](tarefas/T4.md) | core.stop — reconciliação GTFS↔PBH por KNN ≤50m | Épico 1 (1.2) | T3 | sim |
| [T5](tarefas/T5.md) | core.line_shape — materialização de apresentação derivada do pattern ( | Épico 1 (1.3) | T3 | sim |
| [T6](tarefas/T6.md) | core.line_stop — sequência de pontos derivada do pattern (por sentido  | Épico 1 (1.4) | T3, T5 | não |
| [T7](tarefas/T7.md) | core.vehicle + vehicle_line_history (auxiliar, adiável) | Épico 1 (1.5) | T3 | sim |
| [T8](tarefas/T8.md) | core.census_sector — setor + população + renda + classe A-E | Épico 2 (2.1) | T2 | sim |
| [T9](tarefas/T9.md) | core.poi — POIs OSM normalizados | Épico 2 (2.2) | T2 | sim |
| [T10](tarefas/T10.md) | core.road_segment — malha viária + classe arterial (componente de trân | Épico 2 (2.3) | T2 | sim |
| [T11](tarefas/T11.md) | Corredor por linha × censo → demografia/renda (ponderada por área) | Épico 3 (3.1) | T5, T8 | sim |
| [T12](tarefas/T12.md) | POIs no corredor por categoria | Épico 3 (3.2) | T5, T9 | sim |
| [T13](tarefas/T13.md) | Exposição arterial por linha (componente de trânsito do score) | Épico 3 (3.3) | T5, T10 | sim |
| [T14](tarefas/T14.md) | Demanda por linha (MCO volume + embarque distribuição) | Épico 3 (3.4) | T3 | sim |
| [T15](tarefas/T15.md) | pattern_stop_exposure + impressões (back bus) F1 | Épico 3 (3.5) | T11, T14 | não |
| [T16](tarefas/T16.md) | Score 0-100 normalizado entre as 304 linhas (barreira de sincronização | Épico 3 (3.6) | T11, T12, T13, T15 | não |
| [T17](tarefas/T17.md) | Materializar schema serving (flat, sem geometry) + ativar versão | Épico 3 (3.7) | T16 | não |
| [T18](tarefas/T18.md) | Infra: subir ooh-postgis no VPS + seed raw via MinIO + re-run normaliz | Infra / postgis-prod + c | T17 | não |
| [T19](tarefas/T19.md) | Épico 4 — domínio + endpoints Java no intel (repo NOVO do template) | Épico 4 (4.1-4.4) | T18, T0b | não |
| [T20a](tarefas/T20a.md) | intel — ITs (Testcontainers) + mvn verify (cobertura F1) | Épico 4 (4.6) | T19 | não |
| [T20b](tarefas/T20b.md) | uai-ooh-web (novo) — SPA de ficha + ranking consumindo o intel | Épico 4 (4.5) | T19 | sim (∥ T20a) |

## Vista por repo (leva de execução)

> Cada task é **single-repo**: roda no `cwd` do seu repo-alvo (campo `Repo-alvo` no cabeçalho da task).
> A ordem DENTRO de cada leva é por dependência; entre levas, o gate é a dependência cruzada (coluna "Depende de").
> No Bloco 1 (pipeline de dados linear) a ordem por camada coincide com a ordem por dependência — por isso a vista
> por repo é limpa. Em F2/F3 as camadas se entrelaçam (consolidator ↔ comercial), então lá esta vista NÃO vale como ordem.

| Leva (repo) | Stack / cwd | Tasks (ordem por dependência) |
|---|---|---|
| **`uai-ooh-pipeline`** | Python + PostGIS · `~/IdeaProjects/personal/uai/uai-ooh-pipeline` | T0a → T1 → T2 → T3 → (T4 ∥ T5 ∥ T7) → T6 → (T8 ∥ T9 ∥ T10) → (T11 ∥ T12 ∥ T13 ∥ T14) → T15 → T16 → T17 |
| **`uai-ooh-service-template` → `uai-ooh-intel`** | Java 21 / Spring Boot · `~/IdeaProjects/personal/uai/uai-ooh-intel` | T0b → T19 → T20a |
| **`uai-infra`** | Docker Compose / VPS · `~/IdeaProjects/personal/uai/uai-infra` | T18 |
| **`uai-ooh-web`** (novo) | React / MapLibre (SPA) · `~/IdeaProjects/personal/uai/uai-ooh-web` | T20b |

**Gates entre levas (caminho crítico):**
- `pipeline` é a espinha: T0a destrava T1…T17 (raw→core→serving local).
- `infra` (T18) depende de T17 (serving materializado) — sobe o `ooh-postgis` no VPS + seed.
- `intel` (T19) depende de **T18 E T0b** (precisa do schema serving no VPS + do template Java).
- `web` (T20b) e os ITs do intel (T20a) dependem de T19 (a API existir). T20a ∥ T20b.

> ⚠️ **Nota de consistência:** a redação antiga de §1.1 ("intel evolui in-place do bus-lines") foi **superada** pela
> decisão do dono de 2026-06-04 — o `intel` é repo NOVO a partir do `uai-ooh-service-template` (T0b), e o
> `uai-bus-lines-map` fica CONGELADO. A SPA não estende o bus-lines: nasce no `uai-ooh-web` (T20b).
