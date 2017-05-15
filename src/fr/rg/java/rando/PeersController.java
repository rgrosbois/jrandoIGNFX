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

import fr.rg.java.rando.util.Peer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

public class PeersController {
	@FXML
	ListView<String> filesLV;

	@FXML
	TableView<Peer> pairTV;

	@FXML
	TableColumn<Peer, String> colonneAdresse;

	@FXML
	TableColumn<Peer, Integer> colonneTTL;

	@FXML
	TextArea infoTA;

	String lastHostIP = "";
	String netMsg = "";
	Socket commSocket;
	BufferedReader netIn;
	BufferedInputStream netInB;
	PrintWriter netOut;

	// Préférences utilisateur
	Preferences prefs = Preferences.userNodeForPackage(Main.class);

	Main main;

	void setMainInstance(Main m) {
		main = m;
	}

	/**
	 * Fournir la liste des pairs sur le réseau local.
	 *
	 * @param peerList
	 */
	void setPeerList(ObservableList<Peer> peerList) {
		pairTV.setItems(peerList);
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
			commSocket = new Socket(host, Peer.MULTICAST_PORT);
			netIn = new BufferedReader(new InputStreamReader(commSocket.getInputStream()));
			netInB = new BufferedInputStream(commSocket.getInputStream());
			netOut = new PrintWriter(commSocket.getOutputStream(), true);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Finaliser l'initialisation de la fenêtre en surveillant les clics sur les
	 * items des 2 listes.
	 */
	@FXML
	void initialize() {
		// Finaliser l'initialisation du tableau
		colonneAdresse.setCellValueFactory(new PropertyValueFactory<>("ipAddress"));
		colonneTTL.setCellValueFactory(new PropertyValueFactory<>("ttl"));

		// Clic sur un pair -> Récupération de ses informations
		pairTV.setOnMouseClicked((event) -> {
			if (event.getClickCount() >= 2) { // Double clic
				lastHostIP = pairTV.getSelectionModel().getSelectedItem().getIpAddress();
				new Thread(new Task<Void>() {

					@Override
					protected Void call() throws Exception {
						connectToPeer(lastHostIP, (Stage) infoTA.getScene().getWindow());
						return null;
					}
				}).start();
			}
		});

		// Clic sur un fichier KML -> lancement du téléchargement
		filesLV.setOnMouseClicked((event) -> {
			if (event.getClickCount() >= 2) { // Double clic
				Peer p = pairTV.getSelectionModel().getSelectedItem();
				String host = (p==null) ? lastHostIP : p.getIpAddress();
				String file = filesLV.getSelectionModel().getSelectedItem();

				new Thread(new Task<Void>() {

					@Override
					protected Void call() throws Exception {
						// Télécharger le fichier depuis le pair
						String reponse;

						// Connexion au pair
						try {
							openTCPConnection(host);

							// Demande du fichier
							netOut.println("GET FILE");
							netOut.println(file);

							// Récupérer la taille du fichier
							reponse = netIn.readLine();
							int fileSize = Integer.parseInt(reponse);

							// Récupérer les octets du fichier et sauvegarder le
							// fichier
							byte[] buf = new byte[fileSize];
							File newFile = new File(prefs.get(Main.KML_DIR_KEY, "/tmp"), file);
							int bytesRead = netInB.read(buf, 0, fileSize);
							int current = bytesRead;
							while (current < fileSize) {
								bytesRead = netInB.read(buf, current, fileSize - current);
								if (bytesRead >= 0) {
									current += bytesRead;
								}
							}
							// Sauver le fichier
							FileOutputStream fileOut = new FileOutputStream(newFile);
							fileOut.write(buf, 0, fileSize);
							fileOut.flush();
							fileOut.close();

							reponse = netIn.readLine(); // END
							netOut.println("QUIT");

							closeTCPConnection();

							// Supprimer de la liste des nouveaux fichiers et
							// ouvrir la trace
							// sur la carte
							Platform.runLater(new Runnable() {

								@Override
								public void run() {
									// Suppression du fichier de la liste
									filesLV.getItems().remove(file);
									// Ouvrir la trace dans l'application
									main.loadLocalKMLTrack(newFile);
								}
							});

						} catch (IOException e) {
							e.printStackTrace();
						}

						return null;
					}
				}).start();
			}
		});
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
