# T0b — `uai-ooh-service-template` (Java) + esqueleto do `uai-ooh-intel`

> Tarefa do Bloco 1 (uAI-OOH F1). **Auto-suficiente**: a sessão que rodar isto deve ler só os apontamentos abaixo + este arquivo.
> **Épico:** Arquitetura / repos-topologia · **Depende de:** nenhuma · **Paralelizável:** sim (com T0a)
> **Repo-alvo:** `uai-ooh-service-template` → `uai-ooh-intel` (ambos novos) · **cwd:** `~/IdeaProjects/personal/uai/uai-ooh-service-template` · **Stack:** Java 21 / Spring Boot
> ⚠️ **Decisão do dono 2026-06-04:** o `intel` é um repo NOVO, do zero, a partir deste template. O `uai-bus-lines-map` fica CONGELADO — NÃO é fork nem rename. Este template é criado já aqui porque o `intel` é seu primeiro consumidor (T19).

## Objetivo
Criar o `uai-ooh-service-template` como **GitHub Template Repository**, derivado do esqueleto do bus-lines LIMPO de domínio específico, e gerar dele o esqueleto inicial do `uai-ooh-intel` (sem domínio ainda — o domínio é a T19). Captura os fixes do `pom.xml` enquanto estão frescos.

Conteúdo do template: estrutura hexagonal `domain/{model,port/in,port/out}` + `application/usecase` + `adapter/{in/web,out/persistence}`; `pom.xml` com parent Spring Boot 3.3.6 + os fixes (`api.version=1.44`, Testcontainers 1.21.3, JaCoCo 80%, Java 21); Dockerfile multi-stage (Maven temurin-21 build → jre-alpine runtime, non-root); `.github/workflows/deploy.yml` (test → build-push GHCR, deploy delegado ao `uai-infra`); filtro `X-UAI-Internal-Key` para `/internal/**`; `OpenApiConfig`; `application.yml` com env vars padrão (`DB_HOST/PORT/NAME/USER/PASSWORD`) + actuator/health.

## Apontamentos a LER antes de começar
- `git show legacy-frozen:pom.xml` e `git show legacy-frozen:Dockerfile` — os fixes (api.version=1.44, Java 21, Testcontainers 1.21.3, JaCoCo 80%) vivem na tag `legacy-frozen` (código removido do HEAD do uai-ooh-pm; CLAUDE.md já não detalha os fixes)
- /Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pm/docs/arquitetura-servicos.md (Mapa de serviços; Regras-âncora; ADR-003/ADR-004)
- decisão repos-topologia (template Java versionado em vez de copiar-colar)

## Critério de pronto (verificável)
`uai-ooh-service-template` existe como GitHub Template com `pom.xml` (api.version=1.44, Testcontainers 1.21.3, JaCoCo 80%, Java 21) + Dockerfile multi-stage + `deploy.yml` + filtro `X-UAI-Internal-Key` + OpenApiConfig + application.yml, derivado do esqueleto do bus-lines LIMPO de domínio. Esqueleto `uai-ooh-intel` gerado do template **compila** (`mvn compile`) e sobe com `/actuator/health` UP. `uai-bus-lines-map` intocado.

## Apontamentos a PRODUZIR ao terminar
- docs/epicos/runs/T0b-service-template.md (template criado, esqueleto do intel gerado, paridade dos fixes do pom vs bus-lines)
