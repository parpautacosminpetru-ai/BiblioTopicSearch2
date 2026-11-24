package ro.bibliotopicsearch.app;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public final class MapColorPolicyTest {
    @Test public void everyNodeUsesItsOwnMapColour() {
        TopicNode a = new TopicNode("a", "Istorie", "Nod", "Nod", 1);
        TopicNode b = new TopicNode("a", "Istorie", "Alt nod", "Alt nod", 2);
        TopicNode c = new TopicNode("b", "Religie", "Nod", "Nod", 1);
        MapColorPolicy.applyToNodes(Arrays.asList(a, b), 0x00ABCDEF);
        MapColorPolicy.applyToNodes(Arrays.asList(c), 0xFF112233);
        assertEquals(0xFFABCDEF, a.color);
        assertEquals(a.color, b.color);
        assertEquals(0xFF112233, c.color);
        assertNotEquals(a.identityKey(), c.identityKey());
    }
    @Test public void explicitColourFollowsIdNotNameOrFolder() {
        MapProfile p = new MapProfile("a", "Vechi", "Folder", "", 1, 0x123456);
        p.name = "Nou"; p.folder = "Alt folder";
        assertEquals(0xFF123456, p.color);
        assertEquals(MapColorPolicy.defaultColor("a"), new MapProfile("a", "Alt nume", "Alt", "", 1).color);
    }
    @Test public void blackWhiteAndRgbStayLegible() {
        assertEquals(0xFFFFFFFF, MapColorPolicy.contrastText(0xFF000000));
        assertEquals(0xFF000000, MapColorPolicy.contrastText(0xFFFFFFFF));
        assertEquals(0xFF000000, MapColorPolicy.contrastText(0xFFFFFF00));
        assertEquals(0xFF012345, MapColorPolicy.opaque(0x00012345));
    }
}
