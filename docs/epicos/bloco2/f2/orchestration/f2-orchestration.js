export const meta = {
  name: 'f2-orchestration',
  description: 'Orquestra a implementação do F2 (Bloco 2 uAI-OOH, realtime/verificado) em partes por LANE com handoff explícito (state.json), ESTRATÉGIA LOCAL-FIRST: build+ITs → ambiente local (compose+seed+replay) → E2E local com disparos MANUAIS dos RECALs → só então ship pra prod. Cada task roda como pipeline implement→review→test→validate→refute; ship bloqueado sem e2e=validated.',
  whenToUse: 'Executar/retomar a implementação do F2. Rode UMA parte por invocação via args.lane (gate→jobs∥cons→local→e2e→rt∥web→contract→ship→verify-prod). Toda parte lê o handoff no início e grava o seu no fim. Ações irreversíveis exigem args.confirm (create-consolidator; push+deploy).',
  phases: [
    { title: 'gate',        detail: 'read-only paralelo: prereqs-core/poller-live/raw-history (MCP ooh PROD) + repos/local-env (fs) + f1-intel/f1-portal (handoff do F1) ⇒ GREEN/YELLOW/RED' },
    { title: 'jobs',        detail: 'uai-ooh-pipeline: INFRA-03 (tiering raw→MinIO) + RECAL-00 (HLLs de audiência) — validados contra o ambiente LOCAL' },
    { title: 'cons',        detail: 'IRREVERSÍVEL (create-consolidator): cria uai-ooh-trip-consolidator do template + CONS-01..05; validação = ITs Testcontainers (E2E real fica na parte e2e)' },
    { title: 'local',       detail: 'LOCAL-01 (compose.local + seed do banco real) + LOCAL-02 (replayer raw→Kafka local) — repo uai-infra e poller' },
    { title: 'e2e',         detail: 'CORAÇÃO DO LOCAL-FIRST: consolidador local + replay acelerado + checks no medido local + disparos MANUAIS de RECAL-01/02 + reconciliação HLL×exato ⇒ e2e=validated' },
    { title: 'rt',          detail: 'uai-ooh-intel: RT-01/03/02 contra Redis/Postgres LOCAIS (gate f1-intel)' },
    { title: 'web',         detail: 'uai-portal: WEB-00..02 com intel local (gate f1-portal)' },
    { title: 'contract',    detail: 'barrier 2 frentes: contrato Redis CONS-05↔RT-01 + REST RT↔hooks do portal; critical ⇒ pending/reopen' },
    { title: 'ship',        detail: 'IRREVERSÍVEL (push+deploy, BLOQUEADO sem e2e=validated): PRs, GHCR, INFRA-04 no uai-infra (compose consolidador + crons + retenção 14d). Nunca toca nginx/VPS.' },
    { title: 'verify-prod', detail: 'D+1 do deploy: re-checks leves via MCP postgres-ooh (medido crescendo, partição arquivada, recompute D-1 rodou) + run doc de go-live' },
  ],
}

// ── parâmetros (args pode chegar objeto ou string JSON — blindar) ───────────
let _A = args
if (typeof _A === 'string') { try { _A = JSON.parse(_A) } catch (e) { _A = {} } }
if (!_A || typeof _A !== 'object') _A = {}
const LANE    = _A.lane || 'gate'   // gate|jobs|cons|local|e2e|rt|web|contract|ship|verify-prod
const CONFIRM = Array.isArray(_A.confirm) ? _A.confirm : (typeof _A.confirm === 'string' ? _A.confirm.split(',').map(s => s.trim()).filter(Boolean) : [])
const RETRIES = _A.retries || 2
const ONLY    = (Array.isArray(_A.only) && _A.only.length) ? _A.only : null

// ── paths ────────────────────────────────────────────────────────────────────
const PM      = '/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pm'
const F2      = PM + '/docs/epicos/bloco2/f2'
const RUNS    = PM + '/docs/epicos/runs'
const STATE   = F2 + '/orchestration/state.json'
const F1STATE = PM + '/docs/epicos/bloco1/f1/orchestration/state.json'
const REPO = {
  pipeline:     '/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pipeline',
  poller:       '/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-realtime-poller',
  consolidator: '/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-trip-consolidator',
  intel:        '/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-intel',
  portal:       '/Users/marleydiniz/IdeaProjects/personal/uai/uai-portal',
  infra:        '/Users/marleydiniz/IdeaProjects/personal/uai/uai-infra',
  template:     '/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-service-template',
}
const BUILD = {
  pipeline:     { build: '(cd REPO && python -m py_compile $(git ls-files "*.py") || true)', test: '(cd REPO && python -m pytest -q || pytest -q)' },
  poller:       { build: '(cd REPO && python -m py_compile $(git ls-files "*.py") || true)', test: '(cd REPO && python -m pytest -q || pytest -q)' },
  consolidator: { build: '(cd REPO && ./mvnw -q -DskipTests compile 2>/dev/null || mvn -q -DskipTests compile)', test: '(cd REPO && ./mvnw -q verify 2>/dev/null || mvn -q verify)' },
  intel:        { build: '(cd REPO && ./mvnw -q -DskipTests compile 2>/dev/null || mvn -q -DskipTests compile)', test: '(cd REPO && ./mvnw -q verify 2>/dev/null || mvn -q verify)' },
  portal:       { build: '(cd REPO && (bun run build || npm run build))', test: '(cd REPO && (bun run test --run || npx vitest run || npm test))' },
  infra:        { build: '(echo compose-only)', test: '(cd REPO && docker compose -f docker-compose.local.yml config -q)' },
}
// ambiente local (defaults; LOCAL-01 grava o real em lanes.local.env no handoff)
const LOCAL_DEFAULT = { pg: 'postgresql://ooh:ooh@localhost:55432/ooh', kafka: 'localhost:19092', redis: 'localhost:16379' }

// ════════════════════════ SCHEMAS (idênticos ao f1-orchestration) ════════════════════════
const GATE_RESULT = {
  type: 'object',
  properties: {
    gate: { type: 'string', description: 'prereqs-core|poller-live|raw-history|repos|local-env|f1-intel|f1-portal|medido-volume' },
    status: { type: 'string', enum: ['GREEN', 'YELLOW', 'RED'] },
    evidence: { type: 'array', items: { type: 'object', properties: {
      check: { type: 'string' }, expected: { type: 'string' }, actual: { type: 'string' }, pass: { type: 'boolean' },
    }, required: ['check', 'actual', 'pass'] } },
    note: { type: 'string' },
  },
  required: ['gate', 'status', 'evidence'],
}
const IMPLEMENT_OUTPUT = {
  type: 'object',
  properties: {
    status: { type: 'string', enum: ['complete', 'failed'] },
    branch: { type: 'string' }, repoPath: { type: 'string' },
    files_created: { type: 'array', items: { type: 'object', properties: { path: { type: 'string' }, description: { type: 'string' } }, required: ['path'] } },
    files_modified: { type: 'array', items: { type: 'object', properties: { path: { type: 'string' }, description: { type: 'string' } }, required: ['path'] } },
    checklist: { type: 'array', items: { type: 'object', properties: { item: { type: 'string' }, done: { type: 'boolean' }, justification: { type: 'string' } }, required: ['item', 'done'] } },
    compilation: { type: 'object', properties: { result: { type: 'string', enum: ['success', 'failure', 'skipped'] }, details: { type: 'string' } }, required: ['result'] },
    observations: { type: 'string' },
  },
  required: ['status', 'branch', 'checklist', 'compilation'],
}
const REVIEW_VERDICT = {
  type: 'object',
  properties: {
    status: { type: 'string', enum: ['approved', 'rejected'] },
    verdict: { type: 'string' },
    problems: { type: 'array', items: { type: 'object', properties: {
      file: { type: 'string' }, line: { type: 'integer' }, severity: { type: 'string', enum: ['critical', 'major', 'minor'] }, problem: { type: 'string' }, suggestion: { type: 'string' },
    }, required: ['severity', 'problem'] } },
    task_conformance: { type: 'object', properties: { checklist_complete: { type: 'boolean' }, scope_respected: { type: 'boolean' }, patterns_followed: { type: 'boolean' } } },
  },
  required: ['status', 'verdict'],
}
const TEST_RESULT = {
  type: 'object',
  properties: {
    status: { type: 'string', enum: ['passed', 'failed', 'skipped'] },
    test_results: { type: 'object', properties: { command: { type: 'string' }, total: { type: 'integer' }, passed: { type: 'integer' }, failed: { type: 'integer' }, skipped: { type: 'integer' } } },
    failed_tests: { type: 'array', items: { type: 'object', properties: { test: { type: 'string' }, error: { type: 'string' }, file: { type: 'string' } }, required: ['test'] } },
    new_tests: { type: 'object', properties: { expected: { type: 'integer' }, created: { type: 'integer' } } },
  },
  required: ['status'],
}
const DB_VALIDATION = {
  type: 'object',
  properties: {
    ok: { type: 'boolean' },
    checks: { type: 'array', items: { type: 'object', properties: {
      sql: { type: 'string' }, expected: { type: 'string' }, actual: { type: 'string' }, pass: { type: 'boolean' },
    }, required: ['sql', 'actual', 'pass'] } },
    dataset_version: { type: 'object', properties: { version_id: { type: 'string' }, status: { type: 'string' } } },
  },
  required: ['ok', 'checks'],
}
const REFUTE_VERDICT = {
  type: 'object',
  properties: {
    refuted: { type: 'boolean', description: 'true = conseguiu DERRUBAR o "done" com evidência concreta' },
    attacks: { type: 'array', items: { type: 'object', properties: {
      claim: { type: 'string' }, attack: { type: 'string' }, evidence: { type: 'string' }, survived: { type: 'boolean' },
    }, required: ['attack', 'survived'] } },
    reason: { type: 'string' },
  },
  required: ['refuted', 'attacks', 'reason'],
}
const CONTRACT_VERDICT = {
  type: 'object',
  properties: {
    verdict: { type: 'string', enum: ['aligned', 'divergent'] },
    matched: { type: 'array', items: { type: 'object', properties: { hook: { type: 'string' }, operation: { type: 'string' } } } },
    mismatches: { type: 'array', items: { type: 'object', properties: {
      hook: { type: 'string' }, endpoint: { type: 'string' },
      kind: { type: 'string', enum: ['missing-endpoint', 'missing-hook', 'method', 'path', 'query-param', 'request-shape', 'response-shape', 'redis-key', 'redis-field'] },
      expected: { type: 'string' }, actual: { type: 'string' }, severity: { type: 'string', enum: ['critical', 'major', 'minor'] },
    }, required: ['kind', 'severity'] } },
  },
  required: ['verdict', 'matched', 'mismatches'],
}
const HANDOFF_DOC = { type: 'object', properties: { gates: { type: 'object' }, lanes: { type: 'object' }, contract: { type: 'object' }, decisions: { type: 'array' }, runDocs: { type: 'array' }, pending: { type: 'array' } } }

// ════════════════════════ TASK REGISTRY ════════════════════════
// dbTarget: 'local' = valida via Bash psql/redis-cli no ambiente LOCAL (NUNCA MCP) · null = sem estágio de validação externa (ITs cobrem)
const TASKS = {
  // ── JOBS (uai-ooh-pipeline) ──
  'INFRA-03': { lane: 'jobs', repo: 'pipeline', doc: F2 + '/01-infra/INFRA-03-arquivamento-raw-minio.md', run: 'INFRA-03-arquivamento-raw-minio.md', deps: [], dbTarget: 'local',
                db: 'Modo teste contra o LOCAL: rodar o job de archive numa partição do raw local (LOCAL-01 seedou ≥2 dias) → parquet gerado, counts do parquet == counts da partição, partição dropada. Se LOCAL-01 ainda não rodou: validar só o dry-run/unit (status passed) e anotar pendência de validação plena.',
                note: 'Evolui rt_raw_retention.py: arquivar (parquet/zstd via DuckDB) → verificar → dropar. Destino MinIO configurável por env; pra validação local pode escrever em filesystem/MinIO local. NÃO mexer em cron/compose (isso é INFRA-04, parte ship).' },
  'RECAL-00': { lane: 'jobs', repo: 'pipeline', doc: F2 + '/06-recalibracao/RECAL-00-hll-audiencia-hex.md', run: 'RECAL-00-hll-audiencia-hex.md', deps: [], dbTarget: 'local',
                db: 'Contra o Redis LOCAL: job populou hll:hex:*; spot-check 5 hexes conhecidos: redis-cli PFCOUNT ≈ exposure_cell.unicos_hora (±2%, psql local); memória total do Redis medida e registrada (INFO memory).',
                note: 'PFADD em lote da base od_trip (origem+destino × faixa × tipo_dia). Se o Redis local (LOCAL-01) não estiver de pé, suba só o serviço redis do docker-compose.local.yml pra validar.' },

  // ── CONS (uai-ooh-trip-consolidator — repo NOVO) ──
  'CONS-01': { lane: 'cons', repo: 'consolidator', doc: F2 + '/03-consolidator/CONS-01-scaffold.md', run: 'CONS-01-scaffold.md', deps: [], confirm: 'create-consolidator', dbTarget: null, db: null,
               note: 'CRIA o repo: gh repo create mddinizbh/uai-ooh-trip-consolidator --private + push de main VAZIA antes de qualquer código → regerar do template ' + REPO.template + '. Flyway é DONO do schema medido (DDL de referência na techspec). Java 21/Spring, hexagonal single-module, headless. TRIP_TIMEOUT_S configurável por env (replay acelerado precisa).' },
  'CONS-02': { lane: 'cons', repo: 'consolidator', doc: F2 + '/03-consolidator/CONS-02-consumo-estado.md', run: 'CONS-02-consumo-estado.md', deps: ['CONS-01'], dbTarget: null, db: null,
               note: 'h3-java local (latLngToCell res 9) + set dos 2.615 h3_index de core.h3_cell em memória no boot. Idempotência por feed_timestamp. ITs com embedded Kafka + Redis (Testcontainers).' },
  'CONS-03': { lane: 'cons', repo: 'consolidator', doc: F2 + '/03-consolidator/CONS-03-reconstrucao-viagem.md', run: 'CONS-03-reconstrucao-viagem.md', deps: ['CONS-02'], dbTarget: null, db: null,
               note: '⚠️ ARMADILHA (techspec §Decisões): o route_id do feed É o route_short_name — join SEMPRE por core.line.short_name, NUNCA gtfs__routes.route_id. trip_id do RT não existe no estático. 5 condições de fechamento + timeout (env TRIP_TIMEOUT_S). Veículo fora do cadastro NÃO é erro.' },
  'CONS-04': { lane: 'cons', repo: 'consolidator', doc: F2 + '/03-consolidator/CONS-04-fechamento-medido.md', run: 'CONS-04-fechamento-medido.md', deps: ['CONS-03'], dbTarget: null, db: null,
               note: 'Snap 1×/viagem (PostGIS leitura no core, 31983); cobertura H3 EXATA interpolada pelo shape (fonte ping|interpolado; ponto longe do shape ⇒ não interpola); persiste medido.* com ON CONFLICT; evento ooh.trip.completed ENRIQUECIDO (sem track). SEM campaign_id (F2-#5). Validação E2E real é a parte e2e.' },
  'CONS-05': { lane: 'cons', repo: 'consolidator', doc: F2 + '/03-consolidator/CONS-05-acumulador-ao-vivo.md', run: 'CONS-05-acumulador-ao-vivo.md', deps: ['CONS-02'], dbTarget: null, db: null,
               note: 'Contrato Redis PÚBLICO (live:vehicle/live:line/live:reach:trip|day + hll:hex read-only): impressoesParciais (soma, cache exposure_cell em memória) + alcanceParcial (PFMERGE/PFCOUNT, dedup ~±0,8%). Publique o contrato de chaves/campos nas observações — o orquestrador grava no handoff p/ RT-01 e contract.' },

  // ── LOCAL (ambiente de validação) ──
  'LOCAL-01': { lane: 'local', repo: 'infra', doc: F2 + '/07-local/LOCAL-01-ambiente-local.md', run: 'LOCAL-01-ambiente-local.md', deps: [], dbTarget: 'local',
                db: 'compose.local up → serviços saudáveis + tópicos criados (kafka-topics --list no broker local); counts do seed via psql LOCAL: core.line=303, h3_cell=2615, exposure_cell=188280, od_trip≈1.202.903, raw.rt__vehicle_position ≥2 service_dates.',
                note: 'docker-compose.local.yml (NUNCA deployado; portas 55432/19092/16379) + script local/seed-ooh-local.sh (pg_dump seletivo do banco real → restore). Ao terminar, reporte nas observações as ENVs locais (pg/kafka/redis) — o orquestrador grava em lanes.local.env.' },
  'LOCAL-02': { lane: 'local', repo: 'poller', doc: F2 + '/07-local/LOCAL-02-replayer-feed.md', run: 'LOCAL-02-replayer-feed.md', deps: ['LOCAL-01'], dbTarget: 'local',
                db: 'Replay de 1h de raw local: nº de lotes publicados no tópico local == nº de ciclos (_feed_timestamp distintos) do intervalo; ordem por veículo preservada; replay repetido = mesmo resultado.',
                note: 'Subcomando replay no poller (reusa decode/publisher/config): lê raw local agrupado por _feed_timestamp, publica ciclos no Kafka LOCAL com --speed. NÃO landa de volta no raw.' },

  // ── E2E (disparos manuais dos RECALs — rodam dentro da parte e2e) ──
  'RECAL-01': { lane: 'e2e', repo: 'pipeline', doc: F2 + '/06-recalibracao/RECAL-01-recompute-fonte-medida.md', run: 'RECAL-01-recompute-fonte-medida.md', deps: [], dbTarget: 'local',
                db: 'Contra o Postgres LOCAL (pós-replay+consolidador): face_reach/line_reach fonte=medida populados pras linhas com medido; pattern_stop_exposure.v_real agregado; comando reach --fonte=medida idempotente (2ª execução não duplica).',
                note: 'DISPARO MANUAL no local (local-first): estender o CLI reach com --fonte=medida (opção A: reusa face_reach.py/ChunkedBuild/model_params). Apontar OOH_DB_URL pro LOCAL. Cron só existe em prod (INFRA-04, ship).' },
  'RECAL-02': { lane: 'e2e', repo: 'pipeline', doc: F2 + '/06-recalibracao/RECAL-02-validacao-reconciliacao.md', run: 'RECAL-02-validacao-reconciliacao.md', deps: ['RECAL-01'], dbTarget: 'local',
                db: 'No LOCAL: (1) 4107 viagens/dia~frequência, km~extensão×viagens; (2) fixture 11198/20736/30835/40705/40806 conforme techspec §Fixture (30835 com 2 linhas; 20736 ausente sem erro); (3) viagem_hex contíguo ao corredor; (4) medido vs estimado com desvios explicáveis; (5) reconciliação |PFCOUNT live:reach − exato| ≤~5% (redis-cli local).',
                note: 'É o critério de aceite do F2. Registrar counts REAIS no run doc.' },

  // ── RT (uai-ooh-intel) ──
  'RT-01': { lane: 'rt', repo: 'intel', doc: F2 + '/04-intel-rt/RT-01-realtime-read.md', run: 'RT-01-realtime-read.md', deps: [], gate: 'f1-intel', xdep: ['CONS-05'], dbTarget: 'local',
             db: 'Intel local apontando Redis LOCAL: GET /api/realtime/positions?line=4107 devolve carros + acumulado (impressoesParciais, alcanceParcial, selo); conjunto server-derived (nunca aceita vehicle_code do cliente).',
             note: 'Lê o contrato Redis do handoff (lanes.cons.redis_contract). Sem PostGIS no read (ADR-003).' },
  'RT-03': { lane: 'rt', repo: 'intel', doc: F2 + '/04-intel-rt/RT-03-positionfeed-port.md', run: 'RT-03-positionfeed-port.md', deps: ['RT-01'], gate: 'f1-intel', dbTarget: null, db: null,
             note: 'PositionFeed port (polling F2; SSE/WS = adapter futuro). RealtimeScope sealed sem default.' },
  'RT-02': { lane: 'rt', repo: 'intel', doc: F2 + '/04-intel-rt/RT-02-verificado-calibracao.md', run: 'RT-02-verificado-calibracao.md', deps: ['RT-01'], gate: 'f1-intel', xdep: ['RECAL-01'], dbTarget: 'local',
             db: 'Intel local contra Postgres LOCAL (pós-RECAL): GET /api/lines/4107/verified → métricas do medido + face/line_reach fonte medida vs estimada + Selo POR MÉTRICA (viagens/km/vel=MEDIDO; reach/impressões=ESTIMATIVA); cache invalida em ooh.trip.completed.',
             note: 'Selo ADR-058 tipado no payload (substitui aindaEstimativa). Intel NÃO re-deriva modelo (ADR-050).' },

  // ── WEB (uai-portal) ──
  'WEB-00': { lane: 'web', repo: 'portal', doc: F2 + '/05-front-rt/WEB-00-mapa-universal.md', run: 'WEB-00-mapa-universal.md', deps: [], gate: 'f1-portal', dbTarget: null, db: null,
              note: 'Componente de mapa universal (MapLibre, camadas declaráveis). Referência de UX/contrato: uai-ooh-pipeline/docs/design/mapa-alcance-simulacao.html (Leaflet — espelho visual, NÃO código a reusar).' },
  'WEB-01': { lane: 'web', repo: 'portal', doc: F2 + '/05-front-rt/WEB-01-mapa-ao-vivo.md', run: 'WEB-01-mapa-ao-vivo.md', deps: ['WEB-00'], gate: 'f1-portal', xdep: ['RT-01', 'RT-03'], dbTarget: null, db: null,
              note: 'Carros ao vivo + contadores de IMPRESSÕES e ALCANCE (HLL) subindo — selo estimativa SEMPRE visível. Dev server aponta intel LOCAL. Mapa de campanha NUNCA traceja rota (decisão B3 §4b).' },
  'WEB-02': { lane: 'web', repo: 'portal', doc: F2 + '/05-front-rt/WEB-02-verificado-vs-estimado.md', run: 'WEB-02-verificado-vs-estimado.md', deps: ['WEB-00'], gate: 'f1-portal', xdep: ['RT-02'], dbTarget: null, db: null,
              note: 'Painel medido×estimado com selo POR MÉTRICA renderizado (MEDIDO sólido / ESTIMATIVA rotulada).' },
}

const LANE_ORDER = {
  jobs:  ['INFRA-03', 'RECAL-00'],
  cons:  ['CONS-01', 'CONS-02', 'CONS-03', 'CONS-04', 'CONS-05'],
  local: ['LOCAL-01', 'LOCAL-02'],
  rt:    ['RT-01', 'RT-03', 'RT-02'],
  web:   ['WEB-00', 'WEB-01', 'WEB-02'],
}

// ════════════════════════ HELPERS ════════════════════════
const statusOf = (gates, g) => (gates && gates[g] && gates[g].status) || 'UNKNOWN'
const green  = (gates, g) => statusOf(gates, g) === 'GREEN'
const usable = (gates, g) => statusOf(gates, g) !== 'RED'
const localEnv = (handoff) => (handoff && handoff.lanes && handoff.lanes.local && handoff.lanes.local.env) || LOCAL_DEFAULT

// ════════════════════════ HANDOFF I/O ════════════════════════
async function readHandoff() {
  const h = await agent(
    `Leia o handoff do F2 em ${STATE}. Se NÃO existir, devolva o esqueleto vazio { gates:{}, lanes:{jobs:{},cons:{},local:{},e2e:{},rt:{},web:{}}, contract:{}, decisions:[], runDocs:[], pending:[] }. Devolva o JSON parseado como objeto. NÃO escreva nada.`,
    { phase: LANE, label: 'handoff:read', schema: HANDOFF_DOC })
  return h || { gates: {}, lanes: {}, contract: {}, decisions: [], runDocs: [], pending: [] }
}

async function writeHandoff(laneKey, patchObj) {
  await agent(
    `Atualize o handoff do F2 em ${STATE} fazendo MERGE (nunca sobrescreva o arquivo inteiro):
1. Leia o JSON atual de ${STATE} (se não existir, parta do esqueleto { gates:{}, lanes:{jobs:{},cons:{},local:{},e2e:{},rt:{},web:{}}, contract:{}, decisions:[], runDocs:[], pending:[] }).
2. Aplique este patch da lane "${laneKey}": ${JSON.stringify(patchObj)}.
   Regras de merge: substitua só as chaves do patch; em lanes.${laneKey}.tasks faça MERGE POR CHAVE — para CADA task do patch, SOBRESCREVA o valor antigo daquela task com o do patch (o patch é a verdade mais recente); PRESERVE apenas as tasks que o patch NÃO traz; preserve env/redis_contract/endpoints anteriores se o patch não os trouxer; em decisions/runDocs/pending FAÇA APPEND (não duplique); carimbe updatedBy="${laneKey}" e updatedAt com a data real (rode \`date -u +%FT%TZ\`).
3. Escreva o JSON de volta em ${STATE} (indentado, 2 espaços) e mantenha o espelho legível em ${STATE.replace('.json', '.md')} (tabela curta: gates, status por lane, e2e validated?, pendências).
Confirme o que gravou.`,
    { phase: LANE, label: `handoff:write:${laneKey}` })
}

// ════════════════════════ GATES ════════════════════════
function gatePrompt(gate) {
  const common = 'Você verifica um GATE da implementação do F2 (uAI-OOH). Factual: rode as checagens, compare esperado vs real, classifique GREEN/YELLOW/RED. Devolva evidence[] com cada check. **pt-BR.**'
  const mcp = 'Use mcp__postgres-ooh__execute_sql (banco ooh de PROD — carregue via ToolSearch "select:mcp__postgres-ooh__execute_sql"). SOMENTE SELECT.'
  if (gate === 'prereqs-core') return `${common}\n${mcp}\nChecagens:\n- SELECT count(*) FROM core.h3_cell; -- 2615\n- SELECT count(*) FROM core.exposure_cell; -- 188280\n- SELECT count(*) FROM core.od_trip; -- ~1202903\n- SELECT count(*) FROM core.line; -- ~303 · count(*) FROM core.trip_pattern >0 · count(*) FROM core.line_shape >0\nGREEN se todos batem (Onda 1/2 de pé).`
  if (gate === 'poller-live') return `${common}\n${mcp}\nChecagens:\n- SELECT count(*), count(DISTINCT _feed_timestamp) FROM raw.rt__vehicle_position WHERE _feed_timestamp > now() - interval '1 hour';\nGREEN se >0 ciclos na última hora (poller prod streaming). RED se 0 (investigar poller antes de seguir).`
  if (gate === 'raw-history') return `${common}\n${mcp}\nChecagens:\n- SELECT count(DISTINCT (_feed_timestamp AT TIME ZONE 'America/Sao_Paulo')::date) FROM raw.rt__vehicle_position;\nGREEN se ≥2 dias (material pro seed/replay do LOCAL-01/02). YELLOW se 1.`
  if (gate === 'medido-volume') return `${common}\n${mcp}\nChecagens (válidas SÓ pós-deploy em prod — antes disso RED é o esperado):\n- SELECT to_regclass('medido.viagem') IS NOT NULL;\n- SELECT count(DISTINCT service_date) FROM medido.viagem; -- ≥3 ⇒ GREEN\n- SELECT count(DISTINCT vehicle_code) FROM medido.viagem WHERE vehicle_code IN ('11198','20736','30835','40705','40806');\nTabela inexistente ⇒ RED com note "esperado antes do ship do consolidador".`
  if (gate === 'repos') return `${common}\nChecagens (filesystem, Bash, read-only) — conte arquivos reais (fora de .git/.idea/CLAUDE.md):\n- ${REPO.consolidator} (esperado AUSENTE ⇒ CONS-01 cria)\n- ${REPO.template} (existe? tem pom.xml?)\n- ${REPO.pipeline}, ${REPO.poller}, ${REPO.infra}, ${REPO.intel}, ${REPO.portal} (existem)\nstatus YELLOW se consolidator ausente e o resto ok (estado esperado).`
  if (gate === 'local-env') return `${common}\nChecagens (Bash):\n- ls ${REPO.infra}/docker-compose.local.yml (existe?)\n- docker compose -f ${REPO.infra}/docker-compose.local.yml ps --format json 2>/dev/null (serviços up?)\n- psql "${LOCAL_DEFAULT.pg}" -c "SELECT 1" 2>/dev/null\nRED antes do LOCAL-01 é o ESPERADO (anote). GREEN = compose up + psql local responde.`
  if (gate === 'f1-intel') return `${common}\nLeia ${F1STATE} (Bash cat; se não existir ⇒ RED note "F1 sem handoff"). GREEN se lanes.back.tasks tem EP2-03, EP2-07 e EP2-08 em done/partial (intel F1 servindo catálogo/auth/geo). YELLOW se EP2-03 done mas 07/08 não. RED caso contrário ⇒ lane rt fica skip.`
  if (gate === 'f1-portal') return `${common}\nLeia ${F1STATE} (Bash cat; se não existir ⇒ RED). GREEN se lanes.shell.tasks.EP3-01 E lanes.module.tasks EP4-01/EP4-03/EP4-04 em done/partial (shell+módulo+mapa do F1 existem). RED ⇒ lane web fica skip.`
  return common
}

async function runGate() {
  phase('gate')
  const keys = ['prereqs-core', 'poller-live', 'raw-history', 'medido-volume', 'repos', 'local-env', 'f1-intel', 'f1-portal']
  const results = await parallel(keys.map(k => () => agent(gatePrompt(k), { phase: 'gate', label: `gate:${k}`, schema: GATE_RESULT })))
  const gates = {}
  results.filter(Boolean).forEach(r => { gates[r.gate || ''] = r })
  keys.forEach(k => { if (!gates[k]) gates[k] = { gate: k, status: 'UNKNOWN', evidence: [], note: 'agente não retornou' } })
  await writeHandoff('gate', { gates })
  const summary = keys.map(k => `${k}=${gates[k].status}`).join(' · ')
  log(`gate-check: ${summary}`)
  return { status: 'done', checkpoint: 'after-gate', gates, summary,
           next: 'Dispare jobs ∥ cons (cons exige confirm:["create-consolidator"]). Depois local → e2e. Ship só com e2e=validated.' }
}

// ════════════════════════ PIPELINE DE TASK ════════════════════════
function repoOf(t) { return REPO[t.repo] }
function buildCmd(t, kind) { const b = BUILD[t.repo]; return b ? b[kind].replace(/REPO/g, repoOf(t)) : '(sem build)' }

function implementPrompt(id, t, feedback, attempt, handoff) {
  const env = localEnv(handoff)
  return `Você é o IMPLEMENTADOR (perfil ~/.claude/skills/coder/SKILL.md, execução INLINE — responda no schema). **pt-BR.**
REPO-ALVO (opere SOMENTE aqui; path absoluto): ${repoOf(t)}
Use \`git -C ${repoOf(t)}\` em TODO comando git. Crie/use a branch feat/ooh-${id.toLowerCase()}. NÃO toque outro repositório nem o repo de PM.
TASK (leia o arquivo INTEIRO antes): ${t.doc}
Leia também a techspec da lane (mesma pasta, techspec.md) — as Decisões NÃO são reabríveis.
${t.note ? 'NOTA CRÍTICA: ' + t.note : ''}
AMBIENTE LOCAL (quando a task interage com serviços): pg=${env.pg} · kafka=${env.kafka} · redis=${env.redis}. NUNCA aponte pra VPS/prod.
${feedback ? '\n🔁 RETRY #' + attempt + ' — corrija APENAS o que o estágio "' + feedback.from + '" apontou:\n' + JSON.stringify(feedback).slice(0, 1800) : ''}
Passos: (1) leia task+referências; (2) observe os padrões do repo; (3) implemente o checklist item a item; (4) crie os testes pedidos; (5) valide build: ${buildCmd(t, 'build')}. NÃO rode a suíte completa (estágio Test).
Responda no schema IMPLEMENT_OUTPUT.`
}

function reviewPrompt(id, t, impl, attempt) {
  return `Você é o REVIEWER senior (perfil ~/.claude/skills/reviewer/SKILL.md, INLINE — responda no schema). **pt-BR.**
TASK original: ${t.doc} (+ techspec.md da lane — Decisões fechadas).
Implementador alega: ${JSON.stringify(impl).slice(0, 2000)}
Diff real: \`git -C ${repoOf(t)} diff\` + \`git -C ${repoOf(t)} diff --staged\` + arquivos novos da branch feat/ooh-${id.toLowerCase()}.
Revise: conformidade (checklist completo? escopo? decisões da techspec respeitadas?), qualidade (padrões do repo, erro tratado, sem código morto), testes presentes.
${id === 'CONS-03' ? '⚠️ CHEQUE A ARMADILHA: join de linha por core.line.short_name (route_id do feed É short_name) — reprove se joinar por gtfs__routes.route_id.' : ''}
${id.startsWith('RT-') || id.startsWith('WEB-') ? '⚠️ CHEQUE O SELO ADR-058: reach/impressões/acumulado NUNCA podem aparecer como "medido" — selo por métrica obrigatório.' : ''}
Tentativa #${attempt}: se ≥3, pragmático — foque critical/major; em dúvida com task cumprida, aprove.
Responda no schema REVIEW_VERDICT.`
}

function testPrompt(id, t) {
  return `Você é o TESTER (perfil ~/.claude/skills/tester/SKILL.md, INLINE — responda no schema). **pt-BR.**
TASK (seção Verificação/Critério de pronto): ${t.doc}
REPO: ${repoOf(t)}. Verifique que os testes pedidos existem (Glob) e rode a suíte: ${buildCmd(t, 'test')}.
Se falhar por ambiente, tente o equivalente do repo e reporte qual usou. Responda no schema TEST_RESULT. NÃO corrija código.`
}

function validatePrompt(id, t, handoff) {
  const env = localEnv(handoff)
  return `Você é o validador do AMBIENTE LOCAL (local-first — NUNCA use o MCP postgres-ooh, que é PROD). **pt-BR.**
Ferramentas: Bash com psql ("${env.pg}"), redis-cli (-u redis://${env.redis} ou -p porta), kafka via docker exec no broker local. SOMENTE leitura no que não for o objeto da validação.
Critérios da task ${id} a confirmar: ${t.db}
Para cada critério rode o comando, registre expected vs actual e pass. Responda no schema DB_VALIDATION (ok=true só se TODOS passam).`
}

function refutePrompt(id, t, ev, handoff) {
  const env = localEnv(handoff)
  return `Você é o agente CÉTICO (camada adversarial). A task ${id} foi marcada "done". TENTE DERRUBAR com evidência concreta. **pt-BR.**
Vetores:
1) Critério de pronto não batido: releia ${t.doc} e verifique no repo ${repoOf(t)}${t.dbTarget === 'local' ? ' e no ambiente LOCAL (psql ' + env.pg + ' / redis-cli ' + env.redis + ')' : ''} que os critérios estão cumpridos. ⚠️ IGNORE o item "produzir docs/epicos/runs/${t.run}" — o run doc é escrito pelo ORQUESTRADOR DEPOIS; ausência agora NÃO é refutação.
2) Contrato divergente: se a task expõe/consome contrato (Redis keys, endpoint, payload), confronte implementado vs especificado no card/techspec.
3) Evidência errada: os checks reportados (${JSON.stringify((ev.dbv && ev.dbv.checks) || []).slice(0, 900)}) batem com a realidade AGORA?
${t.lane === 'cons' ? '4) ESCOPO LOCAL-FIRST: validação contra banco/Kafka/Redis REAIS DE PROD é IMPOSSÍVEL e NÃO-OBJETIVO aqui (serviços da VPS são 127.0.0.1; o E2E real é a parte e2e). NÃO refute por "não validou em prod" — refute apenas por código/ITs/contrato errados.' : ''}
Sem evidência contrária ⇒ refuted=false (não invente). Responda no schema REFUTE_VERDICT.`
}

async function emitRunDoc(id, t, ev, done) {
  await agent(
    `Escreva o run doc da task ${id} em ${RUNS}/${t.run} (repo de PM — pode escrever aqui). **pt-BR.** Formato dos runs existentes (ver ${RUNS}/F2-infra-poller-golive.md): cabeçalho repo/branch, tabela counts reais vs esperado (✅/⚠️), decisões/desvios, seção "Adversarial" (o que o cético tentou e por quê não derrubou — ou derrubou).
Carimbe a data real (\`date +%F\`). Status final: ${done ? 'DONE' : 'FAILED/BLOQUEADA'}.
Evidência (resuma): implement=${JSON.stringify(ev.impl).slice(0, 700)}; review=${JSON.stringify(ev.review).slice(0, 500)}; test=${JSON.stringify(ev.test).slice(0, 500)}; validate=${JSON.stringify(ev.dbv).slice(0, 700)}; refute=${JSON.stringify(ev.refute).slice(0, 700)}.
Confirme o path gravado.`,
    { phase: LANE, label: `run-doc:${id}` })
}

async function runTask(id, gates, handoff) {
  const t = TASKS[id]
  phase(t.lane)
  let feedback = null, impl = null, review = null, test = null, dbv = { ok: true, checks: [] }, refute = null
  let done = false
  for (let attempt = 1; attempt <= RETRIES; attempt++) {
    impl = await agent(implementPrompt(id, t, feedback, attempt, handoff), { phase: t.lane, label: `impl:${id}#${attempt}`, schema: IMPLEMENT_OUTPUT })
    if (!impl || impl.status === 'failed') { feedback = { from: 'implement', detail: (impl && impl.observations) || 'sem retorno' }; continue }

    review = await agent(reviewPrompt(id, t, impl, attempt), { phase: t.lane, label: `review:${id}#${attempt}`, schema: REVIEW_VERDICT })
    if (!review || review.status === 'rejected') { feedback = { from: 'review', problems: (review && review.problems) || [], verdict: review && review.verdict }; continue }

    test = await agent(testPrompt(id, t), { phase: t.lane, label: `test:${id}#${attempt}`, schema: TEST_RESULT })
    if (test && test.status === 'failed') { feedback = { from: 'test', failed: test.failed_tests || [] }; continue }

    dbv = t.db ? await agent(validatePrompt(id, t, handoff), { phase: t.lane, label: `validate:${id}#${attempt}`, schema: DB_VALIDATION }) : { ok: true, checks: [] }
    if (t.db && (!dbv || !dbv.ok)) { feedback = { from: 'validate', checks: (dbv && dbv.checks) || [] }; continue }

    refute = await agent(refutePrompt(id, t, { impl, review, test, dbv }, handoff), { phase: t.lane, label: `refute:${id}#${attempt}`, schema: REFUTE_VERDICT })
    if (refute && refute.refuted) { feedback = { from: 'refute', attacks: refute.attacks, reason: refute.reason }; continue }

    done = true
    break
  }
  await emitRunDoc(id, t, { impl, review, test, dbv, refute }, done)
  return { task: id, lane: t.lane, status: done ? 'done' : 'failed', runDoc: `${RUNS}/${t.run}`,
           observations: (impl && impl.observations) || '', lastFeedback: done ? null : feedback }
}

async function runLane(laneKey, gates, handoff) {
  phase(laneKey)
  const order = (LANE_ORDER[laneKey] || []).filter(id => !ONLY || ONLY.includes(id))
  const results = []
  const completed = {}
  const doneSet = (h) => h && h.lanes ? Object.values(h.lanes).flatMap(l => Object.keys((l && l.tasks) || {}).filter(k => ['done', 'partial'].includes(l.tasks[k]))) : []
  const priorDone = doneSet(handoff)

  for (const id of order) {
    const t = TASKS[id]
    if (t.confirm && !CONFIRM.includes(t.confirm)) {
      results.push({ task: id, status: 'paused', need_confirm: t.confirm })
      log(`PAUSA: ${id} exige confirm:["${t.confirm}"] (irreversível). Re-dispare {lane:'${laneKey}', confirm:['${t.confirm}']}.`)
      break
    }
    if (t.gate && !usable(gates, t.gate)) {
      log(`${id} PULADA: gate ${t.gate}=${statusOf(gates, t.gate)}.`)
      results.push({ task: id, status: 'skipped', reason: `gate ${t.gate}=${statusOf(gates, t.gate)}` })
      continue
    }
    const missingDeps = (t.deps || []).filter(d => completed[d] !== 'done' && !priorDone.includes(d))
    if (missingDeps.length) { results.push({ task: id, status: 'blocked', missingDeps }); log(`${id} BLOQUEADA: deps ${missingDeps.join(',')}.`); continue }
    const missingX = (t.xdep || []).filter(d => !priorDone.includes(d))
    if (missingX.length) { results.push({ task: id, status: 'blocked', missingX }); log(`${id} BLOQUEADA: cross-lane ${missingX.join(',')} não prontas no handoff.`); continue }
    const r = await runTask(id, gates, handoff)
    completed[id] = r.status
    results.push(r)
  }

  const tasksMap = {}
  results.forEach(r => { tasksMap[r.task] = r.status })
  const lanePatch = { lanes: { [laneKey]: { status: results.length && results.every(r => r.status === 'done') ? 'complete' : 'partial', tasks: tasksMap } },
                      runDocs: results.filter(r => r.runDoc && r.status === 'done').map(r => r.runDoc),
                      pending: results.filter(r => ['skipped', 'blocked', 'failed', 'paused'].includes(r.status)).map(r => ({ task: r.task, status: r.status, reason: r.reason || r.need_confirm || r.missingDeps || r.missingX })) }

  // extras por lane (contexto compartilhado)
  if (laneKey === 'cons') {
    const c5 = results.find(r => r.task === 'CONS-05' && r.status === 'done')
    if (c5) lanePatch.lanes.cons.redis_contract = 'live:vehicle:{code} HASH(lat,lon,bearing,lineId,tripId,currentStopSequence,completudeParcial,impressoesParciais,alcanceParcial,hexesVisitados,ts) · live:line:{lineId} SET · live:reach:trip|day:{code} HLL · hll:hex:{h3}:{tipoDia}:{faixa} HLL (read-only) — detalhes: ' + (c5.observations || '').slice(0, 400)
  }
  if (laneKey === 'local') {
    const l1 = results.find(r => r.task === 'LOCAL-01' && r.status === 'done')
    if (l1) lanePatch.lanes.local.env = LOCAL_DEFAULT // LOCAL-01 reporta envs reais nas observações; agente do writeHandoff preserva/ajusta se divergirem
  }
  if (laneKey === 'rt') lanePatch.lanes.rt.endpoints = ['GET /api/realtime/positions?line=', 'GET /api/lines/{id}/verified']

  await writeHandoff(laneKey, lanePatch)
  log(`lane ${laneKey}: ${results.map(r => r.task + '=' + r.status).join(' · ')}`)
  return { lane: laneKey, results, checkpoint: `after-${laneKey}` }
}

// ════════════════════════ E2E (coração do local-first) ════════════════════════
async function runE2E(gates, handoff) {
  phase('e2e')
  const env = localEnv(handoff)
  const pre = handoff.lanes || {}
  const consReady  = pre.cons  && pre.cons.tasks  && ['CONS-01', 'CONS-02', 'CONS-03', 'CONS-04', 'CONS-05'].every(k => pre.cons.tasks[k] === 'done')
  const localReady = pre.local && pre.local.tasks && pre.local.tasks['LOCAL-01'] === 'done' && pre.local.tasks['LOCAL-02'] === 'done'
  if (!consReady || !localReady) {
    log(`e2e bloqueada: cons=${!!consReady} local=${!!localReady}. Rode as lanes cons e local antes.`)
    return { status: 'blocked', reason: 'e2e precisa de cons (CONS-01..05 done) + local (LOCAL-01/02 done) no handoff.' }
  }

  // 1. sobe consolidador local + 2. replay
  const boot = await agent(
    `Você opera o E2E LOCAL do F2. **pt-BR.** Ambiente: pg=${env.pg} · kafka=${env.kafka} · redis=${env.redis} (compose local do ${REPO.infra}/docker-compose.local.yml — suba com docker compose -f ... up -d se não estiver de pé).
1. Suba o CONSOLIDADOR local (${REPO.consolidator}): \`mvn spring-boot:run\` em background (nohup, log em /tmp/cons-e2e.log) com envs do ambiente local + TRIP_TIMEOUT_S reduzido (ex. 30) pra acompanhar replay acelerado. Confirme que conectou (log: consumer ativo, Flyway aplicou medido).
2. Rode o REPLAY (${REPO.poller}): \`python -m poller replay --speed 60\` de ≥1 dia do raw local (escolha o service_date mais completo). Aguarde terminar.
3. Aguarde ~2min (timeouts fecharem) e reporte: linhas em medido.viagem / viagem_hex / parada_velocidade (psql), nº de eventos ooh.trip.completed no tópico, erros no log do consolidador.
Responda no schema DB_VALIDATION (checks = os counts; ok=true se medido.viagem>0 e fixture parcial presente e sem erro fatal no log).`,
    { phase: 'e2e', label: 'e2e:boot+replay', schema: DB_VALIDATION })
  if (!boot || !boot.ok) {
    await writeHandoff('e2e', { lanes: { e2e: { status: 'failed', boot } }, pending: [{ task: 'E2E', status: 'failed', reason: 'boot/replay não produziu medido válido' }] })
    return { status: 'failed', checkpoint: 'e2e-boot', boot }
  }
  log('e2e: consolidador+replay OK — medido populado no local. Disparando RECALs manualmente…')

  // 3. disparos manuais dos RECALs (pipeline de task completo, validação local)
  const r1 = await runTask('RECAL-01', gates, handoff)
  if (r1.status !== 'done') {
    await writeHandoff('e2e', { lanes: { e2e: { status: 'failed', tasks: { 'RECAL-01': r1.status } } }, pending: [{ task: 'RECAL-01', status: r1.status, reason: 'recompute local falhou' }] })
    return { status: 'failed', checkpoint: 'e2e-recal-01', r1 }
  }
  const r2 = await runTask('RECAL-02', gates, handoff)
  const validated = r2.status === 'done'
  await writeHandoff('e2e', { lanes: { e2e: { status: validated ? 'validated' : 'failed', tasks: { 'RECAL-01': r1.status, 'RECAL-02': r2.status }, boot_checks: (boot.checks || []).length } },
                              decisions: validated ? ['E2E local VALIDADO em ' + 'data real no state' + ' — ship desbloqueado'] : [],
                              pending: validated ? [] : [{ task: 'RECAL-02', status: r2.status, reason: 'validação/reconciliação local falhou' }] })
  log(`e2e: ${validated ? '✅ VALIDATED — ship desbloqueado (confirm push+deploy)' : '❌ falhou no RECAL-02 — ver run doc'}`)
  return { status: validated ? 'validated' : 'failed', checkpoint: 'after-e2e', recal01: r1.status, recal02: r2.status }
}

// ════════════════════════ CONTRACT ════════════════════════
async function runContract(gates, handoff) {
  phase('contract')
  const rc = (handoff.lanes && handoff.lanes.cons && handoff.lanes.cons.redis_contract) || '(contrato não publicado no handoff — derive do código do consolidador)'
  const verdict = await agent(
    `Você é o agente de CONTRATO do F2 (2 frentes). **pt-BR.**
FRENTE 1 — contrato Redis CONS-05 ↔ RT-01: contrato esperado: ${rc}. Compare o que o CONSOLIDADOR escreve (${REPO.consolidator}: adapter Redis, chaves/campos/TTL) com o que o INTEL lê (${REPO.intel}: LiveStateReader/adapter Redis). Divergência de chave ⇒ kind=redis-key; de campo ⇒ redis-field.
FRENTE 2 — REST RT ↔ front: endpoints do intel (GET /api/realtime/positions?line=, GET /api/lines/{id}/verified — derive dos @RestController em ${REPO.intel}) vs hooks/types do portal (${REPO.portal}/src/modules/ooh: usePositions/useVerified, LivePosition.acumulado.alcanceParcial, Selo por métrica). Confira método/path/query/shape — em especial: alcanceParcial presente nos DOIS lados; selo NUNCA permite reach como MEDIDO.
Reporte matched[] e mismatches[] com severidade. verdict=divergent se houver QUALQUER critical. Responda no schema CONTRACT_VERDICT.`,
    { phase: 'contract', label: 'contract:redis+rest', schema: CONTRACT_VERDICT })
  const criticals = ((verdict && verdict.mismatches) || []).filter(m => m.severity === 'critical')
  await writeHandoff('contract', { contract: verdict || { verdict: 'unknown', matched: [], mismatches: [] },
                                   pending: criticals.map(m => ({ task: m.hook || m.endpoint || m.kind, status: 'contract-divergent', reason: `${m.kind}: ${m.expected} vs ${m.actual}` })) })
  log(`contract: ${verdict ? verdict.verdict : 'sem retorno'} · ${criticals.length} críticos`)
  return { checkpoint: 'after-contract', contract: verdict, reopen: criticals.map(m => m.hook || m.endpoint) }
}

// ════════════════════════ SHIP (prod — só com e2e=validated) ════════════════════════
async function runShip(gates, handoff) {
  phase('ship')
  const e2eOk = handoff.lanes && handoff.lanes.e2e && handoff.lanes.e2e.status === 'validated'
  if (!e2eOk) {
    log('SHIP BLOQUEADO: e2e ≠ validated no handoff (local-first é inegociável). Rode {lane:"e2e"} até validar.')
    return { status: 'blocked', reason: 'ship exige lanes.e2e.status=validated no handoff (E2E local passou).' }
  }
  if (!CONFIRM.includes('push') || !CONFIRM.includes('deploy')) {
    log('PAUSA: ship exige confirm:["push","deploy"].')
    return { status: 'paused', checkpoint: 'need-ship-confirm', need_confirm: ['push', 'deploy'],
             willPush: [REPO.consolidator, REPO.pipeline, REPO.poller, REPO.intel, REPO.portal].filter(Boolean),
             willDeploy: ['GHCR ghcr.io/mddinizbh/uai-ooh-trip-consolidator', 'uai-infra: INFRA-04 (compose consolidador + crons + OOH_RT_RAW_RETENTION_DAYS=14)'] }
  }
  const out = await agent(
    `Você é o agente de SHIP do F2 (E2E local já VALIDADO). **pt-BR.** NUNCA toque nginx/VPS direto — só commit/push (deploy é via Actions).
1. Para cada repo com branch feat/ooh-* pronta e run doc done (${REPO.consolidator}, ${REPO.pipeline}, ${REPO.poller}, ${REPO.intel}, ${REPO.portal}): \`git -C <repo> push\` + \`gh pr create\` (base main).
2. Confirme que o CI do consolidador publica a imagem GHCR (ghcr.io/mddinizbh/uai-ooh-trip-consolidator) — o template já traz o workflow; se faltar, é pendência, não improviso.
3. Execute a INFRA-04 no ${REPO.infra} (leia ${F2}/01-infra/INFRA-04-automacao-f2.md): serviço do consolidador no docker-compose.yml (GHCR, envs/secrets, depends_on postgres-ooh+kafka-init+redis, restart, healthcheck, JAVA_TOOL_OPTIONS=-Xmx384m), crons (rt-raw-archive 0 4 * * * · reach --fonte=medida 30 4 * * * · RECAL-00 encadeado no rebuild OD), OOH_RT_RAW_RETENTION_DAYS=14. Commit + push (branch + PR).
Reporte PRs abertos, imagens, pendências.`,
    { phase: 'ship', label: 'ship:prod' })
  await writeHandoff('ship', { decisions: ['ship executado: ' + String(out).slice(0, 300)] })
  return { checkpoint: 'after-ship', out, next: 'Após merge+deploy via Actions: aguardar D+1 e rodar {lane:"verify-prod"}.' }
}

// ════════════════════════ VERIFY-PROD (D+1) ════════════════════════
async function runVerifyProd() {
  phase('verify-prod')
  const v = await agent(
    `Você verifica o GO-LIVE do F2 em PROD (D+1 do deploy). **pt-BR.** Use mcp__postgres-ooh__execute_sql (PROD, SOMENTE SELECT; ToolSearch se necessário).
Checks:
- medido.viagem: count(*) > 0 e count(DISTINCT service_date) crescendo; fixture (11198,20736,30835,40705,40806) aparecendo.
- medido.viagem_hex: linhas do último service_date > 0.
- raw: partições > OOH_RT_RAW_HOT_DAYS dropadas (archive rodou)? pg_class LIKE 'rt__vehicle_position_p%'.
- face_reach fonte=medida: atualizado com max(service_date) recente (recompute D-1 rodou).
Responda no schema DB_VALIDATION. Depois escreva o run doc ${RUNS}/F2-golive-verify-prod.md (formato dos runs; data real via date +%F) com os counts e o veredito.`,
    { phase: 'verify-prod', label: 'verify-prod', schema: DB_VALIDATION })
  await writeHandoff('verify-prod', { decisions: ['verify-prod: ' + ((v && v.ok) ? 'OK' : 'FALHOU — ver checks')], runDocs: [RUNS + '/F2-golive-verify-prod.md'] })
  return { checkpoint: 'after-verify-prod', ok: v && v.ok, checks: (v && v.checks) || [] }
}

// ════════════════════════ DISPATCH ════════════════════════
log(`F2 orchestration (LOCAL-FIRST) · lane=${LANE} · confirm=[${CONFIRM.join(',')}] · retries=${RETRIES}${ONLY ? ' · only=[' + ONLY.join(',') + ']' : ''}`)

if (LANE === 'gate') return await runGate()

const handoff = await readHandoff()
const gates = (handoff && handoff.gates) || {}
if (!Object.keys(gates).length) {
  log('handoff sem gates — rode {lane:"gate"} primeiro.')
  return { status: 'error', reason: 'gates ausentes no handoff; rode a parte gate antes.' }
}

if (LANE === 'e2e')         return await runE2E(gates, handoff)
if (LANE === 'contract')    return await runContract(gates, handoff)
if (LANE === 'ship')        return await runShip(gates, handoff)
if (LANE === 'verify-prod') return await runVerifyProd()
if (['jobs', 'cons', 'local', 'rt', 'web'].includes(LANE)) return await runLane(LANE, gates, handoff)

return { status: 'error', reason: `lane desconhecida: ${LANE}. Use gate|jobs|cons|local|e2e|rt|web|contract|ship|verify-prod.` }
