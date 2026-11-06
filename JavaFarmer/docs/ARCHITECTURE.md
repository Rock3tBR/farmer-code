# Arquitetura — Java Farmer 0.2.0

## Visão geral

```text
lwjgl3 (desktop bootstrap)
  └── JavaFarmerGame (loop, telas, entrada, ciclo de vida)
        ├── render/FarmRenderer (câmera, luzes, cache de blocos, robô/drone)
        │     ├── render/ClayModels (todos os modelos procedurais; dono do dispose)
        │     ├── render/ClayKit (cubo arredondado, esferas, cilindros, AO em cor de vértice)
        │     └── render/ClayPalette (paleta Cozy Clay compartilhada com a UI)
        ├── ui/ClayUi (painéis, botões em pílula, chips, ícones, fontes bitmap)
        ├── world/FarmWorld + FarmTile (mundo procedural, estado dos blocos e construções fixas)
        ├── api/FarmerBot (API Java que o jogador aprende)
        ├── runtime/JavaProgramRunner (validar, compilar, executar subprocesso)
        └── save/GameSave + GameSettings (persistência JSON)
```

## Módulos

### `core`

Contém lógica de jogo independente do inicializador desktop. O módulo depende do libGDX core e não importa código de `lwjgl3`.

### `lwjgl3`

Cria a janela desktop e fornece backend OpenGL, áudio e entrada por LWJGL 3. A configuração de dependências fixa LWJGL 3.4.3 e seleciona somente o native classifier da plataforma/arquitetura que está fazendo o build. Gere cada instalador no seu sistema de destino (Windows, macOS Intel ou Apple Silicon).

## Mundo procedural

O terreno é calculado deterministicamente a partir de `worldSeed`, `x` e `z`. Não é necessário armazenar o mundo inteiro. Apenas blocos modificados (solo preparado ou cultivos) precisam ser salvos. Blocos vazios antigos podem ser descartados e recalculados a partir da seed.

O protótipo cria uma área fértil ao redor da origem para o tutorial, e gera grama, terra e água conforme o jogador explora. A movimentação impede entrar em blocos de água.

## Execução de Java do jogador

O terminal usa `ToolProvider.getSystemJavaCompiler()` para compilar `PlayerProgram.java`. Antes da compilação, `SafeProgramValidator` analisa a árvore sintática pública do compilador (`com.sun.source.tree`) e permite apenas construções simples e invocações de métodos da classe `FarmerBot`.

O código é compilado em uma pasta temporária e executado num processo Java separado, com heap limitado e timeout. `FarmerBot` escreve mensagens marcadas (`@@JF|...`) em stdout. O processo principal interpreta apenas os marcadores conhecidos e aplica as ações ao mundo. A pasta temporária é removida após a execução.

O executor exige um **JDK**, pois usa `JavaCompiler`. Um JRE isolado não é suficiente para executar programas do jogador nesta versão.

### Limite de segurança conhecido

A lista permitida reduz bastante a superfície de ataque, mas subprocesso + allowlist não equivale a sandbox de sistema operacional. A versão atual é destinada a jogo local, com o próprio usuário escrevendo seus programas. Antes de aceitar código de terceiros, desafios compartilhados ou multiplayer, executar o código num sandbox OS/VM/container com filesystem e rede bloqueados.

## Persistência

- `saves/slot1.json`: progresso, seed, moedas, pontos, sementes, posição, ciclos, desafio do dia e blocos modificados.
- `saves/settings.json`: volume, brilho e qualidade gráfica.

Os arquivos ficam no diretório de trabalho local do jogo. Para uma distribuição comercial, mover saves para um diretório de dados apropriado por usuário/OS e incluir versão do schema, backup e recuperação.

## Renderização (Cozy Clay)

- `OrthographicCamera` isométrica que segue o robô com suavização; o zoom muda entre menu (23 blocos) e jogo (17 blocos).
- **O buffer de profundidade é limpo a cada frame** (`ScreenUtils.clear(..., true)`). Na 0.1.0 ele não era limpo, o que causava o mapa listrado/fragmentado assim que a câmera se movia. O launcher pede depth buffer de 24 bits e MSAA 4x.
- `ClayKit` gera cubos arredondados (projeção de uma grade de cubo sobre um núcleo + raio), com normais suaves e gradiente vertical em cor de vértice que imita oclusão ambiente. Todas as formas usam um material branco e cor por vértice, então a maioria dos modelos tem uma malha/um draw call.
- `ClayModels` cria todos os modelos uma única vez (terreno, 3 variações de grama, água brilhante, broto, 4 cultivos × 3 estágios, decorações, trator, drone, hélice, sombra, celeiro, caixote, baú, painel solar, bomba) e os descarta em `dispose()`.
- `FarmRenderer` guarda uma `ModelInstance` por bloco visível num `LongMap` (sem boxing), recriada só quando a "assinatura" do bloco muda (terreno, arado, molhado, cultivo, estágio, construção). O cache é podado periodicamente pelo raio visível.
- Luz: ambiente quente + luz principal vinda do canto superior esquerdo da tela + preenchimento frio fraco do lado direito.
- A borda da área visível encolhe e afunda os blocos, formando uma "ilha" de diorama.

## Interface

`ClayUi` desenha em modo imediato: retângulos arredondados sem sobreposição de triângulos (transparência uniforme), placas com "lábio" inferior escuro, botões em pílula com hover/pressionado, chips do HUD com ícones procedurais e título com extrusão. `SpriteBatch.end()` desliga o blending do OpenGL, por isso cada passada de formas o reativa.

As fontes são BMFont geradas por `tools/bake_fonts.py` a partir de Fredoka e JetBrains Mono (SIL OFL, licenças em `assets/fonts/`). São desenhadas reduzidas com mipmaps, o que mantém o texto nítido em telas comuns e Retina. Se os arquivos faltarem, a UI volta para a fonte embutida do libGDX.

## Próximas extrações arquiteturais recomendadas

À medida que o projeto crescer, retirar de `JavaFarmerGame`:

- `GameScreen` / `MenuScreen` / `SettingsScreen` / `TerminalScreen`.
- `FarmRenderer` e `AssetManager`.
- `DailyChallengeService` e `EconomyService`.
- `SaveService` com migrações/versionamento.
- `MissionEngine` para objetivos e avaliação automática.
