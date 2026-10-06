package gg.fotia.chat.announcement;

import gg.fotia.chat.condition.ConditionContext;
import gg.fotia.chat.condition.ConditionEngine;
import gg.fotia.chat.condition.ConditionSet;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnouncementConditionEvaluatorTest {

    @Test
    void announcementsWithoutConditionsRemainVisible() {
        ConditionEngine engine = ConditionEngine.createDefault();
        Announcement announcement = new Announcement(
                "legacy", "", 300, List.of("message"), true, "", 1, 1);

        assertTrue(new AnnouncementConditionEvaluator(engine)
                .evaluate(announcement, context("world")).passed());
    }

    @Test
    void announcementConditionsFilterEachRecipient() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                conditions:
                  current_world:
                    type: equals
                    value:
                      value1: "%world%"
                      value2: world
                """);
        ConditionEngine engine = ConditionEngine.createDefault();
        ConditionSet conditions = engine.parse(config.getConfigurationSection("conditions"), "conditions");
        Announcement announcement = new Announcement(
                "filtered", "", 300, List.of("message"), true, "", 1, 1,
                false, List.of(), false, net.kyori.adventure.text.event.ClickEvent.suggestCommand("").action(),
                "", conditions);
        AnnouncementConditionEvaluator evaluator = new AnnouncementConditionEvaluator(engine);

        assertTrue(evaluator.evaluate(announcement, context("world")).passed());
        assertFalse(evaluator.evaluate(announcement, context("world_nether")).passed());
    }

    private ConditionContext context(String world) {
        return new ConditionContext(UUID.randomUUID(), null,
                input -> input.equals("%world%") ? world : input);
    }
}
