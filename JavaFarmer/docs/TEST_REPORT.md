# Relatório de validação — Java Farmer 0.3.0

Data da validação: 2026-11-06

## Mudanças validadas

- Tempo em ticks/dias do jogo, posse de terra, economia e loja (`Farm`, `FarmWorld`, `CropType`).
- Sensores do robô com simulação espelhada no JVM filho (snapshot do `Farm`).
- Validador ampliado (métodos/campos `static`, arrays, `Math`, métodos de `String`, ternário, for-each, do-while) e detecção de conceitos usados.
- 16 missões, desafio do dia em 6 níveis, save v2 com migração dos saves 0.1/0.2.
- Telas de Objetivos e Loja, execução animada, referência da API, iluminação de dia/noite, terra à venda.
- Correção: o classpath do JVM filho agora é convertido em caminhos absolutos (falhava quando o jogo era iniciado com classpath relativo).
- Correção visual: cantos arredondados da UI sem frestas (substitui `ShapeRenderer.arc`).

## Testes executados neste ambiente

| Verificação | Resultado | Observação |
|---|---|---|
| Compilação de todo o código (core + lwjgl3) | PASS | `javac --release 21` contra o código-fonte do libGDX 1.14.2 e LWJGL 3.4.0. |
| Smoke test do núcleo | PASS | Regras de tempo/crescimento/rega, posse de terra, construções, economia e loja, snapshot, programa com sensores (resultado idêntico no filho e no jogo), detecção de conceitos, 11 programas inseguros rejeitados, **os 16 exemplos das missões compilam e executam**, exemplos completam suas missões, escalonamento dos desafios nos dias 1–60, migração v1→v2 e ida-e-volta do save v2. |
| Execução gráfica real | PASS | Linux + Xvfb + Mesa: menu, novo jogo, terminal, programa executado de verdade (missões 1 e 2 concluídas), fazenda 6x6 com cultivos, Objetivos, Loja, entardecer e noite. |
| Auditoria de assets | PASS | Inclui verificação das fontes BMFont. |
| Exemplo do README | PASS | Compilado e executado pelo runner com sensores. |

## O que não foi validado

- Build Gradle com JDK 25 / Gradle 9.8.2 (sem acesso ao Maven Central neste ambiente).
- macOS (Intel e Apple Silicon) e Windows.
- Áudio.
- Instaladores `.dmg` / `.exe`.
- Balanceamento fino da economia em uma partida longa.

## Próxima validação obrigatória

1. `gradle clean lwjgl3:run` no macOS e no Windows com JDK 25.
2. `./tools/smoke-test.sh` (macOS) ou `tools\smoke-test.bat` (Windows).
3. Jogar do dia 1 até a missão 8 e conferir ritmo de moedas, preços de terra e dificuldade dos desafios.
