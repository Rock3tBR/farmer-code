# Java Farmer

**Java Farmer** é um jogo 3D isométrico em que você aprende Java programando um robô fazendeiro: a ideia é a de jogos de "automatize sua fazenda com código", só que com Java de verdade. Você escreve programas, o robô os executa passo a passo na fazenda, as colheitas viram moedas, as moedas compram sementes, terra e melhorias, e as missões ensinam um conceito novo de cada vez. A direção artística é **Cozy Clay** (formas arredondadas, cores de massinha, luz suave), sem copiar assets de jogos comerciais.

> **Estado da entrega:** vertical slice 0.3.0 — loop completo de progressão: tempo em dias e horas do jogo, fazenda própria que cresce comprando terra, loja, 16 missões didáticas (de `move()` até uma fazenda totalmente automatizada) e desafio diário cuja dificuldade aumenta com o número de dias.

> **Validação desta entrega:** smoke test do núcleo (regras, economia, sensores, validador, missões, desafios e migração de save), auditoria de assets e execução gráfica real (Linux/Xvfb/Mesa, JDK 21) passaram. O build Gradle com JDK 25 e os testes em Windows/macOS ainda precisam ser feitos na máquina de destino. Veja [o relatório de testes](docs/TEST_REPORT.md).

## Stack

- Java 25 LTS
- libGDX 1.14.2
- LWJGL 3.4.3 (compatibilidade com JDK 25+)
- Gradle 9.8.2
- Renderização 3D com `ModelBatch` e modelos "clay" procedurais gerados uma única vez no início
- Fontes bitmap Fredoka (UI) e JetBrains Mono (terminal), licença SIL OFL, geradas por `tools/bake_fonts.py`
- Salvamento local em JSON (schema v2, com migração automática dos saves antigos)

## Como se joga

1. **Programe.** Abra o terminal (**T**), escreva Java e clique em **EXECUTAR**. O programa é validado, compilado e executado; depois o robô repete as ações na fazenda, uma a uma (velocidade 1x a 8x, **PULAR** ou **PARAR**).
2. **Plante, espere, colha.** Cada cultivo leva um tempo para crescer; regado, cresce 2x mais rápido.
3. **Venda e compre.** Na **LOJA** (**L**): venda a colheita, compre sementes, compre terra (a fazenda vai de 3x3 até 12x12) e melhorias (Regador, Contrato com o mercado).
4. **Siga os objetivos.** Em **OBJETIVOS** (**O**) ficam as missões, cada uma com explicação do conceito e um exemplo que pode ser copiado para o terminal, e o desafio do dia.

### Tempo e dias

| Unidade | Duração |
|---|---|
| 1 ação do robô (andar, arar, plantar, regar, colher, vender) | 6 minutos do jogo |
| `waitCycles(1)` | 1 hora |
| 1 dia | 24 horas (começa às 06:00) |

O dia só avança quando o robô trabalha ou espera. O HUD mostra **DIA n · hh:mm**, e a iluminação acompanha o relógio (tarde alaranjada, noite azulada).

### Cultivos

| Cultivo | Cresce em | Semente | Venda | Liberado |
|---|---|---|---|---|
| Trigo (`WHEAT`) | 3 h | 1 | 3 | início |
| Cenoura (`CARROT`) | 5 h | 5 | 8 | missão 6 |
| Girassol (`SUNFLOWER`) | 7 h | 9 | 15 | missão 10 |
| Abóbora (`PUMPKIN`) | 10 h | 14 | 26 | missão 12 |

### Missões (trilha principal)

1. Olá, robô! — chamar um método · 2. Preparar o solo — sequência · 3. Primeira colheita — o tempo passa · 4. Repetir é com o for · 5. Hora de vender (libera a compra de terra) · 6. Guardar valores — variáveis · 7. Tomar decisões — if/else + sensores · 8. Laços aninhados · 9. Enquanto não fica pronto — while (libera o Regador) · 10. Irrigação · 11. Seus próprios métodos · 12. Rotação de culturas — operador % · 13. Planos com arrays · 14. Expansão · 15. Fazenda automática · 16. Mestre fazendeiro.

A verificação é automática: o jogo analisa quais conceitos Java o programa usou (for, while, if, métodos, arrays, %...) e o que o robô realmente fez.

### Desafio do dia

A cada novo dia do jogo (depois de concluir o desafio anterior) aparece um desafio cuja dificuldade depende do dia:

| Dias | Nível | Exemplos |
|---|---|---|
| 1–2 | 1 | plantar ou colher alguns trigos no total do dia |
| 3–5 | 2 | plantar N cultivos em um único programa usando `for` |
| 6–9 | 3 | colher com `if`, plantar com `while`, regar |
| 10–14 | 4 | preencher uma área k×k com laços aninhados, ganhar moedas |
| 15–20 | 5 | tabuleiro de xadrez com `%`, grandes colheitas com métodos próprios |
| 21+ | 6 | automatizar a fazenda inteira com arrays e métodos com retorno; as quantidades continuam crescendo |

Só são sorteados desafios possíveis com os cultivos, melhorias e terra que você já tem.

## API do robô (`FarmerBot`)

| Ações (gastam tempo) | Sensores (instantâneos) |
|---|---|
| `move("NORTH" / "SOUTH" / "EAST" / "WEST")` | `getX()`, `getY()`, `getFarmSize()` |
| `till()`, `plant("WHEAT")`, `water()`, `harvest()` | `canHarvest()`, `getCrop()`, `getGround()`, `isWatered()` |
| `waitCycles(horas)` | `isMyLand()`, `getDay()`, `getCoins()` |
| `sell("WHEAT")`, `sell("WHEAT", quantidade)`, `log(texto)` | `getSeeds("WHEAT")`, `getItems("WHEAT")` |

As ações devolvem `true`/`false` (sucesso). Coordenadas da fazenda: (0,0) é o canto sudoeste; `x` cresce para o leste e `y` para o norte.

O terminal aceita uma parte delimitada de Java: a classe `PlayerProgram` com métodos e campos `static`, variáveis de tipos primitivos/`String`, arrays (até 2 dimensões), `if/else`, `for`, for-each, `while`, `do-while`, operador ternário, `break`/`continue`/`return`, alguns métodos de `Math` e de `String`, e chamadas `FarmerBot`. Criação de objetos, outras classes, imports, reflexão, arquivos, rede e threads ficam bloqueados — de propósito.

## Exemplo de programa

```java
import com.javafarmer.api.FarmerBot;

public class PlayerProgram {
    static void irPara(int x, int y) {
        while (FarmerBot.getX() < x) FarmerBot.move("EAST");
        while (FarmerBot.getX() > x) FarmerBot.move("WEST");
        while (FarmerBot.getY() < y) FarmerBot.move("NORTH");
        while (FarmerBot.getY() > y) FarmerBot.move("SOUTH");
    }

    public static void main(String[] args) {
        int lado = FarmerBot.getFarmSize();
        for (int y = 0; y < lado; y++) {
            for (int x = 0; x < lado; x++) {
                irPara(x, y);
                if (FarmerBot.canHarvest()) FarmerBot.harvest();
                if (FarmerBot.getCrop().isEmpty()) FarmerBot.plant("WHEAT");
            }
        }
        FarmerBot.sell("WHEAT");
    }
}
```

## Iniciar

Requisitos: JDK 25 e Gradle 9.8.2. Veja as instruções específicas:

- [Instalação no Windows](docs/INSTALL_WINDOWS.md)
- [Instalação no macOS](docs/INSTALL_MACOS.md)
- [Relatório de testes e limitações de validação](docs/TEST_REPORT.md)

```bash
gradle lwjgl3:run
```

Testes de lógica e auditoria de assets:

```bash
./tools/smoke-test.sh      # macOS / Linux
.\tools\smoke-test.bat     # Windows
```

## Controles

| Tecla | Ação |
|---|---|
| W / A / S / D | Mover o robô manualmente (cada passo gasta 6 min) |
| T | Terminal Java |
| O | Objetivos (missões e desafio do dia) |
| L | Loja |
| ESC | Voltar / fechar / menu |
| Setas, Home, End (no terminal) | Mover o cursor no código |

## Construir instaladores

Os instaladores nativos precisam ser montados no próprio sistema operacional de destino:

```bash
# macOS (DMG)
./package-macos.sh
```

```powershell
# Windows (EXE; requer ferramentas de empacotamento do jpackage/WiX)
.\package-windows.ps1
```

O JDK, o Gradle e as dependências serão baixados pelo Gradle na primeira compilação. O ambiente desta entrega não tinha Gradle nem acesso de rede para resolver os artefatos; portanto, os instaladores ainda não foram gerados aqui.

## Estrutura do projeto

```text
JavaFarmer/
├── core/                      # world/ (regras e economia), progress/ (missões e desafios), api/, runtime/, save/, render/, ui/, game/
├── lwjgl3/                    # inicializador desktop
├── assets/                    # música, efeitos, ícones e fonts/ (bitmap + licenças OFL)
├── docs/                      # arquitetura, instalação e referência visual
├── tools/                     # smoke test e auditoria dos assets
├── AGENTS.md                  # instruções para agentes de IA
├── AI_RULES.md                # regras arquiteturais e de segurança
├── package-macos.sh
├── package-windows.ps1
├── run-macos.command
└── run-windows.bat
```

## Limitações conhecidas da 0.3.0

- Um único programa salvo (não há abas de vários scripts).
- Os modelos são procedurais; não há texturas, sombras projetadas reais nem animações rigged.
- O desafio do dia não expira: um novo só aparece depois que o atual é concluído.
- O limite de 250 ações por execução é uma regra de segurança; programas muito grandes precisam ser divididos em execuções.
- A execução Java é restrita por AST allowlist e roda em subprocesso com timeout/limite de memória. Isso protege o modo local, mas não substitui um sandbox de sistema operacional contra um atacante determinado.
- Compilação final e teste visual precisam ser feitos em Windows e macOS reais (Intel e Apple Silicon).

## Referências oficiais de tecnologia

- [libGDX 1.14.2](https://github.com/libgdx/libgdx/releases)
- [libGDX: criação de projetos](https://libgdx.com/wiki/start/project-generation)
- [libGDX: modelos 3D](https://libgdx.com/wiki/graphics/3d/models)
- [libGDX: ModelBatch](https://libgdx.com/wiki/graphics/3d/modelbatch)
- [gdx-liftoff: Java 25 e LWJGL 3.4.3](https://github.com/libgdx/gdx-liftoff)
- [Gradle 9.8.2](https://docs.gradle.org/9.8.2/release-notes.html)
- [Java SE: versões LTS](https://www.oracle.com/br/java/technologies/java-se-support-roadmap.html)
