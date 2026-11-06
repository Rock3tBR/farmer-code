# Instalação — Windows 10/11

## Requisitos

- Windows 10 ou 11 de 64 bits.
- JDK 25 (não somente JRE), com `JAVA_HOME` configurado.
- Gradle 9.8.2.
- Python 3 para executar a auditoria de integridade dos assets.
- Driver de vídeo atualizado com suporte a OpenGL compatível com LWJGL3.
- Internet na primeira compilação para baixar dependências Maven.

## 1. Instalar Java 25

Instale uma distribuição JDK 25 LTS, por exemplo Eclipse Temurin ou Oracle JDK. Depois abra um novo PowerShell e confira:

```powershell
java -version
javac -version
```

Ambos devem indicar versão 25. Configure `JAVA_HOME` para a pasta do JDK e adicione `%JAVA_HOME%\bin` ao `Path`.

## 2. Instalar Gradle 9.8.2

Siga a documentação oficial: https://gradle.org/install/ . Instale a versão 9.8.2 e confirme:

```powershell
gradle --version
```

A JVM exibida pelo Gradle deve ser Java 25.

## 3. Executar o jogo

Extraia a pasta `JavaFarmer` para um caminho sem caracteres especiais, por exemplo `C:\Games\JavaFarmer`.

No PowerShell, dentro da pasta:

```powershell
gradle lwjgl3:run
```

Ou dê dois cliques em `run-windows.bat`.

Na primeira execução, o Gradle baixa libGDX, LWJGL e demais dependências. É necessário estar conectado à internet.

## 4. Executar testes

```powershell
.\tools\smoke-test.bat
```

O script compila o módulo principal, executa testes do mundo e do executor Java e audita arquivos PNG/WAV.

## 5. Gerar instalador EXE

No Windows, com JDK 25 e Gradle instalados:

```powershell
.\package-windows.ps1
```

O comando `jpackage` deve estar disponível no PATH. A geração de instalador EXE pode exigir WiX Toolset compatível; consulte a documentação da versão do JDK instalada. O arquivo gerado ficará em `dist/windows`.

## Problemas comuns

**`JAVA_HOME` aponta para uma versão diferente:** configure o caminho para o JDK 25 e abra um novo terminal.

**Gradle usa uma JVM antiga:** execute `gradle --version`; ajuste `JAVA_HOME` e `Path`.

**Tela preta ou erro de OpenGL:** atualize o driver da GPU. Em máquina virtual ou desktop remoto, o suporte OpenGL pode ser limitado.

**Áudio não inicia:** verifique se o dispositivo de áudio do Windows está habilitado. O jogo deve continuar executando sem áudio.
