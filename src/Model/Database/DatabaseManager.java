package Model.Database;

import Domain.Map.*;

import java.sql.*;
import java.util.*;

/**
 * Created by Brandon on 2/4/2017.
 */
public class DatabaseManager {

    private final String framework = "embedded";
    private final String protocol = "jdbc:derby:/";
    private Connection conn = null;
    private ArrayList<Statement> statements = new ArrayList<Statement>(); // list of Statements, PreparedStatements
    private PreparedStatement psInsert;
    private PreparedStatement psUpdate;
    private Statement s;
    private ResultSet rs = null;
    private String username = "user1";
    private String uname = username.toUpperCase();

    public static HashMap<UUID, MapNode> mapNodes = new HashMap<>();
    public static HashMap<Integer, NodeEdge> edges = new HashMap<>();
    public static HashMap<String, Doctor> doctors = new HashMap<>();
    public static HashMap<String, Floor> floors = new HashMap<>();
    public static HashMap<UUID, Suite> suites = new HashMap<>();
    public static HashMap<String, Office> offices = new HashMap<>();

    public static DatabaseManager instance = null;
    public static Hospital Faulkner;

    public static final String[] createTables = {
            "CREATE TABLE USER1.BUILDING (BUILDING_ID INT PRIMARY KEY NOT NULL, " +
                    "NAME VARCHAR(100))",
            "CREATE TABLE USER1.FLOOR (FLOOR_ID VARCHAR(25) PRIMARY KEY NOT NULL, " +
                    "BUILDING_ID INT," +
                    "NUMBER INT, " +
                    "CONSTRAINT FLOOR_BUILDING_BUILDING_ID_FK FOREIGN KEY (BUILDING_ID) REFERENCES BUILDING (BUILDING_ID))",
            "CREATE TABLE USER1.NODE (NODE_ID CHAR(36) PRIMARY KEY NOT NULL, " +
                    "POSX DOUBLE, " +
                    "POSY DOUBLE, " +
                    "FLOOR_ID VARCHAR(25), " +
                    "TYPE INT, " +
                    "CONSTRAINT NODE_FLOOR_FLOOR_ID_FK FOREIGN KEY (FLOOR_ID) REFERENCES FLOOR (FLOOR_ID))",
            "CREATE TABLE USER1.SUITE (SUITE_ID CHAR(36) PRIMARY KEY NOT NULL, " +
                    "NAME VARCHAR(200), " +
                    "NODE_ID CHAR(36), " +
                    "CONSTRAINT SUITE_NODE_NODE_ID_FK FOREIGN KEY (NODE_ID) REFERENCES NODE (NODE_ID))",
            "CREATE TABLE USER1.SERVICES (SERVICE_ID CHAR(36) PRIMARY KEY, " +
                    "NAME VARCHAR(200), " +
                    "NODE_ID CHAR(36), " +
                    "TYPE VARCHAR(200), " +
                    "FLOOR INT, " +
                    "CONSTRAINT SERVICES_NODE_NODE_ID_FK FOREIGN KEY (NODE_ID) REFERENCES NODE (NODE_ID))",
            "CREATE TABLE USER1.DOCTOR (DOC_ID CHAR(36) PRIMARY KEY NOT NULL, " +
                    "NAME VARCHAR(50), " +
                    "DESCRIPTION VARCHAR(20), " +
                    "NUMBER VARCHAR(20), " +
                    "HOURS VARCHAR(20))",
            "CREATE TABLE USER1.OFFICES (OFFICE_ID CHAR(36) PRIMARY KEY NOT NULL, " +
                    "NAME VARCHAR(200), " +
                    "SUITE_ID CHAR(36), " +
                    "CONSTRAINT OFFICES_SUITE_SUITE_ID_fk FOREIGN KEY (SUITE_ID) REFERENCES SUITE (SUITE_ID))",
            "CREATE TABLE USER1.EDGE (EDGE_ID INT PRIMARY KEY NOT NULL," +
                    "NODEA CHAR(36), " +
                    "NODEB CHAR(36), " +
                    "COST DOUBLE, " +
                    "FLOOR_ID VARCHAR(25), " +
                    "CONSTRAINT EDGE_NODE_NODE_ID_FKA FOREIGN KEY (NODEA) REFERENCES NODE (NODE_ID)," +
                    "CONSTRAINT EDGE_NODE_NODE_ID_FKB FOREIGN KEY (NODEB) REFERENCES NODE (NODE_ID)," +
                    "CONSTRAINT EDGE_FLOOR_FLOOR_ID_FK FOREIGN KEY (FLOOR_ID) REFERENCES FLOOR (FLOOR_ID))",
            "CREATE TABLE USER1.SUITE_DOC (SUITE_ID CHAR(36), " +
                    "DOC_ID CHAR(36), " +
                    "CONSTRAINT SUITE_DOC_SUITE_SUITE_ID_FK1 FOREIGN KEY (SUITE_ID) REFERENCES SUITE (SUITE_ID)," +
                    "CONSTRAINT SUITE_DOC_DOCTOR_DOCTOR_ID_FK2 FOREIGN KEY (DOC_ID) REFERENCES DOCTOR (DOC_ID))"};

    public static final String[] dropTables = {
            "DROP TABLE USER1.SUITE_DOC",
            "DROP TABLE USER1.EDGE",
            "DROP TABLE USER1.OFFICES",
            "DROP TABLE USER1.DOCTOR",
            "DROP TABLE USER1.SERVICES",
            "DROP TABLE USER1.SUITE",
            "DROP TABLE USER1.NODE",
            "DROP TABLE USER1.FLOOR",
            "DROP TABLE USER1.BUILDING"};


    public void loadData() throws SQLException {
        s = conn.createStatement();

        if(Faulkner==null) //if not initialized
        {
            Faulkner = new Hospital();
        }

        loadHospital(Faulkner);
        conn.commit();
    }

    public void saveData() throws SQLException {
        s = conn.createStatement();
        executeStatements(dropTables);
        executeStatements(createTables);
        saveHospital(Faulkner);
        s.close();
        
        System.out.println("Data Saved Correctly");
    }

    private void loadHospital(Hospital h) throws SQLException {
        PreparedStatement floorsPS = conn.prepareStatement("SELECT * from FLOOR where BUILDING_ID = ?");
        PreparedStatement nodesPS = conn.prepareStatement("SELECT * from NODE where FLOOR_ID = ?");
        PreparedStatement edgesPS = conn.prepareStatement("SELECT * from EDGE where FLOOR_ID = ?");
        s = conn.createStatement();

        HashMap<Integer, Building> buildings = new HashMap<>();
        HashMap<UUID, MapNode> mapNodes = new HashMap<>();
        HashMap<Integer, NodeEdge> nodeEdges = new HashMap<>();

        ResultSet rs = s.executeQuery("select * from BUILDING ORDER BY NAME DESC");

        while (rs.next()) {
            HashMap<String, Floor> flr = new HashMap();
            floorsPS.setInt(1,rs.getInt(1));
            ResultSet floorRS = floorsPS.executeQuery();
            while(floorRS.next()) {
                HashMap<UUID, MapNode> nodes = new HashMap<>();
                nodesPS.setString(1, floorRS.getString(1));
                ResultSet nodeRS = nodesPS.executeQuery();
                while(nodeRS.next()) {
                    System.out.println("Here I am");
                    nodes.put(UUID.fromString(nodeRS.getString(1)),
                            new MapNode(UUID.fromString(nodeRS.getString(1)),
                                    nodeRS.getDouble(2),
                                    nodeRS.getDouble(3),
                                    nodeRS.getInt(5)));
                    System.out.println("Here I am");
                    mapNodes.put(UUID.fromString(nodeRS.getString(1)),
                            new MapNode(UUID.fromString(nodeRS.getString(1)),
                                    nodeRS.getDouble(2),
                                    nodeRS.getDouble(3),
                                    nodeRS.getInt(5)));
                }

                HashMap<Integer, NodeEdge> edges = new HashMap<>();
                // select all edges that have floorID of current floor we are loading
                edgesPS.setString(1, floorRS.getString(1));
                ResultSet edgeRS = edgesPS.executeQuery();
                while(edgeRS.next()) {
                    System.out.println("Added Edge to" + floorRS.getString(1));
                    // stores nodeEdges per floor
                    edges.put(edgeRS.getInt(1),
                            new NodeEdge(mapNodes.get(UUID.fromString(edgeRS.getString(2))),
                                    mapNodes.get(UUID.fromString(edgeRS.getString(3))),
                                    edgeRS.getInt(4)));
                    // stores all nodeEdges
                    nodeEdges.put(edgeRS.getInt(1),
                            new NodeEdge(mapNodes.get(UUID.fromString(edgeRS.getString(2))),
                                    mapNodes.get(UUID.fromString(edgeRS.getString(3))),
                                    edgeRS.getInt(4)));
                }
                System.out.println(edges);
                System.out.println(nodeEdges);

                Floor tempFloor = new Floor(floorRS.getInt(3));
                // add floor to list of floors for current building
                flr.put(floorRS.getString(1), tempFloor);
                // add correct mapNodes to their respective floor
                for (MapNode n : nodes.values()) {
                    tempFloor.addNode(n);
                }
                // add correct nodeEdges to their respective floor
                for (NodeEdge e : edges.values()) {
                    tempFloor.addEdge(e);
                }

            }
            System.out.println(flr);
            buildings.put(rs.getInt(1),
                    new Building(rs.getString(2)));
            for (Floor f : flr.values()) {
                try {
                    buildings.get(rs.getInt(1)).addFloor(f);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
        }
        for (Building b : buildings.values()) {
            h.addBuilding(b);
        }

        this.mapNodes = mapNodes;

        this.edges = nodeEdges;

        System.out.println(mapNodes);
        System.out.println(h.getBuildings());

        // loading doctors, suites, and offices to hospital
        HashMap<UUID, Suite> suitesID = new HashMap<>();
        HashMap<String, Doctor> doctors = new HashMap<>();
        HashMap<String, Office> offices = new HashMap<>();
        PreparedStatement suiteDoc = conn.prepareStatement("select suite_id from USER1.SUITE_DOC where doc_id = ?");


        rs = s.executeQuery("select * from USER1.SUITE");
        while (rs.next()) {

            suitesID.put(UUID.fromString(rs.getString(1)),
                    new Suite(UUID.fromString(rs.getString(1)),
                            rs.getString(2)));

            h.addSuites(UUID.fromString(rs.getString(1)),
                    new Suite(UUID.fromString(rs.getString(1)),
                            rs.getString(2)));
        }
        this.suites = suitesID;
        System.out.println(suites.keySet());

        rs = s.executeQuery("select * from USER1.DOCTOR order by NAME");

        while(rs.next()) {
            HashSet<Suite> locations = new HashSet<>();
            suiteDoc.setString(1, rs.getString(1));
            ResultSet results = suiteDoc.executeQuery();
            while(results.next()) {
                locations.add(suitesID.get(results.getString(1)));
            }

            doctors.put(rs.getString(2),
                    new Doctor(UUID.fromString(rs.getString(1)),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getString(5),
                            locations));
            h.addDoctors(rs.getString(2),
                    new Doctor(UUID.fromString(rs.getString(1)),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getString(5),
                            locations));
        }
        this.doctors = doctors;
        System.out.println(doctors.keySet());

        rs = s.executeQuery("select * from OFFICES");
        while(rs.next()) {
            offices.put(rs.getString(2),
                    new Office(UUID.fromString(rs.getString(1)),
                            rs.getString(2),
                            suitesID.get(rs.getString(3))));
            h.addOffices(rs.getString(2),
                    new Office(UUID.fromString(rs.getString(1)),
                            rs.getString(2),
                            (suitesID.get(rs.getString(3)))));
        }
        this.offices = offices;
        System.out.println(offices);
        rs.close();

    }

    private void saveHospital(Hospital h) throws SQLException {
        PreparedStatement insertBuildings = conn.prepareStatement("INSERT INTO BUILDING (BUILDING_ID, NAME) VALUES (?, ?)");
        PreparedStatement insertFloors = conn.prepareStatement("INSERT INTO FLOOR (FLOOR_ID, BUILDING_ID, NUMBER) VALUES (?, ?, ?)");
        PreparedStatement insertNodes = conn.prepareStatement("INSERT INTO NODE (NODE_ID, POSX, POSY, FLOOR_ID, TYPE) VALUES (?, ?, ?, ?, ?)");
        PreparedStatement insertEdges = conn.prepareStatement("INSERT INTO EDGE (EDGE_ID, NODEA, NODEB, COST, FLOOR_ID) VALUES (?, ?, ?, ?, ?)");
        PreparedStatement insertDoctors = conn.prepareStatement("INSERT INTO USER1.DOCTOR (DOC_ID, NAME, DESCRIPTION, NUMBER, HOURS) VALUES (?, ?, ?, ?, ?)");
        PreparedStatement insertSuites = conn.prepareStatement("INSERT INTO USER1.SUITE (SUITE_ID, NAME, NODE_ID) VALUES (?, ?, ?)");
        PreparedStatement insertAssoc = conn.prepareStatement("INSERT INTO USER1.SUITE_DOC (SUITE_ID, DOC_ID) VALUES (?, ?)");
        PreparedStatement insertOffices = conn.prepareStatement("INSERT INTO USER1.OFFICES (OFFICE_ID, NAME, SUITE_ID) VALUES (?, ?, ?)");

        int counter = 1;

        // insert buildings into database
        for (Building b : h.getBuildings()) {
            insertBuildings.setInt(1, counter);
            insertBuildings.setString(2, b.getName());
            insertBuildings.executeUpdate();
            conn.commit();

            int edgesCount = 1;
            // insert floors into database
            for (Floor f : b.getFloors()) {
                String floorID = "" + b.getName() + Integer.toString(f.getFloorNumber()) + "";
                insertFloors.setString(1, floorID);
                insertFloors.setInt(2, counter);
                insertFloors.setInt(3, f.getFloorNumber());
                insertFloors.executeUpdate();
                conn.commit();

                // insert nodes into database
                for (MapNode n : f.getFloorNodes()) {
                    insertNodes.setString(1, n.getNodeID().toString());
                    insertNodes.setDouble(2, n.getPosX());
                    insertNodes.setDouble(3, n.getPosY());
                    insertNodes.setString(4, floorID);
                    insertNodes.setInt(5, n.getType());
                    insertNodes.executeUpdate();
                    conn.commit();
                }
                // insert edges into database
                for (NodeEdge edge : f.getFloorEdges()) {
                    insertEdges.setInt(1, edgesCount);
                    insertEdges.setString(2, edge.getSource().getNodeID().toString());
                    insertEdges.setString(3, edge.getOtherNode(edge.getSource()).getNodeID().toString());
                    insertEdges.setDouble(4, edge.getCost());
                    insertEdges.setString(5, floorID);
                    insertEdges.executeUpdate();
                    conn.commit();
                    edgesCount = edgesCount + 1;
                }
            }
            counter = counter + 1;
        }

        System.out.println("Here");
        // saves Suites
        for (Suite suite : h.getSuites().values()) {
            insertSuites.setString(1, suite.getSuiteID().toString());
            insertSuites.setString(2, suite.getName());
            insertSuites.setString(3, suite.getLocation().getNodeID().toString());
            insertSuites.executeUpdate();
            conn.commit();
        }
        // saves Doctors
        for (Doctor doc : h.getDoctors().values()) {
            System.out.println("Here");
            insertDoctors.setString(1, doc.getDocID().toString());
            insertDoctors.setString(2, doc.getName());
            insertDoctors.setString(3, doc.getDescription());
            insertDoctors.setString(4, "");
            insertDoctors.setString(5, doc.getHours());
            insertDoctors.executeUpdate();
            conn.commit();
            // saves Suite and Doctor Relationships
            for (Suite ste : doc.getSuites()) {
                insertAssoc.setString(1, ste.getSuiteID().toString());
                insertAssoc.setString(2, doc.getDocID().toString());
                System.out.println("Here");
                insertAssoc.executeUpdate();
                conn.commit();
                System.out.println("Added Suites");
            }
        }
        // saves Offices
        for (Office office : h.getOffices().values()) {
            insertOffices.setString(1, office.getId().toString());
            insertOffices.setString(2, office.getName());
            insertOffices.setString(3, office.getSuite().getSuiteID().toString());
            insertOffices.executeUpdate();
            conn.commit();
        }

    }

    public void executeStatements(String[] states) throws SQLException {
        Statement state = conn.createStatement();
        for (String s : states) {
            state.executeUpdate(s);
            conn.commit();
        }
        state.close();
    }

    protected DatabaseManager() throws SQLException
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
               conn = DriverManager.getConnection(protocol + dbName, props);
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
        loadData();
        //executeStatements(dropTables);
        //executeStatements(createTables);
    }

    public static DatabaseManager getInstance() {
        if(instance == null) {
            try
            {
                instance = new DatabaseManager();
            } catch (SQLException e)
            {
                e.printStackTrace();
            }
        }

        return instance;
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

    public void testDatabase() throws SQLException {

        s.execute("CREATE TABLE departments(Name VARCHAR(200), Floor INT, Room VARCHAR(200))");

        psInsert = conn.prepareStatement("insert into departments values (?, ?, ?)");
        statements.add(psInsert);

        psInsert.setString(1, "Addiction Recovery Program");
        psInsert.setInt(2, 2);
        psInsert.setString(3, "n/a");
        psInsert.executeUpdate();
        System.out.println("Good");

        // and add a few rows...

           /* It is recommended to use PreparedStatements when you are
            * repeating execution of an SQL statement. PreparedStatements also
            * allows you to parameterize variables. By using PreparedStatements
            * you may increase performance (because the Derby engine does not
            * have to recompile the SQL statement each time it is executed) and
            * improve security (because of Java type checking).
            */
        // parameter 1 is num (int), parameter 2 is addr (varchar)
        psInsert = conn.prepareStatement("insert into departments values (?, ?, ?)");
        statements.add(psInsert);

        psInsert.setString(1, "Allergy");
        psInsert.setInt(2, 4);
        psInsert.setString(3, "4G");
        psInsert.executeUpdate();
        System.out.println("Great");



            /*
            psInsert.setInt(1, 1910);
            psInsert.setString(2, "Union St.");
            psInsert.executeUpdate();
            System.out.println("Inserted 1910 Union");*/

            // Let's update some rows as well...


          /*  // parameter 1 and 3 are num (int), parameter 2 is addr (varchar)
            psUpdate = conn.prepareStatement("update location set num=?, addr=? where num=?");
            statements.add(psUpdate);*/

/*
            // delete the table
            s.execute("drop table location");
            System.out.println("Dropped table location");*/

            conn.commit();

    }
}
