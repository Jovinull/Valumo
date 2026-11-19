"""Laboratório de conteúdo, lote 2 — conjuntos de ofício (micro-composições autorais).

Um conjunto é uma construção-âncora + itens posicionados no REFERENCIAL DELA (u, +z = frente da âncora, giram juntos):
o ofício se lê pelos objetos em volta, sem texto. O gerador procedural pode pôr o conjunto inteiro onde puser a
âncora. Nada aqui é gerado por sorteio: as posições são autoradas e VALIDADAS por geometria (conteudo_e.py e
ConteudoMundoTest): item fora da pegada da âncora e dos outros itens; nenhum item a menos de 0,3 u de uma entrada da
âncora (o corpo do herói passa); todos os ids existem no props.json.
"""

CONJUNTOS = [
    {"id": "conjunto.ferreiro", "nome": "Ofício do ferreiro", "oficio": "ferreiro", "ancora": "workshop_forge",
     "leitura": "forja acesa + placa com bigorna + ferramentas e lenha em volta: 'aqui se trabalha o ferro'",
     "itens": [("sign_trade", -2.1, 2.7, 0), ("tool_rack", -3.0, -0.2, 270), ("crate_stack", 4.4, 0.2, 0),
               ("woodpile_axe", -0.4, -2.3, 180)]},
    {"id": "conjunto.herbalista", "nome": "Ofício da herbalista", "oficio": "herbalista", "ancora": "hut_herbalist",
     "leitura": "cabana redonda + varal de ervas + cestos de ervas + cerca de estacas da horta + placa com folha",
     "itens": [("herb_rack", 3.0, 0.2, 90), ("basket_group__conteudo-ervas", 2.0, 2.9, 0),
               ("fence_picket", -1.5, 3.2, 0), ("sign_trade__oficio-herbalista", 1.0, 3.3, 0)]},
    {"id": "conjunto.comerciante", "nome": "Ofício do comerciante", "oficio": "comerciante", "ancora": "house_tall",
     "leitura": "sobrado com balcão + barraca de feira ao lado + caixotes e sacos + banco + placa com moeda",
     "itens": [("market_stall", 4.6, 2.6, 0), ("crate_stack", 4.0, -0.9, 90), ("bench_wood", -2.4, 2.9, 0),
               ("sign_trade__oficio-comerciante", 1.9, 2.9, 0)]},
    {"id": "conjunto.cacador", "nome": "Ofício do caçador", "oficio": "cacador", "ancora": "cabin_hunter",
     "leitura": "cabana de troncos + lenheiro coberto + lenha com machado + cesto de cogumelos + placa com flecha",
     "itens": [("woodshed_open", -3.9, -0.3, 90), ("woodpile_axe", 2.4, 3.0, 0),
               ("basket_group__conteudo-cogumelos", -1.9, 2.9, 0), ("sign_trade__oficio-cacador", 0.9, 3.4, 0)]},
    {"id": "conjunto.guardiao", "nome": "Posto do guardião de trilhas", "oficio": "guardiao_de_trilhas",
     "ancora": "tower_watch",
     "leitura": "torre de vigia + braseiro de sinal + lanças e escudos + quadro de avisos + banco",
     "itens": [("guard_brazier", -2.2, 1.9, 0), ("weapon_rack", -2.6, -0.6, 90), ("notice_board", 2.4, 2.6, 0),
               ("bench_wood", 0.3, 4.2, 180)]},
]
