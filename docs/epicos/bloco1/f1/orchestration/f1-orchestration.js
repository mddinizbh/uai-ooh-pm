export const meta = {
  name: 'f1-orchestration',
  description: 'Orquestra a execução do F1 (Bloco 1 uAI-OOH) em partes por LANE que dividem contexto via handoff explícito (state.json). Cada task roda como pipeline implement→review→test→db-validate→refute, com gate-check no banco ooh, contract-check back↔front e checkpoints antes de ações irreversíveis.',
  whenToUse: 'Executar/retomar o F1 do uAI-OOH. Rode UMA parte por invocação via args.lane (gate→data∥back∥shell→module→contract→ship). Toda parte lê o handoff no início e grava o seu no fim. Ações irreversíveis exigem args.confirm.',
  phases: [
    { title: 'gate',     detail: 'read-only paralelo: counts no ooh (T8b/EP1-02/dataset_version) + fs (repos) + uai-auth ⇒ mapa GREEN/RED no handoff' },
    { title: 'data',     detail: 'uai-ooh-pipeline: fecha T8b + roda EP1-02 (serving.line_corridor/line_poi), valida no ooh' },
    { title: 'back',     detail: 'IRREVERSÍVEL: cria uai-ooh-intel do template + EP2-01..09; publica OpenAPI/endpoints no handoff' },
    { title: 'shell',    detail: 'IRREVERSÍVEL: forka spark→portal + EP3-01..03 (EP3-02 stub se uai-auth RED)' },
    { title: 'module',   detail: 'uai-portal: EP4-01..09 consumindo os endpoints do intel; publica hooks no handoff' },
    { title: 'contract', detail: 'barrier: OpenAPI do intel ↔ hooks do intelClient; divergências viram pending' },
    { title: 'ship',     detail: 'IRREVERSÍVEL: push/PR, imagem GHCR, entrada uai-infra; nunca toca nginx/VPS' },
  ],
}

// ── parâmetros (sobrescrevíveis via args) ───────────────────────────────────
// args pode chegar como OBJETO ou como STRING JSON (depende do encoding do harness) — blindar.
let _A = args
if (typeof _A === 'string') { try { _A = JSON.parse(_A) } catch (e) { _A = {} } }
if (!_A || typeof _A !== 'object') _A = {}
const LANE    = _A.lane || 'gate'                                // gate|data|back|shell|module|contract|ship
const CONFIRM = Array.isArray(_A.confirm) ? _A.confirm : (typeof _A.confirm === 'string' ? _A.confirm.split(',').map(s => s.trim()).filter(Boolean) : [])
const RETRIES = _A.retries || 2                                  // tentativas do loop implement→…→refute por task
const ONLY    = (Array.isArray(_A.only) && _A.only.length) ? _A.only : null  // subset de tasks p/ re-run

// ── paths ────────────────────────────────────────────────────────────────────
const PM    = '/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pm'
const F1    = PM + '/docs/epicos/bloco1/f1'
const RUNS  = PM + '/docs/epicos/runs'
const STATE = PM + '/docs/epicos/bloco1/f1/orchestration/state.json'
const PRD   = PM + '/docs/prd/2026-06-05-ooh-intel-front-f1.md'
const REPO  = {
  pipeline: '/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pipeline',
  intel:    '/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-intel',
  portal:   '/Users/marleydiniz/IdeaProjects/personal/uai/uai-portal',
  spark:    '/Users/marleydiniz/IdeaProjects/uai-spark',
  template: '/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-service-template',
  auth:     '/Users/marleydiniz/IdeaProjects/personal/uai/uai-auth',
}
const BUILD = {
  pipeline: { build: '(cd REPO && python -m py_compile $(git ls-files "*.py") || true)', test: '(cd REPO && python -m pytest -q || pytest -q)' },
  intel:    { build: '(cd REPO && ./mvnw -q -DskipTests compile 2>/dev/null || mvn -q -DskipTests compile)', test: '(cd REPO && ./mvnw -q verify 2>/dev/null || mvn -q verify)' },
  portal:   { build: '(cd REPO && (bun run build || npm run build))', test: '(cd REPO && (bun run test --run || npx vitest run || npm test))' },
}

// ════════════════════════ SCHEMAS ════════════════════════
const GATE_RESULT = {
  type: 'object',
  properties: {
    gate: { type: 'string', description: 'T8b|EP1-02|dataset_version|repos|uai-auth' },
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
    openapi_source: { type: 'string', description: 'runtime|build-json|derived-from-controllers' },
    matched: { type: 'array', items: { type: 'object', properties: { hook: { type: 'string' }, operation: { type: 'string' } } } },
    mismatches: { type: 'array', items: { type: 'object', properties: {
      hook: { type: 'string' }, endpoint: { type: 'string' },
      kind: { type: 'string', enum: ['missing-endpoint', 'missing-hook', 'method', 'path', 'query-param', 'request-shape', 'response-shape'] },
      expected: { type: 'string' }, actual: { type: 'string' }, severity: { type: 'string', enum: ['critical', 'major', 'minor'] },
    }, required: ['kind', 'severity'] } },
  },
  required: ['verdict', 'matched', 'mismatches'],
}

// retorno solto do handoff (objeto livre lido do disco por um agente)
const HANDOFF_DOC = { type: 'object', properties: { gates: { type: 'object' }, lanes: { type: 'object' }, contract: { type: 'object' }, decisions: { type: 'array' }, runDocs: { type: 'array' }, pending: { type: 'array' } } }

// ════════════════════════ TASK REGISTRY (23 tasks) ════════════════════════
// repo: chave de REPO/BUILD · doc: arquivo da task · run: arquivo do run doc · deps: ids intra-lane · gate: chave de gate cross-lane
// db: descrição+SQL pro estágio db-validate (null = sem validação no banco) · confirm: token irreversível exigido · auth: 'stub-aware'
const TASKS = {
  // ── DADOS (uai-ooh-pipeline) ──
  'T8b':    { lane: 'data', repo: 'pipeline', doc: F1 + '/01-pipeline-dados/T8b.md', run: 'T8b-regionalizacao.md', deps: [], gate: 'T8b', validateOnly: true,
              db: 'core.regional=9; SELECT count(DISTINCT line_id) FROM core.line_area =303; core.stop nm_bairro/regional 0 nulos onde geom existe; core.census_sector.regional 0 nulos (5166).',
              note: 'Código já mergeado (PR#8/#9). Se gate T8b GREEN: NÃO reimplementar — só validar no banco e fechar o run doc (fast-path validateOnly).' },
  'EP1-02': { lane: 'data', repo: 'pipeline', doc: F1 + '/01-pipeline-dados/EP1-02-camadas-corredor-serving.md', run: 'EP1-02-camadas-corredor.md', deps: [], gate: null,
              db: 'serving.line_corridor e serving.line_poi existem e populados p/ 303 linhas; geom_geojson válido (GeoJSON SRID 4326); counts de POI por categoria batem com line_metrics.n_poi_*.' },

  // ── BACK (uai-ooh-intel) ──
  'EP2-01': { lane: 'back', repo: 'intel', doc: F1 + '/02-intel-backend/EP2-01-scaffold.md', run: 'EP2-01-scaffold.md', deps: [], gate: null, confirm: 'create-intel',
              db: '/actuator/health verde com indicator de ACTIVE version; serving.dataset_version WHERE status=ACTIVE >=1.',
              note: 'CRIA o repo uai-ooh-intel a partir do template (repo CERTO, não uai-ooh). Regerar LIMPO. Porta 8085, pacote com.uai.ooh.intel, datasource ooh_intel_ro.' },
  'EP2-02': { lane: 'back', repo: 'intel', doc: F1 + '/02-intel-backend/EP2-02-dominio-portas.md', run: 'EP2-02-dominio-portas.md', deps: ['EP2-01'], gate: null, db: null },
  'EP2-03': { lane: 'back', repo: 'intel', doc: F1 + '/02-intel-backend/EP2-03-catalogo-ficha.md', run: 'EP2-03-catalogo-ficha.md', deps: ['EP2-02'], gate: null,
              db: 'GET /api/lines devolve 303 linhas com score; /{id} devolve shapes GeoJSON + pontos; /metrics completo; 404 em id inexistente.' },
  'EP2-04': { lane: 'back', repo: 'intel', doc: F1 + '/02-intel-backend/EP2-04-ranking.md', run: 'EP2-04-ranking.md', deps: ['EP2-03'], gate: null,
              db: 'GET /ranking sem params = ordem do score base; weights mudam ordem; publicoAlvo=AB sobe %AB; score 0-100 re-normalizado.' },
  'EP2-05': { lane: 'back', repo: 'intel', doc: F1 + '/02-intel-backend/EP2-05-agregacao.md', run: 'EP2-05-agregacao.md', deps: ['EP2-03'], gate: null,
              db: 'POST /aggregate com N lineIds devolve combinado (Σ impressões/pax, %AB ponderado) + per-linha; honestidade marcada no payload.' },
  'EP2-06': { lane: 'back', repo: 'intel', doc: F1 + '/02-intel-backend/EP2-06-regioes-filtros.md', run: 'EP2-06-regioes-filtros.md', deps: ['EP2-03'], gate: 'T8b',
              db: 'GET /api/regions devolve 9 regionais + bairros; /lines e /ranking filtram por regiao/bairro via serving.line_area.' },
  'EP2-07': { lane: 'back', repo: 'intel', doc: F1 + '/02-intel-backend/EP2-07-auth.md', run: 'EP2-07-auth.md', deps: ['EP2-01'], gate: null, auth: true, db: null,
              note: 'Auth é comportamento HTTP — NÃO materializa nada no ooh (sem db-validate). Validação = ITs (401 sem/com token inválido) + testes do filtro. uai-auth RED ⇒ stub (mode=stub), real deferido ⇒ status PARTIAL, não FAILED.' },
  'EP2-08': { lane: 'back', repo: 'intel', doc: F1 + '/02-intel-backend/EP2-08-camadas-geojson.md', run: 'EP2-08-camadas-geojson.md', deps: ['EP2-03'], gate: 'EP1-02',
              db: 'GET /api/lines/{id}/geo devolve FeatureCollection válido (trajeto+corredor+pontos+POIs por categoria) p/ version ACTIVE; sem ST_* runtime.' },
  'EP2-09': { lane: 'back', repo: 'intel', doc: F1 + '/02-intel-backend/EP2-09-testes-deploy.md', run: 'EP2-09-testes-deploy.md', deps: ['EP2-03', 'EP2-04', 'EP2-05', 'EP2-06', 'EP2-07', 'EP2-08'], gate: null, db: null,
              note: 'ESCOPO nesta lane: SOMENTE ITs Testcontainers postgres:16 (seed próprio, não toca o ooh real) + mvn verify + JaCoCo>=80% + Dockerfile. Validação = build/test, sem db-validate no ooh. NÃO faça git push, NÃO publique no GHCR, NÃO edite uai-infra — a PUBLICAÇÃO é a lane ship (confirm deploy).' },

  // ── SHELL (uai-portal) ──
  'EP3-01': { lane: 'shell', repo: 'portal', doc: F1 + '/03-front/shell/EP3-01-shell-nav.md', run: 'EP3-01-shell-nav.md', deps: [], gate: null, confirm: 'fork-portal',
              db: null, note: 'FORKA uai-spark (~/IdeaProjects/uai-spark) → uai-portal. PortalSidebar com vertical OOH, rota /ooh protegida.' },
  'EP3-02': { lane: 'shell', repo: 'portal', doc: F1 + '/03-front/shell/EP3-02-login-sso.md', run: 'EP3-02-login-sso.md', deps: ['EP3-01'], gate: null, auth: true,
              db: null, note: 'uai-auth RED ⇒ mode=stub: AuthContext real + interceptor Bearer apontando p/ token-stub; SSO redirect real deferido. NÃO é gate-skip — roda em stub e fica "partial" pelo orquestrador.' },
  'EP3-03': { lane: 'shell', repo: 'portal', doc: F1 + '/03-front/shell/EP3-03-deploy-shell.md', run: 'EP3-03-deploy-shell.md', deps: ['EP3-01', 'EP3-02'], gate: null, db: null, ship: true, confirm: 'deploy' },

  // ── MÓDULO (uai-portal) ──
  'EP4-01': { lane: 'module', repo: 'portal', doc: F1 + '/03-front/modulo-ooh/EP4-01-scaffold-modulo.md', run: 'EP4-01-scaffold-modulo.md', deps: [], gate: null, xdep: ['EP3-01', 'EP3-02'],
              db: null, note: 'Módulo /ooh + intelClient (hooks react-query) + ProposalPort. Aponta ao intel direto com Bearer.' },
  'EP4-02': { lane: 'module', repo: 'portal', doc: F1 + '/03-front/modulo-ooh/EP4-02-lista-filtros.md', run: 'EP4-02-lista-filtros.md', deps: ['EP4-01'], gate: null, xdep: ['EP2-04', 'EP2-06'], db: null },
  'EP4-03': { lane: 'module', repo: 'portal', doc: F1 + '/03-front/modulo-ooh/EP4-03-ficha.md', run: 'EP4-03-ficha.md', deps: ['EP4-01'], gate: null, xdep: ['EP2-03'], db: null },
  'EP4-04': { lane: 'module', repo: 'portal', doc: F1 + '/03-front/modulo-ooh/EP4-04-mapa-interativo.md', run: 'EP4-04-mapa.md', deps: ['EP4-03'], gate: null, xdep: ['EP2-08'], db: null,
              note: 'Recupera componente MapLibre do legado (git show legacy-frozen:web/...) e adapta p/ consumir /geo do intel.' },
  'EP4-05': { lane: 'module', repo: 'portal', doc: F1 + '/03-front/modulo-ooh/EP4-05-charts.md', run: 'EP4-05-charts.md', deps: ['EP4-03'], gate: null, xdep: ['EP2-03'], db: null },
  'EP4-06': { lane: 'module', repo: 'portal', doc: F1 + '/03-front/modulo-ooh/EP4-06-cesta.md', run: 'EP4-06-cesta.md', deps: ['EP4-01'], gate: null, xdep: ['EP2-05'], db: null },
  'EP4-09': { lane: 'module', repo: 'portal', doc: F1 + '/03-front/modulo-ooh/EP4-09-mapa-cesta.md', run: 'EP4-09-mapa-cesta.md', deps: ['EP4-06', 'EP4-04'], gate: null, xdep: ['EP2-08'], db: null },
  'EP4-07': { lane: 'module', repo: 'portal', doc: F1 + '/03-front/modulo-ooh/EP4-07-export-pdf.md', run: 'EP4-07-export-pdf.md', deps: ['EP4-06', 'EP4-09'], gate: null, db: null },
  'EP4-08': { lane: 'module', repo: 'portal', doc: F1 + '/03-front/modulo-ooh/EP4-08-honestidade-ui.md', run: 'EP4-08-honestidade.md', deps: ['EP4-02', 'EP4-03', 'EP4-06', 'EP4-07'], gate: null, db: null },
}

const LANE_ORDER = {
  data:   ['T8b', 'EP1-02'],
  back:   ['EP2-01', 'EP2-02', 'EP2-03', 'EP2-07', 'EP2-04', 'EP2-05', 'EP2-06', 'EP2-08', 'EP2-09'],
  shell:  ['EP3-01', 'EP3-02', 'EP3-03'],
  module: ['EP4-01', 'EP4-02', 'EP4-03', 'EP4-04', 'EP4-05', 'EP4-06', 'EP4-09', 'EP4-07', 'EP4-08'],
}

// ════════════════════════ HELPERS — gates ════════════════════════
const statusOf = (gates, g) => (gates && gates[g] && gates[g].status) || 'UNKNOWN'
const green  = (gates, g) => statusOf(gates, g) === 'GREEN'
const usable = (gates, g) => statusOf(gates, g) !== 'RED'   // GREEN ou YELLOW

// ════════════════════════ HANDOFF I/O (via agente — script não tem fs) ════════════════════════
async function readHandoff() {
  const h = await agent(
    `Leia o handoff do F1 em ${STATE}. Se NÃO existir, devolva o esqueleto vazio { gates:{}, lanes:{data:{},back:{},shell:{},module:{}}, contract:{}, decisions:[], runDocs:[], pending:[] }. Devolva o JSON parseado como objeto. NÃO escreva nada.`,
    { phase: LANE, label: 'handoff:read', schema: HANDOFF_DOC })
  return h || { gates: {}, lanes: {}, contract: {}, decisions: [], runDocs: [], pending: [] }
}

async function writeHandoff(laneKey, patchObj) {
  await agent(
    `Atualize o handoff do F1 em ${STATE} fazendo MERGE (nunca sobrescreva o arquivo inteiro):
1. Leia o JSON atual de ${STATE} (se não existir, parta do esqueleto { gates:{}, lanes:{data:{},back:{},shell:{},module:{}}, contract:{}, decisions:[], runDocs:[], pending:[] }).
2. Aplique este patch da lane "${laneKey}": ${JSON.stringify(patchObj)}.
   Regras de merge: substitua só as chaves do patch; em decisions/runDocs/pending FAÇA APPEND (não duplique); carimbe updatedBy="${laneKey}" e updatedAt com a data real (rode \`date -u +%FT%TZ\`).
3. Escreva o JSON resultante de volta em ${STATE} (indentado, 2 espaços) e mantenha um espelho legível em ${STATE.replace('.json', '.md')} (tabela curta: gates, status por lane, pendências).
Confirme o que gravou.`,
    { phase: LANE, label: `handoff:write:${laneKey}` })
}

// ════════════════════════ GATE CHECK (parte gate) ════════════════════════
function gatePrompt(gate) {
  const common = 'Você verifica um GATE de execução do F1 (uAI-OOH). Seja factual: rode as checagens, compare esperado vs real, e classifique status GREEN (tudo passa) / YELLOW (degradável, dá pra seguir) / RED (bloqueia ou ausente). Devolva evidence[] com cada check.'
  const db = `Use mcp__postgres-ooh__execute_sql (carregue via ToolSearch "select:mcp__postgres-ooh__execute_sql" se necessário). SOMENTE SELECT — nunca DDL/DML.`
  if (gate === 'T8b') return `${common}\n${db}\nChecagens (banco ooh):\n- SELECT count(*) FROM core.regional;  -- esperado 9\n- SELECT count(DISTINCT line_id) FROM core.line_area;  -- esperado 303\n- SELECT count(*) FROM core.stop WHERE geom_31983 IS NOT NULL AND (nm_bairro IS NULL OR regional IS NULL);  -- esperado 0\n- SELECT count(*) FROM core.census_sector WHERE regional IS NULL;  -- esperado 0\nGREEN se todos batem (T8b já mergeado, só falta o run doc).`
  if (gate === 'EP1-02') return `${common}\n${db}\nChecagens (banco ooh):\n- SELECT to_regclass('serving.line_corridor') IS NOT NULL AND to_regclass('serving.line_poi') IS NOT NULL;  -- ambos existem?\n- SELECT count(DISTINCT line_id) FROM serving.line_corridor;  -- esperado 303\n- SELECT count(DISTINCT line_id) FROM serving.line_poi;  -- ~303\nRED se as tabelas não existem ou estão vazias (EP1-02 ainda não foi feito).`
  if (gate === 'dataset_version') return `${common}\n${db}\nChecagens (banco ooh):\n- SELECT count(*) FROM serving.dataset_version WHERE status='ACTIVE';  -- precisa >=1\n- SELECT version_id, status FROM serving.dataset_version ORDER BY version_id;\nGREEN se há >=1 ACTIVE; YELLOW se só BUILDING (intel sobe mas health fica vermelho até T17 promover).`
  if (gate === 'repos') return `${common}\nChecagens (filesystem, Bash, read-only). Para cada repo conte arquivos "reais" (fora de .git/.idea/CLAUDE.md):\n- ${REPO.intel} (esperado AUSENTE/inexistente ⇒ EP2-01 cria)\n- ${REPO.portal} (esperado quase vazio ⇒ EP3-01 forka)\n- ${REPO.spark} (esperado existir, fonte do fork)\n- ${REPO.template} (esperado existir, base do scaffold; tem pom.xml?)\nDevolva evidence por repo. status YELLOW (estado esperado p/ scaffold).`
  if (gate === 'uai-auth') return `${common}\nChecagem (filesystem, Bash): conte arquivos reais (fora de .git/.idea/CLAUDE.md) em ${REPO.auth}; procure pom.xml/package.json e qualquer endpoint de introspection/SSO no código. 0 arquivos reais ⇒ RED (SSO não pronto ⇒ EP3-02/EP2-07 rodam em modo stub). Devolva evidence.`
  return common
}

async function runGate() {
  phase('gate')
  const keys = ['T8b', 'EP1-02', 'dataset_version', 'repos', 'uai-auth']
  const results = await parallel(keys.map(k => () => agent(gatePrompt(k), { phase: 'gate', label: `gate:${k}`, schema: GATE_RESULT })))
  const gates = {}
  results.filter(Boolean).forEach(r => { gates[r.gate || ''] = r })
  // garante presença das chaves mesmo se algum agente falhar
  keys.forEach(k => { if (!gates[k]) gates[k] = { gate: k, status: 'UNKNOWN', evidence: [], note: 'agente não retornou' } })
  await writeHandoff('gate', { gates })
  const summary = keys.map(k => `${k}=${gates[k].status}`).join(' · ')
  log(`gate-check: ${summary}`)
  return { status: 'done', checkpoint: 'after-gate', gates, summary,
           next: 'Dispare data∥back∥shell. back exige confirm:["create-intel"]; shell exige confirm:["fork-portal"].' }
}

// ════════════════════════ TASK PIPELINE: implement→review→test→db-validate→refute ════════════════════════
function repoOf(t) { return REPO[t.repo] }
function buildCmd(t, kind) { const b = BUILD[t.repo]; return b ? b[kind].replace(/REPO/g, repoOf(t)) : '(sem build)' }

function implementPrompt(id, t, gates, feedback, attempt) {
  const authNote = t.auth && !green(gates, 'uai-auth')
    ? '\n⚠️ GATE uai-auth=RED ⇒ implemente em modo STUB (token/introspection mock); deixe o SSO/introspection real deferido e documente isso nas observações.' : ''
  return `Você é o IMPLEMENTADOR (perfil do skill ~/.claude/skills/coder/SKILL.md, mas execução INLINE — não escreva coder-output.yaml; responda no schema).
**Sempre pt-BR.**
REPO-ALVO (opere SOMENTE aqui; path absoluto): ${repoOf(t)}
Use \`git -C ${repoOf(t)}\` em TODO comando git. Crie/use a branch feat/ooh-${id.toLowerCase()}. NÃO toque NENHUM outro repositório nem o repo de PM.
TASK (leia o arquivo inteiro antes de implementar): ${t.doc}
${t.note ? 'NOTA DA TASK: ' + t.note : ''}${authNote}
${feedback ? '\n🔁 RETRY #' + attempt + ' — corrija APENAS o que o estágio "' + feedback.from + '" apontou (não refatore o resto):\n' + JSON.stringify(feedback).slice(0, 1800) : ''}
Passos: (1) leia a task e os arquivos referenciados; (2) observe os padrões do repo antes de codar; (3) implemente o checklist item a item; (4) crie os testes que a task pede; (5) valide build: ${buildCmd(t, 'build')}. NÃO rode a suíte completa (isso é do estágio Test).
Responda no schema IMPLEMENT_OUTPUT: branch, arquivos criados/modificados, checklist com done, resultado da compilação, observações.`
}

function reviewPrompt(id, t, impl, attempt) {
  return `Você é o REVIEWER senior (perfil ~/.claude/skills/reviewer/SKILL.md, execução INLINE — responda no schema). **pt-BR.**
TASK original: ${t.doc}
O que o implementador alega ter feito: ${JSON.stringify(impl).slice(0, 2000)}
Veja o diff real: \`git -C ${repoOf(t)} diff\` e \`git -C ${repoOf(t)} diff --staged\` (e os arquivos novos da branch feat/ooh-${id.toLowerCase()}).
Revise: conformidade com a task (checklist completo? escopo respeitado?), qualidade (padrões do repo, segurança OWASP, sem código morto, erro tratado), testes presentes.
Tentativa #${attempt}: se for >=3, seja pragmático — foque em critical/major. Em dúvida com a task cumprida, aprove.
Decida status approved|rejected. Se rejected, liste problems com file/line/severity/sugestão. Responda no schema REVIEW_VERDICT.`
}

function testPrompt(id, t) {
  return `Você é o TESTER (perfil ~/.claude/skills/tester/SKILL.md, execução INLINE — responda no schema). **pt-BR.**
TASK (seção Verificação/Critério de pronto): ${t.doc}
REPO: ${repoOf(t)}. Verifique se os testes que a task pede foram criados (Glob) e rode a suíte: ${buildCmd(t, 'test')}.
Se o comando padrão falhar por ambiente, tente o equivalente do repo e reporte qual usou. Analise: novos testes existem e passam? algum existente quebrou?
Responda no schema TEST_RESULT (status passed|failed|skipped, counts, falhas com erro). NÃO corrija código nem escreva testes — só reporte.`
}

function dbValidatePrompt(id, t) {
  return `Você é o validador de BANCO (o "agente conversa com o banco ooh"). **pt-BR.**
Use mcp__postgres-ooh__execute_sql (SOMENTE SELECT; carregue via ToolSearch se necessário). Se a validação exigir o serviço no ar (endpoints HTTP do intel), suba/cheque conforme a task permitir; senão valide direto as tabelas serving/core.
Critérios da task ${id} a confirmar contra o banco ooh: ${t.db}
Para cada critério rode a query, registre expected vs actual e pass. Cheque também serving.dataset_version (version_id+status). Responda no schema DB_VALIDATION (ok=true só se TODOS os checks passam).`
}

function refutePrompt(id, t, ev, gates) {
  return `Você é o agente CÉTICO (camada adversarial). A task ${id} foi marcada "done". TENTE DERRUBAR isso com evidência concreta — não confie no auto-relato dos estágios anteriores.
**pt-BR.** Vetores de ataque obrigatórios:
1) GATE/Critério não batido: releia "Critério de pronto" em ${t.doc} e ${t.db ? 'rode os counts no banco ooh via mcp__postgres-ooh__execute_sql e compare com o alegado' : 'verifique no repo ' + repoOf(t) + ' que os critérios estão de fato cumpridos'}. ⚠️ IGNORE o item "produzir/registrar docs/epicos/runs/${t.run}" — esse run doc é escrito pelo ORQUESTRADOR DEPOIS desta etapa; a ausência dele AGORA NÃO conta como refutação. Foque em dado/código/contrato/gate reais.
2) CONTRATO divergente: se a task expõe/consome endpoint, confronte o que foi implementado com o esperado (método/path/query/shape).
3) COUNT/EVIDÊNCIA errada: os checks reportados (${JSON.stringify((ev.dbv && ev.dbv.checks) || []).slice(0, 900)}) batem com a realidade AGORA?
${t.auth && !green(gates, 'uai-auth') ? '4) AUTH STUB (uai-auth=RED): o STUB (AuthContext+interceptor Bearer / introspection-stub) é o deliverable CORRETO agora — NÃO refute por "não ser SSO/introspection real" (isso é esperado e deferido; o orquestrador marca a task como PARCIAL). Refute APENAS se o STUB estiver mal feito (interceptor não propaga o Bearer, AuthContext quebrado, /api/** ou rota não realmente protegida no stub). Stub correto ⇒ refuted=false.' : ''}
Se você NÃO conseguir refutar nenhum vetor com evidência, refuted=false. Se derrubar QUALQUER vetor, refuted=true e aponte a evidência. Na dúvida SEM evidência contrária, refuted=false (não invente). Responda no schema REFUTE_VERDICT.`
}

async function emitRunDoc(id, t, ev, done, gates) {
  const partial = t.auth && !green(gates, 'uai-auth')
  await agent(
    `Escreva o run doc da task ${id} em ${RUNS}/${t.run} (repo de PM — pode escrever aqui). **pt-BR.** Formato dos runs existentes (ver ${RUNS}/epico-3-metricas-score-2026-06-05.md): cabeçalho com repo/branch, tabela de counts reais vs esperado (veredito ✅/⚠️), decisões/desvios, estado do dataset_version, e uma seção "Adversarial" com o que o cético tentou e por que não derrubou (ou derrubou).
Carimbe a data real (rode \`date +%F\`). Status final desta task: ${done ? (partial ? 'PARCIAL (auth=stub, fechamento real deferido)' : 'DONE') : 'FAILED/BLOQUEADA'}.
Evidência (resuma, não cole cru): implement=${JSON.stringify(ev.impl).slice(0, 700)}; review=${JSON.stringify(ev.review).slice(0, 500)}; test=${JSON.stringify(ev.test).slice(0, 500)}; db=${JSON.stringify(ev.dbv).slice(0, 700)}; refute=${JSON.stringify(ev.refute).slice(0, 700)}.
Confirme o path gravado.`,
    { phase: LANE, label: `run-doc:${id}` })
}

async function runTask(id, gates) {
  const t = TASKS[id]
  phase(t.lane)
  // fast-path: task já entregue (gate GREEN) que só precisa de validação + run doc (ex.: T8b já mergeado)
  if (t.validateOnly && green(gates, t.gate)) {
    const dbvF = t.db ? await agent(dbValidatePrompt(id, t), { phase: t.lane, label: `db:${id}`, schema: DB_VALIDATION }) : { ok: true, checks: [] }
    const refuteF = await agent(refutePrompt(id, t, { dbv: dbvF }, gates), { phase: t.lane, label: `refute:${id}`, schema: REFUTE_VERDICT })
    const okF = (!t.db || (dbvF && dbvF.ok)) && refuteF && !refuteF.refuted
    await emitRunDoc(id, t, { impl: { status: 'skipped-merged' }, review: null, test: null, dbv: dbvF, refute: refuteF }, okF, gates)
    return { task: id, lane: t.lane, status: okF ? 'done' : 'failed', runDoc: `${RUNS}/${t.run}`, lastFeedback: okF ? null : { from: 'validate-only', refute: refuteF } }
  }
  let feedback = null, impl = null, review = null, test = null, dbv = { ok: true, checks: [] }, refute = null
  let done = false
  for (let attempt = 1; attempt <= RETRIES; attempt++) {
    impl = await agent(implementPrompt(id, t, gates, feedback, attempt), { phase: t.lane, label: `impl:${id}#${attempt}`, schema: IMPLEMENT_OUTPUT })
    if (!impl || impl.status === 'failed') { feedback = { from: 'implement', detail: (impl && impl.observations) || 'sem retorno' }; continue }

    review = await agent(reviewPrompt(id, t, impl, attempt), { phase: t.lane, label: `review:${id}#${attempt}`, schema: REVIEW_VERDICT })
    if (!review || review.status === 'rejected') { feedback = { from: 'review', problems: (review && review.problems) || [], verdict: review && review.verdict }; continue }

    test = await agent(testPrompt(id, t), { phase: t.lane, label: `test:${id}#${attempt}`, schema: TEST_RESULT })
    if (test && test.status === 'failed') { feedback = { from: 'test', failed: test.failed_tests || [] }; continue }

    dbv = t.db ? await agent(dbValidatePrompt(id, t), { phase: t.lane, label: `db:${id}#${attempt}`, schema: DB_VALIDATION }) : { ok: true, checks: [] }
    if (t.db && (!dbv || !dbv.ok)) { feedback = { from: 'db', checks: (dbv && dbv.checks) || [] }; continue }

    refute = await agent(refutePrompt(id, t, { impl, review, test, dbv }, gates), { phase: t.lane, label: `refute:${id}#${attempt}`, schema: REFUTE_VERDICT })
    if (refute && refute.refuted) { feedback = { from: 'refute', attacks: refute.attacks, reason: refute.reason }; continue }

    done = true
    break
  }
  const partial = done && t.auth && !green(gates, 'uai-auth')
  await emitRunDoc(id, t, { impl, review, test, dbv, refute }, done, gates)
  return { task: id, lane: t.lane, status: done ? (partial ? 'partial' : 'done') : 'failed',
           runDoc: `${RUNS}/${t.run}`,
           lastFeedback: done ? null : feedback }
}

// roda uma lane inteira em ordem do DAG, respeitando deps intra-lane, gates e xdeps (do handoff)
async function runLane(laneKey, gates, handoff) {
  phase(laneKey)
  const order = (LANE_ORDER[laneKey] || []).filter(id => !ONLY || ONLY.includes(id))
  const results = []
  const completed = {}            // id -> status desta execução
  const doneSet = (h) => h && h.lanes ? Object.values(h.lanes).flatMap(l => Object.keys((l && l.tasks) || {}).filter(k => ['done', 'partial'].includes(l.tasks[k]))) : []
  const priorDone = doneSet(handoff)

  for (const id of order) {
    const t = TASKS[id]
    // checkpoint irreversível por task (create-intel / fork-portal)
    if (t.confirm && !CONFIRM.includes(t.confirm)) {
      results.push({ task: id, status: 'paused', need_confirm: t.confirm })
      log(`PAUSA: ${id} exige confirm:["${t.confirm}"] (ação irreversível). Re-dispare {lane:'${laneKey}', confirm:['${t.confirm}']}.`)
      break   // não dá pra seguir a lane sem o scaffold base
    }
    // gate cross-lane RED ⇒ pula e marca pending
    if (t.gate && !usable(gates, t.gate)) {
      log(`${id} PULADA: gate ${t.gate}=${statusOf(gates, t.gate)} (cross-lane). Vai pra pending.`)
      results.push({ task: id, status: 'skipped', reason: `gate ${t.gate}=${statusOf(gates, t.gate)}` })
      continue
    }
    // deps intra-lane não cumpridas (nesta execução nem em execuções anteriores)
    const missingDeps = (t.deps || []).filter(d => completed[d] !== 'done' && completed[d] !== 'partial' && !priorDone.includes(d))
    if (missingDeps.length) {
      log(`${id} BLOQUEADA: deps pendentes ${missingDeps.join(',')}.`)
      results.push({ task: id, status: 'blocked', missingDeps })
      continue
    }
    // xdeps cross-lane (ex.: EP4-* dependem de EP2-*/EP3-*) — checa no handoff
    const missingX = (t.xdep || []).filter(d => !priorDone.includes(d))
    if (missingX.length) {
      log(`${id} BLOQUEADA: cross-lane deps ${missingX.join(',')} ainda não prontas no handoff. Rode a lane de origem antes.`)
      results.push({ task: id, status: 'blocked', missingX })
      continue
    }
    const r = await runTask(id, gates)
    completed[id] = r.status
    results.push(r)
  }

  // grava no handoff o estado das tasks desta lane
  const tasksMap = {}
  results.forEach(r => { tasksMap[r.task] = r.status })
  const newRunDocs = results.filter(r => r.runDoc).map(r => r.runDoc)
  const newPending = results.filter(r => ['skipped', 'blocked', 'failed', 'paused'].includes(r.status)).map(r => ({ task: r.task, status: r.status, reason: r.reason || r.need_confirm || r.missingDeps || r.missingX }))
  const lanePatch = { lanes: { [laneKey]: { status: results.every(r => ['done', 'partial'].includes(r.status)) ? 'complete' : 'partial', tasks: tasksMap } }, runDocs: newRunDocs, pending: newPending }

  // extras por lane (contexto compartilhado pro cross-talk)
  if (laneKey === 'back') {
    lanePatch.lanes.back.openapi = `${REPO.intel} (/v3/api-docs em runtime ou openapi.json do build)`
    lanePatch.lanes.back.endpoints = ['GET /api/lines', 'GET /api/lines/{id}', 'GET /api/lines/{id}/metrics', 'GET /api/lines/ranking', 'POST /api/lines/aggregate', 'GET /api/regions', 'GET /api/lines/{id}/geo']
  }
  if (laneKey === 'shell') lanePatch.lanes.shell.auth_mode = green(gates, 'uai-auth') ? 'sso' : 'stub'
  if (laneKey === 'module') lanePatch.lanes.module.hooks = ['useLines', 'useLine', 'useLineMetrics', 'useRanking', 'useAggregate', 'useRegions', 'useLineGeo']

  await writeHandoff(laneKey, lanePatch)
  log(`lane ${laneKey}: ${results.map(r => r.task + '=' + r.status).join(' · ')}`)
  return { lane: laneKey, results, checkpoint: `after-${laneKey}` }
}

// ════════════════════════ CONTRACT SYNC (parte contract) ════════════════════════
async function runContract(gates, handoff) {
  phase('contract')
  const verdict = await agent(
    `Você é o agente de CONTRATO (back↔front do uAI-OOH). **pt-BR.**
LADO BACK (OpenAPI): obtenha o spec do intel em ${REPO.intel} — tente \`curl -s http://localhost:8085/v3/api-docs\`; se não subir, leia o openapi.json do build ou derive dos @RestController.
LADO FRONT: leia o intelClient e os hooks em ${REPO.portal}/src (useLines/useRegions/useLine/useLineMetrics/useRanking/useAggregate/useLineGeo).
Referência de endpoints F1: ${PRD} (seção §6/§7) e os endpoints no handoff: ${JSON.stringify((handoff.lanes && handoff.lanes.back && handoff.lanes.back.endpoints) || [])}.
Para CADA hook do front ache a operação no OpenAPI e verifique método, path (inclui {id}), query (regiao,bairro,weights,publicoAlvo), shape do request (POST /aggregate: lineIds[]) e da resposta (score_total, sub-scores, impressões marcadas estimativa).
Reporte matched[] e mismatches[] (hook sem endpoint, endpoint sem hook, divergência de método/path/query/shape) com severidade. verdict=divergent se houver QUALQUER critical. Responda no schema CONTRACT_VERDICT.`,
    { phase: 'contract', label: 'contract:back-front', schema: CONTRACT_VERDICT })
  const criticals = ((verdict && verdict.mismatches) || []).filter(m => m.severity === 'critical')
  const pending = criticals.map(m => ({ task: m.hook || m.endpoint, status: 'contract-divergent', reason: `${m.kind}: ${m.expected} vs ${m.actual}` }))
  await writeHandoff('contract', { contract: verdict || { verdict: 'unknown', matched: [], mismatches: [] }, pending })
  log(`contract: ${verdict ? verdict.verdict : 'sem retorno'} · ${criticals.length} críticos`)
  return { checkpoint: 'after-contract', contract: verdict, reopen: criticals.map(m => m.hook || m.endpoint) }
}

// ════════════════════════ SHIP (parte ship — irreversível) ════════════════════════
async function runShip(gates, handoff) {
  phase('ship')
  if (!CONFIRM.includes('push') || !CONFIRM.includes('deploy')) {
    log('PAUSA: ship exige confirm:["push","deploy"] (push/PR + GHCR + uai-infra).')
    return { status: 'paused', checkpoint: 'need-ship-confirm',
             need_confirm: ['push', 'deploy'],
             willPush: [REPO.intel, REPO.portal, REPO.pipeline],
             willDeploy: ['GHCR ghcr.io/mddinizbh/uai-ooh-intel', 'GHCR ghcr.io/mddinizbh/uai-portal', 'entrada uai-infra compose'] }
  }
  const out = await agent(
    `Você é o agente de SHIP do F1. **pt-BR.** Execute o deploy SEM tocar nginx/VPS direto (EP2-09:closer / EP3-03).
1. Para cada repo com branch feat/ooh-* pronta (${REPO.intel}, ${REPO.portal}, ${REPO.pipeline}): \`git -C <repo> push\` da branch e abra PR (gh pr create) — só se os run docs marcam done/partial.
2. Build da imagem + push pro GHCR (ghcr.io/mddinizbh/uai-ooh-intel e /uai-portal) conforme o Dockerfile de cada repo.
3. Adicione a entrada no compose do uai-infra (${REPO.intel ? 'uai-ooh-intel' : ''}/uai-portal) — commit + push (deploy via Actions). NÃO edite VPS nem nginx.
Reporte PRs abertos, imagens publicadas e o que ficou pendente.`,
    { phase: 'ship', label: 'ship:deploy' })
  await writeHandoff('ship', { decisions: ['ship executado: ' + String(out).slice(0, 200)] })
  return { checkpoint: 'after-ship', out }
}

// ════════════════════════ DISPATCH ════════════════════════
log(`F1 orchestration · lane=${LANE} · confirm=[${CONFIRM.join(',')}] · retries=${RETRIES}${ONLY ? ' · only=[' + ONLY.join(',') + ']' : ''}`)

if (LANE === 'gate') {
  return await runGate()
}

const handoff = await readHandoff()
const gates = (handoff && handoff.gates) || {}
if (!Object.keys(gates).length) {
  log('handoff sem gates — rode {lane:"gate"} primeiro.')
  return { status: 'error', reason: 'gates ausentes no handoff; rode a parte gate antes.' }
}

if (LANE === 'contract') return await runContract(gates, handoff)
if (LANE === 'ship')     return await runShip(gates, handoff)
if (['data', 'back', 'shell', 'module'].includes(LANE)) {
  // pré-condição cross-lane do módulo: precisa de back.endpoints + shell base
  if (LANE === 'module') {
    const backReady = handoff.lanes && handoff.lanes.back && handoff.lanes.back.endpoints && handoff.lanes.back.endpoints.length
    const shellReady = handoff.lanes && handoff.lanes.shell && handoff.lanes.shell.tasks && ['done', 'partial'].includes(handoff.lanes.shell.tasks['EP3-01'])
    if (!backReady || !shellReady) {
      log(`module bloqueada: back.endpoints=${!!backReady} shell EP3-01=${!!shellReady}. Rode back e shell antes.`)
      return { status: 'blocked', reason: 'module precisa de back (endpoints) + shell (EP3-01) prontos no handoff.' }
    }
  }
  return await runLane(LANE, gates, handoff)
}

return { status: 'error', reason: `lane desconhecida: ${LANE}. Use gate|data|back|shell|module|contract|ship.` }
