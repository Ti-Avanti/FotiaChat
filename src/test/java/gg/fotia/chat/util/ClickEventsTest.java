package gg.fotia.chat.util;

import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClickEventsTest {
    @Test void configuredActionsKeepTheirOriginalBehavior() {
        assertEquals(ClickEvent.runCommand("/spawn"), ClickEvents.create(ClickEvents.action("RUN_COMMAND"), "/spawn"));
        assertEquals(ClickEvent.suggestCommand("/msg "), ClickEvents.create(ClickEvents.action("SUGGEST_COMMAND"), "/msg "));
        assertEquals(ClickEvent.openUrl("https://example.com"), ClickEvents.create(ClickEvents.action("OPEN_URL"), "https://example.com"));
        assertEquals(ClickEvent.copyToClipboard("text"), ClickEvents.create(ClickEvents.action("COPY_TO_CLIPBOARD"), "text"));
    }
    @Test void snapshotCommandsAreRecognizedWithoutLegacyPayloadAccess() {
        org.junit.jupiter.api.Assertions.assertTrue(ClickEvents.isCommandStartingWith(ClickEvent.runCommand("/fotiachat viewsnapshot 中文"), "/fotiachat viewsnapshot "));
        org.junit.jupiter.api.Assertions.assertFalse(ClickEvents.isCommandStartingWith(ClickEvent.suggestCommand("/fotiachat viewsnapshot test"), "/fotiachat viewsnapshot "));
        org.junit.jupiter.api.Assertions.assertFalse(ClickEvents.isCommandStartingWith(null, "/fotiachat viewsnapshot "));
    }
    @Test void unknownActionsRetainSuggestionFallback() {
        assertEquals(ClickEvent.suggestCommand("value"), ClickEvents.create(ClickEvents.action("unknown"), "value"));
    }
}
