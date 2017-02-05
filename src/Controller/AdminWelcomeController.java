package Controller;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;

import java.io.IOException;


public class AdminWelcomeController {
    @FXML
    Button btnBack;
    @FXML
    Button btnModifyDirectory;
    @FXML
    Button btnChangeFloorplan;

    Stage primaryStage;

    public AdminWelcomeController(){

    }

    public void setStage(Stage s)
    {
        primaryStage = s;
    }

    @FXML
    private void clickedBack() throws IOException
    {
        FXMLLoader loader;
        Parent root;

        loader = new FXMLLoader(getClass().getResource("../AdminLoginView.fxml"));

        root = loader.load();
        //create a new scene with root and set the stage
        Scene scene = new Scene(root);

        primaryStage.setScene(scene);

        AdminLoginController controller = loader.getController();
        controller.setStage(primaryStage);
    }
    @FXML
    private void clickedModifyDirectory() throws IOException
    {
        FXMLLoader loader;
        Parent root;

        loader = new FXMLLoader(getClass().getResource("../ChangingDirectoryView.fxml"));

        root = loader.load();
        //create a new scene with root and set the stage
        Scene scene = new Scene(root);

        primaryStage.setScene(scene);

        ChangingDirectoryController controller = loader.getController();
        controller.setStage(primaryStage);
    }
    @FXML
    private void clickedChangeFloorplan() throws IOException
    {
        FXMLLoader loader;
        Parent root;

        loader = new FXMLLoader(getClass().getResource("../modifyLocations.fxml"));

        root = loader.load();
        //create a new scene with root and set the stage
        Scene scene = new Scene(root);

        primaryStage.setScene(scene);

        ModifyLocations controller = loader.getController();
        controller.setStage(primaryStage);
        new ModifyLocations();
    }

    @FXML
    protected void initialize() {

    }
}
