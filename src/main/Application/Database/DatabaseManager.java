package main.Application.Database;

import main.Directory.Entity.Doctor;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import main.Map.Entity.*;

import java.sql.*;
import java.util.*;

/**
 * Created by Brandon on 2/4/2017.
 */
public class DatabaseManager {

    private final String framework = "embedded";
    private final String protocol = CustomFilePath.myFilePath;

    private Connection conn = null;
    private ArrayList<Statement> statements = new ArrayList<Statement>(); // list of Statements, PreparedStatements
    private PreparedStatement psInsert;
    private PreparedStatement psUpdate;
    private Statement s;
    private ResultSet rs = null;
    private String username = "user1";
    private String uname = username.toUpperCase();

    private static String CAMPUS_ID = "CAMPUS";

    public static HashMap<UUID, MapNode> mapNodes = new HashMap<>();
    public static HashMap<UUID, NodeEdge> edges = new HashMap<>();
    public static HashMap<String, Doctor> doctors = new HashMap<>();
    public static HashMap<String, Floor> floors = new HashMap<>();
    public static HashMap<UUID, Destination> destinations = new HashMap<>();
    public static HashMap<String, Office> offices = new HashMap<>();

    public static DatabaseManager instance = null;

    public DatabaseManager() throws SQLException
    {

        String driver = "org.apache.derby.jdbc.EmbeddedDriver";

        try
        {
            Class.forName(driver).newInstance();
        } catch (InstantiationException e)
        {
            e.printStackTrace();
        } catch (IllegalAccessException e)
        {
            e.printStackTrace();
        } catch (ClassNotFoundException e)
        {
            e.printStackTrace();
        }

        Properties props = new Properties(); // connection properties
        // providing a user name and password is optional in the embedded
        // and derbyclient frameworks
        props.put("user", username);
        props.put("password", "user1");

           /* By default, the schema APP will be used when no username is
            * provided.
            * Otherwise, the schema name is the same as the user name (in this
            * case "user1" or USER1.)
            *
            * Note that user authentication is off by default, meaning that any
            * user can connect to your database using any password. To enable
            * authentication, see the Derby Developer's Guide.
            */

        String dbName = "derbyDB"; // the name of the database

           /*
            * This connection specifies create=true in the connection URL to
            * cause the database to be created when connecting for the first
            * time. To remove the database, remove the directory derbyDB (the
            * same as the database name) and its contents.
            *
            * The directory derbyDB will be created under the directory that
            * the system property derby.system.home points to, or the current
            * directory (user.dir) if derby.system.home is not set.
            */

        try {
                conn = DriverManager.getConnection(protocol + dbName + ";create=true", props);
        }
        catch (SQLException se) {
            System.out.println(se.getMessage());
        }

        if (conn != null) {
            System.out.println("Connected to and created database " + dbName);
        }

        // We want to control transactions manually. Autocommit is on by
        // default in JDBC.
        try {
            conn.setAutoCommit(false);
        }
        catch (SQLException se) {
            System.out.println(se.getMessage());

        }

           /* Creating a statement object that we can use for running various
            * SQL statements commands against the database.*/
        try {
            s = conn.createStatement();
        }
        catch (SQLException se) {
            System.out.println(se.getMessage());

        }
        statements.add(s);

        try {
            //executeStatements(dropTables);
            executeStatements(createTables);
            executeStatements(fillTables);
        }
        catch(SQLException e) {
            //allows app to continue loading despite having full tables
        }
    }


    private void executeStatements(String[] states) throws SQLException {
        Statement state = conn.createStatement();

        for (String s : states) {
            state.executeUpdate(s);
            conn.commit();
        }

        state.close();
        System.out.println("I think");
    }

    public Hospital loadData() throws SQLException {
        s = conn.createStatement();

        Hospital h = new Hospital();

        loadHospital(h);

        conn.commit();

        return h;
    }

    public void saveData(Hospital h) throws SQLException {
        s = conn.createStatement();
        executeStatements(dropTables);
        executeStatements(createTables);
        //saveHospital(h);
        s.close();

        System.out.println("Data Saved Correctly");
    }

    private void loadHospital(Hospital h) throws SQLException {
        loadBuilding(h);
        loadDestinationOffice(h);
        loadDoctors(h);
        loadEdges(h);
    }

    private void loadBuilding(Hospital h) throws SQLException
    {
        conn.setAutoCommit(false);

        s = conn.createStatement();
        ResultSet rs = s.executeQuery("SELECT * FROM USER1.BUILDING ORDER BY NAME ASC");

        while(rs.next()) {
            String buildName = rs.getString(2);
            Building b = new Building(UUID.fromString(rs.getString(1)), rs.getString(2));
            loadFloors(h, b);
            System.out.println("Loaded Building" + buildName);

            if (!buildName.equals("Campus")) {
                h.addBuilding(b);
                System.out.println("Not Campus");
            }
        }
    }

    private void loadFloors(Hospital h, Building b) throws SQLException {
        PreparedStatement floorsPS = conn.prepareStatement("SELECT * FROM FLOOR WHERE BUILDING_ID = ?");
        floorsPS.setString(1, b.getBuildID().toString());
        ResultSet floorRS = floorsPS.executeQuery();

        //floorsPS.setString(1, uuid.toString());

        UUID floor_id;
        Floor f;

        while (floorRS.next()) {
            floor_id = UUID.fromString(floorRS.getString(1));
            if (!b.getName().equals("Campus")) {
                f = new Floor(floor_id, floorRS.getInt(3));
                System.out.println("Loaded Floor" + floor_id);
                    f.setImageLocation(floorRS.getString(4));
                    f.setBuilding(b);

                    loadNodes(h, f);
                    try {
                        b.addFloor(f);
                        f.setBuilding(b);
                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                    }
            }
            else {
                f = h.getCampusFloor();
                loadNodes(h, f);
            }
        }
    }

    private void loadNodes(Hospital h, Floor f) throws SQLException
    {
        PreparedStatement nodesPS = conn.prepareStatement("SELECT * from NODE where FLOOR_ID = ?");
        PreparedStatement destPS = conn.prepareStatement("SELECT * FROM DESTINATION WHERE FLOOR_ID = ?");

        String floorID = f.getFloorID().toString();

        // load all nodes with floorID
        nodesPS.setString(1, floorID); // set query for specific floor
        ResultSet nodeRS = nodesPS.executeQuery();

        UUID nodeid;

        HashMap<UUID, MapNode> nodes = new HashMap<>();

        while(nodeRS.next()) {
            nodeid = UUID.fromString(nodeRS.getString(1));
            System.out.println("Loaded Node " + nodeid);
            MapNode tempNode = new MapNode((nodeid),
                    nodeRS.getDouble(2),
                    nodeRS.getDouble(3),
                    nodeRS.getInt(5));

            // keep track of all nodes in hospital
            mapNodes.put(nodeid, tempNode);

            // nodes per floor
            nodes.put(nodeid, tempNode);
        }

        // load all destinations with floorID
        destPS.setString(1, floorID);
        ResultSet destRS = destPS.executeQuery();

        while(destRS.next()) {
            // UUID for node we are dealing with
            UUID tempNodeID = UUID.fromString(destRS.getString(3));
            // get node to be replaced by destination
            MapNode changedNode = mapNodes.get(tempNodeID);
            // create destination
            Destination tempDest = new Destination(UUID.fromString(destRS.getString(1)),
                    changedNode,
                    destRS.getString(2),
                    UUID.fromString(floorID));

            // replace regular nodes with destination nodes
            mapNodes.remove(tempNodeID);
            mapNodes.put(tempNodeID, tempDest);

            // replace nodes in current floor
            nodes.remove(tempNodeID);
            nodes.put(tempNodeID, tempDest);

            // adds destination to hospital list of destinations
            destinations.put(UUID.fromString(destRS.getString(1)), tempDest);
        }

        PreparedStatement kioskPS = conn.prepareStatement("SELECT * FROM KIOSK");

        ResultSet kioskRS = kioskPS.executeQuery();

        while(kioskRS.next()){
            UUID tempID = UUID.fromString(kioskRS.getString(2));
            if (nodes.containsKey(tempID)) {
                // node to replace with kiosk
                MapNode replacedNode = nodes.get(tempID);

                // kiosk to replace node with
                Kiosk tempKiosk = new Kiosk(replacedNode,
                        kioskRS.getString(1),
                        kioskRS.getString(3));

                // if saved as default kiosk, then set as current kiosk
                if (kioskRS.getBoolean(4)) {
                    h.setCurrentKiosk(tempKiosk);
                }

                // replace regular nodes with kiosk nodes
                mapNodes.remove(tempID);
                mapNodes.put(tempID, tempKiosk);

                // replace nodes in current floor
                nodes.remove(tempID);
                nodes.put(tempID, tempKiosk);
                // adds kiosk to list of hospital's kiosks
                h.addKiosk(tempKiosk);
            }
        }

        if (f.getBuilding().getName().equals("Campus")) {
            for (MapNode n : nodes.values()) {
                h.getCampusFloor().addNode(n);
            }
        }
        else {
            // add all nodes to floor
            for (MapNode n : nodes.values()) {
                f.addNode(n);
            }
        }
    }

    private void loadEdges(Hospital h) throws SQLException
    {
        // select all for edges table
        PreparedStatement edgesPS = conn.prepareStatement("SELECT * FROM EDGE");

        ResultSet edgeRS = edgesPS.executeQuery();


        while(edgeRS.next()) {
            // create new edge
            NodeEdge tempEdge = new NodeEdge(UUID.fromString(edgeRS.getString(1)),
                    mapNodes.get(UUID.fromString(edgeRS.getString(2))),
                    mapNodes.get(UUID.fromString(edgeRS.getString(3))),
                    edgeRS.getFloat(4));

            tempEdge.setSource(mapNodes.get(UUID.fromString(edgeRS.getString(2)))); //should be redundant?
            tempEdge.setTarget(mapNodes.get(UUID.fromString(edgeRS.getString(3)))); //should be redundant?

            //System.out.println(mapNodes.get(UUID.fromString(rs.getString(2))).getEdges().contains(tempEdge));

            //stores all nodeEdges
            this.edges.put(UUID.fromString(edgeRS.getString(1)), tempEdge);

        }
    }

    private void loadDoctors(Hospital h) throws SQLException
    {
        PreparedStatement destDoc = conn.prepareStatement("select DEST_ID from DEST_DOC where doc_id = ?");

        PreparedStatement docPS = conn.prepareStatement("SELECT * FROM DOCTOR");

        ResultSet docRS = docPS.executeQuery();

        while(docRS.next()) {
            ObservableList<Destination> locations = FXCollections.observableArrayList();

            // create new Doctor
            Doctor tempDoc = new Doctor(UUID.fromString(docRS.getString(1)),
                    docRS.getString(2),
                    docRS.getString(3),
                    docRS.getString(5),
                    locations);

            tempDoc.setPhoneNum(docRS.getString(4)); // sets phone number

            destDoc.setString(1, docRS.getString(1));
            ResultSet results = destDoc.executeQuery();
            // create doctor - destination relationships within the objects
            while(results.next()) {
                destinations.get(UUID.fromString(results.getString(1))).addDoctor(tempDoc);
                locations.add(destinations.get(UUID.fromString(results.getString(1))));
            }
//            doctors.put(UUID.fromString(rs.getString(2)),
//                    new Doctor(UUID.fromString(rs.getString(1)),
//                            rs.getString(2),
//                            rs.getString(3),
//                            rs.getString(5),
//                            locations));
            doctors.put(docRS.getString(2), tempDoc);

        }

        for (Doctor doc : doctors.values()) {
            h.addDoctor(doc);
        }
        //System.out.println(doctors.keySet());
    }

    private void loadDestinationOffice(Hospital h) throws SQLException
    {
        PreparedStatement destOff = conn.prepareStatement("select * from OFFICES where DEST_ID = ?");

        PreparedStatement destPS = conn.prepareStatement("SELECT * FROM DESTINATION");


        ResultSet destRS = destPS.executeQuery();
        while (destRS.next()) {

            destOff.setString(1, destRS.getString(1));
            // set of offices with particular destination ID foreign key
            ResultSet offRS = destOff.executeQuery();
            while(offRS.next()) {
                Office tempOff = new Office(UUID.fromString(offRS.getString(1)),
                        offRS.getString(2),
                        (destinations.get(UUID.fromString(destRS.getString(1)))));

                // add office to hospital offices list
                //offices.put(offRS.getString(2), tempOff);
                h.addOffice(tempOff);
                //System.out.println("******************************" + tempOff.getName());

                // add office to list of offices for a destination
                destinations.get(UUID.fromString(destRS.getString(1))).addOffice(tempOff);
            }
        }

        for (Destination d : destinations.values()) {
            h.addDestinations(d);
        }
    }

    public void addDocToDB(Doctor d) throws SQLException{
        PreparedStatement insertDoc = conn.prepareStatement("INSERT INTO DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) " +
                "VALUES (?, ?, ?, ?, ?)");
        // insert doctor to database
        insertDoc.setString(1, d.getDocID().toString());
        insertDoc.setString(2, d.getName());
        insertDoc.setString(3, d.getDescription());
        insertDoc.setString(4, d.getPhoneNum());
        insertDoc.setString(5, d.getHours());
        insertDoc.executeUpdate();
        conn.commit();

        for (Destination dest : d.getDestinations()) {
            addDestToDoc(d, dest);
        }
    }

    public void updateDoctor(Doctor d) throws SQLException {
        PreparedStatement upDoc = conn.prepareStatement("UPDATE DOCTOR " +
                "SET NAME = ?, DESCRIPTION = ?, NUMBER = ?, HOURS = ?" +
                "WHERE DOC_ID = ?");

        // update Doctor in database with all info in case it changed
        upDoc.setString(1, d.getName());
        upDoc.setString(2, d.getDescription());
        upDoc.setString(3, d.getPhoneNum());
        upDoc.setString(4, d.getHours());
        upDoc.setString(5, d.getDocID().toString());
        upDoc.executeUpdate();
        conn.commit();
    }

    public void delDocFromDB(Doctor doctor) throws SQLException{
        PreparedStatement deleteDoc = conn.prepareStatement("DELETE FROM DOCTOR WHERE DOC_ID = ?");

        String docUUID = doctor.getDocID().toString();

        // delete doctor
        deleteDoc.setString(1, docUUID);
        deleteDoc.executeUpdate();
        conn.commit();
    }

    public void addDestToDB(Destination dest) throws SQLException {
        PreparedStatement insertDest = conn.prepareStatement("INSERT INTO DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) " +
                "VALUES (?, ?, ?, ?)");
        // insert destination to database
        insertDest.setString(1, dest.getDestUID().toString());
        insertDest.setString(2, dest.getName());
        insertDest.setString(3, dest.getNodeID().toString());
        insertDest.setString(4, dest.getMyFloor().getFloorID().toString());
        insertDest.executeUpdate();
        conn.commit();
    }

    public void updateDestination(Destination dest) throws SQLException {
        PreparedStatement upDest = conn.prepareStatement("UPDATE DESTINATION " +
                "SET NAME = ? " +
                "WHERE DEST_ID = ?");

        upDest.setString(1, dest.getName());
        upDest.setString(2, dest.getDestUID().toString());
        upDest.executeUpdate();
        conn.commit();
    }

    public void delDestFromDB(Destination dest) throws SQLException {
        String destUUID = dest.getDestUID().toString();

        PreparedStatement deleteDest = conn.prepareStatement("DELETE FROM DESTINATION WHERE DEST_ID = ?");

        // delete destination
        deleteDest.setString(1, destUUID);
        deleteDest.executeUpdate();
        conn.commit();
    }

    public void addDestToDoc(Doctor doc, Destination dest) throws SQLException {
        PreparedStatement destDoc = conn.prepareStatement("INSERT INTO DEST_DOC (DEST_ID, DOC_ID) " +
                "VALUES (?, ?)");
        // adds destination - doctor relationships to database
        destDoc.setString(1, dest.getDestUID().toString());
        destDoc.setString(2, doc.getDocID().toString());
        destDoc.executeUpdate();
        conn.commit();
    }

    public void addKioskToDB(Kiosk k) throws SQLException {
        PreparedStatement insertKiosk = conn.prepareStatement("INSERT INTO KIOSK (NAME, NODE_ID, DIRECTION, FLAG) " +
                "VALUES (?, ?, ?, ?)");
        // insert kiosk to database
        insertKiosk.setString(1, k.getName());
        insertKiosk.setString(2, k.getNodeID().toString());
        insertKiosk.setString(3, k.getDirection());
        insertKiosk.setBoolean(4, false);
        insertKiosk.executeUpdate();
        conn.commit();
    }

    public void updateKiosk(Kiosk k) throws SQLException {
        PreparedStatement upKiosk = conn.prepareStatement("UPDATE KIOSK " +
                "SET NAME = ? " +
                "WHERE NODE_ID = ?");

        upKiosk.setString(1, k.getName());
        upKiosk.setString(2, k.getNodeID().toString());
        upKiosk.executeUpdate();
        conn.commit();
    }

    public void updateCurKiosk(Kiosk k) throws SQLException {
        PreparedStatement upOldKiosk = conn.prepareStatement("UPDATE KIOSK " +
                "SET FLAG = ? " +
                "WHERE FLAG = ?");

        upOldKiosk.setBoolean(1, false);
        upOldKiosk.setBoolean(2, true);
        upOldKiosk.executeUpdate();
        conn.commit();

        PreparedStatement upKiosk = conn.prepareStatement("UPDATE KIOSK " +
                "SET FLAG = ? " +
                "WHERE NODE_ID = ?");

        upKiosk.setBoolean(1, true);
        upKiosk.setString(2, k.getNodeID().toString());
        upKiosk.executeUpdate();
        conn.commit();
    }

    public void addNodeToDB(MapNode m) throws SQLException {
        PreparedStatement insertNode = conn.prepareStatement("INSERT INTO NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) " +
                "VALUES (?, ?, ?, ?, ?)");

        insertNode.setString(1, m.getNodeID().toString());
        insertNode.setDouble(2, m.getPosX());
        insertNode.setDouble(3, m.getPosY());
        insertNode.setString(4, m.getMyFloor().getFloorID().toString());
        insertNode.setInt(5, m.getType().ordinal());
        insertNode.executeUpdate();
        conn.commit();
        System.out.println("Added node to database");
    }

    public void updateNode(MapNode m) throws SQLException {
        PreparedStatement upNode = conn.prepareStatement("UPDATE NODE " +
                "SET POSX = ?, POSY = ?" +
                "WHERE NODE_ID = ?");

        // update Node in database with new pos X and pos Y
        upNode.setDouble(1, m.getPosX());
        upNode.setDouble(2, m.getPosY());
        upNode.setString(3, m.getNodeID().toString());
        upNode.executeUpdate();
        conn.commit();
    }

    public void delNodeFromDB(MapNode m) throws SQLException {
        UUID nodeUUID = m.getNodeID();

        PreparedStatement delNode = conn.prepareStatement("DELETE FROM NODE WHERE NODE_ID = ?");

        // delete node with given uuid
        delNode.setString(1, nodeUUID.toString());
        delNode.executeUpdate();
        conn.commit();
    }

    public void addEdgeToDB(NodeEdge e) throws SQLException {
        PreparedStatement insertEdge = conn.prepareStatement("INSERT INTO EDGE (EDGE_ID, NODEA, NODEB, COST) " +
                "VALUES (?, ?, ?, ?)");

        insertEdge.setString(1, e.getEdgeID().toString());
        insertEdge.setString(2, e.getSource().getNodeID().toString());
        insertEdge.setString(3, e.getTarget().getNodeID().toString());
        insertEdge.setDouble(4, e.getCost());
        insertEdge.executeUpdate();
        conn.commit();
    }

    public void updateEdge(NodeEdge e) throws SQLException {
        PreparedStatement upEdge = conn.prepareStatement("UPDATE EDGE " +
                "SET COST = ? " +
                "WHERE EDGE_ID = ?");

        // update edge in database with new cost
        upEdge.setDouble(1, e.getCost());
        upEdge.setString(2, e.getEdgeID().toString());
    }

    public void delEdgeFromDB(NodeEdge e) throws SQLException { // TODO change edges to have UUID
        PreparedStatement delEdge = conn.prepareStatement("DELETE FROM EDGE WHERE EDGE_ID = ?");

        // delete all edges that have given node as source or target
        delEdge.setString(1, e.getEdgeID().toString());
        delEdge.executeUpdate();
        conn.commit();
    }

    public void addOfficeToDB(Office o) throws SQLException {
        PreparedStatement insertOffice = conn.prepareStatement("INSERT INTO OFFICES (OFFICE_ID, NAME, DEST_ID) " +
                "VALUES (?, ?, ?)");
        // insert office to database
        insertOffice.setString(1, o.getId().toString());
        insertOffice.setString(2, o.getName());
        insertOffice.setString(3, o.getDestination().getDestUID().toString());
        insertOffice.executeUpdate();
        conn.commit();
    }

    public void updateOffice(Office o) throws SQLException {
        PreparedStatement upOff = conn.prepareStatement("UPDATE OFFICES " +
                "SET NAME = ?, DEST_ID = ? " +
                "WHERE OFFICE_ID = ?");
        // get office UUID
        String offUUID = o.getId().toString();
        // get destination UUID for office
        String offDest = o.getDestination().getDestUID().toString();
        // update office info in database
        upOff.setString(1, o.getName());
        upOff.setString(2, offDest);
        upOff.setString(3, offUUID);
        upOff.executeUpdate();
        conn.commit();
    }

    public void delOfficeFromDB(Office o) throws SQLException {
        PreparedStatement delOffice = conn.prepareStatement("DELETE FROM OFFICES WHERE OFFICE_ID = ?");
        // get office UUID
        String offUUID = o.getId().toString();
        // delete office from database
        delOffice.setString(1, offUUID);
        delOffice.executeUpdate();
        conn.commit();
    }

    public void deleteFloor(Floor floor) throws SQLException {
        PreparedStatement floorsPS = conn.prepareStatement("DELETE FROM FLOOR WHERE FLOOR_ID = ?");
        s = conn.createStatement();

        s.executeQuery("SELECT * FROM USER1.FLOOR ORDER BY NUMBER DESC ");

        while(rs.next())
        {
            floorsPS.setString(1, floor.getFloorID().toString());
            floorsPS.executeUpdate();
        }
    }

    public void addFloor(Floor f, Building b) throws SQLException {
        PreparedStatement insertFloors = conn.prepareStatement("INSERT INTO FLOOR (FLOOR_ID, BUILDING_ID, NUMBER, IMAGE) VALUES (?, ?, ?, ?)");

        insertFloors.setString(1, f.getFloorID().toString());
        insertFloors.setString(2, b.getBuildID().toString());
        insertFloors.setInt(3, f.getFloorNumber());
        insertFloors.setString(4, f.getImageLocation());
        insertFloors.executeUpdate();
    }

    public void deleteBuilding(Building building) throws SQLException {
        PreparedStatement buildingPS = conn.prepareStatement("DELETE FROM BUILDING WHERE BUILDING_ID = ?");
        s = conn.createStatement();

        s.executeQuery("SELECT * FROM USER1.BUILDING");

        while(rs.next())
        {
            buildingPS.setString(1, building.getBuildID().toString());
            buildingPS.executeUpdate();
        }
    }

    public void addBuilding(Building building) throws SQLException {
        PreparedStatement insertBuildings = conn.prepareStatement("INSERT INTO BUILDING (BUILDING_ID, NAME) VALUES (?, ?)");

        insertBuildings.setString(1, building.getBuildID().toString());
        insertBuildings.setString(2, building.getName());
        insertBuildings.executeUpdate();
    }

    public void shutdown() {
        try
        {
            // the shutdown=true attribute shuts down Derby
            DriverManager.getConnection("jdbc:derby:;shutdown=true");

            // To shut down a specific database only, but keep the
            // engine running (for example for connecting to other
            // databases), specify a database in the connection URL:
            //DriverManager.getConnection("jdbc:derby:" + dbName + ";shutdown=true");
        }
        catch (SQLException se)
        {
            if (( (se.getErrorCode() == 50000)
                    && ("XJ015".equals(se.getSQLState()) ))) {
                // we got the expected exception
                System.out.println("Derby shut down normally");
                // Note that for single database shutdown, the expected
                // SQL state is "08006", and the error code is 45000.
            } else {
                // if the error code or SQLState is different, we have
                // an unexpected exception (shutdown failed)
                System.err.println("Derby did not shut down normally");
                System.out.println(se.getStackTrace());
            }
        }
    }

    public static final String[] createTables = {
            "CREATE TABLE BUILDING (BUILDING_ID CHAR(36) PRIMARY KEY NOT NULL, " +
                    "NAME VARCHAR(100))",

            "CREATE TABLE FLOOR (FLOOR_ID CHAR(36) PRIMARY KEY NOT NULL, " +
                    "BUILDING_ID CHAR(36), " +
                    "NUMBER INT, " +
                    "IMAGE VARCHAR(75), " +
                    "CONSTRAINT FLOOR_BUILDING_BUILDING_ID_FK FOREIGN KEY (BUILDING_ID) REFERENCES BUILDING (BUILDING_ID))",

            "CREATE TABLE NODE (NODE_ID CHAR(36) PRIMARY KEY NOT NULL, " +
                    "POSX FLOAT(52), " +
                    "POSY FLOAT(52), " +
                    "FLOOR_ID CHAR(36), " +
                    "TYPE INT, " +
                    "CONSTRAINT NODE_FLOOR_FLOOR_ID_FK FOREIGN KEY (FLOOR_ID) REFERENCES FLOOR (FLOOR_ID) ON DELETE CASCADE)",

            "CREATE TABLE KIOSK (NAME VARCHAR(30), " +
                    "NODE_ID CHAR(36), " +
                    "DIRECTION VARCHAR(5), " +
                    "FLAG SMALLINT, " +
                    "CONSTRAINT KIOSK_NODE_NODE_ID_FK FOREIGN KEY (NODE_ID) REFERENCES NODE (NODE_ID) ON DELETE CASCADE)",

            "CREATE TABLE DESTINATION (DEST_ID CHAR(36) PRIMARY KEY NOT NULL, " +
                    "NAME VARCHAR(200), " +
                    "NODE_ID CHAR(36), " +
                    "FLOOR_ID CHAR(36), " +
                    "CONSTRAINT DESTINATION_NODE_NODE_ID_FK FOREIGN KEY (NODE_ID) REFERENCES NODE (NODE_ID) ON DELETE CASCADE, " +
                    "CONSTRAINT DESTINATION_FLOOR_FLOOR_ID_FK FOREIGN KEY (FLOOR_ID) REFERENCES FLOOR (FLOOR_ID) ON DELETE CASCADE)",

            "CREATE TABLE DOCTOR (DOC_ID CHAR(36) PRIMARY KEY NOT NULL, " +
                    "NAME VARCHAR(50), " +
                    "DESCRIPTION VARCHAR(20), " +
                    "NUMBER VARCHAR(20), " +
                    "HOURS VARCHAR(20))",

            "CREATE TABLE OFFICES (OFFICE_ID CHAR(36) PRIMARY KEY NOT NULL, " +
                    "NAME VARCHAR(200), " +
                    "DEST_ID CHAR(36), " +
                    "CONSTRAINT OFFICES_DESTINATION_DESTINATION_ID_FK FOREIGN KEY (DEST_ID) REFERENCES DESTINATION (DEST_ID) ON DELETE CASCADE)",

            "CREATE TABLE EDGE (EDGE_ID CHAR(36) PRIMARY KEY NOT NULL, " +
                    "NODEA CHAR(36), " +
                    "NODEB CHAR(36), " +
                    "COST FLOAT(52), " +
                    "CONSTRAINT EDGE_NODE_NODE_ID_FKA FOREIGN KEY (NODEA) REFERENCES NODE (NODE_ID) ON DELETE CASCADE, " +
                    "CONSTRAINT EDGE_NODE_NODE_ID_FKB FOREIGN KEY (NODEB) REFERENCES NODE (NODE_ID) ON DELETE CASCADE)",

            "CREATE TABLE DEST_DOC (DEST_ID CHAR(36), " +
                    "DOC_ID CHAR(36), " +
                    "CONSTRAINT DESTINATION_DOC_DESTINATION_DESTINATION_ID_FK1 FOREIGN KEY (DEST_ID) REFERENCES DESTINATION (DEST_ID) ON DELETE CASCADE, " +
                    "CONSTRAINT DESTINATION_DOC_DOCTOR_DOCTOR_ID_FK2 FOREIGN KEY (DOC_ID) REFERENCES DOCTOR (DOC_ID) ON DELETE CASCADE)"
    };

    public static final String[] dropTables = {
            "DROP TABLE USER1.DEST_DOC",
            "DROP TABLE USER1.EDGE",
            "DROP TABLE USER1.OFFICES",
            "DROP TABLE USER1.DOCTOR",
            "DROP TABLE USER1.DESTINATION",
            "DROP TABLE USER1.KIOSK",
            "DROP TABLE USER1.NODE",
            "DROP TABLE USER1.FLOOR",
            "DROP TABLE USER1.BUILDING"};

    public static final String[] fillTables = {
            "INSERT INTO USER1.BUILDING (BUILDING_ID, NAME) VALUES ('00000000-0000-0000-0003-000000000000', 'Faulkner')",
            "INSERT INTO USER1.BUILDING (BUILDING_ID, NAME) VALUES ('00000000-0000-0000-0002-000000000000', 'Belkin')",
            "INSERT INTO USER1.BUILDING (BUILDING_ID, NAME) VALUES ('00000000-0000-0000-0001-000000000000', 'Campus')",

            "INSERT INTO USER1.FLOOR (FLOOR_ID, BUILDING_ID, NUMBER, IMAGE) VALUES ('00000000-0000-00b1-0000-000000000000', '00000000-0000-0000-0002-000000000000', 1, '1_thefirstfloor.png')",
            "INSERT INTO USER1.FLOOR (FLOOR_ID, BUILDING_ID, NUMBER, IMAGE) VALUES ('00000000-0000-00b3-0000-000000000000', '00000000-0000-0000-0002-000000000000', 3, '3_thethirdfloor.png')",
            "INSERT INTO USER1.FLOOR (FLOOR_ID, BUILDING_ID, NUMBER, IMAGE) VALUES ('00000000-0000-00b4-0000-000000000000', '00000000-0000-0000-0002-000000000000', 4, '4_thefourthfloor.png')",
            "INSERT INTO USER1.FLOOR (FLOOR_ID, BUILDING_ID, NUMBER, IMAGE) VALUES ('00000000-0000-00b2-0000-000000000000', '00000000-0000-0000-0002-000000000000', 2, '2_thesecondfloor.png')",
            "INSERT INTO USER1.FLOOR (FLOOR_ID, BUILDING_ID, NUMBER, IMAGE) VALUES ('00000000-0000-00f2-0000-000000000000', '00000000-0000-0000-0003-000000000000', 2, '2_thesecondfloor.png')",
            "INSERT INTO USER1.FLOOR (FLOOR_ID, BUILDING_ID, NUMBER, IMAGE) VALUES ('00000000-0000-00f5-0000-000000000000', '00000000-0000-0000-0003-000000000000', 5, '5_thefifthfloor.png')",
            "INSERT INTO USER1.FLOOR (FLOOR_ID, BUILDING_ID, NUMBER, IMAGE) VALUES ('00000000-0000-00f3-0000-000000000000', '00000000-0000-0000-0003-000000000000', 3, '3_thethirdfloor.png')",
            "INSERT INTO USER1.FLOOR (FLOOR_ID, BUILDING_ID, NUMBER, IMAGE) VALUES ('00000000-0000-00f4-0000-000000000000', '00000000-0000-0000-0003-000000000000', 4, '4_thefourthfloor.png')",
            "INSERT INTO USER1.FLOOR (FLOOR_ID, BUILDING_ID, NUMBER, IMAGE) VALUES ('00000000-0000-00f1-0000-000000000000', '00000000-0000-0000-0003-000000000000', 1, '1_thefirstfloor.png')",
            "INSERT INTO USER1.FLOOR (FLOOR_ID, BUILDING_ID, NUMBER, IMAGE) VALUES ('00000000-0000-00f7-0000-000000000000', '00000000-0000-0000-0003-000000000000', 7, '7_theseventhfloor.png')",
            "INSERT INTO USER1.FLOOR (FLOOR_ID, BUILDING_ID, NUMBER, IMAGE) VALUES ('00000000-0000-00f6-0000-000000000000', '00000000-0000-0000-0003-000000000000', 6, '6_thesixthfloor.png')",
            "INSERT INTO USER1.FLOOR (FLOOR_ID, BUILDING_ID, NUMBER, IMAGE) VALUES ('00000000-0000-00c1-0000-000000000000', '00000000-0000-0000-0001-000000000000', 1, ''",


            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000001', 'Bachman, William', 'MD', '', '3:30 - 10:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000002', 'Epstein, Lawrence', 'MD', '', '7:00 - 3:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000003', 'Stacks, Robert', 'MD', '', '12:00 - 8:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000004', 'Healy, Barbara', 'RN', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000005', 'Morrison, Beverly', 'MD', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000006', 'Monaghan, Colleen', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000007', 'Malone, Linda', 'DNP, RN, CPNP', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000008', 'Hinton, Nadia', 'RDN, LDN', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000009', 'Quan, Stuart', 'MD', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000010', 'Savage, Robert', 'MD', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000011', 'Walsh Samp, Kathy', 'LICSW', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000012', 'Fitz, Wolfgang', 'MD', '', '6:00 - 2:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000013', 'Kleifield, Allison', 'PA-C', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000014', 'Pilgrim, David', 'MD', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000015', 'Davidson, Paul', 'PhD', '', '3:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000016', 'Smith, Shannon', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000017', 'Jeselsohn, Rinath', 'MD', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000018', 'Lahair, Tracy', 'PA-C', '', '3:30 - 10:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000019', 'Irani, Jennifer', 'MD', '', '3:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000020', 'Earp, Brandon', 'MD', '', '3:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000021', 'Robinson, Malcolm', 'MD', '', '6:00 - 2:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000022', 'Matzkin, Elizabeth', 'MD', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000023', 'Alqueza, Arnold', 'MD', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000024', 'Hergrueter, Charles', 'MD', '', '1:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000025', 'Higgins, Laurence', 'MD', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000026', 'Lauretti, Linda', 'MD', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000027', 'Mathew, Paul', 'MD', '', '5:30 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000028', 'Wickner, Paige', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000029', 'Bluman, Eric', 'MD', '', '1:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000030', 'Lilly, Leonard Stuart', 'MD', '', '3:30 - 10:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000031', 'Groden, Joseph', 'MD', '', '3:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000032', 'Kornack, Fulton', 'MD', '', '7:00 - 3:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000033', 'Owens, Lisa Michelle', 'MD', '', '12:00 - 8:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000034', 'Javaheri, Sogol', 'MD', '', '7:00 - 3:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000035', 'OHare, Kitty', 'MD', '', '7:00 - 3:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000036', 'Cochrane, Thomas', 'MD', '', '1:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000037', 'Shah, Amil', 'MD', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000038', 'Bartool-Anwar, Salma', 'MD, MPH', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000039', 'Ramirez, Alberto', 'MD', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000040', 'Lahive, Karen', 'MD', '', '12:00 - 8:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000041', 'Cua, Christopher', 'MD', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000042', 'Friedman, Pamela', 'PsyD, ABPP', '', '3:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000043', 'Micley, Bruce', 'MD', '', '5:30 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000044', 'Halperin, Florencia', 'MD', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000045', 'Horowitz, Sandra', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000046', 'Halvorson, Eric', 'MD', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000047', 'Kramer, Justine', 'PA-C', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000048', 'Patten, James', 'MD', '', '7:30 - 4:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000049', 'White, David', 'MD', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000050', 'Sweeney, Michael', 'MD', '', '3:30 - 10:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000051', 'Drew, Michael', 'MD', '', '1:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000052', 'Nelson, Ehren', 'MD', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000053', 'Schissel, Scott', 'MD', '', '9:00 - 5:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000054', 'Byrne, Jennifer', 'RN, CPNP', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000055', 'Greenberg, James Adam', 'MD', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000056', 'Altschul, Nomee', 'PA-C', '', '3:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000057', 'Chun, Yoon Sun', 'MD', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000058', 'Eatman, Arlan', 'LCSW', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000059', 'Howard, Neal Anthony', 'LICSW', '', '5:30 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000060', 'Steele, Graeme', 'MD', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000061', 'Fromson, John', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000062', 'Khaodhiar, Lalita', 'MD', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000063', 'Keller, Beth', 'RN, PsyD', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000064', 'Harris, Mitchel', 'MD', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000065', 'Rangel, Erika', 'MD', '', '7:30 - 4:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000066', 'Caterson, Stephanie', 'MD', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000067', 'Miner, Julie', 'MD', '', '7:30 - 4:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000068', 'Novak, Peter', 'MD', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000069', 'Schmults, Chrysalyne', 'MD', '', '3:30 - 10:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000070', 'Todd, Derrick', 'MD, PhD', '', '6:00 - 2:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000071', 'Bernstein, Carolyn', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000072', 'Sharma, Niraj', 'MD', '', '5:30 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000073', 'Dawson, Courtney', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000074', 'King, Tari', 'MD', '', '3:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000075', 'Blazar Phil', 'MD', '', '9:00 - 5:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000076', 'Laskowski, Karl', 'MD', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000077', 'Nadarajah, Sarah', 'WHNP', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000078', 'Ruff, Christian', 'MD', '', '1:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000079', 'Carleen, Mary Anne', 'PA-C', '', '1:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000080', 'Nehs, Matthew', 'MD', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000081', 'Berman, Stephanie', 'MD', '', '7:30 - 4:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000082', 'Melnitchouk, Neyla', 'MD', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000083', 'Viola, Julianne', 'MD', '', '5:30 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000084', 'Balash, Eva', 'MD', '', '12:00 - 8:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000085', 'Hartman, Katy', 'MS, RD, LDN', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000086', 'Samadi, Farrah', 'NP', '', '7:30 - 4:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000087', 'Joyce, Eileen', 'LICSW', '', '1:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000088', 'Ingram, Abbie', 'PA-C', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000089', 'Perry, David', 'LICSW', '', '7:00 - 3:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000090', 'Frangos, Jason', 'MD', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000091', 'McKenna, Robert', 'PA-C', '', '9:00 - 5:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000092', 'Copello, Maria', 'MD', '', '1:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000093', 'Cardet, Juan Carlos', 'MD', '', '6:00 - 2:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000094', 'McDonnell, Marie', 'MD', '', '7:00 - 3:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000095', 'Belkin, Michael', 'MD', '', '5:30 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000096', 'Wagle, Neil', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000097', 'Pingeton, Mallory', 'PA-C', '', '3:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000098', 'Romano, Keith', 'MD', '', '12:00 - 8:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000099', 'Drewniak, Stephen', 'MD', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000100', 'Mullally, William', 'MD', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000244', 'Issa, Mohammed', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000101', 'Ermann, Jeorg', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000102', 'Miatto, Orietta', 'MD', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000103', 'Issac, Zacharia', 'MD', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000104', 'Warth, James', 'MD', '', '6:00 - 2:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000105', 'Yudkoff, Benjamin', 'MD', '', '3:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000106', 'Clark, Roger', 'DO', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000107', 'McKitrick, Charles', 'MD', '', '3:30 - 10:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000108', 'OConnor, Elizabeth', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000109', 'Boatwright, Giuseppina', 'MD', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000110', 'Caplan, Laura', 'PA-C', '', '7:00 - 3:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000111', 'Warth, Maria', 'MD', '', '9:00 - 5:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000112', 'McMahon, Gearoid', 'MD', '', '12:00 - 8:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000113', 'Hentschel, Dirk', 'MD', '', '7:00 - 3:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000114', 'Cahan, David', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000115', 'Haimovici, Florina', 'MD', '', '5:30 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000116', 'Matloff, Daniel', 'MD', '', '1:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000117', 'Stone, Rebecca', 'MD', '', '3:30 - 10:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000118', 'Isom, Kellene', 'MS, RN, LDN', '', '3:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000119', 'Hartigan, Joseph', 'DPM', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000120', 'Homenko, Daria', 'MD', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000121', 'Roditi, Rachel', 'MD', '', '9:00 - 5:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000122', 'Scheff, David', 'MD', '', '6:00 - 2:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000123', 'Humbert, Timberly', 'MD', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000124', 'Lu, Yi', 'MD', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000125', 'Rizzoli, Paul', 'MD', '', '7:00 - 3:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000126', 'Paperno, Halie', 'Au.D, CCC-A', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000127', 'Omobomi, Olabimpe', 'MD', '', '3:30 - 10:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000128', 'Innis, William', 'MD', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000129', 'Divito, Sherrie', 'MD, PhD', '', '7:00 - 3:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000130', 'Dave, Jatin', 'MD', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000131', 'Shoji, Brent', 'MD', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000132', 'Ash, Samuel', 'MD', '', '9:00 - 5:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000133', 'Parker, Leroy', 'MD', '', '3:30 - 10:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000134', 'Goldman, Jill', 'MD', '', '5:30 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000135', 'Kessler, Joshua', 'MD', '', '3:30 - 10:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000136', 'Leone, Amanda', 'LICSW', '', '6:00 - 2:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000137', 'Samara, Mariah', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000138', 'Fanta, Christopher', 'MD', '', '7:00 - 3:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000139', 'Keller, Elisabeth', 'MD', '', '5:30 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000140', 'Conant, Alene', 'MD', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000141', 'Palermo, Nadine', 'MD', '', '1:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000142', 'Chan, Walter', 'MD', '', '3:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000143', 'Dowd, Erin', 'LICSW', '', '7:00 - 3:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000144', 'Nuspl, Kristen', 'PA-C', '', '3:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000145', 'Lafleur, Emily', 'PA-C', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000146', 'Saldana, Fidencio', 'MD', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000147', 'Welker, Roy', 'MD', '', '1:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000148', 'McNabb-Balter, Julia', 'MD', '', '1:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000149', 'Malone, Michael', 'MD', '', '5:30 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000150', 'Oliver, Lynn', 'RN', '', '7:00 - 3:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000151', 'Voiculescu, Adina', 'MD', '', '7:30 - 4:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000152', 'Saluti, Andrew', 'DO', '', '5:30 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000153', 'Sheth, Samira', 'NP', '', '7:30 - 4:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000154', 'Cardin, Kristin', 'NP', '', '9:00 - 5:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000155', 'Butler, Matthew', 'DPM', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000156', 'Duggan, Margaret', 'MD', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000157', 'Connell, Nathan', 'MD', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000158', 'Groff, Michael', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000159', 'Carty, Matthew', 'MD', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000160', 'Pavlova, Milena', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000161', 'Preneta, Ewa', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000162', 'McCarthy, Rita', 'NP', '', '3:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000163', 'Tarpy, Robert', 'MD', '', '12:00 - 8:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000164', 'Matthews, Robert', 'PA-C', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000165', 'Trumble, Julia', 'LICSW', '', '9:00 - 5:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000166', 'Webber, Anthony', 'MD', '', '3:30 - 10:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000167', 'Bhasin, Shalender', 'MD', '', '7:00 - 3:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000168', 'Waldman, Abigail', 'MD', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000169', 'Whitman, Gregory', 'MD', '', '7:30 - 4:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000170', 'Smith, Benjamin', 'MD', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000171', 'Yung, Rachel', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000172', 'Bhattacharyya, Shamik', 'MD', '', '6:00 - 2:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000173', 'Yong, Jason', 'MD', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000174', 'Spector, David', 'MD', '', '3:30 - 10:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000175', 'Doherty, Meghan', 'LCSW', '', '7:30 - 4:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000176', 'Schoenfeld, Andrew', 'MD', '', '12:00 - 8:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000177', 'Corrales, Carleton Eduardo', 'MD', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000178', 'Vernon, Ashley', 'MD', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000179', 'Parnes, Aric', 'MD', '', '12:00 - 8:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000180', 'Lai, Leonard', 'MD', '', '1:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000181', 'Taylor, Cristin', 'PA-C', '', '3:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000182', 'Grossi, Lisa', 'RN, MS, CPNP', '', '7:00 - 3:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000183', 'Healey, Michael', 'MD', '', '3:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000184', 'Angell, Trevor', 'MD', '', '7:00 - 3:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000185', 'Tucker, Kevin', 'MD', '', '3:30 - 10:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000186', 'Prince, Anthony', 'MD', '', '7:00 - 3:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000187', 'Brick, Gregory', 'MD', '', '3:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000188', 'Barr, Joseph Jr.', 'MD', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000189', 'Vigneau, Shari', 'PA-C', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000190', 'Whitlock, Kaitlyn', 'PA-C', '', '5:30 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000191', 'Tenforde, Adam', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000192', 'Frangieh, George', 'MD', '', '9:00 - 5:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000193', 'McDonald, Michael', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000194', 'Smith, Colleen', 'NP', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000195', 'Cotter, Lindsay', 'LICSW', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000196', 'Tunick, Mitchell', 'MD', '', '12:00 - 8:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000197', 'Lilienfeld, Armin', 'MD', '', '9:00 - 5:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000198', 'Dominici, Laura', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000199', 'Nakhlis, Faina', 'MD', '', '5:30 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000200', 'Kathrins, Martin', 'MD', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000201', 'Andromalos, Laura', 'RD, LDN', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000202', 'Hajj, Micheline', 'RN', '', '1:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000203', 'Zampini, Jay', 'MD', '', '7:00 - 3:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000204', 'Tavakkoli, Ali', 'MD', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000205', 'Schueler, Leila', 'MD', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000206', 'Kenney, Pardon', 'MD', '', '9:00 - 5:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000207', 'Reil, Erin', 'RD, LDN', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000208', 'Loder, Elizabeth', 'MD', '', '1:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000209', 'Weisholtz, Daniel', 'MD', '', '12:00 - 8:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000210', 'Chahal, Katie', 'PA-C', '', '11:30 - 7:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000211', 'Dyer, George', 'MD', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000212', 'Wellman, David', 'MD', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000213', 'Matwin, Sonia', 'PhD', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000214', 'Barbie, Thanh', 'MD', '', '1:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000215', 'Vardeh, Daniel', 'MD', '', '1:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000216', 'Mutinga, Muthoka', 'MD', '', '3:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000217', 'Pariser, Kenneth', 'MD', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000218', 'Budhiraja, Rohit', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000219', 'Mariano, Timothy', 'MD', '', '7:30 - 4:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000220', 'OLeary, Michael', 'MD', '', '7:30 - 4:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000221', 'Hoover, Paul', 'MD, PhD', '', '3:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000222', 'Lo, Amy', 'MD', '', '12:00 - 8:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000223', 'Mason, William', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000224', 'Sampson, Christian', 'MD', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000225', 'Morrison-Ma, Samantha', 'NP', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000226', 'Oliveira, Nancy', 'MS, RDN, LDN', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000227', 'Bonaca, Marc', 'MD', '', '2:00 - 12:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000228', 'Smith, Jeremy', 'MD', '', '3:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000229', 'Bono, Christopher', 'MD', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000230', 'Johnsen, Jami', 'MD', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000231', 'DAmbrosio, Carolyn', 'MD', '', '3:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000232', 'Rodriguez, Claudia', 'MD', '', '3:30 - 10:30')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000233', 'Sheu, Eric', 'MD', '', '5:00 - 1:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000234', 'Stephens, Kelly', 'MD', '', '8:00 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000235', 'McGowan, Katherine', 'MD', '', '6:00 - 2:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000236', 'Schoenfeld, Paul', 'MD', '', '1:00 - 11:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000237', 'Stevens, Erin', 'LICSW', '', '5:30 - 4:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000238', 'Stewart, Carl', 'MEd, LADC I', '', '9:00 - 5:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000239', 'Ariagno, Megan', 'RD, LDN', '', '6:00 - 2:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000240', 'Chiodo, Christopher', 'MD', '', '12:00 - 9:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000241', 'Ruiz, Emily', 'MD', '', '10:00 - 7:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000242', 'Burch, Rebecca', 'MD', '', '7:00 - 3:00')",
            "INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES ('00000000-0000-0000-0000-000000000243', 'Hsu, Joyce', 'MD', '', '1:00 - 11:00')",

            "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('bd832f5b-24a3-4a3f-8fea-9030cc1c9c23', 1260, 671, '00000000-0000-00f1-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('c302fd90-c31f-4746-9189-f1c1f71ee68e', 1159, 928, '00000000-0000-00f1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('13a259b6-0247-4b09-a973-99eea593c7ec', 1157, 777, '00000000-0000-00f1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('c6047752-307c-42fd-b3f8-4da1f63e70de', 1157, 652, '00000000-0000-00f1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('33d93cd0-7cf6-4422-ab1a-9b5a3a672cee', 1157, 632, '00000000-0000-00f1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('810f5b0e-e8fa-42a4-b347-919dccae59b5', 1189, 636, '00000000-0000-00f1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('7b84a96e-033d-437f-abfa-ee59f8bd20fc', 1224, 636, '00000000-0000-00f1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('a51d05c4-c4ee-4752-8c52-767378db0627', 1259, 636, '00000000-0000-00f1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('ca122c15-dab1-45ef-9928-2804631eefef', 1225, 751, '00000000-0000-00f1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('dfdcaf64-0196-46a2-b6dd-d0603cadc6e1', 1224, 777, '00000000-0000-00f1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('f6d22425-67e2-499c-b035-6bbc1dc8a616', 1034, 777, '00000000-0000-00f1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('c8263cc9-f87d-4e6c-a5c6-5f452afb3c06', 1034, 800, '00000000-0000-00f1-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('55040dfc-9aa8-479c-bba8-79ffb4e3f06a', 1256, 751, '00000000-0000-00f1-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('1273cd00-c6b9-445b-8568-5241c68d44ae', 1189, 615, '00000000-0000-00f1-0000-000000000000', 6)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('d4f0cba7-dbaf-40ae-bbdb-4f162f5acb78', 1109, 632, '00000000-0000-00f1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('3e55fd13-731b-4614-bd4c-0e84609b06b1', 1089, 631, '00000000-0000-00f1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('39b9c16b-6ff9-4a63-afdb-d6233c6bd909', 1041, 630, '00000000-0000-00f1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('8c231988-7ce6-4ac7-b6ef-36fb0fa70e51', 1014, 630, '00000000-0000-00f1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('0c05e0c5-146b-48b3-bfde-a9ac75b03543', 980, 630, '00000000-0000-00f1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('8dfb9f05-17a2-44b1-ac31-3b717d74e06a', 982, 651, '00000000-0000-00f1-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('3118fc50-68cd-4574-9b59-c0170f7ccb35', 979, 564, '00000000-0000-00f1-0000-000000000000', 4)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('3c37e027-d950-495f-9d22-37972cb6151b', 1109, 611, '00000000-0000-00f1-0000-000000000000', 6)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('d032941f-e61b-4eec-ae3e-8869c68a6a98', 1089, 671, '00000000-0000-00f1-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('be55fe26-290c-46c0-8d3b-12c63e10953f', 1113, 929, '00000000-0000-00f4-0000-000000000000', 2)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('34334e11-57fd-4189-86b5-b77c12acc551', 1113, 929, '00000000-0000-00f1-0000-000000000000', 2)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('3360b81a-dfcb-45c6-9ade-f3225d7de597', 1113, 929, '00000000-0000-00f5-0000-000000000000', 2)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('fed7b60e-9cda-4336-928e-7d153405c701', 1113, 929, '00000000-0000-00f3-0000-000000000000', 2)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('404d384a-250e-4472-8d16-2bd6eb2d588b', 1113, 929, '00000000-0000-00f6-0000-000000000000', 2)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('bdd57762-0efc-4a03-8648-a627106aef94', 1113, 929, '00000000-0000-00f7-0000-000000000000', 2)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('dbee0aa8-7db9-405f-8a6f-02a5e68ed9f4', 1113, 929, '00000000-0000-00f2-0000-000000000000', 2)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('bc826d5a-a726-4db9-af70-696d66b4493d', 1157, 584, '00000000-0000-00f1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('22427ea7-b532-4555-81ad-2beb195127f1', 1128, 582, '00000000-0000-00f4-0000-000000000000', 2)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('3826583d-c4ea-4857-8059-84cb8b408587', 1128, 582, '00000000-0000-00f1-0000-000000000000', 2)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('b13696c3-23a0-4d0e-a317-fc63144c65c8', 1128, 582, '00000000-0000-00f5-0000-000000000000', 2)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('629f2c4f-43ea-4f95-94c0-eb64561714a2', 1128, 582, '00000000-0000-00f3-0000-000000000000', 2)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('53f1da29-fe7a-4492-b417-05784cbefadb', 1128, 582, '00000000-0000-00f2-0000-000000000000', 2)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('bd2ce26a-cb32-4df7-b67d-567e86224688', 1157, 530, '00000000-0000-00f1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('427e9816-54c3-42a6-ae37-0d9784c3574c', 1157, 510, '00000000-0000-00f1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('bcdbec45-fcf6-4bea-aa06-be1acda093a9', 1061, 511, '00000000-0000-00f1-0000-000000000000', 3)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('a390fda8-5547-468b-ac31-c5372a2806fb', 1131, 530, '00000000-0000-00f1-0000-000000000000', 7)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('c82d4085-03f3-4a1c-bff3-4b945e6c4afd', 1013, 587, '00000000-0000-00f1-0000-000000000000', 4)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('04f545de-4a7b-4840-9ac4-a1ddad5fa4f4', 1042, 607, '00000000-0000-00f1-0000-000000000000', 4)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('6c1a1f69-1c2e-4ae0-bb2c-8ab30ee5bdd0', 1158, 582, '00000000-0000-00f2-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('2cdb65d0-44a0-4ed2-a81b-346177e003e3', 1159, 614, '00000000-0000-00f2-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('15def0f7-9b58-4efd-84b9-a953491ded13', 1203, 615, '00000000-0000-00f2-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('9d3bb40f-a420-4a6f-b8ec-0ac1c91fd954', 1203, 627, '00000000-0000-00f2-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('f77033ce-f999-460e-a983-9cbe34594f0c', 1241, 628, '00000000-0000-00f2-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('845d1e97-f26a-48e7-8e25-95405aed8923', 1242, 802, '00000000-0000-00f2-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('75050099-a903-4694-80de-db34c00d7d0b', 1134, 931, '00000000-0000-00f2-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('cebe1733-413b-4d9e-a3a2-077c55ad4d18', 1277, 801, '00000000-0000-00f2-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('29480738-2a1e-46f2-aaca-8d02350ad4d7', 1135, 1005, '00000000-0000-00f2-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('b4f8e042-3b66-4d2a-ac1a-7264f8f551e2', 1157, 542, '00000000-0000-00f2-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('83ee70d9-b6a2-4a18-98b4-46d8dddc5d8d', 1157, 524, '00000000-0000-00f2-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('b3b54ddb-3af9-4de2-8360-8b36311f3c1c', 1224, 534, '00000000-0000-00f2-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('3b228c55-37f1-49d9-939f-60311d69ca86', 1102, 534, '00000000-0000-00f2-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('352963da-672d-4930-af6a-d2235d84abb5', 1157, 930, '00000000-0000-00f3-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('d0a72861-dc43-43f6-ae4f-11311b2b7e1a', 1157, 838, '00000000-0000-00f3-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('39c019ab-8d1c-4c2d-a25a-f716e2b5b374', 1157, 705, '00000000-0000-00f3-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('229990fa-0cf5-4cd1-a835-003fd5affbdf', 1139, 705, '00000000-0000-00f3-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('cb6b6a21-d807-4a62-b321-defc13a2b4f6', 1112, 713, '00000000-0000-00f3-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('a1c359d1-1b77-45cf-bdec-bb0082dbebb2', 1112, 745, '00000000-0000-00f3-0000-000000000000', 4)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('c1faebf7-710b-4e22-b7c2-a09f9710cc7b', 1103, 694, '00000000-0000-00f3-0000-000000000000', 6)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('5190800a-39af-45f3-899f-6f83e4b60fe4', 1128, 839, '00000000-0000-00f3-0000-000000000000', 1)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('24256258-79ca-4a48-9073-7c23e6841d5c', 1208, 705, '00000000-0000-00f3-0000-000000000000', 3)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('ae883595-f017-463a-ba51-8cba2e476cd6', 1139, 618, '00000000-0000-00f3-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('453d67de-fea4-4336-9035-fd914d1cc4d8', 1158, 618, '00000000-0000-00f3-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('23c9505e-a63d-40d4-9323-7217f25b92e8', 1158, 581, '00000000-0000-00f3-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('0defc1d0-46c1-4516-b5f0-b0b14fb0a7a1', 1158, 525, '00000000-0000-00f3-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('0b192602-8982-4d62-97ad-d7bcd08315c8', 1187, 526, '00000000-0000-00f3-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('92c5bf49-cd4f-46b9-a6e5-7250919a7f77', 1205, 536, '00000000-0000-00f3-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('98594832-3d4a-4275-b04a-d8ab760fa674', 1158, 495, '00000000-0000-00f3-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('6c030df1-f1cf-4f02-aaa4-b4e103b048ac', 1198, 497, '00000000-0000-00f3-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('52af8c0a-1eef-42a4-a993-ed4fd00edeac', 1228, 513, '00000000-0000-00f3-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('7113820b-6f16-4b9f-8ac5-882953798550', 1157, 989, '00000000-0000-00f3-0000-000000000000', 7)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('47036dbb-0523-4dc0-bd4d-fdbd3970e88f', 1157, 1010, '00000000-0000-00f3-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('e5b8c12b-6972-481d-bddb-40f4018bb751', 1077, 1010, '00000000-0000-00f3-0000-000000000000', 4)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('1497bb21-0149-479f-9eab-c5c719f3fb3b', 1214, 1011, '00000000-0000-00f3-0000-000000000000', 4)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('e7943528-28f7-4f70-9220-ba5a0b025bb1', 1157, 1052, '00000000-0000-00f3-0000-000000000000', 4)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('951ab5bd-21a7-4c3f-8d63-be9711e30362', 1139, 492, '00000000-0000-00f4-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('90e0ba05-ea29-4bb1-bf60-0ebc96da09b4', 1183, 489, '00000000-0000-00f4-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('8fefce36-6312-4631-b2e9-ef128d9a3dfb', 1247, 550, '00000000-0000-00f4-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('0ba3b0e5-012c-40e6-9226-11bcfeba19ca', 1248, 595, '00000000-0000-00f4-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('f2aaf3d2-9f34-4656-9e90-58c2dda486f0', 1190, 639, '00000000-0000-00f4-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('3bc47ab4-2cef-46b7-897a-f736f17cf45d', 1120, 676, '00000000-0000-00f4-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('47712429-5844-4f63-91a9-0fcf5a32c995', 1197, 692, '00000000-0000-00f4-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('decdfe7a-f3f3-46de-95e0-4ab3701836e4', 1196, 750, '00000000-0000-00f4-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('f8fbd993-2c1d-4952-8c3e-b4eca45ff0fc', 1122, 854, '00000000-0000-00f4-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('24f44874-d250-4a93-9ecb-ce97474c29ac', 1139, 540, '00000000-0000-00f4-0000-000000000000', 6)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('c5d1dc79-9b77-4c40-9483-ec01a56402e5', 1176, 540, '00000000-0000-00f4-0000-000000000000', 6)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('eab54c72-c4d6-4413-9259-c9576f471963', 1157, 929, '00000000-0000-00f4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('df63f532-6748-4eba-8497-1833831551a3', 1158, 856, '00000000-0000-00f4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('c471ec1b-c322-4c2b-98ee-065ce5bc77d5', 1158, 749, '00000000-0000-00f4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('43adc880-a679-42f2-b9b7-70b1f5e8a8a8', 1158, 691, '00000000-0000-00f4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('51fc8c28-ed02-4dd5-a3fe-707ff3fd7e21', 1158, 675, '00000000-0000-00f4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('e45cd934-bf1c-41a0-87c0-d41b338b4da3', 1158, 641, '00000000-0000-00f4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('4929fda4-bde6-4190-9212-118f3b9d67b7', 1157, 584, '00000000-0000-00f4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('99677174-0695-4ac0-accb-5747a45cdcd6', 1157, 541, '00000000-0000-00f4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('8a17fedf-2c07-4e85-a151-424675abdaf9', 1156, 526, '00000000-0000-00f4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('f1803e37-5699-4d6e-ae52-a9523ec19650', 1144, 525, '00000000-0000-00f4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('22d5b680-61b3-423f-a8e7-3ceb2f39b590', 1178, 525, '00000000-0000-00f4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('24fd2506-27a5-46f9-920f-13ea568e74bc', 1205, 535, '00000000-0000-00f4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('02137cc9-8c47-4e6d-a73d-51bb721c3206', 1207, 551, '00000000-0000-00f4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('9c1b96c3-b233-4306-be10-2d39641c8059', 1207, 595, '00000000-0000-00f4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('f75b7640-fe0a-4d98-a809-a636d0d4658b', 1207, 614, '00000000-0000-00f4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('985d4652-1317-4db8-9a05-442567b5f115', 1157, 615, '00000000-0000-00f4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('579d9f10-64ea-4478-ace3-27f417e3f028', 1157, 955, '00000000-0000-00f4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('bee8617e-f69f-445b-92e5-157e5ad03f07', 1296, 955, '00000000-0000-00f4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('69b0b0b2-9075-42c2-a3d6-9e84259b6bcb', 1296, 988, '00000000-0000-00f4-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('706a7020-8637-4c63-a78e-9343e2ffde60', 1076, 955, '00000000-0000-00f4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('a223094f-05aa-48b3-bbaa-8c84222180b3', 1002, 955, '00000000-0000-00f4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('1c0c92c9-ae3a-4258-be77-ff2ad3e726c6', 862, 955, '00000000-0000-00f4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('4c733de2-b3a5-46df-9d45-2700f1b7256a', 786, 956, '00000000-0000-00f4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('4313c69c-364c-4ac4-81c4-809f9ab46c57', 786, 991, '00000000-0000-00f4-0000-000000000000', 4)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('ef8b00a2-fc32-4332-bad9-54ee72b55706', 1076, 989, '00000000-0000-00f4-0000-000000000000', 4)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('1fc8d85f-3156-4ba0-b3e7-61c10fda9422', 862, 932, '00000000-0000-00f4-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('d231b01c-0b11-4496-9cbb-0f3e0099881c', 1001, 979, '00000000-0000-00f4-0000-000000000000', 6)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('5ea060a8-f48a-412a-aeeb-4950f6a43904', 1157, 616, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('3e615a71-8d6f-4850-9207-d109ea43b154', 1109, 615, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('cfad229b-55b6-4ad3-8d5f-7c2bb4ae065f', 1108, 597, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('48ef7b43-e0f9-4b15-b7ca-35a2f4bf366e', 1109, 535, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('28623275-ac3d-46b2-99d0-5bc3414b2e60', 1128, 527, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('119579e3-1642-41c2-bbb4-4fb9fe12fc34', 1154, 524, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('f99c0e0f-0f41-4406-bebf-0724d76247bc', 1178, 525, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('de27a41d-cd6b-4f96-b8c0-b85056e8831f', 1195, 531, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('31c2637b-30d1-462c-aa1a-fd8ff01632bd', 1206, 536, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('a92b1ab6-efbe-42a4-b17d-89b45056af0e', 1207, 597, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('656cde3d-3fa5-4eb3-a916-508899d1b55f', 1206, 614, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('d975f956-5725-45a4-bb10-43a26b9dfba2', 1157, 583, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('56cf250a-6203-4246-a35b-67da5ea1d540', 1157, 540, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('df8a2785-f62a-4dc5-bf9e-49ed48885809', 1139, 540, '00000000-0000-00f5-0000-000000000000', 6)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('4001be8c-9955-4c02-81ad-9e25226931bc', 1175, 540, '00000000-0000-00f5-0000-000000000000', 6)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('6c4d2e61-02b0-48f5-b0da-bbfae185457b', 1238, 598, '00000000-0000-00f5-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('ac715d21-e9bf-4159-ae9e-9f6eab7ccab1', 1216, 502, '00000000-0000-00f5-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('23e780b6-705c-41d0-8f89-546bb8bc59b7', 1154, 495, '00000000-0000-00f5-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('7b1fd3c4-1218-4dc0-84c1-2e1ec5fc0af5', 1102, 506, '00000000-0000-00f5-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('e28f1542-3d18-4b6d-b515-1e7693cbc240', 1069, 597, '00000000-0000-00f5-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('3f9194d8-0133-499b-b013-3de354449728', 1158, 638, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('477b9935-cfc9-4a29-a337-56101b76cccd', 1158, 674, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('bb675ef0-c4bb-448c-94ed-b94b87368655', 1158, 749, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('c6e41ca3-b908-42f3-bb43-d7d1743bc275', 1197, 638, '00000000-0000-00f5-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('3052db50-7c59-492a-adc1-e81dfc2c8cd3', 1120, 639, '00000000-0000-00f5-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('7b82c0be-c2e5-432b-a4c0-913dd5f42333', 1196, 675, '00000000-0000-00f5-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('24291b73-0ce4-4c42-bbf7-13bd46db26e4', 1119, 675, '00000000-0000-00f5-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('4feb23d8-9bd7-471c-8b13-a36dbdba67fc', 1121, 749, '00000000-0000-00f5-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('cc7b4cf9-8700-4d77-8f33-ec1b3e92b27e', 1194, 749, '00000000-0000-00f5-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('6a72bc47-013d-4f20-b238-2e22ceee8004', 1157, 880, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('c70e2081-caaa-46c8-98ff-e92449af5ad9', 1182, 880, '00000000-0000-00f5-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('b2cba5af-3ab8-4b4e-9fda-3d3a3c107bbe', 1157, 903, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('ebfce924-6b63-422f-9590-92b2271da0fd', 1179, 903, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('1fd2df12-7511-47fb-b39a-00e6187c3e8d', 1179, 953, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('c89461d5-97d2-4d38-a0a0-91bee1c6962c', 1158, 953, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('8b2fd605-261d-45f0-b806-8defec11d0a9', 1137, 953, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('e6a2fbef-b734-43e2-bef7-00fff15e211c', 1137, 928, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('4fce9e3e-9004-4a71-9e6d-dcc850d341c7', 1137, 905, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('9efdfa67-c027-4046-8ceb-006c1db2c8ea', 1157, 927, '00000000-0000-00f5-0000-000000000000', 6)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('7f21b4f7-6460-4a0e-b1b8-d642fd6e073f', 994, 953, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('518f7cf0-182e-4825-a119-abddeb7de263', 894, 952, '00000000-0000-00f5-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('78ee4fef-3327-45eb-b5f2-7e89280aaccc', 823, 953, '00000000-0000-00f5-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('c0664191-87f7-44dd-b987-f907dcd6f1a2', 994, 990, '00000000-0000-00f5-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('83b1c136-5f03-4381-a2ad-36de2208382e', 1158, 989, '00000000-0000-00f5-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('7e5bc6fb-46ab-4ae6-8632-52e89464c9a9', 1220, 953, '00000000-0000-00f5-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('3bbcf170-1d5a-46fa-a0d7-5867338ff137', 1134, 929, '00000000-0000-00f6-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('2aa0708b-3402-48b7-b9c0-314189dec5b0', 1134, 952, '00000000-0000-00f6-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('5e6a69f7-e58a-44da-855f-07a6daa0faff', 1177, 952, '00000000-0000-00f6-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('9bf07049-fc56-4cb1-af1e-4b82041eb256', 1240, 952, '00000000-0000-00f6-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('d92fdf58-fc67-42ee-a0b1-66291c329d1c', 1066, 953, '00000000-0000-00f6-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('f9d5d585-0f7e-4f78-953b-1d81abd09f56', 1155, 929, '00000000-0000-00f6-0000-000000000000', 6)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('f14b953c-6a4b-40eb-995c-3b18deeb1744', 1137, 924, '00000000-0000-00f7-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('1d03ae2c-8151-4df5-b2ef-0e24f990b358', 1161, 924, '00000000-0000-00f7-0000-000000000000', 6)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('34f1023f-9e9e-4e8d-a856-f2d943b5d594', 1137, 948, '00000000-0000-00f7-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('0765e05b-c4a8-43df-af56-67c0c160ff26', 1075, 948, '00000000-0000-00f7-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('6284c55b-740b-4756-aec5-f13f1d78d56d', 1181, 948, '00000000-0000-00f7-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('60dc2e02-5689-4318-8526-9dc01a3a90eb', 1234, 948, '00000000-0000-00f7-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('2946e6ae-9bc7-498a-9af4-8d45fd31772d', 731, 342, '00000000-0000-00b1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('68425f6c-b67b-4d01-8340-ea737640733b', 710, 343, '00000000-0000-00b1-0000-000000000000', 7)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('a5d9a8d7-95dc-4e1a-9ebd-cd1f132c8fa9', 710, 318, '00000000-0000-00b1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('a647bc10-7eab-4a20-9fe9-2a667a32527a', 710, 289, '00000000-0000-00b1-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('87aee9a8-cec4-47ae-9eb1-d948a73618f3', 731, 307, '00000000-0000-00b1-0000-000000000000', 2)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('177fec3e-fb64-4305-b40f-d333c4828d5b', 731, 307, '00000000-0000-00b2-0000-000000000000', 2)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('a38f3f87-aea1-466b-82fd-6bef44ed2cb6', 731, 307, '00000000-0000-00b3-0000-000000000000', 2)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('106e85b1-5cd3-4a8d-af46-d2d8c74e95bd', 731, 307, '00000000-0000-00b4-0000-000000000000', 2)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('add0fb85-fe87-4bcb-be32-a5666032feb7', 731, 328, '00000000-0000-00b2-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('db133aa0-b1bd-488b-ab25-e6e573f9aeb1', 648, 327, '00000000-0000-00b2-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('f4862e43-7542-44be-b149-5615eb64b136', 731, 327, '00000000-0000-00b3-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('ccf66544-fbd0-4726-b064-98ce7934cb28', 642, 326, '00000000-0000-00b3-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('f79b8f5c-94f7-4c88-8982-a257c2dec3f3', 731, 327, '00000000-0000-00b4-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('57810305-38f9-481c-ad38-eda1fce5493c', 640, 327, '00000000-0000-00b4-0000-000000000000', 5)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('aa041fb5-79bf-428c-b030-cfdfffea932e', 1157, 456, '00000000-0000-00f1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('2458f7c5-01c9-4c59-93fe-6f5892952290', 1204, 922, '00000000-0000-00f4-0000-000000000000', 2)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('10bb8b65-0648-48ef-a0e3-9542d5ebac34', 1204, 922, '00000000-0000-00f3-0000-000000000000', 2)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('01093d5c-32e2-4b8d-a520-9e32521722d0', 770, 347, '00000000-0000-00c1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('3e73d5d2-16ed-4837-85ac-e321b8763802', 771, 467, '00000000-0000-00c1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('2d052c23-7658-4955-9308-80a00c148f5a', 813, 452, '00000000-0000-00c1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('ca68ef95-d5b3-48ee-95bb-9d5608fa54fb', 857, 446, '00000000-0000-00c1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('63ef5854-c8b7-46f0-8445-4b01c417e423', 894, 447, '00000000-0000-00c1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('5504e383-9cd8-40cc-9149-056760219333', 920, 465, '00000000-0000-00c1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('086e0557-b05c-45e6-9e82-33025d7bba82', 985, 466, '00000000-0000-00c1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('4a699be3-b591-439e-a61d-22eed15e0554', 1010, 470, '00000000-0000-00c1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('5cd5abf4-d9e5-474d-b4bb-b84cd66b5a69', 1031, 476, '00000000-0000-00c1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('eb114265-fc34-40b1-bbc6-bb1551a5179e', 1061, 462, '00000000-0000-00c1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('d9c392a5-b573-4de4-b4ec-555f757e659d', 1108, 444, '00000000-0000-00c1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('a0df4f42-982a-4b54-b880-b47d20889830', 1158, 439, '00000000-0000-00c1-0000-000000000000', 0)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('b5314be1-d4cb-41fa-a6a0-f2f5fe0b7f5b', 732, 485, '00000000-0000-00c1-0000-000000000000', 4)",
                "INSERT INTO USER1.NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES ('280c6df2-174d-425e-b1c1-cd6f817c31ef', 732, 449, '00000000-0000-00c1-0000-000000000000', 7)",

                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('76002431-4934-45b4-81be-7782d371e744', 'Radiology', 'bd832f5b-24a3-4a3f-8fea-9030cc1c9c23', '00000000-0000-00f1-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('8c1b5262-7ddb-4cee-bb97-7e9c79e994a1', 'GI/Endoscopy', 'c8263cc9-f87d-4e6c-a5c6-5f452afb3c06', '00000000-0000-00f1-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('31c3b2b4-c7b9-40c8-b58d-08a3e8e7b70d', 'Day Surgery', '55040dfc-9aa8-479c-bba8-79ffb4e3f06a', '00000000-0000-00f1-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('2e4f43c6-5cb1-4df4-b799-b65113893287', 'Restroom', '1273cd00-c6b9-445b-8568-5241c68d44ae', '00000000-0000-00f1-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('f23fa838-1497-419e-a3cb-aac7ab7cbbdd', 'Emergency', '8dfb9f05-17a2-44b1-ac31-3b717d74e06a', '00000000-0000-00f1-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('73776122-52d7-4991-bc40-251560cd3160', 'Emergency Entrance', '3118fc50-68cd-4574-9b59-c0170f7ccb35', '00000000-0000-00f1-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('6aa1484a-8eda-4999-9c09-43c9d5b0836f', 'Restroom', '3c37e027-d950-495f-9d22-37972cb6151b', '00000000-0000-00f1-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('d2fdec94-872d-469a-b81e-41dfb4555b4e', 'Blood Draw', 'd032941f-e61b-4eec-ae3e-8869c68a6a98', '00000000-0000-00f1-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('8ab1839e-59da-42bb-b7b2-254e28ba78cd', 'Starbucks', 'bcdbec45-fcf6-4bea-aa06-be1acda093a9', '00000000-0000-00f1-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('f0b38317-fddb-45f4-9c8c-35b648f37bb7', 'Taiclet Family Center', 'c82d4085-03f3-4a1c-bff3-4b945e6c4afd', '00000000-0000-00f1-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('27eb9b58-e253-4bcf-a12e-7ac42742918e', 'Patient Registration', '04f545de-4a7b-4840-9ac4-a1ddad5fa4f4', '00000000-0000-00f1-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('67d1c3c2-865f-45a4-98d9-0dbc89a3d714', 'Physical Therapy', 'cebe1733-413b-4d9e-a3a2-077c55ad4d18', '00000000-0000-00f2-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('68c937f0-73d1-4bb3-bf55-efc2090bbc88', 'Outpatient Psychiatry', '29480738-2a1e-46f2-aaca-8d02350ad4d7', '00000000-0000-00f2-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('febf2dc3-3119-425e-a08d-49bf912e3163', '2B', 'b3b54ddb-3af9-4de2-8360-8b36311f3c1c', '00000000-0000-00f2-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('b1837372-4a9c-4bb6-be60-f7bcc2391e0a', '2A', '3b228c55-37f1-49d9-939f-60311d69ca86', '00000000-0000-00f2-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('22fafe4c-770a-4764-a1fb-a70d2a720894', 'Huvos Auditorium', 'a1c359d1-1b77-45cf-bdec-bb0082dbebb2', '00000000-0000-00f3-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('1f1f0a47-48bd-4278-9563-c39db6b0ca64', 'Restroom', 'c1faebf7-710b-4e22-b7c2-a09f9710cc7b', '00000000-0000-00f3-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('d8e4a6a8-52da-489e-8ce0-0888d25c2d83', 'Gift Shop', '5190800a-39af-45f3-899f-6f83e4b60fe4', '00000000-0000-00f3-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('8a249384-1215-4b27-a7a6-838ed74ad56d', 'Cafeteria', '24256258-79ca-4a48-9073-7c23e6841d5c', '00000000-0000-00f3-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('eb4349fe-07f0-4897-be9b-593f134d3a36', '3A', '98594832-3d4a-4275-b04a-d8ab760fa674', '00000000-0000-00f3-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('ef53d8d9-12e2-4b0d-8638-a690fbb22232', '3B', '6c030df1-f1cf-4f02-aaa4-b4e103b048ac', '00000000-0000-00f3-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('d3ebf2d0-9994-4f5c-9349-83ab65602908', '3C', '52af8c0a-1eef-42a4-a993-ed4fd00edeac', '00000000-0000-00f3-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('d45af85e-5274-4a03-a377-3d9040fc91ee', 'Chapel', 'e5b8c12b-6972-481d-bddb-40f4018bb751', '00000000-0000-00f3-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('09c6aa4f-ac67-4ded-b010-1c965e8bceab', 'Volunteer Services', '1497bb21-0149-479f-9eab-c5c719f3fb3b', '00000000-0000-00f3-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('e673fbef-8ab8-44e3-b8b7-d4d5f5c64dbe', 'Shuttle Pickup', 'e7943528-28f7-4f70-9220-ba5a0b025bb1', '00000000-0000-00f3-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('fca9bfb1-b946-4157-8da9-6855904ce62f', '4A', '951ab5bd-21a7-4c3f-8d63-be9711e30362', '00000000-0000-00f4-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('ce833577-2e37-4bd4-98a1-85802d686a63', '4B', '90e0ba05-ea29-4bb1-bf60-0ebc96da09b4', '00000000-0000-00f4-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('a6ca2329-c70f-4f86-87a1-189b65d6e47c', '4C', '8fefce36-6312-4631-b2e9-ef128d9a3dfb', '00000000-0000-00f4-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('bac32de1-9960-43c1-832a-5af2bd7ca895', '4D', '0ba3b0e5-012c-40e6-9226-11bcfeba19ca', '00000000-0000-00f4-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('e048ce91-f551-4c7c-8528-5a3d187ca5fe', '4F', 'f2aaf3d2-9f34-4656-9e90-58c2dda486f0', '00000000-0000-00f4-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('774cb8aa-4b15-4a60-a249-31d403ee5209', '4G', '3bc47ab4-2cef-46b7-897a-f736f17cf45d', '00000000-0000-00f4-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('996db91a-3c90-42bb-8c8a-2a328a45fb68', '4H', '47712429-5844-4f63-91a9-0fcf5a32c995', '00000000-0000-00f4-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('2f057713-422a-469f-8573-cfc23417982d', '4I', 'decdfe7a-f3f3-46de-95e0-4ab3701836e4', '00000000-0000-00f4-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('b6d1117d-ebef-40fb-ba78-90ac21729603', '4J', 'f8fbd993-2c1d-4952-8c3e-b4eca45ff0fc', '00000000-0000-00f4-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('e44b797a-2472-4473-b6b9-4edd435d4c61', 'Restroom', '24f44874-d250-4a93-9ecb-ce97474c29ac', '00000000-0000-00f4-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('bede25e5-828d-4dcf-83fb-7b5f3b9514db', 'Restroom', 'c5d1dc79-9b77-4c40-9483-ec01a56402e5', '00000000-0000-00f4-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('9eafd74c-cd1c-47ba-b245-3334e36113b3', '4S', '69b0b0b2-9075-42c2-a3d6-9e84259b6bcb', '00000000-0000-00f4-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('9eaf1719-a467-4429-8ffd-da8e85ca8146', 'Tynan Conference Room', '4313c69c-364c-4ac4-81c4-809f9ab46c57', '00000000-0000-00f4-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('69adf686-ba4b-4bc7-ba57-4d11609c265a', 'Doherty Conference Room', 'ef8b00a2-fc32-4332-bad9-54ee72b55706', '00000000-0000-00f4-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('13c7cba0-df86-4ee8-967b-dea2a1ade6d4', '4N', '1fc8d85f-3156-4ba0-b3e7-61c10fda9422', '00000000-0000-00f4-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('4ed6c434-5d38-4210-b23e-798268dc7e42', 'Restroom', 'd231b01c-0b11-4496-9cbb-0f3e0099881c', '00000000-0000-00f4-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('781980e9-96e7-441c-a340-4e5258330eb7', 'Restroom', 'df8a2785-f62a-4dc5-bf9e-49ed48885809', '00000000-0000-00f5-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('c6b2f59a-4750-4b34-9a98-ab58e9192a3d', 'Restroom', '4001be8c-9955-4c02-81ad-9e25226931bc', '00000000-0000-00f5-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('25865c16-ed4f-46a3-89d9-cc7132fd2ddd', '5E', '6c4d2e61-02b0-48f5-b0da-bbfae185457b', '00000000-0000-00f5-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('737bd558-a893-4818-8a9b-be6b58c4deb6', '5D', 'ac715d21-e9bf-4159-ae9e-9f6eab7ccab1', '00000000-0000-00f5-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('026403b0-0a2e-4736-9c30-a16355c3604e', '5C', '23e780b6-705c-41d0-8f89-546bb8bc59b7', '00000000-0000-00f5-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('554498fe-5679-4769-bca0-208a65d8ae27', '5B', '7b1fd3c4-1218-4dc0-84c1-2e1ec5fc0af5', '00000000-0000-00f5-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('10af22b2-7c35-4c39-a612-c96d6afe8fe2', '5A', 'e28f1542-3d18-4b6d-b515-1e7693cbc240', '00000000-0000-00f5-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('316a8904-71fe-4ae9-bce8-b7c7dcaae877', '5F', 'c6e41ca3-b908-42f3-bb43-d7d1743bc275', '00000000-0000-00f5-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('f0e9f4a7-b49a-4944-b03f-7c8c32437317', '5G', '3052db50-7c59-492a-adc1-e81dfc2c8cd3', '00000000-0000-00f5-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('5c345e92-1a3a-46a2-a2df-472bf1621d29', '5H', '7b82c0be-c2e5-432b-a4c0-913dd5f42333', '00000000-0000-00f5-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('a179183b-558b-4590-979e-c94c1bec0aa4', '5I', '24291b73-0ce4-4c42-bbf7-13bd46db26e4', '00000000-0000-00f5-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('e85b1e87-c288-48d6-a366-35a73768110d', '5J', '4feb23d8-9bd7-471c-8b13-a36dbdba67fc', '00000000-0000-00f5-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('a7815f93-7bc0-4739-bca4-632c61161e5d', '5K', 'cc7b4cf9-8700-4d77-8f33-ec1b3e92b27e', '00000000-0000-00f5-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('49ca538b-6785-4eb4-b8d8-2a719324c0c7', '5L', 'c70e2081-caaa-46c8-98ff-e92449af5ad9', '00000000-0000-00f5-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('3b648859-e9ad-4ec4-9b5a-f5fa619fe9e6', 'Restroom', '9efdfa67-c027-4046-8ceb-006c1db2c8ea', '00000000-0000-00f5-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('4e02a3f9-c34b-4ca0-929a-77eb23c5e4be', 'Intensive Care Unit', '78ee4fef-3327-45eb-b5f2-7e89280aaccc', '00000000-0000-00f5-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('73479f93-280d-42a7-9751-87c80bc6069e', '5N', 'c0664191-87f7-44dd-b987-f907dcd6f1a2', '00000000-0000-00f5-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('971d7961-b953-4d2f-b32e-fd7960ebd1c9', '5M', '83b1c136-5f03-4381-a2ad-36de2208382e', '00000000-0000-00f5-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('dabc301c-55fc-43b1-ac74-3300e15b902d', '5 South', '7e5bc6fb-46ab-4ae6-8632-52e89464c9a9', '00000000-0000-00f5-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('6e653b83-c0fb-48b5-930a-3ab43a287e21', '6 South', '9bf07049-fc56-4cb1-af1e-4b82041eb256', '00000000-0000-00f6-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('c1cd91eb-16dd-4e90-a602-be95ee27a67e', '6 North', 'd92fdf58-fc67-42ee-a0b1-66291c329d1c', '00000000-0000-00f6-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('3f44790d-c99f-46e8-8673-8a30e4008fe8', 'Restroom', 'f9d5d585-0f7e-4f78-953b-1d81abd09f56', '00000000-0000-00f6-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('4c7f178c-0737-458b-bf9c-f1d3bb82016f', 'Restroom', '1d03ae2c-8151-4df5-b2ef-0e24f990b358', '00000000-0000-00f7-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('379232b5-a455-4aae-b578-7b5dbb80da4d', '7 North', '0765e05b-c4a8-43df-af56-67c0c160ff26', '00000000-0000-00f7-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('dcf5bed9-00f5-472d-a40d-e0bf19b36eff', '7 South', '60dc2e02-5689-4318-8526-9dc01a3a90eb', '00000000-0000-00f7-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('d7158766-c57b-4bfd-9df1-5df0d4fcbfc5', 'Dialysis Clinic', 'a647bc10-7eab-4a20-9fe9-2a667a32527a', '00000000-0000-00b1-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('d435716e-b32a-45ed-8f2a-7a653c81bb28', 'Sagoff Breast Imaging', 'db133aa0-b1bd-488b-ab25-e6e573f9aeb1', '00000000-0000-00b2-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('db1c2a29-5c7d-464e-9fbc-4502d9a8921e', 'Sagoff Breast Center Surgery', 'ccf66544-fbd0-4726-b064-98ce7934cb28', '00000000-0000-00b3-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('bf022ee2-8084-4f15-b02c-577167eb720e', 'Sagoff Breast Imaging Diagnostic Center', '57810305-38f9-481c-ad38-eda1fce5493c', '00000000-0000-00b4-0000-000000000000')",
                "INSERT INTO USER1.DESTINATION (DEST_ID, NAME, NODE_ID, FLOOR_ID) VALUES ('5b3af5df-3342-47be-a81f-402174936fd5', 'Belkin Lot', 'b5314be1-d4cb-41fa-a6a0-f2f5fe0b7f5b', '00000000-0000-00c1-0000-000000000000')",

                "INSERT INTO USER1.KIOSK (NAME, NODE_ID, DIRECTION, FLAG) VALUES ('1st Floor Faulkner', 'a390fda8-5547-468b-ac31-c5372a2806fb', 'N', 1)",
                "INSERT INTO USER1.KIOSK (NAME, NODE_ID, DIRECTION, FLAG) VALUES ('3rd Floor Faulkner', '7113820b-6f16-4b9f-8ac5-882953798550', 'N', 0)",
                "INSERT INTO USER1.KIOSK (NAME, NODE_ID, DIRECTION, FLAG) VALUES ('1st Floor Belkin', '68425f6c-b67b-4d01-8340-ea737640733b', 'N', 0)",
                "INSERT INTO USER1.KIOSK (NAME, NODE_ID, DIRECTION, FLAG) VALUES ('Parking Lot', '280c6df2-174d-425e-b1c1-cd6f817c31ef', 'N', 0)",

                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('790b9b2b-d30d-4bcd-9f86-73a5f4b31eee', 'c302fd90-c31f-4746-9189-f1c1f71ee68e', '13a259b6-0247-4b09-a973-99eea593c7ec', 149.00335566691106)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('34434cd8-5623-4701-929f-af918e8a8776', '13a259b6-0247-4b09-a973-99eea593c7ec', 'c6047752-307c-42fd-b3f8-4da1f63e70de', 127.00393694685216)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('98d35d09-4e58-4cca-a490-abae4458f332', 'c6047752-307c-42fd-b3f8-4da1f63e70de', '33d93cd0-7cf6-4422-ab1a-9b5a3a672cee', 16)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('1304207f-1d6f-4101-88d1-4d332baec5d1', '33d93cd0-7cf6-4422-ab1a-9b5a3a672cee', '810f5b0e-e8fa-42a4-b347-919dccae59b5', 32)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('7935edd1-9751-41eb-aa8f-76cab146c800', '810f5b0e-e8fa-42a4-b347-919dccae59b5', '7b84a96e-033d-437f-abfa-ee59f8bd20fc', 35)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('f890e2d2-0af2-4717-a981-50778d4eb803', '7b84a96e-033d-437f-abfa-ee59f8bd20fc', 'a51d05c4-c4ee-4752-8c52-767378db0627', 30.01666203960727)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('ce7ddf31-e198-431f-a129-82c152c1dd0f', 'a51d05c4-c4ee-4752-8c52-767378db0627', 'bd832f5b-24a3-4a3f-8fea-9030cc1c9c23', 34.52535300326414)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('ffe51200-ad1c-4a9c-a504-b6471d8b70bd', '7b84a96e-033d-437f-abfa-ee59f8bd20fc', 'ca122c15-dab1-45ef-9928-2804631eefef', 115.00434774390054)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('bceba88d-2156-40f4-9783-459291ea4c67', 'ca122c15-dab1-45ef-9928-2804631eefef', 'dfdcaf64-0196-46a2-b6dd-d0603cadc6e1', 26.019223662515376)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('f276cf0e-1b1f-4fb1-a9e8-504337ca88c2', 'dfdcaf64-0196-46a2-b6dd-d0603cadc6e1', '13a259b6-0247-4b09-a973-99eea593c7ec', 66.03029607687671)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('bc20fdcb-07d5-4bf0-8bf8-f6c44d9c9953', '13a259b6-0247-4b09-a973-99eea593c7ec', 'f6d22425-67e2-499c-b035-6bbc1dc8a616', 124.01612798341996)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('5380bb77-2842-44aa-8caa-0acd1b2341d4', 'f6d22425-67e2-499c-b035-6bbc1dc8a616', 'c8263cc9-f87d-4e6c-a5c6-5f452afb3c06', 23)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('1cb65d9d-7c6c-4cd7-99fe-c16eb1e6678e', 'ca122c15-dab1-45ef-9928-2804631eefef', '55040dfc-9aa8-479c-bba8-79ffb4e3f06a', 31)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('6b39ba58-b1a2-4ae4-a54f-ef175c67cf04', '810f5b0e-e8fa-42a4-b347-919dccae59b5', '1273cd00-c6b9-445b-8568-5241c68d44ae', 21)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('29590292-c27c-4a08-96fd-b686157476f2', '33d93cd0-7cf6-4422-ab1a-9b5a3a672cee', 'd4f0cba7-dbaf-40ae-bbdb-4f162f5acb78', 48.16637831516918)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('6bb5a566-58aa-472a-8b61-d4a5db3043ae', 'd4f0cba7-dbaf-40ae-bbdb-4f162f5acb78', '3e55fd13-731b-4614-bd4c-0e84609b06b1', 20.024984394500787)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('4a7f1b91-f579-4892-b085-a3745fe99893', '3e55fd13-731b-4614-bd4c-0e84609b06b1', '39b9c16b-6ff9-4a63-afdb-d6233c6bd909', 48.010415536631214)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('148b317c-ddad-483a-9eea-30be5e23b228', '39b9c16b-6ff9-4a63-afdb-d6233c6bd909', '8c231988-7ce6-4ac7-b6ef-36fb0fa70e51', 27)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('8b54ec22-72b8-4317-8a6b-491fb8d7821f', '8c231988-7ce6-4ac7-b6ef-36fb0fa70e51', '0c05e0c5-146b-48b3-bfde-a9ac75b03543', 33)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('a6f11b84-2901-4982-8d56-df61714487b0', '0c05e0c5-146b-48b3-bfde-a9ac75b03543', '8dfb9f05-17a2-44b1-ac31-3b717d74e06a', 21.02379604162864)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('10b18a2f-da5d-4ce6-b1c8-32a19544cfbf', '0c05e0c5-146b-48b3-bfde-a9ac75b03543', '3118fc50-68cd-4574-9b59-c0170f7ccb35', 66.03029607687671)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('6421693d-1410-4e8d-a66f-988d47920134', 'd4f0cba7-dbaf-40ae-bbdb-4f162f5acb78', '3c37e027-d950-495f-9d22-37972cb6151b', 21)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('84e25d8c-7593-4269-8317-b7c3297012ed', '3e55fd13-731b-4614-bd4c-0e84609b06b1', 'd032941f-e61b-4eec-ae3e-8869c68a6a98', 40)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('e7620ef2-dc44-44c5-b5f2-50690daa1636', 'be55fe26-290c-46c0-8d3b-12c63e10953f', '34334e11-57fd-4189-86b5-b77c12acc551', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('5cfee67b-621d-4d56-9ff3-8a9965c35d6a', 'be55fe26-290c-46c0-8d3b-12c63e10953f', '3360b81a-dfcb-45c6-9ade-f3225d7de597', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('de450549-13a9-468a-9d12-e1754cdc490e', '34334e11-57fd-4189-86b5-b77c12acc551', '3360b81a-dfcb-45c6-9ade-f3225d7de597', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('46f7af98-f69c-42d9-8de1-61a8a237ef31', 'be55fe26-290c-46c0-8d3b-12c63e10953f', 'fed7b60e-9cda-4336-928e-7d153405c701', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('bd4be55e-151d-4fa3-b2d6-b85fbb99b461', '34334e11-57fd-4189-86b5-b77c12acc551', 'fed7b60e-9cda-4336-928e-7d153405c701', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('053e53b9-8af0-4397-93dd-32d8384d9de4', '3360b81a-dfcb-45c6-9ade-f3225d7de597', 'fed7b60e-9cda-4336-928e-7d153405c701', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('93773192-20d8-4cc8-b7c2-a2cfa75b0127', 'be55fe26-290c-46c0-8d3b-12c63e10953f', '404d384a-250e-4472-8d16-2bd6eb2d588b', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('cfb5cb9a-ac15-46de-972d-dcfb8e686074', '34334e11-57fd-4189-86b5-b77c12acc551', '404d384a-250e-4472-8d16-2bd6eb2d588b', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('08f4aa81-d150-4c2e-bbd4-c246e3042556', '3360b81a-dfcb-45c6-9ade-f3225d7de597', '404d384a-250e-4472-8d16-2bd6eb2d588b', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('edb03439-1a21-49e9-b491-23c671753d59', 'fed7b60e-9cda-4336-928e-7d153405c701', '404d384a-250e-4472-8d16-2bd6eb2d588b', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('597e5267-f18c-49c0-8ad2-e1322896677b', 'be55fe26-290c-46c0-8d3b-12c63e10953f', 'bdd57762-0efc-4a03-8648-a627106aef94', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('91907d93-279a-41e2-80b8-2f30744092a0', '34334e11-57fd-4189-86b5-b77c12acc551', 'bdd57762-0efc-4a03-8648-a627106aef94', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('18d77216-a454-47e8-bb20-876417457240', '3360b81a-dfcb-45c6-9ade-f3225d7de597', 'bdd57762-0efc-4a03-8648-a627106aef94', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('5048ebc5-8cd9-4346-b324-ed26e2e4628a', 'fed7b60e-9cda-4336-928e-7d153405c701', 'bdd57762-0efc-4a03-8648-a627106aef94', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('47c6d353-e2be-4a19-b77a-0f3e47f648ae', '404d384a-250e-4472-8d16-2bd6eb2d588b', 'bdd57762-0efc-4a03-8648-a627106aef94', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('5a0bb6db-eb12-47ea-a0cb-2c123e7f97a2', 'be55fe26-290c-46c0-8d3b-12c63e10953f', 'dbee0aa8-7db9-405f-8a6f-02a5e68ed9f4', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('e0c22ff7-27b1-44b5-8743-8f5f5513e699', '34334e11-57fd-4189-86b5-b77c12acc551', 'dbee0aa8-7db9-405f-8a6f-02a5e68ed9f4', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('5756ab7b-2e50-4774-be72-408925f13f4a', '3360b81a-dfcb-45c6-9ade-f3225d7de597', 'dbee0aa8-7db9-405f-8a6f-02a5e68ed9f4', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('fb028cd6-d1a6-4878-a02d-4024b0fcd49c', 'fed7b60e-9cda-4336-928e-7d153405c701', 'dbee0aa8-7db9-405f-8a6f-02a5e68ed9f4', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('c925c2f6-97e8-4e3a-b567-a95b2a39407e', '404d384a-250e-4472-8d16-2bd6eb2d588b', 'dbee0aa8-7db9-405f-8a6f-02a5e68ed9f4', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('74e32e14-22eb-4955-a22d-2c4cadf2e113', 'bdd57762-0efc-4a03-8648-a627106aef94', 'dbee0aa8-7db9-405f-8a6f-02a5e68ed9f4', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('614ec99c-59a5-41f4-a83c-f5a8fcbcae7e', 'c302fd90-c31f-4746-9189-f1c1f71ee68e', '34334e11-57fd-4189-86b5-b77c12acc551', 46.010868281309364)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('da9224e0-92d5-4c67-a2dc-8f51f9834b61', '33d93cd0-7cf6-4422-ab1a-9b5a3a672cee', 'bc826d5a-a726-4db9-af70-696d66b4493d', 49)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('97b1dc2a-85e7-45ae-a9c6-419e97d208b8', '22427ea7-b532-4555-81ad-2beb195127f1', '3826583d-c4ea-4857-8059-84cb8b408587', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('1156b71f-3073-4879-9c9e-b8a014c5a1ae', '22427ea7-b532-4555-81ad-2beb195127f1', 'b13696c3-23a0-4d0e-a317-fc63144c65c8', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('28a0b5fe-609d-4183-87af-2f583ff6fa34', '3826583d-c4ea-4857-8059-84cb8b408587', 'b13696c3-23a0-4d0e-a317-fc63144c65c8', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('337ccc5e-8ad1-4b61-a854-d3675afb36c9', '22427ea7-b532-4555-81ad-2beb195127f1', '629f2c4f-43ea-4f95-94c0-eb64561714a2', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('84c72681-dd84-4ebb-afc8-b79b4ab54d08', '3826583d-c4ea-4857-8059-84cb8b408587', '629f2c4f-43ea-4f95-94c0-eb64561714a2', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('a681311d-8ac3-4d90-b2c0-1beba889c8bf', 'b13696c3-23a0-4d0e-a317-fc63144c65c8', '629f2c4f-43ea-4f95-94c0-eb64561714a2', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('40f3dc22-7e69-4d06-b8fd-012e382a782b', '22427ea7-b532-4555-81ad-2beb195127f1', '53f1da29-fe7a-4492-b417-05784cbefadb', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('c0e40c96-59b6-420a-9a0b-2e87790077c1', '3826583d-c4ea-4857-8059-84cb8b408587', '53f1da29-fe7a-4492-b417-05784cbefadb', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('7d767de7-2c21-4a10-93bd-23ee19925cba', 'b13696c3-23a0-4d0e-a317-fc63144c65c8', '53f1da29-fe7a-4492-b417-05784cbefadb', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('eb82b6f7-08ff-4fc8-8fc5-af8e695be2bd', '629f2c4f-43ea-4f95-94c0-eb64561714a2', '53f1da29-fe7a-4492-b417-05784cbefadb', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('739b9967-bb7e-4675-a38d-2fc702bda650', 'bc826d5a-a726-4db9-af70-696d66b4493d', '3826583d-c4ea-4857-8059-84cb8b408587', 29.017236257093817)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('ad4dba6a-71bb-48a1-bf85-eebb20bea0da', 'bc826d5a-a726-4db9-af70-696d66b4493d', 'bd2ce26a-cb32-4df7-b67d-567e86224688', 54)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('80b89331-04b3-4f41-89f4-e4a36aee4afc', 'bd2ce26a-cb32-4df7-b67d-567e86224688', '427e9816-54c3-42a6-ae37-0d9784c3574c', 20)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('6b8bd150-35c7-4093-9703-6d1554adda54', '427e9816-54c3-42a6-ae37-0d9784c3574c', 'bcdbec45-fcf6-4bea-aa06-be1acda093a9', 96.00520819205592)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('d975237d-567b-4ddb-bcf7-7350a2d13b5a', 'bd2ce26a-cb32-4df7-b67d-567e86224688', 'a390fda8-5547-468b-ac31-c5372a2806fb', 26)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('ab784b32-4669-4afe-912b-a26b85c54aa3', '8c231988-7ce6-4ac7-b6ef-36fb0fa70e51', 'c82d4085-03f3-4a1c-bff3-4b945e6c4afd', 43.01162633521314)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('6a2e5973-f2e6-40f4-bea0-d321636ae458', '39b9c16b-6ff9-4a63-afdb-d6233c6bd909', '04f545de-4a7b-4840-9ac4-a1ddad5fa4f4', 23.021728866442675)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('d20bb0fe-b388-471c-9f4d-6aa679a5b702', '6c1a1f69-1c2e-4ae0-bb2c-8ab30ee5bdd0', '53f1da29-fe7a-4492-b417-05784cbefadb', 30.265491900843113)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('b0ee3261-ec8f-44cf-b602-d5ce111b348a', '6c1a1f69-1c2e-4ae0-bb2c-8ab30ee5bdd0', '2cdb65d0-44a0-4ed2-a81b-346177e003e3', 32.01562118716424)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('42f9a2cd-70fe-4233-bd2e-c9cb5858711a', '2cdb65d0-44a0-4ed2-a81b-346177e003e3', '15def0f7-9b58-4efd-84b9-a953491ded13', 44.01136216933077)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('7b729b90-c4ae-464f-912d-dc955416b0e3', '15def0f7-9b58-4efd-84b9-a953491ded13', '9d3bb40f-a420-4a6f-b8ec-0ac1c91fd954', 12)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('56e87266-d768-4a91-88c6-74d8030f1a65', '9d3bb40f-a420-4a6f-b8ec-0ac1c91fd954', 'f77033ce-f999-460e-a983-9cbe34594f0c', 38.01315561749642)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('274b6167-74e2-4903-8a12-46babc2eac41', 'f77033ce-f999-460e-a983-9cbe34594f0c', '845d1e97-f26a-48e7-8e25-95405aed8923', 174.0028735394907)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('ac107d78-9d41-4739-bd6f-4f63c4d861a8', 'dbee0aa8-7db9-405f-8a6f-02a5e68ed9f4', '75050099-a903-4694-80de-db34c00d7d0b', 21.095023109728988)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('5fa29e96-3cf6-4b84-8aec-4e4f16dbc8d2', '845d1e97-f26a-48e7-8e25-95405aed8923', 'cebe1733-413b-4d9e-a3a2-077c55ad4d18', 35.014282800023196)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('e980e5bf-4e71-444a-85bf-6b18ef9038df', '75050099-a903-4694-80de-db34c00d7d0b', '29480738-2a1e-46f2-aaca-8d02350ad4d7', 75.0066663703967)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('aed711d3-0750-488f-ae3e-4b6ea122c22b', '6c1a1f69-1c2e-4ae0-bb2c-8ab30ee5bdd0', 'b4f8e042-3b66-4d2a-ac1a-7264f8f551e2', 40.01249804748511)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('5e1b6a84-439c-4757-845a-13f8395712e2', 'b4f8e042-3b66-4d2a-ac1a-7264f8f551e2', '83ee70d9-b6a2-4a18-98b4-46d8dddc5d8d', 18)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('08809625-37ea-44cc-8b1a-df0cbe049132', '83ee70d9-b6a2-4a18-98b4-46d8dddc5d8d', 'b3b54ddb-3af9-4de2-8360-8b36311f3c1c', 67.7421582177598)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('f3642975-6a67-4a65-b26f-62d39efe659b', 'b4f8e042-3b66-4d2a-ac1a-7264f8f551e2', '3b228c55-37f1-49d9-939f-60311d69ca86', 55.57877292636101)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('d0226bab-cc53-43ec-b821-e8ffb8fcfc28', 'fed7b60e-9cda-4336-928e-7d153405c701', '352963da-672d-4930-af6a-d2235d84abb5', 44.01136216933077)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('01cfb3a6-8ee9-4345-8ede-1ec7f382e09b', '352963da-672d-4930-af6a-d2235d84abb5', 'd0a72861-dc43-43f6-ae4f-11311b2b7e1a', 92)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('f5815bbd-5b38-4cef-a835-96e8e54c3a75', 'd0a72861-dc43-43f6-ae4f-11311b2b7e1a', '39c019ab-8d1c-4c2d-a25a-f716e2b5b374', 133)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('ae4f1d89-f0dd-4057-8db0-dd0b8cdd3cef', '39c019ab-8d1c-4c2d-a25a-f716e2b5b374', '229990fa-0cf5-4cd1-a835-003fd5affbdf', 18)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('d8b10074-7129-47db-b29b-9f834382df82', '229990fa-0cf5-4cd1-a835-003fd5affbdf', 'cb6b6a21-d807-4a62-b321-defc13a2b4f6', 28.160255680657446)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('601f69b8-ea2a-469d-a986-eeb28333ebcf', 'cb6b6a21-d807-4a62-b321-defc13a2b4f6', 'a1c359d1-1b77-45cf-bdec-bb0082dbebb2', 32)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('0377fa2e-9cb6-4a06-9e28-8b2cbf2b5535', 'cb6b6a21-d807-4a62-b321-defc13a2b4f6', 'c1faebf7-710b-4e22-b7c2-a09f9710cc7b', 21.02379604162864)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('2218373e-4315-4484-89e6-8cf149cf2fb3', 'd0a72861-dc43-43f6-ae4f-11311b2b7e1a', '5190800a-39af-45f3-899f-6f83e4b60fe4', 29.017236257093817)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('136a5d87-3bf2-46a4-80fe-08a6137ce2f8', '39c019ab-8d1c-4c2d-a25a-f716e2b5b374', '24256258-79ca-4a48-9073-7c23e6841d5c', 51)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('c577f389-ed4e-4614-bfc0-e87409f2a0e9', '229990fa-0cf5-4cd1-a835-003fd5affbdf', 'ae883595-f017-463a-ba51-8cba2e476cd6', 87)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('e28cb6dd-9944-4038-a72a-7e0cc8449f7b', 'ae883595-f017-463a-ba51-8cba2e476cd6', '453d67de-fea4-4336-9035-fd914d1cc4d8', 19)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('de3587d9-2147-4393-ba33-7f83d9012583', '453d67de-fea4-4336-9035-fd914d1cc4d8', '23c9505e-a63d-40d4-9323-7217f25b92e8', 33)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('8d3bd910-84e1-4192-9e99-9cd2734ecac3', '23c9505e-a63d-40d4-9323-7217f25b92e8', '629f2c4f-43ea-4f95-94c0-eb64561714a2', 30.14962686336267)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('99e99f0a-abb3-4cea-ae82-5089f3308b4c', '23c9505e-a63d-40d4-9323-7217f25b92e8', '0defc1d0-46c1-4516-b5f0-b0b14fb0a7a1', 54)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('93084108-9ac5-4690-b2b9-403dc22c779a', '0defc1d0-46c1-4516-b5f0-b0b14fb0a7a1', '0b192602-8982-4d62-97ad-d7bcd08315c8', 29.017236257093817)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('c10df489-c30d-42a8-a226-2fe09003eb3a', '0b192602-8982-4d62-97ad-d7bcd08315c8', '92c5bf49-cd4f-46b9-a6e5-7250919a7f77', 20.591260281974)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('7fb447ff-6999-48a0-b997-cf36e8444d92', '0defc1d0-46c1-4516-b5f0-b0b14fb0a7a1', '98594832-3d4a-4275-b04a-d8ab760fa674', 30)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('8465b69e-64df-4b19-9c6c-4ac7ae31cf20', '0b192602-8982-4d62-97ad-d7bcd08315c8', '6c030df1-f1cf-4f02-aaa4-b4e103b048ac', 31.016124838541646)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('321aa7aa-c31b-4368-984e-4016141f7e53', '92c5bf49-cd4f-46b9-a6e5-7250919a7f77', '52af8c0a-1eef-42a4-a993-ed4fd00edeac', 32.526911934581186)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('3d3f99dc-008f-402b-b007-1116ff26cb81', '352963da-672d-4930-af6a-d2235d84abb5', '7113820b-6f16-4b9f-8ac5-882953798550', 59)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('20ced703-e2b6-499c-8bae-860e916f5c77', '7113820b-6f16-4b9f-8ac5-882953798550', '47036dbb-0523-4dc0-bd4d-fdbd3970e88f', 21)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('1514aa21-3482-4a79-8d10-c8074d986fcd', '47036dbb-0523-4dc0-bd4d-fdbd3970e88f', 'e5b8c12b-6972-481d-bddb-40f4018bb751', 80)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('f999779a-330c-4fa8-956f-6e38d10d5f59', '47036dbb-0523-4dc0-bd4d-fdbd3970e88f', '1497bb21-0149-479f-9eab-c5c719f3fb3b', 57.0087712549569)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('ecf244eb-1e2f-44f8-b6b8-7b3df41c3783', '47036dbb-0523-4dc0-bd4d-fdbd3970e88f', 'e7943528-28f7-4f70-9220-ba5a0b025bb1', 42)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('912157bc-479f-44a6-a764-9ef14bec637b', 'be55fe26-290c-46c0-8d3b-12c63e10953f', 'eab54c72-c4d6-4413-9259-c9576f471963', 44)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('b28fca4b-c72c-4073-8bae-735f33c1ec27', 'eab54c72-c4d6-4413-9259-c9576f471963', 'df63f532-6748-4eba-8497-1833831551a3', 73.00684899377592)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('152f3a9d-e10b-4c7d-adb1-e89b4250e8c5', 'df63f532-6748-4eba-8497-1833831551a3', 'c471ec1b-c322-4c2b-98ee-065ce5bc77d5', 107)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('d79555ea-8023-4ddc-81ab-37bd22211269', 'c471ec1b-c322-4c2b-98ee-065ce5bc77d5', '43adc880-a679-42f2-b9b7-70b1f5e8a8a8', 58)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('82fc4ed9-10b8-4ba5-aa8c-ee803e4475e2', '43adc880-a679-42f2-b9b7-70b1f5e8a8a8', '51fc8c28-ed02-4dd5-a3fe-707ff3fd7e21', 16)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('736d3a6d-d0cb-4e80-b015-b7bb36f5a4da', '51fc8c28-ed02-4dd5-a3fe-707ff3fd7e21', 'e45cd934-bf1c-41a0-87c0-d41b338b4da3', 34)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('9a16535a-51e2-4afa-a284-466932d1402d', '4929fda4-bde6-4190-9212-118f3b9d67b7', '99677174-0695-4ac0-accb-5747a45cdcd6', 43)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('e1900762-afd4-4380-a211-4e48e1d48d32', '99677174-0695-4ac0-accb-5747a45cdcd6', '8a17fedf-2c07-4e85-a151-424675abdaf9', 15.033296378372908)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('654f9239-a9e4-4c38-b6fc-b55ed52fdf7a', '8a17fedf-2c07-4e85-a151-424675abdaf9', 'f1803e37-5699-4d6e-ae52-a9523ec19650', 12.041594578792296)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('957846ec-aebf-4fa9-b651-a4539e2e4a85', 'f1803e37-5699-4d6e-ae52-a9523ec19650', '951ab5bd-21a7-4c3f-8d63-be9711e30362', 33.37663853655727)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('0c3a3e16-8bf6-480f-a819-136b8066e244', '99677174-0695-4ac0-accb-5747a45cdcd6', '24f44874-d250-4a93-9ecb-ce97474c29ac', 18.027756377319946)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('ede3a380-ef9d-41e0-a82c-395dd93c0259', '99677174-0695-4ac0-accb-5747a45cdcd6', 'c5d1dc79-9b77-4c40-9483-ec01a56402e5', 19.026297590440446)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('02ef11df-7fae-49a9-8d76-b3fc7ade6098', '4929fda4-bde6-4190-9212-118f3b9d67b7', '22427ea7-b532-4555-81ad-2beb195127f1', 29.068883707497267)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('7fa6d76a-c32b-4ebf-865b-06bd0267e994', '8a17fedf-2c07-4e85-a151-424675abdaf9', '22d5b680-61b3-423f-a8e7-3ceb2f39b590', 22.02271554554524)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('03d90f81-bcfc-4725-8b63-d978603ee420', '22d5b680-61b3-423f-a8e7-3ceb2f39b590', '90e0ba05-ea29-4bb1-bf60-0ebc96da09b4', 36.345563690772494)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('ae3b85b3-f3b7-4a7b-9332-c76fefaa1351', '22d5b680-61b3-423f-a8e7-3ceb2f39b590', '24fd2506-27a5-46f9-920f-13ea568e74bc', 28.792360097775937)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('c9030fb3-5cf3-48a4-9c5e-25e77f7c21e1', '24fd2506-27a5-46f9-920f-13ea568e74bc', '02137cc9-8c47-4e6d-a73d-51bb721c3206', 16.1245154965971)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('ad972d3d-b7d3-4d01-9007-8201264fcf7f', '02137cc9-8c47-4e6d-a73d-51bb721c3206', '8fefce36-6312-4631-b2e9-ef128d9a3dfb', 40.01249804748511)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('d5554298-ddc1-4b39-9671-1a48715cbbb2', '02137cc9-8c47-4e6d-a73d-51bb721c3206', '9c1b96c3-b233-4306-be10-2d39641c8059', 44)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('e360517b-6d28-417e-9c98-a05bb5433a0e', '9c1b96c3-b233-4306-be10-2d39641c8059', '0ba3b0e5-012c-40e6-9226-11bcfeba19ca', 41)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('76482fa9-dc7e-4220-b3ac-60a7286c2719', '9c1b96c3-b233-4306-be10-2d39641c8059', 'f75b7640-fe0a-4d98-a809-a636d0d4658b', 19)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('4c0f6783-557e-4979-8f21-098de44da567', 'e45cd934-bf1c-41a0-87c0-d41b338b4da3', '985d4652-1317-4db8-9a05-442567b5f115', 27.018512172212592)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('b211d5ea-9192-4fac-af42-c8a99bad0c0f', '4929fda4-bde6-4190-9212-118f3b9d67b7', '985d4652-1317-4db8-9a05-442567b5f115', 30.066592756745816)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('0095e7c9-b655-4f51-9869-e79108eea397', 'f75b7640-fe0a-4d98-a809-a636d0d4658b', '985d4652-1317-4db8-9a05-442567b5f115', 48)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('5347370b-6d1c-49e5-880f-4fdf33741eb8', 'e45cd934-bf1c-41a0-87c0-d41b338b4da3', 'f2aaf3d2-9f34-4656-9e90-58c2dda486f0', 32.0624390837628)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('d6427eb3-b901-4f9b-8824-1ab7c2b47e10', '51fc8c28-ed02-4dd5-a3fe-707ff3fd7e21', '3bc47ab4-2cef-46b7-897a-f736f17cf45d', 38.01315561749642)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('9cb599c5-e39f-47a6-a402-dba2846ffe93', '43adc880-a679-42f2-b9b7-70b1f5e8a8a8', '47712429-5844-4f63-91a9-0fcf5a32c995', 39.01281840626232)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('0ca7d124-4b12-4432-a417-ea00fc06d662', 'c471ec1b-c322-4c2b-98ee-065ce5bc77d5', 'decdfe7a-f3f3-46de-95e0-4ab3701836e4', 38.01315561749642)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('1b52392a-9a73-4dd7-ab6f-7db71755c64a', 'df63f532-6748-4eba-8497-1833831551a3', 'f8fbd993-2c1d-4952-8c3e-b4eca45ff0fc', 36.05551275463989)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('d24857c3-dbd6-49ce-b008-6de57e0fe0f9', 'eab54c72-c4d6-4413-9259-c9576f471963', '579d9f10-64ea-4478-ace3-27f417e3f028', 26)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('890b18c2-cf5f-405b-9dc4-c1fe5cba0089', '579d9f10-64ea-4478-ace3-27f417e3f028', 'bee8617e-f69f-445b-92e5-157e5ad03f07', 139)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('8d2b4265-aff6-4d39-892b-fba0b7770abd', 'bee8617e-f69f-445b-92e5-157e5ad03f07', '69b0b0b2-9075-42c2-a3d6-9e84259b6bcb', 33)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('7d42cdee-3ffb-40d2-8269-6687e7f44bbd', '579d9f10-64ea-4478-ace3-27f417e3f028', '706a7020-8637-4c63-a78e-9343e2ffde60', 81)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('da30f10e-05ca-4f39-8b5b-f3ebbc117171', '706a7020-8637-4c63-a78e-9343e2ffde60', 'a223094f-05aa-48b3-bbaa-8c84222180b3', 74)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('4149f691-594a-4275-b072-04066baf2aa4', 'a223094f-05aa-48b3-bbaa-8c84222180b3', '1c0c92c9-ae3a-4258-be77-ff2ad3e726c6', 140)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('4e5523f1-3406-4d3a-91d3-cf0a2e25c7c5', '1c0c92c9-ae3a-4258-be77-ff2ad3e726c6', '4c733de2-b3a5-46df-9d45-2700f1b7256a', 76.00657866263946)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('7d29a48f-8d30-42f2-bef0-7d363eee7a7f', '4c733de2-b3a5-46df-9d45-2700f1b7256a', '4313c69c-364c-4ac4-81c4-809f9ab46c57', 35)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('c6c4f7b3-67a9-4a61-9db8-11026b2a00fe', '706a7020-8637-4c63-a78e-9343e2ffde60', 'ef8b00a2-fc32-4332-bad9-54ee72b55706', 34)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('42b04c42-87ff-4b22-b896-9001571253c1', '1c0c92c9-ae3a-4258-be77-ff2ad3e726c6', '1fc8d85f-3156-4ba0-b3e7-61c10fda9422', 23)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('85948235-8fd8-4234-8da2-85c66be2496c', 'a223094f-05aa-48b3-bbaa-8c84222180b3', 'd231b01c-0b11-4496-9cbb-0f3e0099881c', 24.020824298928627)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('9627969f-492e-40ed-839b-492dd52e6ba7', '5ea060a8-f48a-412a-aeeb-4950f6a43904', '3e615a71-8d6f-4850-9207-d109ea43b154', 48.010415536631214)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('bd25dcd7-9c6c-4000-9569-7159a5582724', '3e615a71-8d6f-4850-9207-d109ea43b154', 'cfad229b-55b6-4ad3-8d5f-7c2bb4ae065f', 18.027756377319946)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('932a20ab-ec02-4fc6-b6dc-6b1dabfaea34', 'cfad229b-55b6-4ad3-8d5f-7c2bb4ae065f', '48ef7b43-e0f9-4b15-b7ca-35a2f4bf366e', 62.00806399170998)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('e9c4629a-b364-4876-9323-6f8051a7657f', '48ef7b43-e0f9-4b15-b7ca-35a2f4bf366e', '28623275-ac3d-46b2-99d0-5bc3414b2e60', 20.615528128088304)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('3cf6686b-4cd0-4a02-82f3-699e8728b3c4', '28623275-ac3d-46b2-99d0-5bc3414b2e60', '119579e3-1642-41c2-bbb4-4fb9fe12fc34', 26.1725046566048)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('f17d9402-8ed3-44d3-9252-9d4a608177c8', '119579e3-1642-41c2-bbb4-4fb9fe12fc34', 'f99c0e0f-0f41-4406-bebf-0724d76247bc', 24.020824298928627)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('6043e653-6551-485b-840f-a08302826f95', 'f99c0e0f-0f41-4406-bebf-0724d76247bc', 'de27a41d-cd6b-4f96-b8c0-b85056e8831f', 18.027756377319946)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('d1951f5d-5181-49a1-b30a-5c4138bc2760', 'de27a41d-cd6b-4f96-b8c0-b85056e8831f', '31c2637b-30d1-462c-aa1a-fd8ff01632bd', 12.083045973594572)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('573e5149-16ec-4316-a58e-ca0a68e596a4', '31c2637b-30d1-462c-aa1a-fd8ff01632bd', 'a92b1ab6-efbe-42a4-b17d-89b45056af0e', 61.00819617067857)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('b2401a7b-1dd5-432f-aba9-1a0e6708d613', 'a92b1ab6-efbe-42a4-b17d-89b45056af0e', '656cde3d-3fa5-4eb3-a916-508899d1b55f', 17.029386365926403)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('b04591c9-24c2-4e96-ba34-ccc763735cb0', '656cde3d-3fa5-4eb3-a916-508899d1b55f', '5ea060a8-f48a-412a-aeeb-4950f6a43904', 49.040799340956916)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('18cea583-047f-42df-a1d1-c390e1fb3ead', '5ea060a8-f48a-412a-aeeb-4950f6a43904', 'd975f956-5725-45a4-bb10-43a26b9dfba2', 33)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('7b4dbd5f-c9c7-4d48-9492-86ea1792d72d', 'd975f956-5725-45a4-bb10-43a26b9dfba2', '56cf250a-6203-4246-a35b-67da5ea1d540', 43)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('e05bb71f-7095-4bad-8829-50a685ba6c90', '56cf250a-6203-4246-a35b-67da5ea1d540', '119579e3-1642-41c2-bbb4-4fb9fe12fc34', 16.278820596099706)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('295f9b42-f8bf-4c46-bce5-fbe702aa05ff', '56cf250a-6203-4246-a35b-67da5ea1d540', 'df8a2785-f62a-4dc5-bf9e-49ed48885809', 18)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('2e2aceb8-38db-4acd-9657-c3e66171d49d', '56cf250a-6203-4246-a35b-67da5ea1d540', '4001be8c-9955-4c02-81ad-9e25226931bc', 18)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('966160c5-71ce-4757-921b-e18fc84c9bc2', 'a92b1ab6-efbe-42a4-b17d-89b45056af0e', '6c4d2e61-02b0-48f5-b0da-bbfae185457b', 31.016124838541646)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('b422fa64-cc6b-4a66-9983-cded29e14c50', 'de27a41d-cd6b-4f96-b8c0-b85056e8831f', 'ac715d21-e9bf-4159-ae9e-9f6eab7ccab1', 35.805027579936315)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('852ef080-58b1-4d38-aa8a-c8605f769f46', '119579e3-1642-41c2-bbb4-4fb9fe12fc34', '23e780b6-705c-41d0-8f89-546bb8bc59b7', 30)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('5b2854b5-4fb6-459b-a90c-771e758121af', '48ef7b43-e0f9-4b15-b7ca-35a2f4bf366e', '7b1fd3c4-1218-4dc0-84c1-2e1ec5fc0af5', 29.832867780352597)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('2a8a2c0c-50fc-49c0-b6d4-4d249f43105b', 'cfad229b-55b6-4ad3-8d5f-7c2bb4ae065f', 'e28f1542-3d18-4b6d-b515-1e7693cbc240', 39)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('061b2e03-ff29-43c4-876c-39c91644524b', '5ea060a8-f48a-412a-aeeb-4950f6a43904', '3f9194d8-0133-499b-b013-3de354449728', 22.02271554554524)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('35bd42ba-395e-4bd3-990f-18a2a9f5efeb', '3f9194d8-0133-499b-b013-3de354449728', '477b9935-cfc9-4a29-a337-56101b76cccd', 36)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('58a2517c-f239-4634-bdc7-39d09e186d98', '477b9935-cfc9-4a29-a337-56101b76cccd', 'bb675ef0-c4bb-448c-94ed-b94b87368655', 75)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('5efb3caf-e6b5-4952-b7c3-1d63164d1a64', '3f9194d8-0133-499b-b013-3de354449728', 'c6e41ca3-b908-42f3-bb43-d7d1743bc275', 39)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('9a4cc152-e145-43ec-8c8a-021dba3d48c6', '3f9194d8-0133-499b-b013-3de354449728', '3052db50-7c59-492a-adc1-e81dfc2c8cd3', 38.01315561749642)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('d9b6e925-ab2a-4718-a4cb-b1e342b19143', '477b9935-cfc9-4a29-a337-56101b76cccd', '7b82c0be-c2e5-432b-a4c0-913dd5f42333', 38.01315561749642)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('b3484802-b0b8-4b1b-bf59-dfd333499973', '477b9935-cfc9-4a29-a337-56101b76cccd', '24291b73-0ce4-4c42-bbf7-13bd46db26e4', 39.01281840626232)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('cca301fa-920b-41ba-9458-1d8bdd4ad191', 'bb675ef0-c4bb-448c-94ed-b94b87368655', '4feb23d8-9bd7-471c-8b13-a36dbdba67fc', 37)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('f9163274-4849-4894-b599-22186fa58729', 'bb675ef0-c4bb-448c-94ed-b94b87368655', 'cc7b4cf9-8700-4d77-8f33-ec1b3e92b27e', 36)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('abdb7878-99a1-430c-b556-114a2ec8e2dd', 'bb675ef0-c4bb-448c-94ed-b94b87368655', '6a72bc47-013d-4f20-b238-2e22ceee8004', 131.00381673829202)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('3585bca4-b25f-4362-a4b9-2e0f6bacdfac', '6a72bc47-013d-4f20-b238-2e22ceee8004', 'c70e2081-caaa-46c8-98ff-e92449af5ad9', 25)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('9931a85e-62f5-4569-b969-b3ea54c1554d', '6a72bc47-013d-4f20-b238-2e22ceee8004', 'b2cba5af-3ab8-4b4e-9fda-3d3a3c107bbe', 23)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('b9188553-5b87-4e39-b8b7-e2ef7a2a7713', 'b2cba5af-3ab8-4b4e-9fda-3d3a3c107bbe', 'ebfce924-6b63-422f-9590-92b2271da0fd', 22)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('8c5c000c-6df4-4f0f-a493-f676457e38d4', 'ebfce924-6b63-422f-9590-92b2271da0fd', '1fd2df12-7511-47fb-b39a-00e6187c3e8d', 50)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('9fd1e644-fa28-45ab-b1a8-7186f9d05330', '1fd2df12-7511-47fb-b39a-00e6187c3e8d', 'c89461d5-97d2-4d38-a0a0-91bee1c6962c', 21)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('7ff60f2a-9a51-4343-91d8-3457118f2d10', 'c89461d5-97d2-4d38-a0a0-91bee1c6962c', '8b2fd605-261d-45f0-b806-8defec11d0a9', 21)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('d747b85d-3e88-49ff-9880-e8197df711da', '8b2fd605-261d-45f0-b806-8defec11d0a9', 'e6a2fbef-b734-43e2-bef7-00fff15e211c', 25)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('6586ab0f-0267-47e8-bb92-3bf53b735a1d', 'e6a2fbef-b734-43e2-bef7-00fff15e211c', '4fce9e3e-9004-4a71-9e6d-dcc850d341c7', 23)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('9bb02bc3-0804-4e0b-b9ba-815e377241a7', '4fce9e3e-9004-4a71-9e6d-dcc850d341c7', 'b2cba5af-3ab8-4b4e-9fda-3d3a3c107bbe', 20.09975124224178)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('edddc57e-9392-42f9-87f3-a8c795e32acb', 'e6a2fbef-b734-43e2-bef7-00fff15e211c', '9efdfa67-c027-4046-8ceb-006c1db2c8ea', 20.024984394500787)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('482c2f0a-90c0-4263-af38-33cc7189f666', 'e6a2fbef-b734-43e2-bef7-00fff15e211c', '3360b81a-dfcb-45c6-9ade-f3225d7de597', 24.020824298928627)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('e155b25a-cd28-4f08-b0a4-edf0ca6f4861', '8b2fd605-261d-45f0-b806-8defec11d0a9', '7f21b4f7-6460-4a0e-b1b8-d642fd6e073f', 143)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('d124508e-642b-4e94-83ad-4f97f4410554', '7f21b4f7-6460-4a0e-b1b8-d642fd6e073f', '518f7cf0-182e-4825-a119-abddeb7de263', 100.00499987500625)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('c2a6069e-6c2f-4e1b-8ec0-4e6cbce017fa', '518f7cf0-182e-4825-a119-abddeb7de263', '78ee4fef-3327-45eb-b5f2-7e89280aaccc', 71.00704190430693)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('c9a09986-eb3c-427a-83dd-891ae96697a8', '7f21b4f7-6460-4a0e-b1b8-d642fd6e073f', 'c0664191-87f7-44dd-b987-f907dcd6f1a2', 37)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('225a6e76-91fb-4c61-8e65-26cd9aa8ca6c', 'c89461d5-97d2-4d38-a0a0-91bee1c6962c', '83b1c136-5f03-4381-a2ad-36de2208382e', 36)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('2dcb7c93-c9e0-4b43-a68e-1b9aec3d1f4b', '1fd2df12-7511-47fb-b39a-00e6187c3e8d', '7e5bc6fb-46ab-4ae6-8632-52e89464c9a9', 41)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('c14bfed9-388d-4cc9-a775-2061e6e09788', 'd975f956-5725-45a4-bb10-43a26b9dfba2', 'b13696c3-23a0-4d0e-a317-fc63144c65c8', 29.017236257093817)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('ffd04d2e-cd0f-4ec3-a92b-8801b734a18c', '404d384a-250e-4472-8d16-2bd6eb2d588b', '3bbcf170-1d5a-46fa-a0d7-5867338ff137', 21)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('52c30a86-eae4-4c77-a501-053fb8131fe5', '3bbcf170-1d5a-46fa-a0d7-5867338ff137', '2aa0708b-3402-48b7-b9c0-314189dec5b0', 23)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('9cc7cbff-a89a-431d-bd2f-1f3786b9fff7', '2aa0708b-3402-48b7-b9c0-314189dec5b0', '5e6a69f7-e58a-44da-855f-07a6daa0faff', 43)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('0a669c01-7dda-42eb-8f1a-597a5ea9d8e2', '5e6a69f7-e58a-44da-855f-07a6daa0faff', '9bf07049-fc56-4cb1-af1e-4b82041eb256', 63)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('de99739d-fbbf-4ee6-9c7b-09a5aee17327', '2aa0708b-3402-48b7-b9c0-314189dec5b0', 'd92fdf58-fc67-42ee-a0b1-66291c329d1c', 68.00735254367721)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('a3cd823c-7956-4416-a527-84738a01a2be', '3bbcf170-1d5a-46fa-a0d7-5867338ff137', 'f9d5d585-0f7e-4f78-953b-1d81abd09f56', 21)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('26eb67c4-fbb9-4402-8b43-8215aca2c43c', 'bdd57762-0efc-4a03-8648-a627106aef94', 'f14b953c-6a4b-40eb-995c-3b18deeb1744', 24.515301344262525)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('20de0ac3-6a15-4e3a-a831-7af3967e3345', 'f14b953c-6a4b-40eb-995c-3b18deeb1744', '1d03ae2c-8151-4df5-b2ef-0e24f990b358', 24)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('7469f9d2-550b-4416-8b52-ff18057277c5', 'f14b953c-6a4b-40eb-995c-3b18deeb1744', '34f1023f-9e9e-4e8d-a856-f2d943b5d594', 24)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('29a336e1-1b5a-4ecb-9c45-6264ed4826ef', '34f1023f-9e9e-4e8d-a856-f2d943b5d594', '0765e05b-c4a8-43df-af56-67c0c160ff26', 62)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('b45594b9-fa67-4246-83f2-4d70cd6c8ebc', '34f1023f-9e9e-4e8d-a856-f2d943b5d594', '6284c55b-740b-4756-aec5-f13f1d78d56d', 44)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('75d469f1-2e62-40db-8b73-b6ed7ddbf5aa', '6284c55b-740b-4756-aec5-f13f1d78d56d', '60dc2e02-5689-4318-8526-9dc01a3a90eb', 53)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('eed70477-1444-41d3-b8c3-8b70ef3b4d6e', '2946e6ae-9bc7-498a-9af4-8d45fd31772d', '68425f6c-b67b-4d01-8340-ea737640733b', 21.02379604162864)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('86ab6176-d841-4f29-a018-727319c79853', '68425f6c-b67b-4d01-8340-ea737640733b', 'a5d9a8d7-95dc-4e1a-9ebd-cd1f132c8fa9', 25)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('f25fecaa-ec32-4561-b025-0b48122f68b2', 'a5d9a8d7-95dc-4e1a-9ebd-cd1f132c8fa9', 'a647bc10-7eab-4a20-9fe9-2a667a32527a', 29)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('fd75b15a-7eec-4926-8b82-6cf70e6769ae', '87aee9a8-cec4-47ae-9eb1-d948a73618f3', '177fec3e-fb64-4305-b40f-d333c4828d5b', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('f97115dc-4b2e-45db-9e2c-3f5618a3ce0a', '87aee9a8-cec4-47ae-9eb1-d948a73618f3', 'a38f3f87-aea1-466b-82fd-6bef44ed2cb6', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('0bbc3558-7be9-46b6-8908-6e68c04b7ece', '177fec3e-fb64-4305-b40f-d333c4828d5b', 'a38f3f87-aea1-466b-82fd-6bef44ed2cb6', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('eefe0f2b-cb07-4f84-9a8e-3109fb901064', '87aee9a8-cec4-47ae-9eb1-d948a73618f3', '106e85b1-5cd3-4a8d-af46-d2d8c74e95bd', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('08767e59-bcf6-429b-913c-45e59ea95c88', '177fec3e-fb64-4305-b40f-d333c4828d5b', '106e85b1-5cd3-4a8d-af46-d2d8c74e95bd', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('604d2625-d4d9-4952-a18c-0df29f171312', 'a38f3f87-aea1-466b-82fd-6bef44ed2cb6', '106e85b1-5cd3-4a8d-af46-d2d8c74e95bd', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('e1bd1073-7863-4717-a152-0b82dd82b4e9', '2946e6ae-9bc7-498a-9af4-8d45fd31772d', '87aee9a8-cec4-47ae-9eb1-d948a73618f3', 35)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('7e749dd1-5dbe-4890-8665-301b414bfae7', '177fec3e-fb64-4305-b40f-d333c4828d5b', 'add0fb85-fe87-4bcb-be32-a5666032feb7', 21)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('08e75776-9cdc-43a7-a20f-2737db193df2', 'add0fb85-fe87-4bcb-be32-a5666032feb7', 'db133aa0-b1bd-488b-ab25-e6e573f9aeb1', 83.00602387778854)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('8c346c64-28f8-4acb-87b1-997e9f202dfb', 'a38f3f87-aea1-466b-82fd-6bef44ed2cb6', 'f4862e43-7542-44be-b149-5615eb64b136', 20)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('852e58a1-7b56-4e10-8de9-6b5363b83fd5', 'f4862e43-7542-44be-b149-5615eb64b136', 'ccf66544-fbd0-4726-b064-98ce7934cb28', 89.00561780022652)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('f2abdb40-ae5b-4036-82e8-90d6425b96e0', '106e85b1-5cd3-4a8d-af46-d2d8c74e95bd', 'f79b8f5c-94f7-4c88-8982-a257c2dec3f3', 20)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('27c250d7-418a-4c3e-882c-fb3be5afe2f5', 'f79b8f5c-94f7-4c88-8982-a257c2dec3f3', '57810305-38f9-481c-ad38-eda1fce5493c', 91)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('8ae53c0e-f51c-4e8d-88e8-0dc445a7001f', '427e9816-54c3-42a6-ae37-0d9784c3574c', 'aa041fb5-79bf-428c-b030-cfdfffea932e', 54)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('1835cf99-fa1c-4b61-b5b1-76d7c2612cd1', '2458f7c5-01c9-4c59-93fe-6f5892952290', '10bb8b65-0648-48ef-a0e3-9542d5ebac34', 0)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('3600fca6-2933-4603-adf8-4872138211c4', 'eab54c72-c4d6-4413-9259-c9576f471963', '2458f7c5-01c9-4c59-93fe-6f5892952290', 47.51841748206689)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('5c748944-f0da-461e-a5dd-2e96d25f188a', '352963da-672d-4930-af6a-d2235d84abb5', '10bb8b65-0648-48ef-a0e3-9542d5ebac34', 47.67598976424087)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('e9ca422a-9c18-4737-9b2f-f4dc6becd0a5', '2946e6ae-9bc7-498a-9af4-8d45fd31772d', '01093d5c-32e2-4b8d-a520-9e32521722d0', 39.319206502675)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('aca43b96-c19e-48de-899f-2f6624bd7c2c', '01093d5c-32e2-4b8d-a520-9e32521722d0', '3e73d5d2-16ed-4837-85ac-e321b8763802', 120.00416659433121)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('0369873f-e800-403a-b421-c6e4702c2c9a', '3e73d5d2-16ed-4837-85ac-e321b8763802', '2d052c23-7658-4955-9308-80a00c148f5a', 44.598206241955516)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('1a3e1146-8887-4852-8346-ba9457c4f8b4', '2d052c23-7658-4955-9308-80a00c148f5a', 'ca68ef95-d5b3-48ee-95bb-9d5608fa54fb', 44.40720662234904)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('92f3b539-c7ee-4261-a904-2b2e22959a80', 'ca68ef95-d5b3-48ee-95bb-9d5608fa54fb', '63ef5854-c8b7-46f0-8445-4b01c417e423', 37.013511046643494)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('056956cf-c204-4bad-980c-6b9f7ebbb559', '63ef5854-c8b7-46f0-8445-4b01c417e423', '5504e383-9cd8-40cc-9149-056760219333', 31.622776601683793)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('c1d9766e-7b3e-4dfe-bcce-8ff85a4621e3', '5504e383-9cd8-40cc-9149-056760219333', '086e0557-b05c-45e6-9e82-33025d7bba82', 65.00769185258002)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('234d8ba7-8a6f-4f4f-9e44-9e7acf9fd844', '086e0557-b05c-45e6-9e82-33025d7bba82', '4a699be3-b591-439e-a61d-22eed15e0554', 25.317977802344327)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('ede20606-4d34-4b28-b98f-481e86fd175d', '4a699be3-b591-439e-a61d-22eed15e0554', '5cd5abf4-d9e5-474d-b4bb-b84cd66b5a69', 21.840329667841555)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('10a933ab-f84c-40ae-acc3-d4b55f303abe', '5cd5abf4-d9e5-474d-b4bb-b84cd66b5a69', 'eb114265-fc34-40b1-bbc6-bb1551a5179e', 33.1058907144937)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('0a11e67d-c798-4a62-860b-5c9d16ab6496', 'eb114265-fc34-40b1-bbc6-bb1551a5179e', 'd9c392a5-b573-4de4-b4ec-555f757e659d', 50.32891812864648)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('bc0d33c0-7fbd-4f68-8a0b-1017cfeb4af6', 'd9c392a5-b573-4de4-b4ec-555f757e659d', 'a0df4f42-982a-4b54-b880-b47d20889830', 50.24937810560445)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('db7d2bee-889b-4e07-8f6f-905ae36f9c3c', 'a0df4f42-982a-4b54-b880-b47d20889830', 'aa041fb5-79bf-428c-b030-cfdfffea932e', 17.029386365926403)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('cbb2e5c9-a9c2-49a0-9557-afddee14f917', '3e73d5d2-16ed-4837-85ac-e321b8763802', 'b5314be1-d4cb-41fa-a6a0-f2f5fe0b7f5b', 42.95346318982906)",
                "INSERT INTO USER1.EDGE (EDGE_ID, NODEA, NODEB, COST) VALUES ('6484836c-ff9f-4eed-97aa-045d4f00ea45', '3e73d5d2-16ed-4837-85ac-e321b8763802', '280c6df2-174d-425e-b1c1-cd6f817c31ef', 39)"
    };
}
