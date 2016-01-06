package client;


import java.awt.Point;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;

import client.controle.Console;
import logger.LoggerProjet;
import serveur.IArene;
import serveur.element.Caracteristique;
import serveur.element.Element;
import serveur.element.Personnage;
import serveur.element.Potion;
import utilitaires.Calculs;
import utilitaires.Constantes;

/**
 * Strategie d'un personnage. 
 */
public class StrategiePersonnage {
	
	/**
	 * Console permettant d'ajouter une phrase et de recuperer le serveur 
	 * (l'arene).
	 */
	protected Console console;
	
	protected StrategiePersonnage(LoggerProjet logger){
		logger.info("Lanceur", "Creation de la console...");
	}

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
		this(logger);

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


	/** 
	 * Decrit la strategie.
	 * Les methodes pour evoluer dans le jeu doivent etre les methodes RMI
	 * de Arene et de ConsolePersonnage. 
	 * @param voisins element voisins de cet element (elements qu'il voit)
	 * @throws RemoteException
	 */
	public void executeStrategie(HashMap<Integer, Point> voisins) throws RemoteException {
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
			
			// Réf et score du voisin le plus intéressant
			int refVois = 0;
			int scoreVois = 0;
			int scoreCourant;
			
			// Calcul de la distance avec le voisin
			int distVois = Calculs.distanceChebyshev(position, arene.getPosition(refVois));
			
			// Recherche du voisin
			for (Map.Entry<Integer, Point> e : voisins.entrySet()) {
				
				if (arene.estPotionFromRef(e.getKey()))
					scoreCourant = calculScorePopo(e.getKey());
				
				else if (arene.estPersonnageFromRef(e.getKey()))
					scoreCourant = calculScorePerso(e.getKey());
				
				else
					scoreCourant = 0;
				
				// Modificateur de distance
				scoreCourant -= distVois;
				
				// Si le voisin est meilleur, on le remplace
				if (scoreCourant > scoreVois) {
					scoreVois = scoreCourant;
					refVois = e.getKey();
				}
			}
			
			if (voisins.size() == 0) { // Pas de voisins...
				console.setPhrase("J'attend...)");
				arene.deplace(refRMI, 0);
				
			} if (scoreVois <= -200) { // Pas de voisin intéressant, donc on erre...
				console.setPhrase("Pas de voisin sympa (" + arene.nomFromRef(refVois) + "-" + refVois + " à " + scoreVois + ")");
				arene.deplace(refRMI, 0); 
			
			} else {
				
				if(distVois <= Constantes.DISTANCE_MIN_INTERACTION) { // Le voisin est à portée...
					
					if (arene.estPotionFromRef(refVois)) {
						// Si c'est une popo, on la boit !
						console.setPhrase("Je bois ma potion cible !");
						arene.ramassePotion(refRMI, refVois);
					
					} else if (arene.estPersonnageFromRef(refVois)) {
						// Si c'est un perso, on attaque !
						console.setPhrase("En garde, " + arene.nomFromRef(refVois) + "-" + refVois + " !");
						arene.lanceAttaque(refRMI, refVois);
						
					} else {
						// Sinon, on se plaint...
						console.setPhrase("M'aurait-on menti ?");
					}
					
				} else { 
					// Sinon, on se déplace vers lui
					console.setPhrase("Je vais vers mon voisin " + arene.nomFromRef(refVois) + "-" + refVois + " à " + scoreVois);
					arene.deplace(refRMI, refVois);
				}
			}
		}
	}
	
	
	/**
	 * Calcule le score d'une potion en fonction de ses caractéristiques et de l'état du personnage courant,
	 * princialement celui de sa vie. Plus le score sera élevé, plus la potion sera intéressante. Ne prend pas 
	 * en compte la distance entre le personnage et la potion.
	 * @param popo La potion dont on veut calculer le score
	 * @return Le score de la potion passée en paramètre, ou -5000 ou -4000 en cas de problème dans le calcul.
	 */
	private int calculScorePopo(int popo) {

		// Score de la potion
		int scorePopo = -5000;
		
		try {
			// Récupération de l'arène
			IArene arene = console.getArene();
			
			// Récupérations des caractéristiques du personnage
			int viePerso = console.getPersonnage().getCaract(Caracteristique.VIE);
			int forcePerso = console.getPersonnage().getCaract(Caracteristique.FORCE);
			int initPerso = console.getPersonnage().getCaract(Caracteristique.INITIATIVE);
			int defensePerso = console.getPersonnage().getCaract(Caracteristique.DEFENSE);
			
			// Récupérations des caractéristiques de la popo
			int viePopo = arene.caractFromRef(popo, Caracteristique.VIE);
			int forcePopo = arene.caractFromRef(popo, Caracteristique.FORCE);
			int initPopo = arene.caractFromRef(popo, Caracteristique.INITIATIVE);
			int defensePopo = arene.caractFromRef(popo, Caracteristique.DEFENSE);
			
			// Si la popo peut tuer, on ne la prend surtout pas !
			if (viePopo * -1 >= viePerso)
				return scorePopo;
			
			// Si la popo est une potion de TP, on lui donne une valeur correcte mais sans plus.
			if (forcePopo == initPopo && initPopo == defensePopo 
					&& defensePopo == viePopo && viePopo == 0)
				return 30;
			
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
					
				} else { // Il y a plus de force que d'initiative...
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
					
				} else { // Il y a plus de force que d'initiative...
					scorePopo += forcePopo < 0 ? forcePopo : (forcePopo / 2);
					scorePopo += initPopo ;
				}
				
			} else { 
				// Le perso va mal, donc
				// on met la priorité sur la régen
				scorePopo = (viePopo <= 0) ? -100 : (viePopo * 4);
				
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
			
			// Si la défense est faible, on favorise la défense
			if (defensePerso <= 20)
				scorePopo += defensePopo * 3;
			else
				scorePopo += defensePopo * 2;
			
			// Si le perso est assez mal, on va favoriser les popo
			if (viePerso < 50)
				scorePopo *= 1.5;
			
			// Si le perso est vraiment mal, on ne se bat pas
			if (viePerso < 25)
				scorePopo *= 1.5;
			
			
		} catch (Exception e) {
			e.printStackTrace();
			scorePopo = -4000;
		}
		
		return scorePopo;
	}


	/**
	 * Calcule le score d'un personnage en fonction de ses caractéristiques et de l'état du personnage courant,
	 * princialement celui de sa vie. Plus le score sera élevé, plus le personnage sera intéressant à attaquer. 
	 * Ne prend pas en compte la distance entre le personnage et nous.
	 * @param perso Le personnage dont on veut calculer le score
	 * @return Le score du personnage passée en paramètre, ou -5000 ou -4000 en cas de problème dans le calcul.
	 */
	private int calculScorePerso(int perso) {

		// TODO : Faire le calcul !!
		
		// Score du personnage
		int scorePerso = -5000;
		
		try {
			// Récupération de l'arène
			IArene arene = console.getArene();
			
			// Récupérations des caractéristiques du personnage
			int vieMonPerso = console.getPersonnage().getCaract(Caracteristique.VIE);
			int forceMonPerso = console.getPersonnage().getCaract(Caracteristique.FORCE);
			int initMonPerso = console.getPersonnage().getCaract(Caracteristique.INITIATIVE);
			int defenseMonPerso = console.getPersonnage().getCaract(Caracteristique.DEFENSE);
			
			// Récupérations des caractéristiques de la popo
			int viePerso = arene.caractFromRef(perso, Caracteristique.VIE);
			int forcePerso = arene.caractFromRef(perso, Caracteristique.FORCE);
			int initPerso = arene.caractFromRef(perso, Caracteristique.INITIATIVE) / 2;
			
			
			if (vieMonPerso > 75) { 
				
			} else if (vieMonPerso > 40) {
				
			} else { 
				
			}
			
			// Si le perso est assez mal, on va favoriser les popo
			if (vieMonPerso < 50)
				scorePerso -= (scorePerso / 2);
			
			// Si le perso est vraiment mal, on ne se bat pas
			if (vieMonPerso < 25)
				scorePerso -= (scorePerso / 2);
			
			
		} catch (Exception e) {
			e.printStackTrace();
			scorePerso = -4000;
		}
		
		return scorePerso;
	}
}