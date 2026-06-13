# Handoff — Footfall / população em trânsito pra OOH (bancas + back bus)

> **Sessão 2026-06-11/12. Ponto de retomada.** Investigação de fontes de "população em trânsito por área de
> BH" pra enriquecer audiência OOH. Começou pelas **bancas**, mas o uso mais central acabou sendo o **back bus**.

## Decisões e achados-chave

1. **Frente de bancas é inventário NOVO** (≠ ônibus). Banca = mídia fixa → footfall no ponto **é a audiência
   inteira**; ônibus = footfall é só **uma perna** (exposição externa). Formalizado no `README.md`.
2. **`exposure_cell.footfall` é proxy constante** (`82` nas 24h, 188.280 reg) — não é medido. **Lacuna** = footfall
   medido por área × hora.
3. **Fonte vencedora grátis = Matriz O-D telefonia VIVO da RMBH** (Agência RMBH): 393 zonas (165 em BH) × **5 faixas
   horárias** × 2 períodos (nov/2019, mai/2021). Dá o **"quando"**. Aberta, por zona+hora+shapefile.
4. **Unidade certa = H3** (não setor): `exposure_cell`/`h3_cell` já são H3×hora com `pop` preenchido (2.615 hex,
   ~340 m). A fase **F-b = `UPDATE exposure_cell SET footfall = pop × perfil_norm(zona_do_hex, hora)`**.
5. **Normalização = NÍVEL × FORMA.** (a) ÷ duração da faixa → viagens/hora; (b) ÷ média → índice adimensional.
   Pico/vale **11,2×**. O perfil **varia por zona**: Praça Sete = comuter (6h=222, 19h=34); Pampulha = pico tarde.
   Modelo hoje crava 82 na madrugada (real ~12) e no pico (real ~129).
6. **Granularidade difere por frente.** Banca precisa **fino por ponto** → **CNEFE** (endereço) é o mais fino grátis.
   Ônibus tolera grosso → telefonia basta. Hierarquia grátis (fino→grosso): **CNEFE (endereço) > embarque/parada
   (ponto) > grade 200 m > hex 340 m > zona OD**.
7. **Custo Claro Geodata ≈ R$ 810 mil** (benchmark CPTM, projeto estadual; BH/banca seria menos, dezenas–centenas de
   milhares). Tecnicamente ideal pra banca (separa lados de via), mas caro → **só com cliente âncora/receita**.
8. **Embarque por parada já está no nosso banco, subaproveitado:** `raw.pbh__embarque_ped_sublinha` = 8.444 pontos
   geolocalizados × hora × volume — mas o `core` reduziu a `line_boarding_profile` (share por linha). **Re-normalizar
   dá footfall por parada de graça.**
9. **Grade 200 m IBGE validada** (`grade_id36`, 221 m, SIRGAS 2000): cobre a **RMBH inteira** (nosso banco só BH).
   Descer pra 50 m só via **dasymetric com âncora** (CNEFE/prédios) — mas pra banca melhor usar o CNEFE direto.
10. **Unacast** = testar sample (validar **cobertura BH** + se tem footfall **por POI**, não só grade H3 340 m).
    **Strava Metro** descartado (sem download aberto; parceria, provável inelegível; viés de esportistas).

## Artefatos no repo

- `docs/pesquisa-fontes/2026-06-11-scout-fontes.md` — relatório das 2 rodadas do scout + validações (embarque, grade,
  contratos Claro, fontes grátis novas).
- `docs/plano-recalibracao-impressao-back-bus.md` — plano F-a→F-d + método de normalização/cruzamento (seção 6.1).
- `docs/pesquisa-fontes/viz/` — **footfall-comparacao.html** (constante vs telefonia) e **cruzamento.html** (4 zonas).
- Memórias: `uai-ooh-frente-bancas-revista`, `uai-ooh-footfall-proxy-calibracao`, `uai-ooh-footfall-granularidade-por-frente`.

## Dados EFÊMEROS (`/tmp/odrmbh` — somem; rebaixar pelos links no scout-fontes.md)

`faixa.csv` (O-D telefonia horária) · `shp_rmbh_v3.*` (zonas OD) · `g36/` (grade 200 m) · `zonas_bh.json` (165 zonas BH
parseadas). Links de download e comandos (User-Agent p/ shp, FTP IBGE p/ grade) estão no scout-fontes.md.

## Fixture de teste

**Banca Glória** — POI `164899`, Av. Afonso Pena 726, `-19.919615, -43.938639`, **zona OD 69** (Praça Sete),
perfil comuter (6h=222). 222 moradores vs 1.801 POIs em 150 m.

## Próximos passos (abertos, priorizados)

1. **Validar o CNEFE de BH** — baixar, contar estabelecimentos por área; é o mais fino pra banca (por endereço).
2. **Re-normalizar embarque por parada** (raw→core) — footfall por parada de graça (8.444 pontos × hora).
3. **Abrir épico F-b** de recalibração no `uai-ooh-pipeline` — importar zonas (`shp2pgsql`) + `UPDATE exposure_cell`.
4. **Testar sample da Unacast** — confirmar BH + footfall por POI.
5. _(futuro)_ Claro Geodata só com cliente âncora; expansão RMBH usa grade 200 m + telefonia (ambas já cobrem RMBH).

## Pendências de higiene

- Tudo no working tree da branch `docs/f2-replan-medido`, **nada commitado** — revisar/commitar quando quiser.
- `CLAUDE.md` do repo ainda descreve OOH só como "ônibus" — atualizar a frase pra incluir bancas (pendente).
- Promover pro vault Obsidian via `/vault-update` quando fechar (vault = canônico).
