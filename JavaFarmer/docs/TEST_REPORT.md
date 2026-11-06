# Relatório de validação — Java Farmer 0.2.0

Data da validação: 2026-11-06

## Mudanças validadas

- Correção do bug de renderização (depth buffer não era limpo a cada frame → mapa listrado/fragmentado ao mover a câmera).
- Glifos ausentes na fonte padrão (`•`, `−`) que apareciam como quadrados no HUD e nas configurações.
- Dica inferior que se sobrepunha ao painel de log.
- Translucidez da UI que virava opaca depois de qualquer texto (`SpriteBatch.end()` desliga o blending).
- Tecla **T** que, ao abrir o terminal, também digitava "t" no código.
- Estilo Cozy Clay completo em 3D e na interface.

## Testes executados neste ambiente

| Verificação | Resultado | Observação |
|---|---|---|
| Compilação de todo o código (core + lwjgl3) | PASS | `javac --release 21` contra o código-fonte do libGDX 1.14.2 e LWJGL 3.4.0. |
| Execução gráfica real | PASS | Jogo inteiro rodando em Linux + Xvfb + Mesa (llvmpipe), MSAA 4x; capturas do menu, jogo, movimento, terminal, desafios, configurações e exploração. |
| Smoke test do núcleo | PASS | Ciclo de cultivo, execução Java permitida, bloqueio de API, limite de saída e novos testes das construções (celeiro bloqueia o robô; área inicial livre). |
| Auditoria de assets | PASS | 14 arquivos; inclui nova verificação de fontes BMFont (atlas, limites dos glifos, acentos do português e licença OFL). |

## O que não foi validado

- Build Gradle com JDK 25 / Gradle 9.8.2 (o ambiente não tinha acesso ao Maven Central; foram usados os fontes do libGDX via GitHub e JDK 21).
- macOS (Intel e Apple Silicon) e Windows: ainda é preciso abrir o jogo nessas máquinas.
- Áudio (desligado no teste por falta de dispositivo de som).
- Instaladores `.dmg` / `.exe`.

## Próxima validação obrigatória

1. `gradle clean lwjgl3:run` no macOS e no Windows com JDK 25.
2. `./tools/smoke-test.sh` (macOS) ou `tools\smoke-test.bat` (Windows).
3. Conferir visualmente: menu, HUD, terminal (realce de sintaxe e cursor), movimento do robô, cultivos crescendo e construções bloqueando a passagem.
