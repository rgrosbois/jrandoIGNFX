package fr.rg.java.rando;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.fxml.FXMLLoader;

public class Main extends Application {
	// Sauvegarde du dernier répertoire de fichier KML consulté
	static final String KML_DIR_KEY = "last_used_dir";
	// Sauvegarde de la latitude
	static final String SAVED_LATITUDE_KEY = "saved_latitude";
	// Sauvegarde de la longitude
	static final String SAVED_LONGITUDE_KEY = "saved_longitude";
	// Sauvegarde de la clé IGN
	static final String IGNKEY_KEY = "ign_key";

	static final double DEFAULT_LATITUDE = 45.145f;
	static final double DEFAULT_LONGITUDE = 5.72f;
	static final String DEFAULT_IGNKEY = "ry9bshqmzmv1gao9srw610oq";

	@Override
	public void start(Stage primaryStage) {
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/res/Map_ihm.fxml"));
			BorderPane root = (BorderPane) loader.load();
			IGNMapController controleur = ((IGNMapController) loader.getController());
			controleur.setMainStage(primaryStage);
			Scene scene = new Scene(root);

			primaryStage.setScene(scene);
			primaryStage.setResizable(false);
			primaryStage.setTitle("Parcours de randonnées");
			primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {

				@Override
				public void handle(WindowEvent event) {
					controleur.closeConnections();
					System.exit(0);
				}
			});
			primaryStage.show();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		launch(args);
	}
}
