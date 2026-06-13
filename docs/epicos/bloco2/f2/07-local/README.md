# 07 · local — 🟫 VALIDAÇÃO LOCAL-FIRST

> **Decisão do dono (2026-06-10):** construir tudo local → validar ponta a ponta com **disparos
> manuais dos RECALs** → só então subir pra prod. **Nada vai pra VPS antes do E2E local passar**
> (o ship do workflow fica bloqueado sem `e2e=validated` no handoff).
> Bônus: Kafka/Redis da VPS são inacessíveis de fora (127.0.0.1) — local, o contrato Redis
> (CONS-05) e a reconciliação HLL ao-vivo×exato são validados **de verdade**, não inferidos de ITs.

| Task | O que | Nota |
|---|---|---|
| [LOCAL-01](LOCAL-01-ambiente-local.md) | **ambiente local completo**: `docker-compose.local.yml` (postgres+kafka+redis+init) + **seed** do banco real (core que o F2 consome + N dias de raw) | repo-alvo `uai-infra` |
| [LOCAL-02](LOCAL-02-replayer-feed.md) | **replayer**: `python -m poller replay --speed 60` — re-publica os ciclos do raw no Kafka local (dias de operação em minutos, determinístico) | repo-alvo `uai-ooh-realtime-poller` |

## Roteiro E2E (parte `e2e` do workflow `f2-orchestration`)

```
1. compose.local up (LOCAL-01)            4. RECAL-01 manual (recompute no local)
2. consolidador local consumindo          5. RECAL-02 manual (fixture + 4107 + reconciliação
3. poller replay --speed 60 (≥1 dia)         HLL×exato via redis-cli — tudo no Postgres local)
                                          6. tudo verde → e2e=validated → ship desbloqueia
```

**Cron não existe no local** — os disparos são manuais por desenho (é a simulação que valida o
comando antes da INFRA-04 agendar em prod).
