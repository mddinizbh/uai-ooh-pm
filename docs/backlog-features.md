# Backlog de features (fora do escopo F1)

Ideias registradas para discutir/priorizar depois. Não fazem parte da normalização F1.

## Enriquecimento de contatos de POIs do corredor
**Origem:** simulação 4107 — ao listar estabelecimentos de educação no corredor, o OSM tem
contato de só ~27% (enviesado pra rede pública, com typos e dados velhos). Os colégios
particulares (público pagante mais provável de mídia) ficaram sem contato.

**O que é:** dado um corredor de linha (ou área), enriquecer os POIs com **contato real**
(telefone, e-mail, site, responsável) para virar **lista de prospecção** — "anuncie na linha que
passa na porta da sua escola/loja".

**Fontes candidatas:** Google Places API (Place Details: phone/website/rating), CNPJ/Receita por
endereço, ou base de POIs comercial. OSM cru NÃO serve como fonte de contato.

**Por que é feature à parte:** F1 é planejamento/score (alcance, perfil, ranking). Prospecção de
POIs é um produto comercial adjacente (lead-gen para a equipe de vendas), com custo de API e
questões de LGPD/uso de dado de contato. Avaliar modelo (sob demanda por campanha? cota mensal?).

**Valor:** transforma o mapa de POIs (já temos) num motor de leads — "quem no trajeto da linha X
é cliente-alvo e como falar com ele".

---

## (outras ideias entram aqui conforme surgirem)
- v2 — sinal de trânsito próprio (coletor de congestionamento): já documentado no
  `plano-normalizacao-core.md`, seção "v2".
