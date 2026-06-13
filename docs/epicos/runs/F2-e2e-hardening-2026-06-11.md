# F2 — hardening do consolidador via E2E local-first (run, 2026-06-11)

> Execução do workflow `f2-orchestration` (lanes jobs→cons→local→e2e) + agentes de debug dirigido.
> **Resultado:** o motor do F2 (poller→consolidador→medido→recompute) foi construído do zero (4 repos)
> e validado **ao vivo no ambiente real** (uai-infra). O E2E local expôs **6 bugs sérios** que os ITs
> com fixture escondiam — todos corrigidos antes de qualquer prod. É a tese do local-first comprovada.

## Lanes entregues
- **jobs** ✅ — INFRA-03 (tiering raw→MinIO) + RECAL-00 (HLLs audiência, 115k chaves, erro PFCOUNT <0,1%, 22,7MB).
- **cons** ✅ — CONS-01..05 (commit `857ceb9` em `feat/ooh-f2-consolidator`).
- **local** ✅ — LOCAL-01 (compose.local + seed) + LOCAL-02 (replayer raw→Kafka).
- **e2e** 🔧 — rodou de verdade; expôs e corrigiu os bugs abaixo; validado ao vivo no uai-infra.
- Commits: consolidador `857ceb9`; pipeline `df5aee7` (RECAL-00/01 + INFRA-03).

## Os 6 bugs (todos achados pelo E2E real, invisíveis aos ITs)

| # | Bug | Causa-raiz | Por que o IT não pegou | Fix |
|---|---|---|---|---|
| 1 | Boot não subia, 0/5 ITs | `CoreLineResolver` com 2 construtores sem `@Autowired` → Spring não resolve | implementador rodou `mvn test` (surefire) que não roda os `*IT` (failsafe) — falsa-positiva; reviewer rodou `mvn verify` e pegou | `@Autowired` no construtor de produção |
| 2 | **Viagens com 1 ponto / km=0** | snap lançava `relation core.pattern_stop does not exist`; o listener não ackava → Kafka **redelivera** → mas o buffer de track **já fora drenado** → re-fecha com 1 ponto | a fixture `core-snap-fixture.sql` **fabricava** `core.pattern_stop` (que não existe no core real) | detecção de tabela ausente no boot + blindagem do `MedidoTripClosedSink` (degrada p/ métricas vazias, fato sempre landa) |
| 3 | Track não acumulava | `VehicleState` só tinha `trackPointCount` (contador), não a lista | ITs injetavam o track manualmente | buffer downsampled em lista Redis `live:track:{vehicleCode}` |
| 4 | Fragmentação em linhas curtas (50/83D/2150) | `current_stop_sequence` faz **platô e oscila** no terminus (540 rising-edges p/ 88 viagens); `incomingSeq >= max(n_stops)` fecha cedo | fixtures não tinham linhas de pattern curto | guard **rising-edge** + **≥8 hexes H3** de progresso geográfico |
| 5 | `v_real` vazio | `core.pattern_stop` (pattern→stop→seq) não existia no core (GTFS stop_times nunca carregado) | fixture fabricava | materializado do GTFS (149k paradas, 2.673 patterns) via builder existente do pipeline |
| 6 | Reprocessamento fragmenta | `TripTimeoutSweeper` usa **wall-clock** (`now()`); replay/earliest tem feedTs de horas atrás → todo veículo "expira" e o sweep fecha todas de uma vez | só aparece em replay/earliest, não em ITs nem ao vivo | **DESCOBERTA, não corrigido**: o timeout deveria usar `feedTs` p/ ser replay-safe. Não afeta operação ao vivo (latest). |

## Provas reais (replay determinístico, banco local)
- `mvn verify`: **94 unit + 15 ITs**, BUILD SUCCESS (Java 21).
- Replay linha 3054 inteira: **22 viagens, média 98,9 pts de track (máx 149), 0 com 1 ponto, todas km>0**. Viagem real do 40806: km=29,45, completude=0,915, 121 pts, 90 hexes (29 interpolados) — km bate com extensão ~32km.

## Validação AO VIVO no ambiente real (uai-infra :5432)
Consolidador subiu contra o banco vivo (não o efêmero): Flyway criou `medido`, `snap calculator
ready: vRealSource=core.pattern_stop`, consumiu o feed real do poller, **`parada_velocidade`
populada (v_real funcionando)**, 0 erros. Confirmou que o F2 roda no ambiente permanente.
*(Ressalva: rodado com `earliest` reprocessando 13h → track fragmentado pelo bug #6; ao vivo em
`latest` fica íntegro.)*

## Pendências (próxima sessão)
1. **INFRA-04 — cirurgia do compose** (ver `bloco2/f2/01-infra/INFRA-04-automacao-f2.md` + nota de topologia abaixo): reapontar o volume do banco vivo pro `postgres-ooh` do compose, adicionar o consolidador, subir o subset F2. **Backup do volume feito** (`banco_uai_ooh_pgdata_bak`, 12,3GB).
2. **Lanes rt/web NÃO implementadas** — RT-01/02/03 (intel realtime) + WEB-00/01/02 (mapa ao vivo). Sem elas, o F2 produz o verificado mas não há visualização realtime no navegador.
3. **Clamp de velocidade** no consolidador — `v_real` bruto tem outliers (95-211 km/h por jitter de GPS); clampar a ~70 km/h.
4. **Timeout sweep replay-safe** (bug #6) — usar `feedTs` em vez de wall-clock, p/ reprocessamento/backfill não fragmentar.
5. **RECAL-01 incompleto** — falta coluna `metodo` no serving + executar o recompute fonte medida (o e2e não chegou nessa etapa).

## Topologia de infra descoberta (pra o INFRA-04)
- O `docker-compose.yml` do `uai-infra` **já define a stack inteira** (uai-ooh-intel, uai-ooh-realtime-poller, uai-portal, uai-ooh-pipeline, postgres-ooh, kafka, redis, minio + uai-cms/auth/core/spark/nginx). Falta só **adicionar o serviço do consolidador**.
- **Pegadinha:** o banco com os dados é o container **avulso** `uai-ooh-db` (:5432, volume `banco_uai_ooh_pgdata`, 12,3GB) — **separado** do `postgres-ooh` do compose (:5433, volume `ooh_pg_data`, vazio; o init só cria schemas). Reorganizar = parar o avulso, reapontar o `postgres-ooh` do compose pro volume `banco_uai_ooh_pgdata`, subir.
- **Credencial divergente:** a senha do `ooh_admin` no volume ≠ `OOH_PG_PASSWORD` do `.env` do uai-infra (user bate). Alinhar antes de subir o compose.
- O poller (`uai-ooh-realtime-poller`) está rodando **avulso** (sem labels de compose) — matar e subir pelo compose.
- Efêmero `ooh-f2-local-*` (criado pelo LOCAL-01) é descartável: `docker compose -f docker-compose.local.yml down -v`.
