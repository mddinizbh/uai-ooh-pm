# Detalhamento Técnico: lane poller (F2 · GTFS-RT → raw + Kafka)

> Techspec da lane **02-poller** (`uai-ooh-realtime-poller`, **novo**, Python, worker contínuo). Units = POLL-01..03.
> Greenfield. Contratos em Python (dataclasses/Protocols/assinaturas — sem implementação).

## Contexto
Worker que faz polling do GTFS-RT vehicle-positions (~15–20s), decodifica protobuf, **landa a frota inteira** no `raw` (durável) e **publica** no Kafka `ooh.rt.position` (stream pro consolidador). Idempotente por `feed_timestamp`.

## Decisões Técnicas
- **Repo separado** (worker contínuo ≠ pipeline batch). Lib Kafka **`confluent-kafka`** (`key=vehicle_id`). Decode **`gtfs-realtime-bindings`**.
- **Idempotência por `feed_timestamp`**: se o feed não avançou, pula o ciclo.
- **Ordem land-then-publish**, **at-least-once** (consolidador é idempotente por estado).

## Padrões do Projeto a Seguir
- Módulo `poller/` (CLI `python -m poller`), config via env, ports como `Protocol` (hexagonal-light). Reusa `db.py`/toolchain do pipeline se compartilhar via pacote.
- Scaffold: `gh repo create` + `main` vazia → template.

## Riscos Globais
- **Feed instável** (HTTP/vazio): retries/backoff, não derruba o worker. **Lag** monitorado.
- **Duplicata** (at-least-once): absorvida por `feed_timestamp` (poller) + estado (consolidador).

---

## Unit: POLL-01 — scaffold + config
- **Responsabilidade**: worker Python + config + clients (Kafka, Postgres).
- **Localização**: `poller/__main__.py`, `poller/config.py`, `poller/clients/`.
- **Contrato**:
```python
@dataclass(frozen=True)
class PollerConfig:
    gtfs_rt_url: str
    poll_interval_s: int      # 15–20
    kafka_bootstrap: str
    kafka_topic: str          # "ooh.rt.position"
    ooh_db_url: str
```
- **Pré-condições**: INFRA-01 (tópico) + INFRA-02 (`raw`).
- **Pós-condições**: worker sobe, lê config, conecta Kafka+Postgres; healthcheck (último ciclo/lag).
- **Verificação**: sobe com config válida; loop vazio não crasha.

## Unit: POLL-02 — polling + decode + resiliência
- **Responsabilidade**: fetch → decode → posições normalizadas, com idempotência e métricas.
- **Localização**: `poller/feed.py`, `poller/decode.py`, `poller/poller.py`, `poller/domain.py`.
- **Contrato**:
```python
@dataclass(frozen=True)
class VehiclePosition:
    vehicle_id: str
    trip_id: str | None
    route_id: str | None
    lat: float
    lon: float
    bearing: float | None
    current_stop_sequence: int | None
    feed_timestamp: int

@dataclass(frozen=True)
class FeedSnapshot:
    feed_timestamp: int
    positions: list[VehiclePosition]

class FeedClient(Protocol):
    def fetch(self) -> bytes: ...            # raw protobuf (com retries/backoff/UA)

def decode(raw: bytes) -> FeedSnapshot: ...  # gtfs-realtime-bindings

@dataclass
class CycleMetrics:
    count: int
    lag_s: float
    skipped_duplicate: bool
```
- **Pré-condições**: POLL-01.
- **Pós-condições**: decodifica ~451 posições/ciclo; ciclo com `feed_timestamp` repetido é **skipped**; métricas (taxa/lag) expostas.
- **Decisões locais**: backoff exponencial; tolera feed vazio/erro (loga e segue).
- **Verificação**: teste de `decode` com fixture protobuf; teste de idempotência (mesmo `feed_timestamp` → skip).

## Unit: POLL-03 — landing + publish
- **Responsabilidade**: por ciclo, landar no `raw` e publicar no Kafka.
- **Localização**: `poller/raw_writer.py`, `poller/publisher.py`, `poller/poller.py` (orquestra o ciclo).
- **Contrato**:
```python
class RawWriter(Protocol):
    def write(self, snapshot: FeedSnapshot) -> int: ...   # COPY no raw (partição do dia); retorna linhas

class PositionPublisher(Protocol):
    def publish(self, positions: list[VehiclePosition]) -> None: ...  # Kafka, key=vehicle_id, lote

class Poller:
    def __init__(self, feed: FeedClient, writer: RawWriter, publisher: PositionPublisher, cfg: PollerConfig): ...
    def run_once(self, last_feed_ts: int) -> tuple[int, CycleMetrics]: ...  # land → publish
    def run_forever(self) -> None: ...
```
- **Pré-condições**: POLL-02 + INFRA-01/02.
- **Pós-condições**: cada ciclo → N linhas no `raw` (partição do dia) **+** N mensagens no tópico (`key=vehicle_id`); **land-then-publish**; at-least-once.
- **Decisões locais**: garantir partição do dia antes do COPY.
- **Verificação**: IT com Postgres + Kafka (Testcontainers) — 1 ciclo → counts batem no `raw` e no tópico.

## Diagrama de dependências
```
POLL-01 (scaffold/config)
   └─> POLL-02 (fetch/decode/idempotência)
          └─> POLL-03 (land-then-publish)
```
