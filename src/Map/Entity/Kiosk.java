package Map.Entity;

import Map.Controller.Popover.KioskEditController;
import org.controlsfx.control.PopOver;

import java.util.UUID;

/**
 * Created by Samuel on 2/26/2017.
 */
public class Kiosk extends MapNode{
    Character direction;

    String name = "Kiosk";

    UUID kioskID;

    private final String popOverEditFXML = "/Admin/Popup/KioskEditPopup.fxml";

    public Kiosk () {this.kioskID = UUID.randomUUID();}

    public Kiosk (String name) {this.name = name;}

    public Kiosk (Character direction) {this.direction = direction;}

    public Kiosk (UUID uuid, MapNode m, String name, Character direction)
    {
        this.kioskID = uuid;
        this.name = name;
        this.direction = direction;


        this.setPosX(m.getPosX());
        this.setPosY(m.getPosY());

        this.setType(m.getType());


    }

    @Override
    public PopOver getEditPopover() {
        KioskEditController controller = new KioskEditController(this);
        return getPopOver(controller, popOverEditFXML);
    }


    @Override
    public void setType(NodeType type)
    {
        super.setType(type);
    }

    @Override
    public String toString()
    {
        return this.name;
    }

    public UUID getKioskID() {
        return this.kioskID;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String s) {
        this.name = s;
    }

    public Character getDirection() { return this.direction; }

    @Override
    public String getLabel()
    {
        return name;
    }


}
