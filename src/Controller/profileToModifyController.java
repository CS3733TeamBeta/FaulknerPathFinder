package Controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.ScrollPane;

public class profileToModifyController {
    @FXML
    Button logout, back;

    @FXML
    TextField searchModDoc;

    @FXML
    ScrollPane filteredProfiles;

    @FXML
    private void logoutHit(){
        Main.thisStage.setScene(Main.changingDirectoryView);
    }

    @FXML
    private void backHit(){
        Main.thisStage.setScene(Main.changingDirectoryView);
    }

}
