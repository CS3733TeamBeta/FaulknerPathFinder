package main.Map.Entity;

import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;

/**
 * Represents a building with floors
 */
public class Building {

    String name;

    UUID buildID;

    ObservableSet<Floor> buildingFloors;
    /**
     * Creates a building with no floors
     */
    public Building() {
        this.buildingFloors = FXCollections.observableSet(new HashSet<Floor>());
    }

    Hospital hospital;

    public Building(String name)
    {
        this();
        this.name = name;
        this.buildID = UUID.randomUUID();
    }

    public Building(UUID id, String name) {
        this();
        this.buildID = id;
        this.name = name;
    }

    public void setHospital(Hospital h)
    {
        hospital = h;
    }

    public Hospital getHospital()
    {
        return hospital;
    }
 
    /**
     * Adds a floor to this building, throwing an exception if the floor already exists
     * @param f the floor to be added
     * @throws Exception if the floor already exists
     */
    public void addFloor(Floor f) throws Exception {
        buildingFloors.add(f);

        f.setBuilding(this);
    }

    /**
     * Retrieves the floor with the given floorNumber from this building's list of floors
     * @param floorNumber
     * @return floor with matching number
     */
    public Floor getFloor(int floorNumber)
    {
        if(floorNumber==1)
        {
            return hospital.getCampusFloor();
        }
        else
        {
            for (Floor f : buildingFloors)
            {
                if (f.getFloorNumber() == floorNumber)
                {
                    return f;
                }
            }
        }

        return null;
    }

    public Floor getBaseFloor()
    {
        for(Floor f: buildingFloors)
        {
            if(f.getFloorNumber()==1)
            {
                return f;
            }
        }

        return null;
    }

    public Collection<Floor> getFloors()
    {
        return buildingFloors;
    }

    /**
     *
     * @return name of building
     */
    public String getName()
    {
        return name;
    }

    /**
     * @param name Set name of building
     */
    public void setName(String name)
    {
        this.name = name;
    }

    public UUID getBuildID() {
        return this.buildID;
    }

    /**
     * Provides the name of the building
     */
    @Override
    public String toString()
    {
        return name;
    }

}
