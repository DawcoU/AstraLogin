package pl.dawcou.astralogin.auth.security.captcha.generator;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import pl.dawcou.astralogin.AstraLogin;

import java.util.*;
import java.util.function.Predicate;

public class GuiCaptchaGenerator {

    private record ChallengeDefinition(String langKey, Material targetMaterial,
                                       Predicate<Material> distractorPredicate) {

        public boolean isValidDistractor(Material material) {
                return distractorPredicate.test(material);
            }
        }

    public static class ChallengeResult {
        private final Set<Material> targetMaterials;
        private final String langKey;
        private final List<ItemStack> items;

        public ChallengeResult(Set<Material> targetMaterials, String langKey, List<ItemStack> items) {
            this.targetMaterials = targetMaterials;
            this.langKey = langKey;
            this.items = items;
        }

        public Set<Material> getTargetMaterials() {
            return targetMaterials;
        }

        public String getLangKey() {
            return langKey;
        }

        public List<ItemStack> getItems(int size) {
            List<ItemStack> fullItems = new ArrayList<>(items);
            Collections.shuffle(fullItems);
            return fullItems.subList(0, Math.min(fullItems.size(), size));
        }
    }

    private static final Map<String, List<ChallengeDefinition>> DIFFICULTIES = new HashMap<>();

    static {
        DIFFICULTIES.put("easy", Arrays.asList(
                new ChallengeDefinition("captcha.modes.gui.prompts.edible", Material.APPLE, m -> !m.isEdible()),
                new ChallengeDefinition("captcha.modes.gui.prompts.red", Material.RED_WOOL, m -> !m.name().contains("RED")),
                new ChallengeDefinition("captcha.modes.gui.prompts.blue", Material.BLUE_WOOL, m -> !m.name().contains("BLUE")),
                new ChallengeDefinition("captcha.modes.gui.prompts.green", Material.GREEN_WOOL, m -> !m.name().contains("GREEN")),
                new ChallengeDefinition("captcha.modes.gui.prompts.yellow", Material.YELLOW_WOOL, m -> !m.name().contains("YELLOW")),
                new ChallengeDefinition("captcha.modes.gui.prompts.wood", Material.OAK_LOG, m -> !m.name().contains("LOG") && !m.name().contains("WOOD"))
        ));

        DIFFICULTIES.put("medium", Arrays.asList(
                new ChallengeDefinition("captcha.modes.gui.prompts.bow_ammo", Material.ARROW, m -> m != Material.ARROW && m != Material.TIPPED_ARROW && m != Material.SPECTRAL_ARROW),
                new ChallengeDefinition("captcha.modes.gui.prompts.chicken_drop", Material.FEATHER, m -> m != Material.FEATHER && m != Material.CHICKEN && m != Material.EGG),
                new ChallengeDefinition("captcha.modes.gui.prompts.redstone", Material.REDSTONE, m -> !isRedstoneComponent(m)),
                new ChallengeDefinition("captcha.modes.gui.prompts.ore", Material.DIAMOND_ORE, m -> !m.name().contains("ORE")),
                new ChallengeDefinition("captcha.modes.gui.prompts.helmet", Material.IRON_HELMET, m -> !m.name().contains("HELMET"))
        ));

        DIFFICULTIES.put("hard", Arrays.asList(
                new ChallengeDefinition("captcha.modes.gui.prompts.netherite", Material.NETHERITE_INGOT, m -> m != Material.NETHERITE_INGOT && m != Material.NETHERITE_SCRAP && m != Material.ANCIENT_DEBRIS),
                new ChallengeDefinition("captcha.modes.gui.prompts.potion", Material.POTION, m -> !m.name().contains("POTION")),
                new ChallengeDefinition("captcha.modes.gui.prompts.music_disc", Material.MUSIC_DISC_CAT, m -> !m.name().contains("MUSIC_DISC")),
                new ChallengeDefinition("captcha.modes.gui.prompts.boat", Material.OAK_BOAT, m -> !m.name().contains("BOAT") && !m.name().contains("RAFT")),
                new ChallengeDefinition("captcha.modes.gui.prompts.tool", Material.DIAMOND_PICKAXE, m -> !m.name().contains("PICKAXE") && !m.name().contains("AXE") && !m.name().contains("SHOVEL") && !m.name().contains("HOE"))
        ));
    }

    public static ChallengeResult generate(int size, AstraLogin plugin) {
        String difficulty = plugin.getConfig().getString("security.captcha.modes.gui.difficulty", "medium").toLowerCase();
        List<ChallengeDefinition> pool = DIFFICULTIES.getOrDefault(difficulty, DIFFICULTIES.get("medium"));
        if (pool.isEmpty()) {
            pool = DIFFICULTIES.get("medium");
        }

        ChallengeDefinition def = pool.get(new Random().nextInt(pool.size()));
        int countConfig = plugin.getConfig().getInt("security.captcha.modes.gui.correct-items-count", 3);
        int correctCount = Math.min(Math.max(countConfig, 1), 3);

        Set<Material> targets = new HashSet<>();
        targets.add(def.targetMaterial());

        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < correctCount; i++) {
            items.add(new ItemStack(def.targetMaterial()));
        }

        Random random = new Random();
        Material[] allMaterials = Material.values();

        while (items.size() < size) {
            Material randomMat = allMaterials[random.nextInt(allMaterials.length)];
            if (!randomMat.isItem() || randomMat.isAir() || targets.contains(randomMat)) {
                continue;
            }

            if (def.isValidDistractor(randomMat)) {
                items.add(new ItemStack(randomMat));
            }
        }

        return new ChallengeResult(targets, def.langKey(), items);
    }

    private static boolean isRedstoneComponent(Material material) {
        String name = material.name();
        return name.contains("PISTON") || name.contains("LEVER") || name.contains("BUTTON")
                || name.contains("REDSTONE") || name.contains("REPEATER") || name.contains("COMPARATOR")
                || name.contains("OBSERVER") || name.contains("TARGET") || name.contains("DAYLIGHT");
    }
}