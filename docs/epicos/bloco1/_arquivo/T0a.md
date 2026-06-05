# T0a — Greenfield `uai-ooh-pipeline` (Python: ingestor + normalizer) + mover scripts + rotacionar secret

> Tarefa do Bloco 1 (uAI-OOH F1). **Auto-suficiente**: a sessão que rodar isto deve ler só os apontamentos abaixo + este arquivo.
> **Épico:** Arquitetura / repos-topologia · **Depende de:** nenhuma · **Paralelizável:** sim (com T0b)
> **Repo-alvo:** `uai-ooh-pipeline` (novo) · **cwd:** `~/IdeaProjects/personal/uai/uai-ooh-pipeline` · **Stack:** Python + PostGIS
> ℹ️ Lê a origem (`.data/scripts/` + `gen_ficha_4107.py`) do `uai-bus-lines-map` (move PARA o pipeline). O bus-lines fica CONGELADO no resto.

## Objetivo
Criar o repo greenfield `uai-ooh-pipeline` (service-first no esqueleto), mover o miolo de `.data/scripts/` + `gen_ficha_4107.py` do bus-lines para o pipeline versionado, parametrizar `db.py` por env e **rotacionar a credencial `ooh_admin`** exposta em claro no `db.py`. NÃO criar o template Java (é a T0b). NÃO renomear o bus-lines nem mover a SPA.

## Apontamentos a LER antes de começar
- /Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pm/docs/arquitetura-servicos.md (Mapa de serviços; Regras-âncora)
- /Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pm/docs/epicos/bloco1/plano-execucao.md (§1.1 topologia, §1.3 versionar o motor, §1.4 fronteira local→VPS, §1.5 CI/CD)
- decisão repos-topologia (polyrepo; fronteira ingestor↔normalizer no mesmo repo)

## Critério de pronto (verificável)
Repo `uai-ooh-pipeline` existe com: estrutura `ooh_ingestor/` + `ooh_normalizer/` + `db.py` (DSN 100% por env — `DB_HOST/PORT/NAME/USER/PASSWORD`, zero hardcoded) + `requirements.txt` + `Dockerfile` (job roda-e-sai) + CI build-only verde. O pipeline versionado reproduz a ficha 4107 contra o banco `ooh` local (mesmo número que `gen_ficha_4107.py` produzia). Credencial `ooh_admin` **rotacionada**; nenhum secret em claro em nenhum commit.

## Apontamentos a PRODUZIR ao terminar
- docs/epicos/runs/T0a-pipeline-greenfield.md (repo criado, decisão de fronteira ingestor↔normalizer, confirmação de rotação do secret, paridade do pipeline movido vs `gen_ficha_4107.py`)
