"""Rodada 03 — estudos controlados dos protagonistas da E (uma variável por vez).

    adv_r02, adv_h1, adv_h2, adv_h3   cabelo do aventureiro (rosto, roupa e equipamento iguais; rosto neutro padrão)
    adv_face0, adv_face1              rosto padrão × rosto próprio do aventureiro (cabelo escolhido)
    rng_c0, rng_c1, rng_c2, rng_c3    capuz da exploradora (rosto e corpo iguais)
    mage_m0, mage_m1, mage_m2         chapéu da maga (rosto, corpo e cajado iguais)
    mage_staff_r02, mage_staff_r3     cajado (chapéu escolhido)

Saída: assets/characters/r3/<nome>. Comparação: modo LINHA (conjuntos r3-* em App.LINEUPS) e
docs/historico/experimentos/rodada-03-estudos.md. Os heróis finais continuam em tools/chars_e.py.
"""
import chars_e as E


def build(out):
    d = f"{out}/characters/r3"
    for hs in ("r02", "h1", "h2", "h3"):
        E.adventurer("e", hair_style=hs, face={}, name=f"Aventureiro cabelo {hs.upper()}").save(f"{d}/adv_{hs}")
    E.adventurer("e", face={}, name="Aventureiro rosto padrao").save(f"{d}/adv_face0")
    E.adventurer("e", name="Aventureiro rosto proprio").save(f"{d}/adv_face1")
    for hs in ("c0", "c1", "c2", "c3"):
        E.ranger(hs, name=f"Exploradora capuz {hs.upper()}").save(f"{d}/rng_{hs}")
    for hs in ("m0", "m1", "m2"):
        E.mage(hs, staff_style="r02", name=f"Maga chapeu {hs.upper()}").save(f"{d}/mage_{hs}")
    for st in ("r02", "r3"):
        E.mage(staff_style=st, name=f"Maga cajado {st.upper()}").save(f"{d}/mage_staff_{st}")
    # rodada 04: kits de coreografia (mesmo aventureiro, outra arma)
    d4 = f"{out}/characters/r4"
    E.adventurer("e", kit="shield", name="Aventureiro espada e broquel").save(f"{d4}/adventurer_shield")
    E.adventurer("e", kit="longblade", name="Aventureiro lamina longa").save(f"{d4}/adventurer_longblade")
