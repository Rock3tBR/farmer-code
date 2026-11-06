# Guia de instalação — Java Farmer no macOS

Este guia foi escrito para quem nunca configurou Java no Mac. Siga a ordem dos passos e copie os comandos exatamente como aparecem.

> **Estado do protótipo:** esta versão ainda precisa ter o primeiro build gráfico validado em um Mac real. O guia prepara o ambiente e explica como iniciar o projeto; não significa que um instalador `.dmg` já tenha sido gerado ou testado. Consulte `docs/TEST_REPORT.md` para ver o que foi e o que não foi validado.

## O que você vai instalar

- **JDK 25:** ambiente Java completo, incluindo `java`, `javac` e `jpackage`.
- **Gradle 9.8.2:** ferramenta que baixa bibliotecas, compila e inicia o jogo.
- **Python 3:** usado somente pelo script de auditoria de assets/testes.
- **Java Farmer:** os arquivos do projeto que você baixou.

Você precisa de internet na primeira compilação para baixar as dependências do jogo.

## Antes de começar: descubra qual é o processador do Mac

1. Clique no menu Apple ``, no canto superior esquerdo.
2. Clique em **Sobre Este Mac**.
3. Veja o campo **Chip** ou **Processador**:
   - `Apple M1`, `M2`, `M3`, `M4` ou outro Apple Silicon: arquitetura `arm64`.
   - `Intel`: arquitetura `x86_64`.

Você também pode conferir pelo Terminal:

```bash
uname -m
```

O resultado esperado é `arm64` ou `x86_64`. Guarde essa informação: não misture componentes Intel e Apple Silicon na mesma instalação.

## Passo 1 — Abra o Terminal

1. Pressione `Command (⌘) + Espaço` para abrir o Spotlight.
2. Digite `Terminal`.
3. Pressione `Enter`.

Todos os comandos deste guia devem ser colados no Terminal e executados com `Enter`. Quando o macOS pedir sua senha durante uma instalação, digite a senha de login do Mac. **A senha não aparece na tela enquanto você digita; isso é normal.**

## Passo 2 — Instale o Homebrew (se ainda não tiver)

O Homebrew é um gerenciador de programas para macOS. Ele facilita a instalação do Java e do Python.

Primeiro, verifique se ele já está instalado:

```bash
brew --version
```

Se aparecer uma versão, pule para o Passo 3. Se aparecer `command not found`, instale-o pelo comando oficial:

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

Leia as mensagens do instalador e confirme quando solicitado. Ao terminar, o instalador mostrará comandos para configurar o `PATH`. **Execute os comandos que ele próprio mostrar.**

Em instalações padrão, o comando de inicialização costuma ser um destes:

**Mac com Apple Silicon:**

```bash
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"
```

**Mac com Intel:**

```bash
echo 'eval "$(/usr/local/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/usr/local/bin/brew shellenv)"
```

Confira se funcionou:

```bash
brew --version
```

Se o comando continuar não sendo encontrado, feche o Terminal, abra novamente e tente mais uma vez. Consulte também o guia oficial do Homebrew: <https://docs.brew.sh/Installation>.

## Passo 3 — Instale o Java 25 (JDK)

Use o Eclipse Temurin 25, uma distribuição OpenJDK. O Homebrew seleciona o instalador adequado à arquitetura do Mac.

Copie e execute:

```bash
brew install --cask temurin@25
```

Quando terminar, feche e abra o Terminal novamente. Agora confira se o Java 25 está disponível:

```bash
java -version
javac -version
/usr/libexec/java_home -V
```

Você deve ver uma versão começando com `25` tanto em `java` quanto em `javac`.

**Atenção:** `java` e `javac` são comandos diferentes. O primeiro executa programas; o segundo compila código. O projeto precisa dos dois, portanto instalar somente uma JRE não é suficiente.

Se o Mac tiver mais de uma versão do Java, selecione o JDK 25 nesta janela do Terminal:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
```

Confira novamente:

```bash
java -version
javac -version
echo "$JAVA_HOME"
```

O caminho de `JAVA_HOME` deve apontar para uma instalação que contenha `Contents/Home` e corresponda ao JDK 25.

## Passo 4 — Instale exatamente o Gradle 9.8.2

Este projeto foi configurado para Gradle **9.8.2**. Não use `brew install gradle` neste guia: o Homebrew pode disponibilizar outra versão, diferente da que o projeto espera.

Vamos instalar a distribuição oficial e isolá-la dentro da sua pasta de usuário. Não é necessário usar `sudo`.

1. Baixe o ZIP oficial:

```bash
mkdir -p "$HOME/Downloads" "$HOME/.local/gradle"
curl -fL "https://services.gradle.org/distributions/gradle-9.8.2-bin.zip" -o "$HOME/Downloads/gradle-9.8.2-bin.zip"
```

2. Extraia o arquivo:

```bash
unzip -q "$HOME/Downloads/gradle-9.8.2-bin.zip" -d "$HOME/.local/gradle"
```

3. Configure o Terminal para encontrar Java 25 e Gradle 9.8.2 automaticamente. O macOS usa Zsh por padrão. Acrescente estas linhas ao arquivo de configuração:

```bash
cat <<'EOF' >> "$HOME/.zprofile"

# Java Farmer — ambiente de desenvolvimento
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$HOME/.local/gradle/gradle-9.8.2/bin:$JAVA_HOME/bin:$PATH"
EOF
```

4. Carregue as configurações sem reiniciar o Mac:

```bash
source "$HOME/.zprofile"
```

5. Confira a versão instalada:

```bash
gradle --version
```

Na saída, confirme estes dois pontos:

- Gradle: `9.8.2`.
- JVM: `25`.

Se aparecer outra versão de Gradle, execute `which gradle` para descobrir qual instalação está sendo usada. O caminho esperado começa com a sua pasta pessoal e contém `.local/gradle/gradle-9.8.2/bin/gradle`.

## Passo 5 — Instale o Python 3

O jogo não usa Python para funcionar. Ele é necessário para executar a auditoria automática dos arquivos de imagem e áudio.

Instale-o assim:

```bash
brew install python
```

Confira:

```bash
python3 --version
```

Se aparecer `Python 3.x`, está pronto.

## Passo 6 — Extraia o Java Farmer

1. Localize o arquivo `JavaFarmer_Prototype_0.3.0.zip` na pasta **Downloads**.
2. Dê dois cliques no ZIP para o Finder extrair a pasta `JavaFarmer`.
3. Mova a pasta `JavaFarmer` para um local simples, por exemplo, `Documentos` ou `Desenvolvimento`.
4. Não mova ou renomeie as pastas `core`, `lwjgl3`, `assets` ou `tools` que ficam dentro dela.

## Passo 7 — Abra a pasta correta no Terminal

A forma mais simples de evitar erros de caminho:

1. Abra o Terminal.
2. Digite `cd ` (com um espaço depois de `cd`, mas ainda não pressione Enter).
3. Arraste a pasta `JavaFarmer` do Finder para dentro da janela do Terminal. O macOS preencherá o caminho completo.
4. Pressione `Enter`.

O comando ficará parecido com este exemplo (o seu caminho pode ser diferente):

```bash
cd "$HOME/Documents/JavaFarmer"
```

Confirme que está na pasta certa:

```bash
pwd
ls
```

Você deve encontrar arquivos e pastas como `build.gradle`, `settings.gradle`, `core`, `lwjgl3`, `assets`, `docs` e `tools`.

> **Importante:** os comandos seguintes devem ser executados dentro da pasta principal `JavaFarmer`, não dentro de `core`, `lwjgl3` ou `assets`.

## Passo 8 — Faça a primeira compilação e abra o jogo

Primeiro, deixe o Gradle baixar as bibliotecas necessárias e compilar o projeto:

```bash
gradle lwjgl3:run
```

Na primeira execução, pode levar mais tempo porque o Gradle precisa baixar dependências da internet. Não feche o Terminal enquanto a compilação estiver acontecendo.

Se tudo estiver correto, a janela do Java Farmer será aberta. Mantenha o Terminal aberto enquanto estiver jogando. Para encerrar, feche a janela do jogo ou pressione `Control + C` no Terminal.

**Execução alternativa:** o arquivo `run-macos.command` também pode ser usado. No Terminal, dentro da pasta principal do projeto:

```bash
chmod +x run-macos.command
./run-macos.command
```

Se o Finder bloquear a abertura do arquivo `.command`, execute pelo Terminal como mostrado acima.

## Passo 9 — Teste o projeto e confira os assets

Ainda na pasta principal `JavaFarmer`, execute:

```bash
chmod +x tools/smoke-test.sh tools/asset_audit.py
./tools/smoke-test.sh
```

Esse processo:

1. Compila o módulo principal do jogo.
2. Executa os testes automatizados do núcleo Java.
3. Verifica integridade dos arquivos PNG e WAV.
4. Aponta assets vazios, corrompidos ou acima do limite configurado.

Ao final, procure as mensagens `Core smoke test: PASS` e `ASSET AUDIT: PASS`. Se aparecer `FAIL` ou `ERRO`, copie a mensagem completa do Terminal antes de tentar alterar arquivos.

## Passo 10 — Gerar o instalador DMG (opcional)

Só faça este passo depois que o jogo abrir corretamente pelo Passo 8.

1. Confirme que o JDK 25 está ativo:

```bash
jpackage --version
```

2. Na pasta principal do projeto, execute:

```bash
chmod +x package-macos.sh
./package-macos.sh
```

3. Quando terminar, procure o arquivo `.dmg` dentro de:

```text
dist/macos/
```

4. Dê dois cliques no DMG, arraste o aplicativo para a pasta **Applications / Aplicativos** quando o instalador apresentar essa opção e ejete o volume depois de copiar.

O instalador deve ser criado no próprio Mac em que será utilizado. Para distribuir builds específicas de Apple Silicon e Intel, gere e teste cada arquitetura no hardware correspondente.

> **Aviso de segurança do macOS:** o protótipo não está assinado com certificado Developer ID nem notarizado pela Apple. Por isso, o Gatekeeper pode exibir um aviso ao abrir o aplicativo gerado localmente. Só autorize a abertura se você mesmo gerou o DMG a partir deste código e confia nos arquivos. Não desative o Gatekeeper globalmente. Para distribuição pública, será necessário configurar assinatura e notarização com uma conta Apple Developer.

## Controles do jogo

| Tecla | Ação |
|---|---|
| `W` / `A` / `S` / `D` | Mover o robô |
| `T` | Abrir o terminal Java |
| `C` | Abrir o desafio diário |
| `Esc` | Voltar ao menu ou fechar o terminal |
| Clique do mouse | Interagir com botões e editor |

## Problemas comuns e como resolver

### `brew: command not found`

O Homebrew não foi instalado ou o caminho não foi adicionado ao Terminal. Volte ao Passo 2, execute os comandos `shellenv` correspondentes ao seu processador e abra uma nova janela do Terminal.

### `java: command not found` ou `Unable to locate a Java Runtime`

Execute:

```bash
/usr/libexec/java_home -V
```

Se não listar um JDK 25, reinstale o Temurin com `brew install --cask temurin@25`. Depois selecione o JDK 25 com `export JAVA_HOME=$(/usr/libexec/java_home -v 25)`.

### `javac: command not found`

Isso normalmente indica que você instalou apenas um runtime, ou que o `PATH` está apontando para outra versão. Instale o JDK completo e execute:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
javac -version
```

### `gradle: command not found`

Execute:

```bash
source "$HOME/.zprofile"
which gradle
gradle --version
```

Se ainda não encontrar o Gradle, refaça o Passo 4 e confirme que a pasta `~/.local/gradle/gradle-9.8.2/bin` existe.

### Gradle usa Java 21 ou outra versão

Selecione explicitamente o JDK 25 e pare processos antigos do Gradle:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
gradle --stop
gradle --version
```

Confirme que a saída mostra JVM 25.

### `Permission denied` ao executar um script

Na raiz do projeto, execute:

```bash
chmod +x run-macos.command tools/smoke-test.sh package-macos.sh
```

Tente novamente o comando desejado.

### Erro nativo ou janela que fecha imediatamente

Confirme a arquitetura com `uname -m`, o Java com `java -version` e execute novamente `gradle lwjgl3:run` pelo Terminal. Assim, a mensagem de erro ficará visível. Copie toda a saída do Terminal para diagnosticar; não instale bibliotecas aleatórias da internet.

### O jogo não abre ou apresenta erro gráfico

O backend desktop utiliza OpenGL. Atualize o macOS e tente novamente. O suporte de OpenGL no macOS tem limites próprios; a documentação do libGDX informa que o perfil Core disponível chega a OpenGL 4.1. Se houver erro, registre o modelo do Mac, versão do macOS, arquitetura (`arm64`/`x86_64`) e a mensagem do Terminal.

## Como desinstalar as ferramentas opcionais

- **Java Farmer:** apague a pasta `JavaFarmer` e, caso tenha criado um DMG, o aplicativo em `Aplicativos`.
- **Gradle instalado por este guia:** apague `~/.local/gradle/gradle-9.8.2` e remova do `~/.zprofile` a linha que adiciona essa pasta ao `PATH`.
- **Temurin 25 e Python:** podem ser removidos pelo Homebrew, mas faça isso somente se nenhum outro projeto seu depender deles.

## Links oficiais utilizados

- [Eclipse Temurin 25 no Homebrew](https://formulae.brew.sh/cask/temurin@25)
- [Instruções de instalação do Homebrew](https://docs.brew.sh/Installation)
- [Instalação oficial do Gradle 9.8.2](https://docs.gradle.org/9.8.2/userguide/installation.html)
- [Distribuição oficial do Gradle 9.8.2](https://services.gradle.org/distributions/gradle-9.8.2-bin.zip)
- [Java 25 — downloads oficiais da Oracle](https://www.oracle.com/java/technologies/downloads/)
- [libGDX — suporte OpenGL](https://libgdx.com/wiki/graphics/opengl-es-support)
