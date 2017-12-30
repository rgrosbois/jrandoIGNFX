package fr.rg.java.rando;

import java.util.prefs.Preferences;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

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

	static final String APP_TITLE = "Parcours de randonnées";

	// Préférences utilisateur
	Preferences prefs = Preferences.userNodeForPackage(Main.class);

	Stage mainStage; // Fenêtre principale

	/**
	 * Dimensionner et créer la fenêtre principale puis lancer les services réseau.
	 */
	@Override
	public void start(Stage primaryStage) {
		mainStage = primaryStage;

		// Charger la scène et configurer la fenêtre
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/res/Main_ihm.fxml"));
			Parent root = loader.load();
			Scene scene = new Scene(root);
			primaryStage.setScene(scene);
			//primaryStage.setResizable(false);
			//primaryStage.setFullScreen(true);
			primaryStage.setTitle(APP_TITLE);
			primaryStage.show();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void updateTitle(String subTitle) {

	}

	public static void main(String[] args) {
		launch(args);
	}
}
