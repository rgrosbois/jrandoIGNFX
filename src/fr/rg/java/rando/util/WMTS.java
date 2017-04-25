package fr.rg.java.rando.util;

import java.awt.Point;

import javafx.geometry.Point2D;

/**
 * <p>
 * Classe contenant les outils de conversion entre coordonnées GPS (latitude,
 * longitude) et coordonnées et tuiles WMTS.
 * </p>
 *
 * <p>
 * La projection Mercator est utilisée pour représenter la Terre sur une
 * planisphère: elle conserve les angles mais pas les distances ni les surfaces
 * (distances et surfaces apparaissent plus grandes au niveau des pôles) : C'est
 * pourquoi le Groenland peut apparaître aussi grand que l'Afrique alors qu'il
 * est 14 fois plus petit.
 * </p>
 */
public class WMTS {

	// Rayon de la Terre (en m)
	private static final double RAYON_TERRE = 6_378_137;
	// Abscisse minimale des longitudes dans la projection Mercator (i.e. la
	// longitude la plus à l'ouest=-180 degrés)
	private final static int LONG_XMIN_MERCARTOR = -(int) (RAYON_TERRE * Math.PI);
	// Ordonnée minimale des latitudes dans la projection Mercator
	private final static int LAT_YMIN_MERCARTOR = LONG_XMIN_MERCARTOR;

	/**
	 * Distance (en mètres) couverte par une tuile.
	 *
	 * @param zoom
	 *            Niveau de zoom correspondant
	 */
	public static double getTileDim(int zoom) {
		return (2 * Math.PI * RAYON_TERRE / (1 << zoom));
	}

	/**
	 * Calcule les indices de ligne et colonne de la tuile contenant la
	 * géolocalisation donnée pour le niveau spécifié.
	 *
	 * @param longitude
	 *            Longitude de la géolocalisation (en degrés)
	 * @param latitude
	 *            Latitude de la géolocalisation (en degrés)
	 * @param zoom
	 *            niveau de zoom IGN
	 * @return Indices de colonne (x) et de ligne (y)
	 */
	public static Point getTileIndex(double longitude, double latitude, int zoom) {
		Point2D p = getWmtsDim(latitude, longitude).multiply(1 / getTileDim(zoom));
		return new Point((int) p.getX(), (int) p.getY());
	}

	/**
	 * Calcule les coordonnées WMTS d'une géolocalisation donnée.
	 *
	 * @param latitude
	 *            en degrés
	 * @param longitude
	 *            en degrés
	 * @return coordonnées en mètres
	 */
	public static Point2D getWmtsDim(double latitude, double longitude) {
		double x = RAYON_TERRE * Math.toRadians(longitude) - LONG_XMIN_MERCARTOR;
		double y = -RAYON_TERRE * Math.log(Math.tan(Math.toRadians(latitude) / 2 + Math.PI / 4)) - LAT_YMIN_MERCARTOR;
		return new Point2D(x, y);
	}

	/**
	 * Calculer la latitude et la longitude d'une position WMTS.
	 *
	 * @param p
	 *            abscisse et ordonnée WMTS (en mètres)
	 * @return longitude (x) et latitude (y) de la position.
	 */
	public static GeoLocation getGeolocation(Point2D p) {
		return new GeoLocation(Math.toDegrees((p.getX() + LONG_XMIN_MERCARTOR) / RAYON_TERRE), Math
				.toDegrees(2 * (Math.atan(Math.exp((-p.getY() - LAT_YMIN_MERCARTOR) / RAYON_TERRE)) - Math.PI / 4)));
	}

}
