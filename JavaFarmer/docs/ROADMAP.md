# Roadmap do jogo

## Implementado na versão 0.1.0 — primeiro vertical slice (build desktop pendente de validação)

- Janela desktop e renderização 3D isométrica.
- Drone/robô e blocos de grama, terra, água e terra preparada.
- Cultivo com plantio, crescimento, irrigação e colheita.
- Mundo procedural determinístico que cresce enquanto o jogador explora.
- Menu, configurações de som/brilho/qualidade e desafio diário local.
- Terminal integrado que compila Java real em um subconjunto permitido.
- Economia inicial com moedas, sementes e pontos.
- Save JSON local, música e efeitos sonoros originais.
- Smoke tests de lógica e auditoria de assets.

## Implementado na versão 0.2.0 — direção visual Cozy Clay

- Correção do bug de profundidade que deixava a cena listrada/fragmentada.
- Blocos arredondados com oclusão suave, grama com relevo, água brilhante, terra arada e molhada.
- Cultivos por espécie e estágio (trigo, cenoura, girassol, abóbora), robô-trator animado e drone acompanhante.
- Celeiro, caixote, baú, painel solar e bomba d'água como obstáculos fixos.
- Interface Cozy Clay (placas, pílulas, chips com ícones), fontes Fredoka/JetBrains Mono e terminal com realce de sintaxe e cursor.

## Implementado na versão 0.3.0 — progressão estilo "automatize sua fazenda"

- Tempo em dias/horas do jogo, que avança com o trabalho do robô; ciclo de dia e noite.
- Fazenda própria de 3x3 a 12x12, comprada aos poucos na loja.
- Loja: venda de colheita, sementes por cultivo, terra e melhorias (Regador, Contrato com o mercado).
- 16 missões didáticas com verificação automática de conceitos Java e exemplos prontos.
- Desafio do dia em 6 níveis de dificuldade, crescendo com os dias.
- Sensores no robô (getX, canHarvest, getCrop...), métodos próprios, arrays e Math no terminal.
- Execução animada passo a passo (1x–8x, pular, parar) e referência da API no terminal.

## Próxima fase — vertical slice educacional completo

1. Extrair telas e serviços da classe principal.
2. Tutorial guiado de variáveis, tipos, operadores, condicionais, loops e métodos.
3. Sistema de missões com critérios programáveis, testes e mensagens de feedback didáticas.
4. Vários scripts (abas) e chamada entre eles; depurador passo a passo com destaque da linha executada.
5. Partículas (terra ao arar, gotas ao regar, brilho na colheita) e sons por ação.
6. Mais mecânicas que pedem algoritmos: cultivos que crescem melhor perto de outros, árvores, abóboras gigantes que se fundem, labirintos.
6. Sistema de inventário, sementes por espécie, loja, upgrades e árvore tecnológica.
7. Mais biomas, chunks e streaming de mundo com limites explícitos de memória.
8. Tela de progresso de aprendizagem e histórico de programas.

## Fase de conteúdo e retenção

- Desafios diários variados com dificuldade crescente.
- Contratos e desafios semanais.
- Novos cultivos, máquinas e cadeias de produção.
- Sistema de conquistas e pontos de habilidade.
- Tutorial contextual e glossário Java.
- Configurações avançadas de acessibilidade e remapeamento.

## Fase de produção

- Asset pipeline Blender → GLB/GLTF, atlas e compressão.
- Animações rigged, partículas, sombras e pós-processamento com orçamento definido.
- Saves versionados e backup automático.
- Testes de performance em Windows, macOS Intel e Apple Silicon.
- Instaladores assinados/notarizados e atualização de versão.
- Sandbox OS-level antes de qualquer modo que execute código de terceiros.
