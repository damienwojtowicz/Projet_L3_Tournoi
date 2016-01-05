package client;


import java.awt.Point;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;

import logger.LoggerProjet;
import serveur.IArene;
import serveur.element.Caracteristique;
import serveur.element.Element;
import serveur.element.Personnage;
import serveur.element.Potion;
import utilitaires.Calculs;
import utilitaires.Constantes;
import client.controle.Console;

/**
 * Strategie d'un personnage. 
 */
public class StrategiePersonnage {
	
	/**
	 * Console permettant d'ajouter une phrase et de recuperer le serveur 
	 * (l'arene).
	 */
	protected Console console;

	/**
	 * Cree un personnage, la console associe et sa strategie.
	 * @param ipArene ip de communication avec l'arene
	 * @param port port de communication avec l'arene
	 * @param ipConsole ip de la console du personnage
	 * @param nom nom du personnage
	 * @param groupe groupe d'etudiants du personnage
	 * @param nbTours nombre de tours pour ce personnage (si negatif, illimite)
	 * @param position position initiale du personnage dans l'arene
	 * @param logger gestionnaire de log
	 */
	public StrategiePersonnage(String ipArene, int port, String ipConsole, 
			String nom, String groupe, HashMap<Caracteristique, Integer> caracts,
			int nbTours, Point position, LoggerProjet logger) {
		
		logger.info("Lanceur", "Creation de la console...");
		
		try {
			console = new Console(ipArene, port, ipConsole, this, 
					new Personnage(nom, groupe, caracts), 
					nbTours, position, logger);
			logger.info("Lanceur", "Creation de la console reussie");
			
		} catch (Exception e) {
			logger.info("Personnage", "Erreur lors de la creation de la console : \n" + e.toString());
			e.printStackTrace();
		}
	}

	// TODO etablir une strategie afin d'evoluer dans l'arene de combat
	// une proposition de strategie (simple) est donnee ci-dessous
	/** 
	 * Decrit la strategie.
	 * Les methodes pour evoluer dans le jeu doivent etre les methodes RMI
	 * de Arene et de ConsolePersonnage. 
	 * @param voisins element voisins de cet element (elements qu'il voit)
	 * @throws RemoteException
	 */
	public void executeStrategie(HashMap<Integer, Point> voisins) throws RemoteException {
		/*// arene
		IArene arene = console.getArene();
		
		// reference RMI de l'element courant
		int refRMI = 0;
		
		// position de l'element courant
		Point position = null;
		
		try {
			refRMI = console.getRefRMI();
			position = arene.getPosition(refRMI);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		
		if (voisins.isEmpty()) { // je n'ai pas de voisins, j'erre
			console.setPhrase("J'erre...");
			arene.deplace(refRMI, 0); 
			
		} else {
			int refCible = Calculs.chercheElementProche(position, voisins);
			int distPlusProche = Calculs.distanceChebyshev(position, arene.getPosition(refCible));

			Element elemPlusProche = arene.elementFromRef(refCible);

			if(distPlusProche <= Constantes.DISTANCE_MIN_INTERACTION) { // si suffisamment proches
				// j'interagis directement
				if(elemPlusProche instanceof Potion) { // potion
					// ramassage
					console.setPhrase("Je ramasse une potion");
					arene.ramassePotion(refRMI, refCible);

				} else { // personnage
					// duel
					console.setPhrase("Je fais un duel avec " + elemPlusProche.getNom());
					arene.lanceAttaque(refRMI, refCible);
				}
				
			} else { // si voisins, mais plus eloignes
				// je vais vers le plus proche
				console.setPhrase("Je vais vers mon voisin " + elemPlusProche.getNom());
				arene.deplace(refRMI, refCible);
			}
		}*/
		
		stratDamien(voisins);
	}
	
	public void stratDamien(HashMap<Integer, Point> voisins) throws RemoteException {
		// arene
		IArene arene = console.getArene();
		
		// reference RMI de l'element courant
		int refRMI = 0;
		
		// position de l'element courant
		Point position = null;
		
		try {
			refRMI = console.getRefRMI();
			position = arene.getPosition(refRMI);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		
		if (voisins.isEmpty()) { // je n'ai pas de voisins, j'erre
			console.setPhrase("J'erre...");
			arene.deplace(refRMI, 0); 
			
		} else {
			
			// Réf et score de la popo la plus intéressante
			int refPopo = 0;
			int scorePopo = 0;
			
			// Recherche de la popo
			for (Map.Entry<Integer, Point> e : voisins.entrySet()) {
				Element elt = arene.elementFromRef(e.getKey());
				
				if (elt instanceof Potion) {
					int scoreCourant = calculScorePopo((Potion) elt, e.getKey(), e.getValue());
					
					// Si la potion est meilleure, on la remplace
					if (scoreCourant > scorePopo) {
						scorePopo = scoreCourant;
						refPopo = e.getKey();
					}
					
				}
			}
			
			if (scorePopo <= -200) { // Pas de popo intéressante, donc on erre...
				console.setPhrase("Pas de popo sympa (" + arene.elementFromRef(refPopo).getNom() + "-" + arene.elementFromRef(refPopo).getGroupe()  + " à " + scorePopo + ")");
				arene.deplace(refRMI, 0); 
			} else {
	
				// Calcul de la distance avec la potion
				int distPopo = Calculs.distanceChebyshev(position, arene.getPosition(refPopo));
				
				if(distPopo <= Constantes.DISTANCE_MIN_INTERACTION) {
					// La popo est à portée, donc on la boit
					console.setPhrase("Je bois ma potion cible !");
					arene.ramassePotion(refRMI, refPopo);
	
				} else { 
					// Sinon, on se déplace vers elle
					console.setPhrase("Je vais vers ma potion " + arene.elementFromRef(refPopo).getNom() + "-" + arene.elementFromRef(refPopo).getGroupe()  + " à " + scorePopo);
					arene.deplace(refRMI, refPopo);
				}
			}
		}
	}
	
	private int calculScorePopo(Potion popo, int refPopo, Point pt) {
		
		// Score de la potion
		int scorePopo = -5000;
		
		try {
			// Récupérations des caractéristiques du personnage
			int viePerso = console.getPersonnage().getCaract(Caracteristique.VIE);
			int forcePerso = console.getPersonnage().getCaract(Caracteristique.FORCE);
			int initPerso = console.getPersonnage().getCaract(Caracteristique.INITIATIVE);
			
			// Récupérations des caractéristiques de la popo
			int viePopo = popo.getCaract(Caracteristique.VIE);
			int forcePopo = popo.getCaract(Caracteristique.FORCE);
			int initPopo = popo.getCaract(Caracteristique.INITIATIVE) / 2;
			
			// Si la popo peut tuer, on ne la prend surtout pas !
			if (viePopo * -1 >= viePerso)
				return scorePopo;
			
			scorePopo = 0;

			// Ratio de combat du perso
			float ratio = forcePerso / initPerso;
			
			// Calcul du besoin en potion
			if (viePerso > 75) { 
				// Le perso est en bonne santé, donc
				// on met la régen est peu importante
				scorePopo = viePopo;
				
				// On choisit la meilleure en fonction du ratio de combat				
				if (ratio < 0.9) {	// Il y a plus d'initiative que de force...
					scorePopo += forcePopo * 2;
					scorePopo += initPopo;
					
				} else if (ratio < 1.2) { // Quantités de force et d'initiative équitables
					scorePopo += forcePopo;
					scorePopo += initPopo;
					
				} else { // Il y a plus d'initiative que de force...
					scorePopo += forcePopo;
					scorePopo += initPopo * 2;
				}
				
			} else if (viePerso > 40) {
				// Le perso est en santé moyenne, donc
				// la régen est assez importante
				scorePopo = viePopo * 2;
				
				// On choisit la meilleure en fonction du ratio de combat				
				if (ratio < 0.95) {	// Il y a plus d'initiative que de force...
					scorePopo += forcePopo ;
					scorePopo += initPopo < 0 ? initPopo : (initPopo / 2);
					
				} else if (ratio < 1.05) { // Quantités de force et d'initiative équitables
					scorePopo += forcePopo;
					scorePopo += initPopo;
					
				} else { // Il y a plus d'initiative que de force...
					scorePopo += forcePopo < 0 ? forcePopo : (forcePopo / 2);
					scorePopo += initPopo ;
				}
				
			} else { 
				// Le perso va mal, donc
				// on met la priorité sur la régen
				scorePopo = viePopo * 4;
				
				// On choisit la meilleure en fonction du ratio de combat				
				if (ratio < 0.95) {	// Il y a plus d'initiative que de force...
					scorePopo += forcePopo;
					scorePopo += initPopo < 0 ? initPopo : (initPopo / 2);
					
				} else if (ratio < 1.05) { // Quantités de force et d'initiative équitables
					scorePopo += forcePopo;
					scorePopo += initPopo;
					
				} else { // Il y a plus d'initiative que de force...
					scorePopo += forcePopo < 0 ? forcePopo : (forcePopo / 2);
					scorePopo += initPopo;
				}
			}
			
			
			// Récupération de la position du perso
			Point position = console.getArene().getPosition(console.getRefRMI());
			int distancePopo = Calculs.distanceChebyshev(position, console.getArene().getPosition(refPopo));
			
			// On soustrait la distance mise sur 100
			scorePopo -= distancePopo*10/3;
			
			
		} catch (Exception e) {
			e.printStackTrace();
			scorePopo = -4000;
		}
		
		return scorePopo;
	}
}
