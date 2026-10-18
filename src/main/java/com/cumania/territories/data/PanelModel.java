package com.cumania.territories.data;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** What the info panel says about a country and/or a locality, laid out like the site's side panel. */
public final class PanelModel {
    private PanelModel() {}

    /** One row: a heading, or "label: text". Colours are RGB; label and text share the row. */
    public record Line(String label, String text, int rgb, boolean heading) {}

    private static final String[][] COUNTRY_ROWS = {
            {"type", "Type"}, {"officialName", "Official name"}, {"formed", "Formed"}, {"leader", "Leader"},
            {"government", "Government"}, {"demonym", "Demonym"}, {"ethnicity", "Ethnicity"}, {"capital", "Capital"}};

    /** The locality card (if any) followed by the country's panel (if any). */
    public static List<Line> build(TerritoryIndex index, int country, TerritoryFile.Locality locality) {
        List<Line> out = new ArrayList<>();
        if (locality != null) {
            int ci = index.countryIndex(locality.country);
            int rgb = ci < 0 ? 0xFFFFFF : index.countryRgb(ci);
            out.add(new Line(null, locality.name, rgb, true));
            String type = locality.development != null ? locality.development : "Non-settlement";
            if (locality.kind != null && locality.kind.startsWith("Fort")) type += " (fort)";
            row(out, "Type", type);
            if (ci >= 0) row(out, "Country", displayName(index, ci));
            else row(out, "Country", locality.country);
            row(out, "Lord", locality.lord);
            row(out, "Ethnicity", locality.ethnicity);
            if (locality.designations != null && !locality.designations.isEmpty()) row(out, "Designations", String.join(", ", locality.designations));
            row(out, "Coordinates", "X " + locality.x + " Z " + locality.z);
        }
        if (country >= 0) {
            if (locality != null) out.add(new Line(null, "", 0, false));
            TerritoryFile.Country c = index.countryData(country);
            int rgb = index.countryRgb(country);
            String shown = displayName(index, country);
            out.add(new Line(null, shown, rgb, true));
            if (c.info != null) {
                for (String[] r : COUNTRY_ROWS) {
                    String value = c.info.get(r[0]);
                    if (value == null || value.isBlank()) continue;
                    if (r[0].equals("officialName") && value.equals(shown)) continue;
                    row(out, r[1], value);
                }
            }
            int provinces = 0;
            for (int i = 0; i < index.provinceCount(); i++) if (index.countryOfProvince(i) == country) provinces++;
            if (provinces > 0) row(out, "Provinces", String.valueOf(provinces));
            if (c.population != null && !c.population.isEmpty()) row(out, "Population (" + c.population.size() + ")", String.join(", ", c.population));
            TerritoryFile.Diplomacy d = c.diplomacy;
            if (d != null) {
                List<Line> dip = new ArrayList<>();
                row(dip, "Overlord", d.overlord);
                row(dip, "Subjects", d.subject);
                row(dip, "Allies", d.allies);
                row(dip, "NAP", d.nap);
                row(dip, "Alliances", d.alliances);
                if (d.wars != null && !d.wars.isEmpty()) {
                    List<String> wars = new ArrayList<>();
                    for (TerritoryFile.War w : d.wars) wars.add(w.name + " (" + w.side + ")");
                    row(dip, "At war", wars);
                }
                if (!dip.isEmpty()) { out.add(new Line(null, "Diplomacy", 0xCCCCCC, true)); out.addAll(dip); }
            }
        }
        return out;
    }

    private static String displayName(TerritoryIndex index, int country) {
        TerritoryFile.Country c = index.countryData(country);
        String shown = c.info != null ? c.info.get("displayName") : null;
        return shown != null && !shown.isBlank() ? shown : index.countryId(country);
    }

    private static void row(List<Line> out, String label, String value) {
        if (value != null && !value.isBlank()) out.add(new Line(label, value, 0xFFFFFF, false));
    }

    private static void row(List<Line> out, String label, List<String> values) {
        if (values != null && !values.isEmpty()) row(out, label, String.join(", ", values));
    }

    /** Greedy word wrap; a single word wider than the limit is hard-split. */
    public static List<String> wrap(String text, int maxWidth, ToIntFunction<String> width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            while (!word.isEmpty() && width.applyAsInt(word) > maxWidth) {
                int cut = word.length();
                while (cut > 1 && width.applyAsInt(word.substring(0, cut)) > maxWidth) cut--;
                if (line.length() > 0) { lines.add(line.toString()); line.setLength(0); }
                lines.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            if (word.isEmpty()) continue;
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && width.applyAsInt(candidate) > maxWidth) { lines.add(line.toString()); line.setLength(0); line.append(word); }
            else { line.setLength(0); line.append(candidate); }
        }
        if (line.length() > 0 || lines.isEmpty()) lines.add(line.toString());
        return lines;
    }
}
