package com.cumania.territories.compat;

import com.cumania.territories.TerritoryManager;
import com.cumania.territories.data.DynmapClient;
import com.cumania.territories.data.LiveControl;
import com.cumania.territories.data.PanelModel;
import com.cumania.territories.data.TerritoryFile;
import com.cumania.territories.data.WarPaint;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The war menu on Xaero's World Map (a WAR button at the top right, or /territories war): the site's wars with their sides,
 * captures and status, the alliances, and the running diplomatic timers (truces, ultimatums, NAPs, diplolocks).
 * Opening a war or alliance paints the map with its sides; captured cities can be jumped to. Everything comes from the site's data.
 */
public final class WarMenu {
    private WarMenu() {}

    private static final int PW = 268, PAD = 9, LINE = 12, TOP = 30, HEADER_H = 24, TABS_H = 22, CHIP_H = 18;
    private static final int TEXT = 0xFFF4F4F4, MUTED = 0xFFB4B4B4, LINK = 0xFF8FD0FF, WAR_RED = 0xFFFF6060, OK = 0xFF7CD67C, TIME = 0xFFFFD080;
    private static final String[] TABS = {"Wars", "Alliances", "Timers"};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private static boolean open;
    private static int tab, scroll;
    private static String warId, allianceId;

    /** One row of the body. heading = a section title (taller, with a bar), spacer = empty space. swatch >= 0 draws a colour square first. */
    private record Bar(int[] weights, int[] colors) {}

    private record L(String text, int rgb, Runnable action, int swatch, boolean heading, boolean spacer, Bar bar) {
        L(String text, int rgb, Runnable action, int swatch, boolean heading) { this(text, rgb, action, swatch, heading, false, null); }
        L(String text, int rgb, Runnable action, int swatch, boolean heading, boolean spacer) { this(text, rgb, action, swatch, heading, spacer, null); }
        int height() { return bar != null ? 13 : spacer ? 5 : heading ? 17 : LINE; }
    }
    private static L gap() { return new L("", 0, null, -1, false, true); }

    /**
     * Who is ahead in a war, from what each side has taken off the other (captured localities held by one side that belong to a
     * country of another). Returns {captures per side} plus the verdict text through the lists.
     */
    private record Standing(int[] taken, int[] colors, String[] names, int leader, boolean even) {}

    private static Standing standing(TerritoryFile.WarInfo war) {
        int n = war.sides == null ? 0 : war.sides.size();
        int[] taken = new int[n], colors = new int[n];
        String[] names = new String[n];
        for (int i = 0; i < n; i++) { colors[i] = WarPaint.sideColor(war.sides.get(i), i); names[i] = war.sides.get(i).name; }
        for (var c : LiveControl.captured(TerritoryManager.index(), war)) {
            if (!"held".equals(c.state) || (c.holderKey == null && c.liveSide == null)) continue;
            int captor = c.liveSide != null ? c.liveSide : sideOfCountry(war, c.holderKey), owner = sideOfCountry(war, c.country);
            if (captor >= 0 && owner >= 0 && captor != owner) taken[captor]++;
        }
        int leader = 0, best = -1; boolean even = false;
        for (int i = 0; i < n; i++) { if (taken[i] > best) { best = taken[i]; leader = i; } }
        for (int i = 0; i < n; i++) if (i != leader && taken[i] == best) even = true;   // another side ties for the lead
        if (best <= 0) even = true;
        return new Standing(taken, colors, names, leader, even);
    }

    private static int sideOfCountry(TerritoryFile.WarInfo war, String country) {
        if (country == null || war.sides == null) return -1;
        for (int i = 0; i < war.sides.size(); i++) {
            var members = war.sides.get(i).members;
            if (members == null) continue;
            for (var m : members) if (m.countries != null) for (String c : m.countries) if (c.equalsIgnoreCase(country)) return i;
        }
        return -1;
    }

    /** The "who is winning" slider: a bar split by captures, then a line saying who leads. */
    private static void addStanding(List<L> out, Font font, int width, TerritoryFile.WarInfo war, boolean full) {
        Standing st = standing(war);
        if (st.taken.length < 2) return;
        int total = 0;
        for (int t : st.taken) total += t;
        int[] weights = new int[st.taken.length];
        for (int i = 0; i < weights.length; i++) weights[i] = total == 0 ? 1 : st.taken[i];
        out.add(new L("", 0, null, -1, false, false, new Bar(weights, st.colors)));
        StringBuilder counts = new StringBuilder();
        for (int i = 0; i < st.taken.length; i++) counts.append(i > 0 ? "   " : "").append(st.names[i]).append(' ').append(st.taken[i]);
        if (full) {
            String verdict = total == 0 ? "No captures yet: nobody is ahead" : st.even ? "Even: both sides have taken the same" : st.names[st.leader] + " are ahead";
            out.add(new L(verdict, total == 0 || st.even ? MUTED : (0xFF000000 | st.colors[st.leader]), null, -1, false));
            for (String part : PanelModel.wrap("Captures: " + counts, width, font::width)) out.add(new L(part, MUTED, null, -1, false));
            out.add(new L("Counted from the cities each side has taken off the other.", MUTED, null, -1, false));
            out.add(gap());
        } else {
            for (String part : PanelModel.wrap(total == 0 ? "No captures yet" : counts.toString(), width - 12, font::width)) out.add(new L("   " + part, MUTED, null, -1, false));
        }
    }
    private record Hit(int x0, int y0, int x1, int y1, Runnable action) {}

    private static final List<Hit> hits = new ArrayList<>();
    private static int mouseX, mouseY, panelX, panelH, chipX, chipW = 52;

    public static void toggle() { setOpen(!open); }
    /** Closes the menu (the settings menu takes its place). */
    public static void close() { if (open) setOpen(false); }
    /** Where the WAR button starts, so the BMC button can sit to its left. */
    static int chipLeft(int screenW) {
        var font = Minecraft.getInstance().font;
        int running = activeCount();
        return screenW - (font.width(running > 0 ? "WAR  " + running : "WAR") + 18) - 6;
    }

    private static void setOpen(boolean value) {
        if (value) SettingsMenu.close();
        open = value;
        if (!open) leaveDetail();
    }

    /** Called from the map screen's click: true when the click was ours (the WAR button or the menu). */
    public static boolean onClick() {
        if (!TerritoryManager.active() || !TerritoryManager.config().showWarMenu) return false;
        if (mouseX >= chipX && mouseX < chipX + chipW && mouseY >= 6 && mouseY < 6 + CHIP_H) { setOpen(!open); return true; }
        if (!open || panelH <= 0 || mouseX < panelX || mouseX >= panelX + PW || mouseY < TOP || mouseY >= TOP + panelH) return false;
        for (Hit hit : hits) {
            if (mouseX >= hit.x0 && mouseX < hit.x1 && mouseY >= hit.y0 && mouseY < hit.y1) { hit.action.run(); break; }
        }
        return true;
    }

    // ---------------------------------------------------------------- state changes

    private static void leaveDetail() {
        boolean was = warId != null || allianceId != null;
        warId = null; allianceId = null; scroll = 0;
        if (was || WarPaint.current() != null) WarPaint.apply(TerritoryManager.index(), null);
    }

    private static void openWar(String id) {
        warId = id; allianceId = null; scroll = 0;
        TerritoryFile.WarInfo war = findWar(id);
        if (war != null) WarPaint.apply(TerritoryManager.index(), WarPaint.forWar(TerritoryManager.index(), war));
    }

    private static void openAlliance(String id) {
        allianceId = id; warId = null; scroll = 0;
        TerritoryFile.AllianceInfo a = findAlliance(id);
        if (a != null) WarPaint.apply(TerritoryManager.index(), WarPaint.forAlliance(TerritoryManager.index(), a));
    }

    private static void selectTab(int t) { if (t != tab || warId != null || allianceId != null) { leaveDetail(); tab = t; scroll = 0; } }

    private static TerritoryFile.WarInfo findWar(String id) {
        var w = TerritoryManager.index().war().wars;
        if (w != null) for (var x : w) if (x.id != null && x.id.equals(id)) return x;
        return null;
    }

    private static TerritoryFile.AllianceInfo findAlliance(String id) {
        var a = TerritoryManager.index().war().alliances;
        if (a != null) for (var x : a) if (x.id != null && x.id.equals(id)) return x;
        return null;
    }

    private static void goTo(int x, int z) {
        if (Minecraft.getInstance().screen instanceof CameraTarget camera) camera.cumania$goTo(x, z, TerritoryManager.config().goToZoom);
    }

    // ---------------------------------------------------------------- time helpers

    private static Instant at(String iso) {
        try { return iso == null || iso.isBlank() ? null : Instant.parse(iso); } catch (Exception e) { return null; }
    }

    private static boolean active(TerritoryFile.WarInfo war) {
        Instant ended = at(war.ended);
        return ended == null || ended.isAfter(Instant.now());
    }

    private static String date(Instant t) { return DATE.format(t); }

    /** "in 2d 3h", "in 5h 10m", "3 days ago". */
    private static String relative(Instant t) {
        Duration d = Duration.between(Instant.now(), t);
        boolean future = !d.isNegative();
        long minutes = Math.abs(d.toMinutes());
        String span = minutes >= 2880 ? (minutes / 1440) + " days" : minutes >= 1440 ? "1d " + (minutes % 1440) / 60 + "h"
                : minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m";
        return future ? "in " + span : span + " ago";
    }

    // ---------------------------------------------------------------- content

    private static void text(List<L> out, Font font, int width, String text, int rgb) {
        for (String part : PanelModel.wrap(text, width, font::width)) out.add(new L(part, rgb, null, -1, false));
    }

    private static void kv(List<L> out, Font font, int width, String label, String value) {
        if (value == null || value.isBlank()) return;
        out.add(new L(label, MUTED, null, -1, false));
        for (String part : PanelModel.wrap(value, width, font::width)) out.add(new L(part, TEXT, null, -1, false));
        out.add(gap());
    }

    private static L title(String text, int rgb, int swatch) { return new L(text, rgb, null, swatch, true); }

    private static boolean hasLiveWar(com.cumania.territories.data.TerritoryIndex index) {
        var wars = index.war().wars;
        if (wars != null) for (var w : wars) if (w.live && w.ended == null) return true;
        return false;
    }

    /** dynmap's markers changed: if the open war is live, paint what it says now (only the regions that differ are redrawn). */
    public static void liveChanged() {
        Minecraft.getInstance().execute(() -> {
            if (!open || warId == null) return;
            var war = findWar(warId);
            if (war != null && war.live) WarPaint.apply(TerritoryManager.index(), WarPaint.forWar(TerritoryManager.index(), war));
        });
    }

    private static int activeCount() {
        var wars = TerritoryManager.index().war().wars;
        int n = 0;
        if (wars != null) for (var w : wars) if (active(w)) n++;
        return n;
    }

    private static List<L> buildWars(Font font, int width) {
        List<L> out = new ArrayList<>();
        var wars = TerritoryManager.index().war().wars;
        if (wars == null || wars.isEmpty()) { text(out, font, width, "No wars on record on the site.", MUTED); return out; }
        for (boolean wantActive : new boolean[]{true, false}) {
            int count = 0;
            for (var w : wars) if (active(w) == wantActive) count++;
            if (count == 0) continue;
            out.add(title((wantActive ? "Active wars" : "Ended wars") + "  " + count, wantActive ? WAR_RED : MUTED, -1));
            for (var w : wars) {
                if (active(w) != wantActive) continue;
                String id = w.id;
                out.add(new L(w.name, wantActive ? TEXT : MUTED, () -> openWar(id), wantActive ? 0xE05050 : 0x808080, false));
                Instant started = at(w.started);
                String sub = (w.type == null ? "" : w.type) + (started != null ? "  -  started " + relative(started) : "");
                for (String part : PanelModel.wrap(sub, width - 12, font::width)) out.add(new L("   " + part, MUTED, null, -1, false));
                if (wantActive) addStanding(out, font, width, w, false);
                out.add(gap());
            }
        }
        out.add(new L("Click a war to see its sides and paint the map.", MUTED, null, -1, false));
        return out;
    }

    private static List<L> buildWar(Font font, int width, TerritoryFile.WarInfo war) {
        List<L> out = new ArrayList<>();
        Instant started = at(war.started), ended = at(war.ended);
        boolean over = ended != null && !ended.isAfter(Instant.now());
        out.add(new L(over ? "ENDED" : "ONGOING", over ? MUTED : WAR_RED, null, over ? 0x808080 : 0xE05050, false));
        out.add(gap());
        out.add(title("Who is winning", TEXT, -1));
        addStanding(out, font, width, war, true);
        kv(out, font, width, "Type", war.type);
        if (war.rule != null) { text(out, font, width, war.rule, MUTED); out.add(gap()); }
        if (started != null) kv(out, font, width, "Started", date(started) + "  (" + relative(started) + ")");
        if (over) {
            kv(out, font, width, "Ended", date(ended) + "  (" + relative(ended) + ")");
            Instant truce = ended.plus(Duration.ofDays(3));
            if (truce.isAfter(Instant.now())) kv(out, font, width, "Truce until", date(truce) + "  (" + relative(truce) + ")");
        } else if (ended != null) kv(out, font, width, "Ends", date(ended) + "  (" + relative(ended) + ")");
        if (war.noAllies) { text(out, font, width, "No allies: defenders may not call in allies (7.1.6)", TEXT); out.add(gap()); }
        kv(out, font, width, "Theater", war.theater);
        if (war.sides != null) for (int i = 0; i < war.sides.size(); i++) {
            var side = war.sides.get(i);
            int count = side.members == null ? 0 : side.members.size();
            out.add(title(side.name + "  " + count, TEXT, WarPaint.sideColor(side, i)));
            if (side.members != null) for (var m : side.members) {
                text(out, font, width - 10, "  " + m.name + (m.note != null && !m.note.isBlank() ? "  (" + m.note + ")" : ""), TEXT);
            }
            out.add(gap());
        }
        var captured = LiveControl.captured(TerritoryManager.index(), war);
        if (!captured.isEmpty()) {
            out.add(title("Captured  " + captured.size() + "   (click to go)", TEXT, -1));
            if (LiveControl.isLive(war)) out.add(new L("Live from dynmap", OK, null, -1, false));
            for (var c : captured) {
                String holder = "contested".equals(c.state) ? "contested" : "uncontrolled".equals(c.state) ? "uncontrolled" : "held by " + c.holder;
                int swatch = "contested".equals(c.state) ? WarPaint.CONTESTED : "uncontrolled".equals(c.state) ? WarPaint.UNCONTROLLED
                        : c.liveSide != null && war.sides != null && c.liveSide < war.sides.size() ? WarPaint.sideColor(war.sides.get(c.liveSide), c.liveSide) : -1;
                int x = c.x, z = c.z;
                out.add(new L(c.name, LINK, () -> goTo(x, z), swatch, false));
                out.add(new L("   " + holder, MUTED, null, -1, false));
            }
            out.add(gap());
        }
        if (war.notes != null && !war.notes.isBlank()) { out.add(title("Notes", TEXT, -1)); text(out, font, width, war.notes, MUTED); }
        return out;
    }

    /** The country picked in the Alliances tab (a country id), "*" for every alliance, or null for none yet. */
    private static String allianceFilter;
    private static boolean filterOpen;

    private static boolean includes(TerritoryFile.AllianceInfo a, String countryId) {
        if (a.members == null) return false;
        for (var m : a.members) if (m.countries != null && m.countries.contains(countryId)) return true;
        return false;
    }

    private static List<L> buildAlliances(Font font, int width) {
        List<L> out = new ArrayList<>();
        var index = TerritoryManager.index();
        var list = index.war().alliances;
        if (list == null || list.isEmpty()) { text(out, font, width, "No alliances on record on the site.", MUTED); return out; }

        // countries that are in at least one alliance, by name
        java.util.TreeMap<String, String> countries = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (var a : list) if (a.members != null) for (var m : a.members) if (m.countries != null)
            for (String id : m.countries) { int ci = index.countryIndex(id); if (ci >= 0) countries.put(index.countryName(ci), id); }

        out.add(title("Filter by country", TEXT, -1));
        String current = allianceFilter == null ? "Pick a country..." : "*".equals(allianceFilter) ? "All alliances"
                : index.countryIndex(allianceFilter) >= 0 ? index.countryName(index.countryIndex(allianceFilter)) : allianceFilter;
        int currentCi = allianceFilter == null || "*".equals(allianceFilter) ? -1 : index.countryIndex(allianceFilter);
        out.add(new L((filterOpen ? "[-]  " : "[+]  ") + current, LINK, () -> filterOpen = !filterOpen, currentCi >= 0 ? index.countryRgb(currentCi) : -1, false));
        if (filterOpen) {
            out.add(new L("   All alliances", TEXT, () -> { allianceFilter = "*"; filterOpen = false; scroll = 0; }, -1, false));
            for (var e : countries.entrySet()) {
                String id = e.getValue();
                int ci = index.countryIndex(id);
                out.add(new L("   " + e.getKey(), TEXT, () -> { allianceFilter = id; filterOpen = false; scroll = 0; }, ci >= 0 ? index.countryRgb(ci) : -1, false));
            }
            return out;
        }
        out.add(gap());

        if (allianceFilter == null) {
            text(out, font, width, "Pick a country to see its allies and the alliances it belongs to.", MUTED);
            return out;
        }
        if ("*".equals(allianceFilter)) {
            out.add(title("Alliances  " + list.size(), TEXT, -1));
            for (var a : list) addAllianceEntry(out, font, width, a, null);
            out.add(new L("Click an alliance to paint its members.", MUTED, null, -1, false));
            return out;
        }

        String name = currentCi >= 0 ? index.countryName(currentCi) : allianceFilter;
        List<TerritoryFile.AllianceInfo> its = new ArrayList<>();
        for (var a : list) if (includes(a, allianceFilter)) its.add(a);
        if (its.isEmpty()) { text(out, font, width, name + " is not in any alliance on record.", MUTED); return out; }

        // every other country that shares an alliance with it
        java.util.TreeSet<String> allies = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (var a : its) for (var m : a.members) if (m.countries != null) for (String id : m.countries) {
            if (id.equals(allianceFilter)) continue;
            int ci = index.countryIndex(id);
            allies.add(ci >= 0 ? index.countryName(ci) : id);
        }
        out.add(title("Allies of " + name + "  " + allies.size(), TEXT, currentCi >= 0 ? index.countryRgb(currentCi) : -1));
        text(out, font, width, String.join(", ", allies), TEXT);
        out.add(gap());
        out.add(title("Alliances  " + its.size(), TEXT, -1));
        for (var a : its) addAllianceEntry(out, font, width, a, allianceFilter);
        out.add(new L("Click an alliance to paint its members.", MUTED, null, -1, false));
        return out;
    }

    /** One alliance in the list: its name (click to open), then who is in it. */
    private static void addAllianceEntry(List<L> out, Font font, int width, TerritoryFile.AllianceInfo a, String exceptCountry) {
        String id = a.id;
        Integer color = WarPaint.parse(a.color);
        out.add(new L(a.name, TEXT, () -> openAlliance(id), color != null ? color : 0x3A6FD8, false));
        String info;
        if (exceptCountry == null) info = (a.members == null ? 0 : a.members.size()) + " members" + (a.formed != null ? "  -  formed " + a.formed : "");
        else {
            List<String> names = new ArrayList<>();
            if (a.members != null) for (var m : a.members) if (m.countries == null || !m.countries.contains(exceptCountry) || m.countries.size() > 1) names.add(m.name);
            info = "With: " + String.join(", ", names) + (a.formed != null ? "  -  formed " + a.formed : "");
        }
        for (String part : PanelModel.wrap(info, width - 12, font::width)) out.add(new L("   " + part, MUTED, null, -1, false));
        out.add(gap());
    }

    private static List<L> buildAlliance(Font font, int width, TerritoryFile.AllianceInfo a) {
        List<L> out = new ArrayList<>();
        Integer color = WarPaint.parse(a.color);
        kv(out, font, width, "Official name", a.officialName);
        kv(out, font, width, "Formed", a.formed);
        out.add(title("Members  " + (a.members == null ? 0 : a.members.size()), TEXT, color != null ? color : 0x3A6FD8));
        if (a.members != null) for (var m : a.members) {
            out.add(new L("  " + m.name, TEXT, null, -1, false));
            String extra = (m.note != null ? m.note : "");
            if (!extra.isBlank()) out.add(new L("     " + extra, MUTED, null, -1, false));
        }
        if (a.notes != null && !a.notes.isBlank()) { out.add(gap()); out.add(title("Notes", TEXT, -1)); text(out, font, width, a.notes, MUTED); }
        return out;
    }

    /** Two rows per entry: who (white), then when (gold). */
    private static void entry(List<L> out, Font font, int width, String who, String time, int whoRgb) {
        for (String part : PanelModel.wrap(who, width - 8, font::width)) out.add(new L(part, whoRgb, null, -1, false));
        for (String part : PanelModel.wrap(time, width - 8, font::width)) out.add(new L("   " + part, TIME, null, -1, false));
    }

    private static List<L> buildTimers(Font font, int width) {
        List<L> out = new ArrayList<>();
        var war = TerritoryManager.index().war();
        Instant now = Instant.now();
        Duration truceLength = Duration.ofDays(3), ultimatumLength = Duration.ofHours(72);

        // each group: its rows plus how many entries it has
        List<L> truces = new ArrayList<>(); int truceN = 0;
        if (war.truces != null) for (var t : war.truces) {
            Instant from = at(t.from), until = at(t.until);
            if (until == null || !until.isAfter(now) || (from != null && from.isAfter(now))) continue;
            entry(truces, font, width, t.a + " & " + t.b, "until " + date(until) + "  (" + relative(until) + ")", TEXT); truceN++;
        }
        if (war.wars != null) for (var w : war.wars) {   // an ended war leaves a 3-day truce between its sides (6.9.2)
            Instant ended = at(w.ended);
            if (ended == null || ended.isAfter(now) || !ended.plus(truceLength).isAfter(now)) continue;
            Instant until = ended.plus(truceLength);
            entry(truces, font, width, "After the " + w.name, "until " + date(until) + "  (" + relative(until) + ")", TEXT); truceN++;
        }
        List<L> ultimatums = new ArrayList<>(); int ultimatumN = 0;
        if (war.ultimatums != null) for (var u : war.ultimatums) {
            Instant sent = at(u.sent);
            if (sent == null) continue;
            Instant accepted = sent.plus(ultimatumLength);
            if (accepted.isAfter(now)) { entry(ultimatums, font, width, u.from + "  ->  " + u.to, "auto-accepted " + relative(accepted) + "  (" + date(accepted) + ")", WAR_RED); ultimatumN++; }
            else if (accepted.plus(truceLength).isAfter(now)) { entry(truces, font, width, u.from + " & " + u.to + "  (ultimatum accepted)", "until " + date(accepted.plus(truceLength)), TEXT); truceN++; }
        }
        List<L> naps = new ArrayList<>(); int napN = 0;
        if (war.naps != null) for (var n : war.naps) {
            Instant until = at(n.until);
            if (until != null && until.isAfter(now)) { entry(naps, font, width, n.a + " & " + n.b, "until " + date(until) + "  (" + relative(until) + ")", TEXT); napN++; }
        }
        Map<String, List<String>> belligerent = new LinkedHashMap<>();
        if (war.wars != null) for (var w : war.wars) {
            if (!active(w) || w.sides == null) continue;
            for (var side : w.sides) if (side.members != null) for (var m : side.members) belligerent.computeIfAbsent(m.name, k -> new ArrayList<>()).add(w.name);
        }
        if (war.ultimatums != null) for (var u : war.ultimatums) {
            Instant sent = at(u.sent);
            if (sent != null && sent.plus(ultimatumLength).isAfter(now)) {
                belligerent.computeIfAbsent(u.from, k -> new ArrayList<>()).add("ultimatum with " + u.to);
                belligerent.computeIfAbsent(u.to, k -> new ArrayList<>()).add("ultimatum with " + u.from);
            }
        }
        List<L> locks = new ArrayList<>(); int lockN = 0;
        for (var e : belligerent.entrySet()) { entry(locks, font, width, e.getKey() + "  -  Belligerent", String.join(", ", e.getValue()), TEXT); lockN++; }
        if (war.locks != null) for (var l : war.locks) {
            Instant until = at(l.until);
            if (l.until != null && (until == null || !until.isAfter(now))) continue;
            entry(locks, font, width, l.nation + "  -  " + l.kind, until != null ? "until " + date(until) + "  (" + relative(until) + ")" : "no end set", TEXT); lockN++;
        }

        group(out, "Truces", truceN, truces);
        group(out, "Ultimatums pending", ultimatumN, ultimatums);
        group(out, "Non-aggression pacts with an end date", napN, naps);
        group(out, "Diplolocked nations", lockN, locks);
        return out;
    }

    private static void group(List<L> out, String name, int count, List<L> rows) {
        out.add(title(name + "  " + (count == 0 ? "none" : count), count == 0 ? MUTED : TEXT, -1));
        if (count > 0) { out.addAll(rows); }
        out.add(gap());
    }

    // ---------------------------------------------------------------- drawing

    public static void render(GuiGraphics g, int mx, int my) {
        mouseX = mx; mouseY = my;
        hits.clear();
        var config = TerritoryManager.config();
        if (!TerritoryManager.active() || !config.showWarMenu) { panelH = 0; return; }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth(), screenH = mc.getWindow().getGuiScaledHeight();

        // the WAR button: red when there are wars going, with how many
        int running = activeCount();
        String chipText = running > 0 ? "WAR  " + running : "WAR";
        chipW = font.width(chipText) + 18;
        chipX = screenW - chipW - 6;
        boolean overChip = mx >= chipX && mx < chipX + chipW && my >= 6 && my < 6 + CHIP_H;
        int chipBg = open ? 0xFFB03030 : running > 0 ? (overChip ? 0xFFC04040 : 0xFF8A2424) : (overChip ? 0xFF505050 : 0xFF303030);
        g.fill(chipX - 1, 5, chipX + chipW + 1, 7 + CHIP_H, 0xFFFFB0B0 & (open || running > 0 ? 0xFFFFFFFF : 0xFFAAAAAA));
        g.fill(chipX, 6, chipX + chipW, 6 + CHIP_H, chipBg);
        g.drawString(font, chipText, chipX + (chipW - font.width(chipText)) / 2, 6 + 5, 0xFFFFFFFF, true);
        if (!open) { panelH = 0; return; }

        // content (an open war or alliance that vanished from the data goes back to the list)
        var index = TerritoryManager.index();
        TerritoryFile.WarInfo war = warId == null ? null : findWar(warId);
        TerritoryFile.AllianceInfo alliance = allianceId == null ? null : findAlliance(allianceId);
        if ((warId != null && war == null) || (allianceId != null && alliance == null)) { leaveDetail(); war = null; alliance = null; }
        if ((war != null || alliance != null) && WarPaint.current() == null) {
            WarPaint.apply(index, war != null ? WarPaint.forWar(index, war) : WarPaint.forAlliance(index, alliance));
        }
        if (config.liveWar && hasLiveWar(index) && (tab == 0 || war != null)) DynmapClient.wantMarkers();
        int innerW = PW - 2 * PAD;
        boolean detail = war != null || alliance != null;
        List<L> lines = war != null ? buildWar(font, innerW, war) : alliance != null ? buildAlliance(font, innerW, alliance)
                : tab == 0 ? buildWars(font, innerW) : tab == 1 ? buildAlliances(font, innerW) : buildTimers(font, innerW);

        int tabsH = detail ? 0 : TABS_H;
        int footerH = 16;
        int budget = screenH - TOP - 10 - HEADER_H - tabsH - 12;          // pixels for the body
        int total = 0;
        for (L l : lines) total += l.height();
        boolean overflow = total > budget;
        if (overflow) budget -= footerH;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - 1)));
        int first = scroll, used = 0, last = first;
        while (last < lines.size() && used + lines.get(last).height() <= budget) used += lines.get(last++).height();
        if (last == first && first < lines.size()) last = first + 1;
        panelH = HEADER_H + tabsH + used + 12 + (overflow ? footerH : 0);
        int x0 = Math.max(2, screenW - PW - 6), y0 = TOP;
        panelX = x0;

        int accent = 0xFFE05050;
        g.fill(x0 - 1, y0 - 1, x0 + PW + 1, y0 + panelH + 1, accent);
        g.fill(x0, y0, x0 + PW, y0 + panelH, 0xF2101010);
        g.fill(x0, y0, x0 + PW, y0 + HEADER_H, 0xFF4A1818);
        g.fill(x0, y0 + HEADER_H - 1, x0 + PW, y0 + HEADER_H, accent);
        String title = Fit.bold(font, war != null ? war.name : alliance != null ? alliance.name : "War menu", PW - 62);
        Component titleText = Component.literal(title).withStyle(ChatFormatting.BOLD);
        g.drawString(font, titleText, x0 + (PW - font.width(titleText)) / 2, y0 + 8, 0xFFFFFFFF, true);
        g.drawString(font, "x", x0 + PW - 14, y0 + 8, 0xFFDDDDDD, true);
        hits.add(new Hit(x0 + PW - 22, y0, x0 + PW, y0 + HEADER_H, () -> setOpen(false)));
        if (detail) {
            g.drawString(font, "< Back", x0 + 8, y0 + 8, LINK, true);
            hits.add(new Hit(x0, y0, x0 + 50, y0 + HEADER_H, () -> leaveDetail()));
        } else {
            int tx = x0 + PAD, ty = y0 + HEADER_H + 4;
            for (int i = 0; i < TABS.length; i++) {
                int w = font.width(TABS[i]) + 14;
                int t = i;
                boolean hover = mx >= tx && mx < tx + w && my >= ty && my < ty + 15;
                g.fill(tx, ty, tx + w, ty + 15, i == tab ? 0xFF5A2020 : hover ? 0xFF2A2A2A : 0xFF1A1A1A);
                if (i == tab) g.fill(tx, ty + 14, tx + w, ty + 15, accent);
                g.drawString(font, TABS[i], tx + 7, ty + 4, i == tab ? 0xFFFFFFFF : MUTED, true);
                hits.add(new Hit(tx, ty, tx + w, ty + 15, () -> selectTab(t)));
                tx += w + 4;
            }
        }

        int y = y0 + HEADER_H + tabsH + 6;
        for (int i = first; i < last; i++) {
            L line = lines.get(i);
            int x = x0 + PAD;
            if (line.spacer()) { y += line.height(); continue; }
            if (line.bar() != null) {
                int bw = PW - 2 * PAD, sum = 0;
                for (int wgt : line.bar().weights()) sum += wgt;
                g.fill(x0 + PAD - 1, y + 1, x0 + PAD + bw + 1, y + 11, 0xFF555555);
                int bx = x0 + PAD, used2 = 0;
                for (int k = 0; k < line.bar().weights().length; k++) {
                    used2 += line.bar().weights()[k];
                    int end = x0 + PAD + (int) ((long) bw * used2 / Math.max(1, sum));
                    g.fill(bx, y + 2, end, y + 10, 0xFF000000 | line.bar().colors()[k]);
                    bx = end;
                }
                y += line.height();
                continue;
            }
            if (line.heading()) {
                y += 3;
                g.fill(x0 + PAD - 3, y - 1, x0 + PAD, y + 10, 0xFF000000 | (line.swatch >= 0 ? line.swatch : 0xE05050));
                x += 3;
                g.drawString(font, Component.literal(Fit.bold(font, line.text, x0 + PW - PAD - x)).withStyle(ChatFormatting.BOLD), x, y + 1, line.rgb, true);
                y += line.height() - 3;
                continue;
            }
            if (line.swatch >= 0) { g.fill(x, y + 1, x + 8, y + 9, 0xFF000000 | line.swatch); x += 12; }
            String shown = Fit.text(font, line.text, x0 + PW - PAD - x);
            int w = font.width(shown);
            boolean hover = line.action != null && mx >= x && mx < x + w && my >= y - 1 && my < y + LINE - 1;
            int color = line.action != null ? (hover ? 0xFFFFFFFF : LINK) : line.rgb;
            g.drawString(font, shown, x, y + 1, color, line.action != null || color == TEXT);
            if (line.action != null) {
                g.fill(x, y + 10, x + w, y + 11, hover ? 0xFFFFFFFF : 0x808FD0FF);
                hits.add(new Hit(x - 2, y - 1, x + w + 2, y + LINE, line.action));
            }
            y += LINE;
        }
        if (overflow) {
            int fy = y0 + panelH - footerH + 2;
            g.fill(x0 + PAD, fy - 3, x0 + PW - PAD, fy - 2, 0xFF3A3A3A);
            boolean canUp = first > 0, canDown = last < lines.size();
            String up = "[ Up ]", down = "[ Down ]";
            int dx = x0 + PAD + font.width(up) + 10;
            g.drawString(font, up, x0 + PAD, fy + 1, canUp ? LINK : 0xFF555555, false);
            g.drawString(font, down, dx, fy + 1, canDown ? LINK : 0xFF555555, false);
            int stepUp = Math.max(1, last - first - 2);
            hits.add(new Hit(x0 + PAD - 2, fy - 2, x0 + PAD + font.width(up) + 2, fy + LINE + 2, () -> scroll = Math.max(0, scroll - stepUp)));
            int nextFirst = Math.min(lines.size() - 1, Math.max(first + 1, last - 1));
            hits.add(new Hit(dx - 2, fy - 2, dx + font.width(down) + 2, fy + LINE + 2, () -> scroll = nextFirst));
            String where = (first + 1) + "-" + last + " / " + lines.size();
            g.drawString(font, where, x0 + PW - PAD - font.width(where), fy + 1, MUTED, false);
        }
    }
}
