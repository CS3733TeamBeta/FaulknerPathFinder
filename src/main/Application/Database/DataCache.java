package main.Application.Database;

import javafx.collections.ListChangeListener;
import main.Directory.Entity.Doctor;
import main.Map.Entity.Hospital;

import java.sql.SQLException;

/**
 * Created by benhylak on 2/25/17.
 *
 * This is where all of the tracking changes and updating database should go. When it
 * creates the hospital, it should be able to subscribe to all of the changes in all of the floors
 * and just like ya, everything. If you want to be cool anyway...
 */
public class DataCache
{
    static DataCache dC =null;
    DatabaseManager db;

    Hospital h = null;

    protected DataCache()
    {
        try
        {
            db = new DatabaseManager();
        } catch (SQLException e)
        {
            e.printStackTrace();
        }
    }

    public static DataCache getInstance()
    {
        if(dC == null)
        {
            dC = new DataCache();
        }

        return dC;
    }

    public Hospital getHospital()
    {
        try
        {
           if(h==null) h = db.loadData();

            h.getDoctors().addListener((ListChangeListener<Doctor>) e->
            {
                if(e.wasAdded())
                {
                    System.out.println("Added!*******");
                    for(Doctor d: e.getAddedSubList())
                    {
                        try
                        {
                            db.addDocToDB(d);
                        } catch (SQLException e1)
                        {
                            e1.printStackTrace();
                        }
                    }
                } else if (e.wasRemoved()) {

                    System.out.println("Removed!**********");
                    for (Doctor d: e.getRemoved()) {
                        try
                        {
                            db.delDocFromDB(d);
                        } catch (SQLException e1)
                        {
                            e1.printStackTrace();
                        }
                    }
                }
            });

        } catch (SQLException e)
        {
            e.printStackTrace();
        }

        return h;
    }

    public void save()
    {
        try
        {
            db.saveData(h);
        } catch (SQLException e)
        {
            e.printStackTrace();
        }
    }

    public DatabaseManager getDbManager()
    {
        return db;
    }
}
