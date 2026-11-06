# 🌱 Java Farmer

### Aprenda Java programando um robô fazendeiro em um mundo 3D!

Java Farmer é um jogo educacional desenvolvido em Java, no qual o jogador aprende conceitos de programação enquanto administra e desenvolve sua própria fazenda.

A proposta é transformar o aprendizado de programação em uma experiência interativa: em vez de apenas estudar códigos e executar exercícios tradicionais, você escreve programas Java que controlam um robô responsável por preparar o solo, plantar, irrigar e colher plantações.

Com um mundo procedural, desafios progressivos e uma interface visual aconchegante, Java Farmer combina aprendizado, exploração e simulação.

---

## 🎮 Sobre o projeto

O jogador assume o controle de uma fazenda automatizada, utilizando um pequeno robô-trator para executar tarefas agrícolas.

Através de um terminal integrado ao jogo, é possível escrever códigos Java reais e utilizar uma API própria para controlar as ações do robô.

O objetivo é aprender programação na prática, explorando conceitos como:

- Variáveis e tipos de dados.
- Operadores e expressões.
- Estruturas condicionais (`if/else`).
- Estruturas de repetição (`for` e `while`).
- Métodos e chamadas de funções.
- Lógica de programação.
- Automação de tarefas.
- Organização e resolução de problemas.

## ✨ Funcionalidades

### 🌾 Simulação agrícola

- Fazenda em ambiente 3D isométrico.
- Geração procedural determinística do mundo.
- Exploração de diferentes áreas.
- Preparação e irrigação do solo.
- Plantio e crescimento de diferentes espécies.
- Colheita e gerenciamento de recursos.
- Construções e obstáculos espalhados pela fazenda.
- Robô-trator animado e drone acompanhante.

### 💻 Programação Java integrada

- Terminal de programação dentro do jogo.
- Editor com cursor, navegação e indentação automática.
- Realce de sintaxe Java.
- Compilação de código Java real.
- API própria para controlar o robô.
- Execução dos programas em processo separado.
- Validação de sintaxe e restrições de execução.

### 🏆 Progressão e desafios

- Sistema de moedas e sementes.
- Pontuação e progresso local.
- Desafios diários.
- Recompensas por atividades.
- Salvamento automático do progresso.
- Configurações de áudio, brilho e qualidade gráfica.

### 🎨 Identidade visual

O jogo utiliza uma direção artística chamada **Cozy Clay**, caracterizada por:

- Modelos 3D com formas arredondadas.
- Cores suaves e iluminação aconchegante.
- Elementos visuais inspirados em massinha de modelar.
- Interface com componentes arredondados.
- Tipografia Fredoka e JetBrains Mono.
- Música ambiente e efeitos sonoros originais.

---

## 🖥️ Tecnologias utilizadas

| Tecnologia | Aplicação |
|---|---|
| Java 25 | Linguagem principal |
| libGDX 1.14.2 | Framework de desenvolvimento do jogo |
| LWJGL 3.4.3 | Integração desktop e OpenGL |
| Gradle 9.8.2 | Build e gerenciamento de dependências |
| JSON | Persistência local |
| BitmapFont | Renderização de fontes |
| Python 3 | Auditoria de assets e ferramentas auxiliares |

---

## 🚀 Como executar

### Requisitos

- JDK 25.
- Gradle 9.8.2.
- Python 3 (para auditorias e testes auxiliares).
- Windows 10/11 ou macOS.
- Conexão com internet na primeira compilação.

### 1. Clonar o repositório

```bash
git clone https://github.com/Rock3tBR/farmer-code.git
```

### 2. Acessar o projeto

```bash
cd farmer-code/JavaFarmer
```

### 3. Executar o jogo

```bash
gradle lwjgl3:run
```

O Gradle fará o download das dependências necessárias e iniciará o jogo.

### Execução alternativa

**macOS:**

```bash
chmod +x run-macos.command
./run-macos.command
```

**Windows:**

```bat
run-windows.bat
```

Para instruções detalhadas de configuração:

- [Instalação no macOS](JavaFarmer/docs/INSTALL_MACOS.md)
- [Instalação no Windows](JavaFarmer/docs/INSTALL_WINDOWS.md)

---

## 🎮 Controles

| Tecla | Ação |
|---|---|
| W / A / S / D | Movimentar o robô |
| T | Abrir terminal Java |
| C | Abrir desafio diário |
| ESC | Voltar ao menu ou fechar uma interface |
| Setas direcionais | Navegar pelo código no terminal |
| Mouse | Interagir com botões e editor |

---

## 👨‍💻 Programando com FarmerBot

O Java Farmer disponibiliza uma API própria chamada `FarmerBot`, responsável por conectar os programas Java às mecânicas do jogo.

### Exemplo de código

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

### Utilizando estruturas de repetição

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

### Métodos disponíveis

| Método | Descrição |
|---|---|
| `move()` | Movimenta o robô |
| `till()` | Prepara o solo |
| `plant()` | Planta uma espécie |
| `water()` | Irriga a plantação |
| `waitCycles()` | Avança ciclos de crescimento |
| `harvest()` | Realiza a colheita |
| `log()` | Exibe mensagens no jogo |

**Importante:** a versão atual trabalha com um subconjunto controlado da linguagem Java. Não se trata de uma JVM irrestrita dentro do jogo.

---

## 🏗️ Arquitetura do projeto

O projeto utiliza uma arquitetura modular, separando as responsabilidades do jogo e da execução desktop.

```text
JavaFarmer/
│
├── core/
│   └── src/main/java/com/javafarmer/
│       ├── api/          # API FarmerBot
│       ├── render/       # Renderização 3D
│       ├── runtime/      # Execução de programas Java
│       ├── save/         # Persistência local
│       ├── ui/           # Interface gráfica
│       └── world/        # Mundo e lógica da fazenda
│
├── lwjgl3/
│   └── src/main/java/    # Inicialização desktop
│
├── assets/
│   ├── audio/            # Músicas e efeitos sonoros
│   ├── fonts/            # Fontes e atlas
│   └── saves/            # Dados locais
│
├── docs/
│   ├── ARCHITECTURE.md
│   ├── INSTALL_MACOS.md
│   ├── INSTALL_WINDOWS.md
│   ├── ROADMAP.md
│   └── TEST_REPORT.md
│
├── tools/                # Testes e auditoria
│
├── AGENTS.md
├── AI_RULES.md
├── build.gradle
└── settings.gradle
```

---

## 🧪 Testes e validação

O projeto possui ferramentas auxiliares para verificar o funcionamento do núcleo e a integridade dos recursos.

**macOS / Linux:**

```bash
./tools/smoke-test.sh
```

**Windows:**

```bat
tools\smoke-test.bat
```

**Auditoria de assets:**

```bash
python tools/asset_audit.py
```

A validação registrada na versão 0.2.0 inclui:

- Testes do núcleo de jogo.
- Verificação da execução de programas Java.
- Testes de restrições de segurança.
- Auditoria de arquivos gráficos e sonoros.
- Execução gráfica em ambiente Linux com Xvfb e Mesa.

A compilação final com JDK 25 e os testes em Windows e macOS reais ainda precisam ser validados.

---

## 🗺️ Roadmap

O Java Farmer está em desenvolvimento. As próximas etapas previstas incluem:

### Próxima fase — Experiência educacional

- [ ] Tutorial interativo de Java.
- [ ] Missões com objetivos programáveis.
- [ ] Progressão por conceitos de programação.
- [ ] Execução animada dos comandos do robô.
- [ ] Seleção de blocos e pathfinding.
- [ ] Feedback didático sobre erros de programação.

### Expansão da fazenda

- [ ] Inventário e gerenciamento de sementes.
- [ ] Loja e sistema de upgrades.
- [ ] Árvore tecnológica.
- [ ] Novos biomas e cultivos.
- [ ] Máquinas e cadeias de produção.
- [ ] Conquistas e contratos.

### Evolução técnica

- [ ] Saves versionados e backup automático.
- [ ] Melhorias de desempenho e gerenciamento de memória.
- [ ] Pipeline de modelos 3D.
- [ ] Animações avançadas e efeitos visuais.
- [ ] Instaladores assinados para Windows e macOS.
- [ ] Validação multiplataforma completa.

Para acompanhar o planejamento detalhado, consulte [ROADMAP.md](JavaFarmer/docs/ROADMAP.md).

---

## 🔐 Segurança

A execução dos programas escritos pelo jogador possui mecanismos de restrição:

- Validação da estrutura sintática Java.
- Lista permitida de operações e métodos.
- Execução em processo separado.
- Limite de memória.
- Tempo máximo de execução.
- Limite de tamanho do código e da saída.

O projeto é voltado à execução local de programas escritos pelo próprio jogador. Esses mecanismos não representam um sandbox completo de sistema operacional.

---

## 📚 Documentação

| Documento | Descrição |
|---|---|
| [Arquitetura](JavaFarmer/docs/ARCHITECTURE.md) | Organização técnica e funcionamento interno |
| [Roadmap](JavaFarmer/docs/ROADMAP.md) | Planejamento de funcionalidades |
| [Instalação macOS](JavaFarmer/docs/INSTALL_MACOS.md) | Configuração e execução no Mac |
| [Instalação Windows](JavaFarmer/docs/INSTALL_WINDOWS.md) | Configuração e execução no Windows |
| [Relatório de testes](JavaFarmer/docs/TEST_REPORT.md) | Resultados de validação |
| [Regras para agentes](JavaFarmer/AGENTS.md) | Orientações para desenvolvimento assistido por IA |
| [Regras arquiteturais](JavaFarmer/AI_RULES.md) | Restrições técnicas e de segurança |

---

## 🤝 Contribuição

Contribuições são bem-vindas!

Antes de implementar mudanças:

1. Consulte a documentação de arquitetura.
2. Leia as regras de desenvolvimento.
3. Verifique as funcionalidades já existentes.
4. Execute os testes após as alterações.
5. Documente novas funcionalidades e mudanças técnicas.

Utilize branches específicas para suas alterações e envie Pull Requests para revisão.

---

## 📄 Licença

A licença de distribuição do projeto ainda não foi definida.

Os arquivos de fontes e recursos de terceiros possuem suas próprias condições de licenciamento, que devem ser respeitadas.

---

**Desenvolvido por [Rock3tBR](https://github.com/Rock3tBR)**

*Transformando código em experiências.* 🌱
