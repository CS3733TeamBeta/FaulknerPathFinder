package Domain.Map;

import java.util.HashSet;

/**
 * Created by IanCJ on 1/29/2017.
 */
public class Hospital {
    HashSet<Building> buildings;

    /**
     * Creates a new Hospital with an empty HashSet of buildings
     */
    public Hospital() {
        buildings = new HashSet<Building>();
    }

    /**
     * Adds a building to this Hosptial's list of buildings
     * @param b
     */
    public void addBuilding(Building b) {
        buildings.add(b);
    }
}
