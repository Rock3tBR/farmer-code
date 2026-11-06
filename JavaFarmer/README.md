# Java Farmer

**Java Farmer** é um projeto de jogo 3D isométrico para aprender Java programando um robô fazendeiro. A direção artística usa formas arredondadas, cores de massinha e iluminação suave, seguindo a referência visual enviada pelo usuário sem copiar assets de jogos comerciais.

> **Estado da entrega:** vertical slice 0.2.0 — direção visual **Cozy Clay** aplicada (blocos 3D arredondados, cultivos por espécie, robô-trator, drone, construções da fazenda e interface em "massinha"), além da correção do bug de renderização que deixava o mapa listrado/fragmentado. Ainda não é o produto completo descrito no Prompt Mestre.

> **Validação desta entrega:** smoke test do núcleo, auditoria de assets e execução gráfica real (Linux/Xvfb/Mesa, JDK 21) passaram. O build Gradle com JDK 25 e os testes em Windows/macOS ainda precisam ser feitos na máquina de destino. Consulte [o relatório de testes](docs/TEST_REPORT.md).

## Stack

- Java 25 LTS
- libGDX 1.14.2
- LWJGL 3.4.3 (compatibilidade com JDK 25+)
- Gradle 9.8.2
- Renderização 3D com `ModelBatch`, `ModelInstance`, `Environment` e modelos "clay" procedurais (cubos arredondados com oclusão suave em cor de vértice), gerados uma única vez no início
- Fontes bitmap Fredoka (UI) e JetBrains Mono (terminal), licença SIL OFL, geradas por `tools/bake_fonts.py`
- Salvamento local em JSON

## O que já funciona

- Implementação do menu inicial com começar, continuar, desafios, configurações e sair.
- Fazenda 3D isométrica no estilo Cozy Clay: terra, terra arada, terra molhada, grama, água, robô-trator animado (gira para a direção do movimento) e drone que o acompanha.
- Cultivos com modelo próprio por espécie e por estágio: trigo, cenoura, girassol e abóbora.
- Construções fixas perto do início (celeiro, caixote de sementes, baú, painel solar e bomba d'água) — são obstáculos e não podem ser atravessadas.
- Decoração procedural discreta (tufos, flores, pedrinhas, cactos) e borda de "ilha" que acompanha a câmera.
- Mundo procedural determinístico: caminhe com WASD e explore novas coordenadas sem um limite fixo de mapa.
- Terminal integrado com editor (cursor visível, setas ↑↓←→, Home/End, indentação automática e realce de sintaxe Java) e compilação de Java real.
- API didática `FarmerBot`: `move`, `till`, `plant`, `water`, `waitCycles`, `harvest` e `log`.
- Execução de código em processo separado, com lista permitida de sintaxe/API, limite de memória e timeout.
- Progresso local: moedas, sementes, pontos, ciclos, cultivos e posição.
- Desafio diário local, com recompensa de pontos, moedas e sementes.
- Configurações de volume, brilho e qualidade gráfica.
- Música ambiente original e efeitos sonoros curtos.
- Auditoria de integridade e tamanho dos assets.

## Iniciar

Requisitos: JDK 25 e Gradle 9.8.2. Veja as instruções específicas:

- [Instalação no Windows](docs/INSTALL_WINDOWS.md)
- [Instalação no macOS](docs/INSTALL_MACOS.md)
- [Relatório de testes e limitações de validação](docs/TEST_REPORT.md)

Depois de instalar os requisitos:

```bash
gradle lwjgl3:run
```

Para rodar a auditoria de lógica e assets:

```bash
# macOS
./tools/smoke-test.sh

# Windows PowerShell / CMD
.\tools\smoke-test.bat
```

## Controles

| Tecla | Ação |
|---|---|
| W / A / S / D | Mover o robô |
| T | Abrir terminal Java |
| C | Abrir desafio diário |
| ESC | Voltar ao menu / fechar terminal ou desafios |
| Setas (no terminal) | Mover o cursor no código |
| Clique | Usar botões e focar o editor |

No terminal, digite Java e pressione **EXECUTAR** com o mouse. O exemplo inicial prepara o solo, planta trigo, avança três ciclos e colhe.

## Exemplo de programa

```java
import com.javafarmer.api.FarmerBot;

public class PlayerProgram {
    public static void main(String[] args) {
        FarmerBot.till();
        FarmerBot.plant("WHEAT");
        FarmerBot.waitCycles(3);
        FarmerBot.harvest();
    }
}
```

Para praticar loops:

```java
import com.javafarmer.api.FarmerBot;

public class PlayerProgram {
    public static void main(String[] args) {
        FarmerBot.till();
        for (int i = 0; i < 3; i++) {
            FarmerBot.plant("WHEAT");
            FarmerBot.move("EAST");
            FarmerBot.till();
        }
    }
}
```

O terminal da versão 0.2.0 aceita uma parte delimitada de Java: classe `PlayerProgram`, `main`, variáveis simples, operações, `if/else`, `for`, `while` e chamadas `FarmerBot`. Não é uma JVM irrestrita dentro do jogo. Essa restrição é intencional.

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
├── core/                      # jogo, mundo, API, executor Java, render/ (cena clay) e ui/ (interface clay)
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

## Limitações conhecidas da 0.2.0

- Os modelos são procedurais (formas arredondadas geradas em código). Não há texturas, sombras projetadas reais (usamos sombras suaves "blob") nem animações rigged.
- Ações de um programa do terminal são aplicadas de uma vez; o robô desliza direto até a posição final em vez de animar passo a passo.
- A câmera é isométrica fixa; não há seleção de blocos por clique nem pathfinding.
- O desafio diário usa a data local do computador; sem servidor, é possível alterar o relógio do sistema.
- A execução Java é restrita por AST allowlist e roda em subprocesso com timeout/limite de memória. Isso é uma camada de contenção para o modo local, não substitui um sandbox de sistema operacional contra um atacante determinado.
- A versão atual ainda não tem tutorial por capítulos, árvore tecnológica, loja, contratos, salvamento em nuvem, multiplayer ou geração de desafios remotos.
- A compilação final e o teste visual precisam ser feitos em Windows e macOS reais, incluindo macOS Intel e Apple Silicon.

## Referências oficiais de tecnologia

- [libGDX 1.14.2](https://github.com/libgdx/libgdx/releases)
- [libGDX: criação de projetos](https://libgdx.com/wiki/start/project-generation)
- [libGDX: modelos 3D](https://libgdx.com/wiki/graphics/3d/models)
- [libGDX: ModelBatch](https://libgdx.com/wiki/graphics/3d/modelbatch)
- [gdx-liftoff: Java 25 e LWJGL 3.4.3](https://github.com/libgdx/gdx-liftoff)
- [Gradle 9.8.2](https://docs.gradle.org/9.8.2/release-notes.html)
- [Java SE: versões LTS](https://www.oracle.com/br/java/technologies/java-se-support-roadmap.html)
