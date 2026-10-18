package com.cumania.war.engine;

import com.cumania.war.site.SiteData;

import java.util.Map;

/** How the capture works: how long a side must hold a city, by the city's type, and what resistance does. */
public record Rules(Map<String, Double> minutesByKind, double defaultMinutes, double capitalMultiplier, boolean resetOnResistance,
                    double graceSeconds, double cooldownSeconds, int minAttackers) {

    /** Seconds a side must stay in the city, with no one of the holding side inside, to take it. */
    public double secondsFor(SiteData.City city) {
        double minutes = minutesByKind.getOrDefault(city.baseKind(), defaultMinutes);
        if (city.capital()) minutes *= capitalMultiplier;
        return minutes * 60;
    }
}
