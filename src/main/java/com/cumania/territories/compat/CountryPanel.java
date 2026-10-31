package com.cumania.territories.compat;

import com.cumania.territories.TerritoryManager;
import com.cumania.territories.data.DynmapClient;
import com.cumania.territories.data.FlagCache;
import com.cumania.territories.data.PanelModel;
import com.cumania.territories.data.SkinCache;
import com.cumania.territories.data.TerritoryColors;
import com.cumania.territories.data.TerritoryFile;
import com.cumania.territories.data.TerritoryIndex;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * The info panel on Xaero's World Map: click a country or a city to open it, click open sea or the X to close it.
 * Click a name in the population list to open that player's card (skin and the site's record) beside it.
 * Drawn after the map screen renders (Fabric's ScreenEvents), so Xaero's own code is untouched.
 */
public final class CountryPanel {
    private CountryPanel() {}

    private static final int WIDTH = 236, PAD = 7, LINE = 10, TOP = 28, LEFT = 6, LABEL_W = 76, HEADER_H = 22;
    private static final int TEXT = 0xFFF2F2F2, MUTED = 0xFF9A9A9A;

    private static volatile TerritoryFile.Locality locality;
    private static int mouseX, mouseY, boxW, boxH;   // last drawn box, in GUI pixels

    private static final int MENU_W = 190, GAP = 8, ONLINE_DOT = 7;

    /** Whether dynmap says this person (by the site's name) is online right now. */
    private static boolean isOnline(String siteName) { return TerritoryManager.config().showOnlinePlayers && DynmapClient.find(siteName) != null; }
    private static volatile String selectedPlayer;
    private static int menuX, menuW, menuH, linkY0, linkY1;   // the player card's box (menuW 0 = not shown) and its copy-link line
    private static long copiedAt;
    private record NameHit(int x0, int y0, int x1, int y1, String name) {}
    private static final List<NameHit> nameHits = new ArrayList<>();
    private record GoHit(int x0, int y0, int x1, int y1, TerritoryFile.Locality target) {}
    private static final List<GoHit> goHits = new ArrayList<>();

    /** Called from the GuiMap.mapClicked mixin (blocks under the mouse, and the city if one was clicked). */
    public static void onMapClick(int blockX, int blockZ, TerritoryFile.Locality clickedLocality) {
        if (SettingsMenu.onClick() || WarMenu.onClick()) return;
        if (!TerritoryManager.active() || !TerritoryManager.config().showPanel) return;
        boolean inside = boxW > 0 && mouseX >= LEFT && mouseX <= LEFT + boxW && mouseY >= TOP && mouseY <= TOP + boxH;
        if (inside) {
            if (mouseX >= LEFT + boxW - 16 && mouseY <= TOP + HEADER_H) { clear(); return; }   // the X
            for (NameHit hit : nameHits) {
                if (mouseX >= hit.x0 && mouseX < hit.x1 && mouseY >= hit.y0 && mouseY < hit.y1) { selectedPlayer = hit.name; copiedAt = 0; return; }
            }
            return;
        }
        if (menuW > 0 && mouseX >= menuX && mouseX <= menuX + menuW && mouseY >= TOP && mouseY <= TOP + menuH) {
            if (mouseX >= menuX + menuW - 16 && mouseY <= TOP + HEADER_H) selectedPlayer = null;                      // the X
            else if (goTo(mouseX, mouseY)) { /* camera moved */ }
            else if (mouseY >= linkY0 && mouseY < linkY1) {                                                         // copy the NameMC link
                Minecraft.getInstance().keyboardHandler.setClipboard(nameMcUrl());
                copiedAt = System.currentTimeMillis();
            }
            return;
        }
        selectedPlayer = null;
        TerritoryIndex index = TerritoryManager.index();
        if (clickedLocality != null) {
            locality = clickedLocality;
            TerritoryManager.select(index.countryIndex(clickedLocality.country));
            return;
        }
        locality = null;
        TerritoryManager.select(index.countryAtBlock(blockX, blockZ));
    }

    /** A player clicked on the map (or anywhere else): open their card beside their country's panel. */
    public static void openPlayer(String siteName) {
        if (SettingsMenu.onClick() || WarMenu.onClick()) return;
        TerritoryIndex index = TerritoryManager.index();
        TerritoryFile.Player record = index.player(siteName);
        selectedPlayer = siteName;
        copiedAt = 0;
        locality = null;
        int country = -1;
        if (record != null && record.current != null) {   // the player's nation, from their current affiliations
            for (String a : record.current) for (int c = 0; c < index.countryCount() && country < 0; c++) if (a.toLowerCase().contains(index.countryName(c).toLowerCase())) country = c;
        }
        if (country >= 0) TerritoryManager.select(country);
        else if (TerritoryManager.selected() < 0) { var online = DynmapClient.find(siteName); if (online != null) TerritoryManager.select(index.countryAtBlock((int) online.x(), (int) online.z())); }
    }

    /** Click on one of the player's lordships: glide the map there. */
    private static boolean goTo(int mx, int my) {
        for (GoHit hit : goHits) {
            if (mx >= hit.x0 && mx < hit.x1 && my >= hit.y0 && my < hit.y1) {
                if (Screens.current() instanceof CameraTarget camera) camera.cumania$goTo(hit.target.x, hit.target.z, TerritoryManager.config().goToZoom);
                return true;
            }
        }
        return false;
    }

    public static void clear() { locality = null; boxW = 0; menuW = 0; selectedPlayer = null; nameHits.clear(); goHits.clear(); TerritoryManager.select(-1); }

    private sealed interface Row permits Pair, Block, Section, Names, Go {}
    /** A line of text that, when clicked, moves the map's camera to a locality. */
    private record Go(List<String> value, TerritoryFile.Locality target) implements Row {}
    /** A list of people, laid out in lines of names that can each be clicked. */
    private record Names(String label, List<List<String>> lines) implements Row {}
    private record Pair(String label, String value) implements Row {}
    private record Block(String label, List<String> value) implements Row {}
    private record Section(String title, int rgb) implements Row {}

    public static void render(GuiGraphicsExtractor g, int mouseXNow, int mouseYNow) {
        mouseX = mouseXNow; mouseY = mouseYNow;
        TerritoryIndex index = TerritoryManager.index();
        int c = TerritoryManager.selected();
        TerritoryFile.Locality loc = locality;
        if (!TerritoryManager.active() || !TerritoryManager.config().showPanel || (c < 0 && loc == null) || c >= index.countryCount()) { boxW = 0; menuW = 0; nameHits.clear(); goHits.clear(); return; }

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int innerW = WIDTH - 2 * PAD;

        if (TerritoryManager.config().showOnlinePlayers) DynmapClient.wantPlayers();
        // ---- content ----
        List<PanelModel.Line> lines = PanelModel.build(index, c, loc);
        String title = "";
        int titleRgb = c >= 0 ? index.countryRgb(c) : 0xFFFFFF;
        List<Row> rows = new ArrayList<>();
        boolean first = true;
        for (PanelModel.Line line : lines) {
            if (line.label() == null) {
                if (line.text().isEmpty()) continue;
                if (first) { title = line.text(); titleRgb = line.rgb(); first = false; }
                else rows.add(new Section(line.text(), line.rgb()));
                continue;
            }
            first = false;
            if (line.label().startsWith("Population") && c >= 0 && index.countryData(c).population != null && index.countryData(c).population.size() > 0) {
                rows.add(new Names(line.label(), layoutNames(font, index.countryData(c).population, innerW)));
                continue;
            }
            addRow(rows, font, line.label(), line.text(), innerW);
        }

        // flag of the country (a city's card shows its country's flag too)
        FlagCache.Flag flag = null;
        if (c >= 0 && TerritoryManager.index().countryData(c).info != null) {
            String rel = TerritoryManager.index().countryData(c).info.get("flag");
            String url = TerritoryManager.mapUrl();
            if (rel != null && !rel.isBlank() && url != null && url.contains("/")) flag = FlagCache.get(url.substring(0, url.lastIndexOf('/') + 1) + rel);
        }

        // ---- layout: how tall is it, and does it fit? ----
        int available = mc.getWindow().getGuiScaledHeight() - TOP - 8;
        int flagScale = flag == null ? 0 : 2;
        int bodyH = bodyHeight(rows);
        int flagH = flag == null ? 0 : flag.h() * flagScale + 10;
        // Too tall for the screen? Give up the least important things first: a smaller flag, the list of
        // names behind the population, then minor rows one by one, and only then cut off the tail.
        if (HEADER_H + flagH + bodyH + PAD > available && flag != null) { flagScale = 1; flagH = flag.h() + 10; }
        if (HEADER_H + flagH + bodyHeight(rows) + PAD > available) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i) instanceof Block b && b.label().startsWith("Population")) rows.set(i, new Pair("Population", b.label().replaceAll("\\D", "")));
                else if (rows.get(i) instanceof Names n && n.label().startsWith("Population")) rows.set(i, new Pair("Population", n.label().replaceAll("\\D", "")));
            }
        }
        for (String minor : new String[]{"Demonym", "Official name", "Provinces", "Ethnicity", "NAP", "Formed"}) {
            if (HEADER_H + flagH + bodyHeight(rows) + PAD <= available) break;
            rows.removeIf(r -> r instanceof Pair p && p.label().equals(minor) || r instanceof Block b && b.label().equals(minor));
        }
        boolean cut = false;
        while (rows.size() > 1 && HEADER_H + flagH + bodyHeight(rows) + PAD + (cut ? LINE : 0) > available) { rows.remove(rows.size() - 1); cut = true; }
        boxW = WIDTH; boxH = HEADER_H + flagH + bodyHeight(rows) + PAD + (cut ? LINE : 0);

        // ---- draw ----
        int x0 = LEFT, y0 = TOP;
        int accent = 0xFF000000 | titleRgb;
        g.fill(x0 - 1, y0 - 1, x0 + boxW + 1, y0 + boxH + 1, accent);          // coloured edge
        g.fill(x0, y0, x0 + boxW, y0 + boxH, 0xF0121212);                      // body
        g.fill(x0, y0, x0 + boxW, y0 + HEADER_H, 0xFF000000 | dim(titleRgb)); // header tinted with the country's colour
        g.fill(x0, y0 + HEADER_H - 1, x0 + boxW, y0 + HEADER_H, accent);

        Component titleText = Component.literal(Fit.bold(font, title, boxW - 34)).withStyle(ChatFormatting.BOLD);
        g.text(font, titleText, x0 + (boxW - font.width(titleText)) / 2, y0 + 7, 0xFFFFFFFF, true);
        g.text(font, "x", x0 + boxW - 12, y0 + 7, 0xFFCCCCCC, true);

        int y = y0 + HEADER_H + 5;
        if (flag != null) {
            int fw = flag.w() * flagScale, fh = flag.h() * flagScale, fx = x0 + (boxW - fw) / 2;
            g.fill(fx - 1, y - 1, fx + fw + 1, y + fh + 1, 0xFF2A2A2A);
            drawFlag(g, flag, fx, y, flagScale);
            y += fh + 6;
        }
        nameHits.clear();
        y = drawRows(g, font, rows, x0, boxW, y, true);
        if (cut) g.text(font, "...", x0 + PAD, y, MUTED, false);

        String who = selectedPlayer;
        if (who != null) renderPlayer(g, font, mc, who, Math.max(x0, Math.min(x0 + boxW + GAP, mc.getWindow().getGuiScaledWidth() - MENU_W - 4)), y0, titleRgb, available); else { menuW = 0; goHits.clear(); }
    }

    private static void addRow(List<Row> rows, Font font, String label, String text, int innerW) {
        if (font.width(label.toUpperCase()) <= LABEL_W - 6 && font.width(text) <= innerW - LABEL_W) rows.add(new Pair(label, text));
        else rows.add(new Block(label, PanelModel.wrap(text, innerW, font::width)));
    }

    /** Names separated by commas, flowing over as many lines as needed. */
    private static List<List<String>> layoutNames(Font font, List<String> names, int innerW) {
        List<List<String>> lines = new ArrayList<>();
        List<String> line = new ArrayList<>();
        int used = 0;
        for (int i = 0; i < names.size(); i++) {
            int w = font.width(names.get(i)) + (i < names.size() - 1 ? font.width(", ") : 0) + (isOnline(names.get(i)) ? ONLINE_DOT : 0);
            if (!line.isEmpty() && used + w > innerW) { lines.add(line); line = new ArrayList<>(); used = 0; }
            line.add(names.get(i)); used += w;
        }
        if (!line.isEmpty()) lines.add(line);
        return lines;
    }

    private static int drawRows(GuiGraphicsExtractor g, Font font, List<Row> rows, int x0, int boxW, int y, boolean recordHits) {
        for (Row row : rows) {
            if (row instanceof Section s) {
                y += 2;
                g.fill(x0 + PAD, y, x0 + boxW - PAD, y + 1, 0xFF3A3A3A);
                g.text(font, Fit.text(font, s.title().toUpperCase(), boxW - 2 * PAD), x0 + PAD, y + 4, 0xFF000000 | TerritoryColors.lighten(s.rgb() == 0 ? 0xAAAAAA : s.rgb(), 0.35), true);
                y += LINE + 4;
            } else if (row instanceof Pair p) {
                g.text(font, Fit.text(font, p.label().toUpperCase(), LABEL_W - 4), x0 + PAD, y, MUTED, false);
                g.text(font, Fit.text(font, p.value(), boxW - 2 * PAD - LABEL_W), x0 + PAD + LABEL_W, y, TEXT, true);
                y += LINE + 2;
            } else if (row instanceof Block b) {
                if (!b.label().isEmpty()) { g.text(font, Fit.text(font, b.label().toUpperCase(), boxW - 2 * PAD), x0 + PAD, y, MUTED, false); y += LINE; }
                for (String part : b.value()) { g.text(font, Fit.text(font, part, boxW - 2 * PAD), x0 + PAD, y, TEXT, true); y += LINE; }
                y += 2;
            } else if (row instanceof Go go) {
                int top = y;
                int width = 0;
                for (String part : go.value()) width = Math.max(width, font.width(part));
                boolean hover = mouseX >= x0 + PAD && mouseX < x0 + PAD + width && mouseY >= top - 1 && mouseY < top + go.value().size() * LINE - 1;
                for (String part : go.value()) { g.text(font, Fit.text(font, part, boxW - 2 * PAD), x0 + PAD, y, hover ? 0xFFFFFFFF : 0xFF8FD0FF, true); y += LINE; }
                if (!recordHits) goHits.add(new GoHit(x0 + PAD, top - 1, x0 + PAD + width, y - 1, go.target()));
                y += 2;
            } else if (row instanceof Names n) {
                g.text(font, Fit.text(font, n.label().toUpperCase(), boxW - 2 * PAD), x0 + PAD, y, MUTED, false);
                y += LINE;
                for (List<String> line : n.lines()) {
                    int x = x0 + PAD;
                    for (int i = 0; i < line.size(); i++) {
                        String name = line.get(i);
                        String shown = Fit.text(font, name, boxW - 2 * PAD - 8);
                        boolean online = isOnline(name);
                        int w = font.width(shown) + (online ? ONLINE_DOT : 0);
                        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y - 1 && mouseY < y + LINE - 1;
                        boolean open = name.equals(selectedPlayer);
                        if (online) g.fill(x, y + 2, x + 4, y + 6, 0xFF3CD65A);
                        g.text(font, shown, x + (online ? ONLINE_DOT : 0), y, hover || open ? 0xFFFFFFFF : 0xFF8FD0FF, true);
                        if (hover || open) g.fill(x, y + 9, x + w, y + 10, 0xFFFFFFFF);
                        if (recordHits) nameHits.add(new NameHit(x, y - 1, x + w, y + LINE - 1, name));
                        x += w;
                        if (i < line.size() - 1 || line != n.lines().get(n.lines().size() - 1)) { g.text(font, ",", x, y, MUTED, false); x += font.width(", "); }
                    }
                    y += LINE;
                }
                y += 2;
            }
        }
        return y;
    }

    /** "2026-10-08" or "2026-10-08T21:30Z" as the site writes them; null for anything else (world dates, blanks). */
    private static Instant parseDate(String text) {
        if (text == null || text.isBlank()) return null;
        try { return OffsetDateTime.parse(text.trim()).toInstant(); } catch (Exception ignored) {}
        try { return LocalDate.parse(text.trim()).atStartOfDay().toInstant(ZoneOffset.UTC); } catch (Exception ignored) {}
        return null;
    }

    /** "12 Nov 2026 - in 25 days". */
    private static String dateText(Instant when) {
        long days = ChronoUnit.DAYS.between(Instant.now(), when);
        String rel = days == 0 ? "today" : days > 0 ? "in " + days + (days == 1 ? " day" : " days") : -days + (days == -1 ? " day" : " days") + " ago";
        return DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH).withZone(ZoneOffset.UTC).format(when) + " - " + rel;
    }

    private static String nameMcUrl() {
        String who = selectedPlayer;
        TerritoryFile.Player p = who == null ? null : TerritoryManager.index().player(who);
        String ign = p != null && p.ign != null && !p.ign.isBlank() ? p.ign : who;
        return "https://pt.namemc.com/profile/" + ign;
    }

    /** The player card beside the country panel: skin, in-game name, where from, last join, affiliations and notes - all from the site's records. */
    private static void renderPlayer(GuiGraphicsExtractor g, Font font, Minecraft mc, String who, int x0, int y0, int rgb, int available) {
        TerritoryFile.Player p = TerritoryManager.index().player(who);
        int innerW = MENU_W - 2 * PAD;
        goHits.clear();
        List<Row> rows = new ArrayList<>();
        if (TerritoryManager.config().showOnlinePlayers) {
            DynmapClient.wantPlayers();
            DynmapClient.Online online = DynmapClient.find(who);
            if (online != null) {
                TerritoryFile.Locality here = new TerritoryFile.Locality();
                here.name = who; here.x = (int) Math.round(online.x()); here.z = (int) Math.round(online.z());
                rows.add(new Go(PanelModel.wrap("Online now at " + here.x + ", " + here.z + " (click to go)", innerW, font::width), here));
            } else if (DynmapClient.playersKnown()) addRow(rows, font, "Status", "Offline", innerW);
        }
        if (p != null) {
            if (p.ign != null && !p.ign.isBlank()) addRow(rows, font, "In-game", p.ign, innerW);
            if (p.country != null && !p.country.isBlank()) addRow(rows, font, "From", p.country, innerW);
            if (p.current != null && !p.current.isEmpty()) {
                rows.add(new Section("Current affiliations", rgb));
                for (String a : p.current) rows.add(new Block("", PanelModel.wrap(a, innerW, font::width)));
            }
            Instant joined = parseDate(p.lastJoined);
            boolean immune = p.immune != null && !p.immune.isBlank() && !p.immune.equals("false")
                    && (p.immune.equals("true") || parseDate(p.immune) == null || parseDate(p.immune).isAfter(Instant.now()));
            if (p.purge != null && p.purge.days > 0) {
                rows.add(new Section("Purge timer", rgb));
                rows.add(new Block("", PanelModel.wrap(p.purge.days + " days - realm of " + p.purge.realm + ", population " + p.purge.population, innerW, font::width)));
            }
            if (immune) addRow(rows, font, "Last joined", "Immune", innerW);
            else if (joined != null) {
                addRow(rows, font, "Last joined", dateText(joined), innerW);
                if (p.purge != null && p.purge.days > 0) {
                    Instant from = joined.plus(p.purge.days, ChronoUnit.DAYS);
                    addRow(rows, font, from.isAfter(Instant.now()) ? "Purgeable" : "Purgeable since", dateText(from), innerW);
                }
            } else if (p.lastJoined != null && !p.lastJoined.isBlank()) addRow(rows, font, "Last joined", p.lastJoined, innerW);
            List<TerritoryFile.Locality> lordships = TerritoryManager.index().localitiesOfLord(who);
            if (!lordships.isEmpty()) {
                rows.add(new Section("Lord of (" + lordships.size() + ") - click to go", rgb));
                for (TerritoryFile.Locality l : lordships) {
                    String text = l.name + (l.development != null ? " - " + l.development : "");
                    if (l.designations != null && !l.designations.isEmpty()) text += " (" + String.join(", ", l.designations) + ")";
                    rows.add(new Go(PanelModel.wrap(text, innerW, font::width), l));
                }
            }
            if (p.affiliations != null && !p.affiliations.isEmpty()) {
                rows.add(new Section("History", rgb));
                for (String a : p.affiliations) rows.add(new Block("", PanelModel.wrap(a, innerW, font::width)));
            }
            if (p.notes != null && !p.notes.isBlank()) { rows.add(new Section("Notes", rgb)); rows.add(new Block("", PanelModel.wrap(p.notes, innerW, font::width))); }
        } else rows.add(new Block("", PanelModel.wrap("No record of this player on the site.", innerW, font::width)));

        SkinCache.Figure figure = null;
        String url = TerritoryManager.mapUrl();
        if (p != null && p.skin != null && !p.skin.isBlank() && url != null && url.contains("/")) figure = SkinCache.get(url.substring(0, url.lastIndexOf('/') + 1) + p.skin);
        int scale = figure == null ? 0 : 3;
        int linkH = LINE + 8;
        int figH = figure == null ? 0 : SkinCache.H * scale + 10;
        if (HEADER_H + figH + bodyHeight(rows) + linkH + PAD > available && figure != null) { scale = 2; figH = SkinCache.H * scale + 10; }
        if (HEADER_H + figH + bodyHeight(rows) + linkH + PAD > available && figure != null) { figure = null; figH = 0; }
        boolean cut = false;
        while (rows.size() > 1 && HEADER_H + figH + bodyHeight(rows) + linkH + PAD + (cut ? LINE : 0) > available) { rows.remove(rows.size() - 1); cut = true; }
        menuX = x0; menuW = MENU_W; menuH = HEADER_H + figH + bodyHeight(rows) + linkH + PAD + (cut ? LINE : 0);

        int accent = 0xFF000000 | rgb;
        g.fill(x0 - 1, y0 - 1, x0 + menuW + 1, y0 + menuH + 1, accent);
        g.fill(x0, y0, x0 + menuW, y0 + menuH, 0xF0121212);
        g.fill(x0, y0, x0 + menuW, y0 + HEADER_H, 0xFF000000 | dim(rgb));
        g.fill(x0, y0 + HEADER_H - 1, x0 + menuW, y0 + HEADER_H, accent);
        Component titleText = Component.literal(Fit.bold(font, who, menuW - 34)).withStyle(ChatFormatting.BOLD);
        g.text(font, titleText, x0 + (menuW - font.width(titleText)) / 2, y0 + 7, 0xFFFFFFFF, true);
        g.text(font, "x", x0 + menuW - 12, y0 + 7, 0xFFCCCCCC, true);

        int y = y0 + HEADER_H + 5;
        if (figure != null) {
            int fx = x0 + (menuW - SkinCache.W * scale) / 2;
            int[] px = figure.argb();
            for (int row = 0; row < SkinCache.H; row++) {
                int col = 0;
                while (col < SkinCache.W) {
                    int color = px[row * SkinCache.W + col], end = col + 1;
                    while (end < SkinCache.W && px[row * SkinCache.W + end] == color) end++;
                    if (color != 0) g.fill(fx + col * scale, y + row * scale, fx + end * scale, y + (row + 1) * scale, color);
                    col = end;
                }
            }
            y += SkinCache.H * scale + 6;
        }
        y = drawRows(g, font, rows, x0, menuW, y, false);
        if (cut) { g.text(font, "...", x0 + PAD, y, MUTED, false); y += LINE; }
        y += 4;
        linkY0 = y - 2; linkY1 = y + LINE + 2;
        boolean copied = System.currentTimeMillis() - copiedAt < 2500;
        boolean hover = mouseX >= x0 && mouseX <= x0 + menuW && mouseY >= linkY0 && mouseY < linkY1;
        g.fill(x0 + PAD, linkY0 - 2, x0 + menuW - PAD, linkY0 - 1, 0xFF3A3A3A);
        g.text(font, copied ? "Link copied!" : "Copy NameMC link", x0 + PAD, y, copied ? 0xFF7CFF7C : hover ? 0xFFFFFFFF : 0xFF8FD0FF, true);
    }

    private static int bodyHeight(List<Row> rows) {
        int h = 0;
        for (Row row : rows) {
            if (row instanceof Section) h += 2 + LINE + 4;
            else if (row instanceof Pair) h += LINE + 2;
            else if (row instanceof Block b) h += (b.label().isEmpty() ? 0 : LINE) + b.value().size() * LINE + 2;
            else if (row instanceof Names n) h += LINE + n.lines().size() * LINE + 2;
            else if (row instanceof Go go) h += go.value().size() * LINE + 2;
        }
        return h;
    }

    /** The country's colour at about a quarter brightness, for the header background. */
    private static int dim(int rgb) {
        return (((rgb >> 16) & 255) / 4 << 16) | (((rgb >> 8) & 255) / 4 << 8) | ((rgb & 255) / 4);
    }

    /** The flag as plain rectangles: runs of the same colour on a row are one rectangle. */
    private static void drawFlag(GuiGraphicsExtractor g, FlagCache.Flag flag, int x, int y, int scale) {
        int[] px = flag.argb();
        for (int row = 0; row < flag.h(); row++) {
            int col = 0;
            while (col < flag.w()) {
                int color = px[row * flag.w() + col], end = col + 1;
                while (end < flag.w() && px[row * flag.w() + end] == color) end++;
                if (color != 0) g.fill(x + col * scale, y + row * scale, x + end * scale, y + (row + 1) * scale, color);
                col = end;
            }
        }
    }
}
