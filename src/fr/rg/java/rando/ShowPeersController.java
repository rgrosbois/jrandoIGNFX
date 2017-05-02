package fr.rg.java.rando;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.prefs.Preferences;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

public class ShowPeersController {
	@FXML
	ListView<String> peerLV;

	@FXML
	ListView<String> filesLV;

	@FXML
	TextArea infoTA;

	Stage peerStage;
	String netMsg = "";
	Socket commSocket;
	BufferedReader netIn;
	BufferedInputStream netInB;
	PrintWriter netOut;

	// Préférences utilisateur
	Preferences prefs = Preferences.userNodeForPackage(Main.class);

	/**
	 * Fenêtre où afficher les informations.
	 *
	 * @param stage
	 */
	void setPeerStage(Stage stage) {
		peerStage = stage;
	}

	/**
	 * Fournir la liste des pairs sur le réseau local.
	 *
	 * @param peerList
	 */
	void setPeerList(ObservableList<String> peerList) {
		peerLV.setItems(peerList);
	}

	/**
	 * Fermer les canaux de communication.
	 */
	public void closeTCPConnection() {
		try {
			if (netIn != null) {
				netIn.close();
			}
			if (netOut != null) {
				netOut.close();
			}
			if (commSocket != null) {
				commSocket.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * Ouvrir une connexion TCP avec un pair.
	 *
	 * @param host
	 *            IP du pair
	 */
	private void openTCPConnection(String host) {
		try {
			commSocket = new Socket(host, IGNMapController.MULTICAST_PORT);
			netIn = new BufferedReader(new InputStreamReader(commSocket.getInputStream()));
			netInB = new BufferedInputStream(commSocket.getInputStream());
			netOut = new PrintWriter(commSocket.getOutputStream(), true);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Finaliser l'initialisation de la fenêtre en surveillant les clics sur les
	 * items de la liste.
	 */
	@FXML
	void initialize() {
		// Gestion du clic sur les listes
		peerLV.setOnMouseClicked((event) -> {
			String host = peerLV.getSelectionModel().getSelectedItem();
			new Thread(new Task<Void>() {

				@Override
				protected Void call() throws Exception {
					connectToPeer(host, peerStage);
					return null;
				}
			}).start();
		});

		filesLV.setOnMouseClicked((event) -> {
			if (event.getClickCount() >= 2) { // Double clic
				String host = peerLV.getSelectionModel().getSelectedItem();
				String file = filesLV.getSelectionModel().getSelectedItem();

				new Thread(new Task<Void>() {

					@Override
					protected Void call() throws Exception {
						downloadFileFromPeer(host, file, peerStage);
						return null;
					}
				}).start();
			}
		});
	}

	/**
	 * Récupérer un fichier (KML) depuis le pair.
	 *
	 * @param host
	 *            Adresse IP du pair
	 * @param fileName
	 *            Nom du fichier à récupérer
	 * @param stage
	 *            Fenêtre principale
	 */
	private void downloadFileFromPeer(String host, String fileName, Stage stage) {
		String reponse;

		System.out.println("Télécharger " + fileName + " depuis " + host);
		// Connexion au pair
		try {
			openTCPConnection(host);

			// Demande du fichier
			netOut.println("GET FILE");
			netOut.println(fileName);

			// Récupérer la taille du fichier
			reponse = netIn.readLine();
			int fileSize = Integer.parseInt(reponse);
			System.out.println("Taille=" + fileSize);

			// Récupérer les octets du fichier et sauvegarder le fichier
			int bufSize = 1024;
			byte[] buf = new byte[bufSize];
			File newFile = new File(prefs.get(Main.KML_DIR_KEY, "/tmp"), fileName);
			FileOutputStream fileOut = new FileOutputStream(newFile);

			while (fileSize > 0) {
				if (fileSize >= bufSize) {
					System.out.println(" -> 1024");
					netInB.read(buf, 0, bufSize);
					fileOut.write(buf, 0, bufSize);
					fileSize -= bufSize;
				} else {
					System.out.println(" -> " + fileSize);
					netInB.read(buf, 0, fileSize);
					fileOut.write(buf, 0, fileSize);
					fileSize = 0;
				}
				System.out.println("FileSize=" + fileSize);
			}

			fileOut.flush();
			fileOut.close();
			System.out.println("Fichier " + newFile.getAbsolutePath() + " enregistré (" + newFile.length() + ")");

			reponse = netIn.readLine(); // END
			System.out.println("réponse = " + reponse);
			netOut.println("QUIT");

			closeTCPConnection();

			// Supprimer de la liste des nouveaux fichiers
			Platform.runLater(new Runnable() {

				@Override
				public void run() {
					System.out.println("Enlever " + fileName + " de la liste");
					filesLV.getItems().remove(fileName);
				}
			});

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * Connexion TCP avec un pair.
	 *
	 * @param host
	 *            adresse IP du pair
	 * @param lv
	 *            ListView pour afficher les résultats de communication.
	 */
	private void connectToPeer(String host, Stage stage) {
		String reponse;
		ArrayList<String> tmpList = new ArrayList<>();

		// Connexion au pair
		try {
			openTCPConnection(host);

			// +------------------------+
			// | Demande d'informations |
			// +------------------------+
			netOut.println("INFO");
			// Réception de la réponse terminant par END
			reponse = netIn.readLine();
			while (!"END".equals(reponse)) {
				netMsg += reponse + "\n";
				reponse = netIn.readLine();
			}

			Thread.sleep(100);

			// +------------------------+
			// | Liste de fichiers KML |
			// +------------------------+
			netOut.println("FILES");
			reponse = netIn.readLine();
			File localFile;
			while (!"END".equals(reponse)) { // réponse terminant par END
				// Ajouter seulement si le fichier n'existe pas en local
				localFile = new File(prefs.get(Main.KML_DIR_KEY, "/tmp"), reponse);
				if (!localFile.exists()) {
					tmpList.add(reponse);
				}

				reponse = netIn.readLine();
			}
			netOut.println("QUIT");

			closeTCPConnection();

		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}

		// Afficher le message reçu dans la fenêtre
		Platform.runLater(new Runnable() {

			@Override
			public void run() {
				infoTA.clear();
				infoTA.setText(netMsg);
				filesLV.setItems(FXCollections.observableArrayList(tmpList));
			}
		});
	}

}
