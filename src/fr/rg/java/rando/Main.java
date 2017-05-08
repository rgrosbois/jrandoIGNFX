package fr.rg.java.rando;

import java.awt.MouseInfo;
import java.awt.Point;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

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
		// Identifier la taille de l'écran courant afin de dimensionner
		// la fenêtre et calculer le nombre de tuiles nécessaire
		Point p = MouseInfo.getPointerInfo().getLocation();
		Screen currentScreen = Screen.getPrimary();
		for (Screen s : Screen.getScreens()) {
			if (s.getBounds().contains(p.x, p.y)) {
				currentScreen = s;
			}
		}
		Rectangle2D screenBounds = currentScreen.getBounds();
		double width = screenBounds.getWidth();
		double height = screenBounds.getHeight();
		int stageWidth = (int) (13 * width / 15);
		int stageHeight = (int) (13 * height / 15);
		int numTileX = (int) Math.floor(width / IGNMapController.TILE_PIXEL_DIM);
		numTileX = 2 * numTileX + 1;
		int numTileY = (int) Math.floor(height / IGNMapController.TILE_PIXEL_DIM);
		numTileY = 2 * numTileY + 1;

		// Charger la scène et configurer la fenêtre
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/res/Map_ihm.fxml"));
			Parent root = loader.load();
			IGNMapController controleur = ((IGNMapController) loader.getController());
			controleur.setMainStage(primaryStage);
			controleur.setTilesNumber(numTileX, numTileY);
			Scene scene = new Scene(root);

			primaryStage.setScene(scene);
			primaryStage.setResizable(false);
			primaryStage.setX(screenBounds.getMinX() + width / 15);
			primaryStage.setY(screenBounds.getMinY() + height / 15);
			primaryStage.setWidth(stageWidth);
			primaryStage.setHeight(stageHeight);

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
