package Domain.ViewElements;

import Domain.Map.Destination;
import Domain.Map.MapNode;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.scene.layout.AnchorPane;

import java.io.IOException;

/**
 *
 */
public class DragIcon extends AnchorPane{
	
	@FXML AnchorPane root_pane;

	private DragIconType mType = null;

	public DragIcon() {

		FXMLLoader fxmlLoader = new FXMLLoader(
				getClass().getResource("/Admin/MapBuilder/DragIcon.fxml")
				);
		
		fxmlLoader.setRoot(this); 
		fxmlLoader.setController(this);
		
		try { 
			fxmlLoader.load();
        
		} catch (IOException exception) {
		    throw new RuntimeException(exception);
		}
	}
	
	@FXML
	private void initialize() {

	}

	/**
	 * Relocates the drag icon to a specific point
	 * @param p point to relocate to
	 */
	public void relocateToPoint (Point2D p) {

		//relocates the object to a point that has been converted to
		//scene coordinates
		Point2D localCoords = getParent().sceneToLocal(p);
		
		relocate ( 
				(localCoords.getX()),
				(localCoords.getY())
			);
	}

	/**
	 *
	 * @return the type of this drag icon
	 */
	public DragIconType getType () { return mType; }

	/**
	 * Sets the type of this drag icon, changes picture accordingly
	 * @param type of drag icon
	 */
	public void setType (DragIconType type) {
		
		mType = type;
		
		getStyleClass().clear();
		getStyleClass().add("dragicon");
		
		switch (mType) {

			case Restroom:
				getStyleClass().add("bathroom");
				break;

			case Store:
				getStyleClass().add("store");
				break;

			case Elevator:
				getStyleClass().add("elevator");
				break;

			case Department:
				getStyleClass().add("doctor");
				break;

			case Food:
				getStyleClass().add("food");
				break;

			case Info:
				getStyleClass().add("info");
				break;

			case Connector:
				getStyleClass().add("connector");
				break;

			default:
				break;
		}
	}

	public static MapNode constructMapNodeFromType(DragIconType type)
	{
		MapNode newNode;

		switch (type)
		{
			case Elevator:
			{
				newNode = new MapNode();
				newNode.setIsElevator(true);
				break;
			}

			case Connector:
			{
				newNode = new MapNode();
				break;
			}

			default:
			{
				newNode = new Destination();
				newNode.setType(type);
				((Destination)newNode).setName(type.name());

				break;
			}

		}

		return newNode;
	}


}
