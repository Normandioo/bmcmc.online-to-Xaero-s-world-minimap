package com.cumania.territories.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.List;
import java.util.Map;

/** The territories.json the site publishes (see tools/export_territories.py). Coordinates are blocks. */
public final class TerritoryFile {
    public int format;
    public String world;
    public String dimension;
    public String generated;
    public Map<String, Country> countries;
    public List<Province> provinces;
    /** Optional (mod 1.2+): the site's localities with their position. */
    public List<Locality> localities;
    /** Optional (mod 1.3+): the site's records of the people behind the countries' populations, by site name. */
    public Map<String, Player> players;
    /** Optional (mod 1.4+): wars, alliances and diplomatic timers, as the site works them out. */
    public WarData war;
    /**
     * Optional (site's capture limits, gaia/capture.svg): the land a side must hold to take a city, where it differs from the province.
     * name and country match the localities; rings are even-odd, x0,z0,x1,z1,... in blocks (read as numbers so a decimal can not spoil the file).
     */
    public List<Zone> zones;
    /** Optional (the site's Connections, for In Play, 7.8.1): without it the war menu shows no front. */
    public Network network;

    public static final class Zone {
        public String name, country;
        public List<double[]> rings;
    }

    public static final class WarData {
        public List<WarInfo> wars;
        public List<AllianceInfo> alliances;
        public List<Truce> truces;
        public List<Ultimatum> ultimatums;
        public List<Nap> naps;
        public List<Lock> locks;
    }

    /** Times are ISO UTC ("2026-10-12T23:19:00Z"); null when not set. */
    public static final class WarInfo {
        public String id, name, type, rule, started, ended, theater, notes;
        public boolean noAllies;
        /** The site reads who holds its cities from dynmap while this is set. */
        public boolean live;
        public List<Side> sides;
        public List<Captured> captured;
        /** Staff's picks for a tie across neutral land (7.7.4): pairs of "Name(Country)". */
        public List<List<String>> shortCircuits;
        /** false = the site draws no automatic Short Circuits. */
        public Boolean shortCircuit;
        /** Supply Capitals (7.8.11): nation -> city keys. */
        public Map<String, List<String>> supplyCapitals;
        /** Bridge/Strait key -> "open" / "closed", for this war. */
        public Map<String, String> bridges;
    }

    /** The Connections between localities, Warzones, Bridges and Straits (the site's js/network.js). */
    public static final class Network {
        /** "Name(Country)" of a Bridge or Strait -> what it is and whether it is drawn open. */
        public Map<String, Link> links;
        /** [a, b] = Connected; [a, b, 1] = and in the same Warzone. */
        public List<List<Object>> edges;
    }

    public static final class Link {
        public String kind;   // bridge / strait
        public boolean open;
    }

    public static final class Side {
        public String name, color;   // color: "#rrggbb" when the site gives one
        public List<Member> members;
    }

    /** A nation, "@nation and subjects" or confederation as the site lists it; countries = the countries (keys of the file) it covers. */
    public static final class Member {
        public String name, note;
        public List<String> countries;
    }

    public static final class Captured {
        public String name, country, state, holder, holderKey;   // state: held / contested / uncontrolled
        public int x, z;
        /** Set by live control (not in the file): which side's colour holds it. */
        public transient Integer liveSide;
    }

    public static final class AllianceInfo {
        public String id, name, formed, officialName, color, notes, flag;
        public List<Member> members;
    }

    public static final class Truce { public String a, b, from, until; }
    public static final class Ultimatum { public String from, to, sent; }
    public static final class Nap { public String a, b, until; }
    public static final class Lock { public String nation, kind, until; }

    public static final class Player {
        /** In-game name, real-life country, last join (as the site writes it), free notes, skin picture (relative to the file). */
        public String ign, country, lastJoined, notes, skin;
        /** Immune to purging: "true" or the date it lasts until. */
        public String immune;
        /** What they are part of now ("Greneshire - Leader"), and their past affiliations. */
        public List<String> current, affiliations;
        public Purge purge;
    }

    /** Their realm's purge timer: the realm's name, its population and the days a player may stay away. */
    public static final class Purge {
        public String realm;
        public int population, days;
    }

    public static final class Country {
        public String name;
        public String color;   // "#rrggbb"
        /** Optional panel data, as the site stores it: displayName, type, officialName, formed, leader, government, demonym, ethnicity, capital. */
        public Map<String, String> info;
        public List<String> population;
        public Diplomacy diplomacy;
    }

    public static final class Diplomacy {
        public List<String> overlord, subject, allies, nap, alliances;
        public List<War> wars;
    }

    public static final class War {
        public String name;
        public String side;   // attacker / defender
    }

    public static final class Locality {
        public String name, country, kind, development, lord, ethnicity;
        public int x, z;
        public List<String> designations;
    }

    public static final class Province {
        public String id;
        public String country;
        /** Each ring is x0,z0,x1,z1,... ; a province is the even-odd union of its rings. */
        public List<int[]> rings;
    }

    private static final java.util.regex.Pattern WAR_COLOUR = java.util.regex.Pattern.compile("(Red|Blue|Green|Contested|Uncontrolled)$");

    public static TerritoryFile parse(String json) throws JsonSyntaxException {
        TerritoryFile f = new Gson().fromJson(json, TerritoryFile.class);
        if (f == null || f.countries == null || f.provinces == null) throw new JsonSyntaxException("not a territories file");
        if (f.format != 1) throw new JsonSyntaxException("unsupported territories format " + f.format);
        // the site's marker types now carry the colour of a live war's side ("SmallRed", "FortBlue", "SmallCapitalRed"): the kind is the
        // plain type (icons, capitals and city values go by it), and who holds the city is read from the war itself
        if (f.localities != null) for (Locality l : f.localities) if (l != null && l.kind != null) l.kind = WAR_COLOUR.matcher(l.kind).replaceFirst("");
        return f;
    }
}
