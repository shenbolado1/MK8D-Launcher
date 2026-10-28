# MK8D-Launcher

English first, Portuguese below / Primeiro em inglês, depois em português.

## English

Launcher for Android of a Mario Kart 8 Deluxe port, based on update 3.0.5.
Snapdragon-only, mid-high-end phones only.

Unofficial project, not affiliated with Nintendo in any way.

### What this repository contains — and what it does not

This repository contains **only the launcher**: the Android start screen
(`launcher/src/.../LauncherActivity.java`) and the packaging script
(`launcher/mk2apk.py`).

It does **not** contain the game, the update, keys, Nintendo artwork,
the translation host, or the data pack. Nothing here lets you play without
owning the game.

### Piracy

I do not endorse piracy and **do not provide links to game copies**.
Use only your own legally acquired copy of the base game plus update 3.0.5.
The launcher imports a data pack already ported on PC from that copy of
yours — it does not port the game and does not accept a raw NSP.

### Requirements

- A mid-high-end Android phone with **Snapdragon**. The port was made and
  tested for Snapdragon; other SoCs are not supported.
- Your own copy of the game (base + update 3.0.5) and a data pack ported
  from it on PC.
- Android 10 or later (minSdk 29).

### What the launcher does

- **Play**: starts the game with the imported data pack.
- **Import data pack**: imports the zip of the already-ported data pack
  (with `RomFS/`, `mods/`, `shaders.dmsv` and `nx2a-data.json` at the root).
  The zip is read straight from the file picker, with no pre-copy.
- **Mode**: Performance (handheld) or Resolution (docked).
- **Present resolution**: 720p or Native.
- **Driver**: the system Turnip driver, or an imported Turnip driver
  (a zip with `libvulkan_*.so`, unpacked to `drivers/<name>/`).
- It writes `env.txt` and `settings.txt`, preserving lines it does not know
  (such as `DM_LANG`, `DM_VK_PROF` and other host keys).

### How to package

Besides this code, the launcher needs a compatible `libdmhost.so`
(the translation host, which is **not** in this repository), an icon,
and optionally your own background art (`launcher_bg.png` or
`launcher_bg.jpg`) and the Bungee font (OFL license). Without art, the
launcher uses the default background; without the font, it uses the
default bold font.

```text
javac -encoding UTF-8 -cp <android.jar da platform android-36> \
  -d classes launcher/src/org/nx2a/t0100152000022000/LauncherActivity.java
d8 --min-api 29 --output dexout $(find classes -name '*.class')
cp dexout/classes.dex classes.dex
python3 launcher/mk2apk.py <libdmhost.so> <saida.apk> <signing-key.json> \
  --package org.nx2a.t0100152000022000 --label 'MKart8Port' \
  --lib dmhost --landscape --icon <icone.png> --release \
  --version-code <codigo> --version-name 3.0.5 --dex classes.dex \
  [--asset launcher_bg.png] [--asset launcher_bg.jpg] [--asset Bungee.ttf]
```

### Legal notice

Mario Kart 8 Deluxe is a Nintendo trademark and work. This launcher is an
independent compatibility project, not endorsed by Nintendo, and it does
not distribute any of the company's copyrighted material.

## Português

Launcher para Android de um port de Mario Kart 8 Deluxe, baseado na atualização 3.0.5.
Somente Snapdragon, somente celulares mid-high end.

Projeto não oficial, sem qualquer afiliação com a Nintendo.

### O que este repositório contém — e o que não contém

Este repositório contém **somente o launcher**: a tela inicial em Android
(`launcher/src/.../LauncherActivity.java`) e o script de empacotamento
(`launcher/mk2apk.py`).

Ele **não contém** o jogo, a atualização, chaves, artwork da Nintendo,
o host de tradução, nem o data pack. Nada aqui permite jogar sem ter o jogo.

### Pirataria

Não endosso pirataria e **não disponibilizo links para cópias do jogo**.
Use somente a sua própria cópia, adquirida legalmente, do jogo base
mais a atualização 3.0.5. O launcher importa um data pack já portado no PC
a partir dessa sua cópia — ele não porta o jogo e não aceita um NSP cru.

### Requisitos

- Celular Android mid-high end com **Snapdragon**. O port foi feito e testado
  para Snapdragon; outros SoCs não são suportados.
- Sua própria cópia do jogo (base + update 3.0.5) e um data pack portado
  a partir dela no PC.
- Android 10 ou superior (minSdk 29).

### O que o launcher faz

- **Play**: inicia o jogo com o data pack importado.
- **Import data pack**: importa o zip do data pack já portado
  (com `RomFS/`, `mods/`, `shaders.dmsv` e `nx2a-data.json` na raiz).
  O zip é lido direto do seletor de arquivos, sem cópia prévia.
- **Mode**: Performance (handheld) ou Resolution (docked).
- **Present resolution**: 720p ou Native.
- **Driver**: o driver Turnip do sistema, ou um driver Turnip importado
  (um zip com `libvulkan_*.so`, descompactado para `drivers/<nome>/`).
- Escreve `env.txt` e `settings.txt` preservando as linhas que não conhece
  (como `DM_LANG`, `DM_VK_PROF` e outras chaves do host).

### Como empacotar

O launcher precisa, além deste código, de um `libdmhost.so` compatível
(o host de tradução, que **não** está neste repositório), de um ícone,
e, opcionalmente, de arte de fundo própria (`launcher_bg.png` ou
`launcher_bg.jpg`) e da fonte Bungee (licença OFL). Sem a arte, o launcher
usa o fundo padrão; sem a fonte, usa a fonte padrão em negrito.

```text
javac -encoding UTF-8 -cp <android.jar da platform android-36> \
  -d classes launcher/src/org/nx2a/t0100152000022000/LauncherActivity.java
d8 --min-api 29 --output dexout $(find classes -name '*.class')
cp dexout/classes.dex classes.dex
python3 launcher/mk2apk.py <libdmhost.so> <saida.apk> <signing-key.json> \
  --package org.nx2a.t0100152000022000 --label 'MKart8Port' \
  --lib dmhost --landscape --icon <icone.png> --release \
  --version-code <codigo> --version-name 3.0.5 --dex classes.dex \
  [--asset launcher_bg.png] [--asset launcher_bg.jpg] [--asset Bungee.ttf]
```

### Aviso legal

Mario Kart 8 Deluxe é marca e obra da Nintendo. Este launcher é um
projeto independente de compatibilidade, sem endosso da Nintendo, e não
distribui nenhum material protegido por direitos autorais da empresa.
