package com.javafarmer.progress;

import com.javafarmer.runtime.ProgramFeature;
import com.javafarmer.world.CropType;
import com.javafarmer.world.Farm;

import java.util.List;
import java.util.function.Predicate;

/**
 * The main objective track: a Java curriculum, one concept per mission, from a single method call to a
 * fully automated farm. Missions are completed in order; each one rewards coins and unlocks content.
 */
public final class Missions {
    private Missions() {}

    /** Everything a mission may look at. {@code report} is null when the check comes from the shop. */
    public record Context(RunReport report, Farm farm, Lifetime lifetime) {
        boolean run(Predicate<RunReport> test) { return report != null && test.test(report); }
    }

    /** Totals over the whole save. */
    public static final class Lifetime {
        public int harvests;
        public int plants;
        public int itemsSold;
        public int coinsEarned;
    }

    public record Mission(String title, String concept, String goal, String explanation, String hint,
                          int rewardCoins, CropType unlockCrop, int rewardSeeds, Predicate<Context> check) { }

    private static final String HEADER = "import com.javafarmer.api.FarmerBot;\n\npublic class PlayerProgram {\n";
    private static final String GO_TO = """
                static void irPara(int x, int y) {
                    while (FarmerBot.getX() < x) FarmerBot.move("EAST");
                    while (FarmerBot.getX() > x) FarmerBot.move("WEST");
                    while (FarmerBot.getY() < y) FarmerBot.move("NORTH");
                    while (FarmerBot.getY() > y) FarmerBot.move("SOUTH");
                }

            """;

    private static String program(String body) {
        return program("", body);
    }

    private static String program(String methods, String body) {
        StringBuilder out = new StringBuilder(HEADER);
        out.append(methods);
        out.append("    public static void main(String[] args) {\n");
        for (String line : body.strip().split("\n")) out.append("        ").append(line).append('\n');
        out.append("    }\n}\n");
        return out.toString();
    }

    public static final List<Mission> ALL = List.of(
            new Mission("Olá, robô!", "Chamar um método",
                    "Faça o robô andar pelo menos 1 bloco usando um programa.",
                    "Em Java, mandamos objetos fazerem coisas chamando métodos: FarmerBot.move(\"EAST\"). "
                            + "O texto entre aspas é uma String que diz a direção: NORTH, SOUTH, EAST ou WEST.",
                    program("FarmerBot.move(\"EAST\");\nFarmerBot.move(\"NORTH\");"),
                    10, null, 0, c -> c.run(r -> r.moves >= 1)),
            new Mission("Preparar o solo", "Sequência de instruções",
                    "Em um programa, are a terra (till) e plante trigo (plant) na sua fazenda.",
                    "O Java executa as linhas de cima para baixo, uma por vez. Primeiro preparamos o solo, depois plantamos. "
                            + "Você só pode plantar na sua terra: o quadrado que começa no canto (0,0).",
                    program("FarmerBot.till();\nFarmerBot.plant(\"WHEAT\");"),
                    10, null, 5, c -> c.run(r -> r.tills >= 1 && r.plants() >= 1)),
            new Mission("Primeira colheita", "O tempo passa",
                    "Colha 1 cultivo pronto.",
                    "Cultivos precisam de tempo. FarmerBot.waitCycles(3) espera 3 horas do jogo. O trigo leva 3 horas para "
                            + "amadurecer. Cada ação do robô também gasta tempo (6 minutos).",
                    program("FarmerBot.plant(\"WHEAT\");\nFarmerBot.waitCycles(3);\nFarmerBot.harvest();"),
                    15, null, 0, c -> c.run(r -> r.harvests() >= 1)),
            new Mission("Repetir é com o for", "Laço for",
                    "Use um laço for para plantar pelo menos 3 cultivos em um único programa.",
                    "O for repete um bloco: for (int i = 0; i < 3; i++) roda 3 vezes, com i valendo 0, 1 e 2. "
                            + "Comece no canto oeste da fazenda (x = 0) para caber tudo.",
                    program("for (int i = 0; i < 3; i++) {\n    FarmerBot.plant(\"WHEAT\");\n    FarmerBot.move(\"EAST\");\n}"),
                    25, null, 0, c -> c.run(r -> r.has(ProgramFeature.FOR) && r.plants() >= 3)),
            new Mission("Hora de vender", "Economia",
                    "Venda 3 produtos colhidos (na LOJA ou com FarmerBot.sell).",
                    "Colheitas vão para o seu estoque. Vendendo, você ganha moedas para comprar sementes, terra e melhorias. "
                            + "A partir desta missão, a loja também vende terra.",
                    program("FarmerBot.sell(\"WHEAT\");"),
                    20, null, 0, c -> c.lifetime().itemsSold >= 3),
            new Mission("Guardar valores", "Variáveis",
                    "Crie uma variável e mostre o valor dela com FarmerBot.log.",
                    "Variáveis guardam valores com um tipo: int para inteiros, String para textos, boolean para verdadeiro/falso. "
                            + "O + junta textos e números. Recompensa: cenouras liberadas!",
                    program("int sementes = FarmerBot.getSeeds(\"WHEAT\");\nFarmerBot.log(\"Tenho \" + sementes + \" sementes de trigo\");"),
                    20, CropType.CARROT, 5, c -> c.run(r -> r.has(ProgramFeature.VARIABLE) && r.has(ProgramFeature.LOG))),
            new Mission("Tomar decisões", "if / else + sensores",
                    "Use if com um sensor (ex.: canHarvest) e colha pelo menos 1 cultivo.",
                    "Sensores perguntam algo ao mundo e não gastam tempo. canHarvest() devolve true ou false; "
                            + "o if executa um bloco só quando a condição é verdadeira, e o else no caso contrário.",
                    program("if (FarmerBot.canHarvest()) {\n    FarmerBot.harvest();\n} else {\n    FarmerBot.log(\"Ainda não está pronto\");\n}"),
                    30, null, 0, c -> c.run(r -> r.has(ProgramFeature.IF) && r.has(ProgramFeature.SENSE) && r.harvests() >= 1)),
            new Mission("Laços aninhados", "for dentro de for",
                    "Plante a fazenda 3x3 inteira (9 cultivos) em um programa, usando um laço dentro de outro.",
                    "Um for de fora percorre as linhas (y) e um for de dentro percorre as colunas (x). "
                            + "Comece no canto (0,0) e tenha 9 sementes.",
                    program("for (int y = 0; y < 3; y++) {\n    for (int x = 0; x < 3; x++) {\n        FarmerBot.plant(\"WHEAT\");\n"
                            + "        if (x < 2) FarmerBot.move(\"EAST\");\n    }\n    FarmerBot.move(\"NORTH\");\n"
                            + "    for (int x = 0; x < 2; x++) FarmerBot.move(\"WEST\");\n}"),
                    40, null, 0, c -> c.run(r -> r.has(ProgramFeature.NESTED_LOOP) && r.plants() >= 9)),
            new Mission("Enquanto não fica pronto...", "Laço while",
                    "Use while para esperar um cultivo amadurecer e colha-o.",
                    "O while repete enquanto a condição for verdadeira. O ! inverte um boolean: !canHarvest() significa "
                            + "\"não dá para colher ainda\". Recompensa: o Regador aparece na loja.",
                    program("FarmerBot.plant(\"WHEAT\");\nwhile (!FarmerBot.canHarvest()) {\n    FarmerBot.waitCycles(1);\n}\nFarmerBot.harvest();"),
                    40, null, 0, c -> c.run(r -> r.has(ProgramFeature.WHILE) && r.harvests() >= 1)),
            new Mission("Irrigação", "Melhorias",
                    "Compre o Regador e regue 4 cultivos em um programa.",
                    "Cultivos regados crescem 2x mais rápido por 6 horas. Combine for e water() para regar uma linha inteira. "
                            + "Recompensa: girassóis liberados!",
                    program("for (int i = 0; i < 4; i++) {\n    FarmerBot.water();\n    FarmerBot.move(\"EAST\");\n}"),
                    50, CropType.SUNFLOWER, 3, c -> c.run(r -> r.waters >= 4)),
            new Mission("Seus próprios métodos", "Métodos com parâmetros",
                    "Crie um método static seu, chame-o e faça o robô andar 4 blocos ou mais.",
                    "Métodos dão nome a um pedaço de código que você reutiliza. irPara(x, y) recebe dois parâmetros int e usa "
                            + "os sensores getX()/getY() para levar o robô a qualquer bloco da fazenda.",
                    program(GO_TO, "irPara(0, 0);\nirPara(2, 2);\nirPara(0, 0);"),
                    60, null, 0, c -> c.run(r -> r.has(ProgramFeature.METHOD) && r.moves >= 4)),
            new Mission("Rotação de culturas", "Operador % (resto)",
                    "Use % para alternar 2 cultivos e plante pelo menos 6 em um programa (padrão xadrez).",
                    "a % b é o resto da divisão. (x + y) % 2 vale 0 ou 1 alternadamente, perfeito para um tabuleiro de xadrez. "
                            + "Recompensa: abóboras liberadas!",
                    program(GO_TO, "for (int y = 0; y < 3; y++) {\n    for (int x = 0; x < 3; x++) {\n        irPara(x, y);\n"
                            + "        if ((x + y) % 2 == 0) FarmerBot.plant(\"WHEAT\");\n        else FarmerBot.plant(\"CARROT\");\n    }\n}"),
                    70, CropType.PUMPKIN, 3, c -> c.run(r -> r.has(ProgramFeature.MODULO) && r.plantedTypes() >= 2 && r.plants() >= 6)),
            new Mission("Planos com arrays", "Arrays",
                    "Use um array para guardar um plano de plantio e plante pelo menos 4 cultivos com ele.",
                    "Um array guarda vários valores do mesmo tipo: String[] plano = {\"WHEAT\", \"CARROT\"}. "
                            + "plano[0] é o primeiro item e plano.length é o tamanho.",
                    program(GO_TO, "String[] plano = {\"WHEAT\", \"CARROT\", \"WHEAT\", \"PUMPKIN\"};\n"
                            + "for (int i = 0; i < plano.length; i++) {\n    irPara(i % 3, i / 3);\n    FarmerBot.plant(plano[i]);\n}"),
                    80, null, 0, c -> c.run(r -> r.has(ProgramFeature.ARRAY) && r.plants() >= 4)),
            new Mission("Expansão", "Planejamento",
                    "Aumente a sua fazenda para 6x6 comprando terra na loja.",
                    "Mais terra = mais cultivos por programa. Use getFarmSize() nos seus laços para que o mesmo código "
                            + "funcione com qualquer tamanho de fazenda.",
                    program("int lado = FarmerBot.getFarmSize();\nFarmerBot.log(\"Minha fazenda tem \" + (lado * lado) + \" blocos\");"),
                    100, null, 0, c -> c.farm().world.farmSize() >= 6),
            new Mission("Fazenda automática", "Algoritmo completo",
                    "Em um único programa, colha 15 cultivos e plante 15 novamente.",
                    "Junte tudo: percorra a fazenda inteira com laços aninhados, decida com if, reaproveite métodos. "
                            + "Recompensa: contrato com o mercado disponível na loja.",
                    program(GO_TO, "int lado = FarmerBot.getFarmSize();\nfor (int y = 0; y < lado; y++) {\n    for (int x = 0; x < lado; x++) {\n"
                            + "        irPara(x, y);\n        if (FarmerBot.canHarvest()) FarmerBot.harvest();\n"
                            + "        if (FarmerBot.getCrop().isEmpty()) FarmerBot.plant(\"CARROT\");\n    }\n}"),
                    150, null, 0, c -> c.run(r -> r.harvests() >= 15 && r.plants() >= 15)),
            new Mission("Mestre fazendeiro", "Otimização",
                    "Ganhe 2000 moedas no total com vendas e tenha uma fazenda 8x8.",
                    "Agora é com você: escolha os cultivos mais lucrativos, regue, venda com sell() direto do programa e "
                            + "melhore seu algoritmo para fazer mais em menos tempo.",
                    program("FarmerBot.log(\"Moedas: \" + FarmerBot.getCoins());"),
                    300, null, 0, c -> c.lifetime().coinsEarned >= 2000 && c.farm().world.farmSize() >= 8)
    );

    public static final int LAND_UNLOCK_MISSION = 4;      // "Hora de vender"
    public static final int REGADOR_UNLOCK_MISSION = 8;   // "Enquanto não fica pronto..."
    public static final int MERCADO_UNLOCK_MISSION = 14;  // "Fazenda automática"

    /** True if the shop may offer this upgrade, given how many missions are completed. */
    public static boolean upgradeAvailable(Farm.Upgrade upgrade, int completed) {
        return switch (upgrade) {
            case REGADOR -> completed > REGADOR_UNLOCK_MISSION;
            case MERCADO -> completed > MERCADO_UNLOCK_MISSION;
        };
    }

    public static boolean landAvailable(int completed) {
        return completed > LAND_UNLOCK_MISSION;
    }

    /** Index of the mission that unlocks a crop, or -1 for crops available from the start. */
    public static int unlockMissionFor(CropType crop) {
        for (int i = 0; i < ALL.size(); i++) if (ALL.get(i).unlockCrop() == crop) return i;
        return -1;
    }
}
