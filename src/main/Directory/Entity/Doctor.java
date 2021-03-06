package main.Directory.Entity;

import main.Map.Entity.Destination;
import javafx.collections.ObservableList;

import java.util.Observable;
import java.util.UUID;

public class Doctor extends Observable {

    UUID docID;
    protected String phoneNum = "N/A";
    private String name;
    private String description;
    private ObservableList<Destination> destinations;

    protected String hours;

    /*
    HashSet<String> lstNames;
    HashSet<HashSet<Suite>> lstSuites;
    HashSet<String> lstDescriptions;
*/


    public Doctor(String name, String description, String hours, ObservableList<Destination> destinations) {
        this.name = name;
        this.description = description;
        this.hours = hours;
        this.docID = UUID.randomUUID();
        this.destinations = destinations;

    }

    public Doctor(UUID docID, String name, String description, String hours, ObservableList<Destination> destinations) {
        this.name = name;
        this.description = description;
        this.hours = hours;
        this.docID = docID;
        this.destinations = destinations;


    }

//    public Doctor(String dept, String phoneNum, Office docOff, String name, String description, String hours)
//    {
//        super(name, description, hours);
//
//        //this.department.add(dept);
//        this.phoneNum = phoneNum;
//        //this.myOffice.add(docOff);
//        super.name = name;
//        super.description = description;
//        super.hours = hours;
//    }

    public void setDocID(UUID docID){
        this.docID = docID;
    }

    public UUID getDocID() {
        return docID;
    }

    public String getPhoneNum() {
        return phoneNum;
    }

    public void setPhoneNum(String phoneNumber) {
        this.phoneNum = phoneNumber;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String s) {
        this.name = s;
    }

    public String getDescription() {
        return this.description;
    }

    public String getHours() {
        return this.hours;
    }

    public ObservableList<Destination> getDestinations() {
        return this.destinations;
    }

    public void setDestinations(ObservableList<Destination> destinations) {
        this.destinations = destinations;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setHours(String hours) {
        this.hours = hours;
    }

    public String[] splitName() {
        String[] name = this.name.split(", ");

        return name;
    }

    public String[] splitPhoneNum() {
        String[] phoneNum = this.phoneNum.split("-");

        return phoneNum;
    }

    public String[] splitHours() {
        String[] hours = this.hours.split(" - ");

        return hours;
    }
}
