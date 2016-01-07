package client;


import java.awt.Point;
import java.net.CacheResponse;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import client.controle.Console;
import logger.LoggerProjet;
import serveur.IArene;
import serveur.element.Caracteristique;
import serveur.element.Personnage;
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
	
	private static ArrayList<MemoirePersonnage> memoireClervoyance = new ArrayList<MemoirePersonnage>();
	
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
		
		// Nettoyage des entrées obsolètes dans le mémoire
		memoireClervoyance = MemoirePersonnage.nettoyerMemoire(memoireClervoyance, arene.getTour());
		
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
		
		// Si la vie du personage est en dessous de 15, on se met en PLS
		boolean enPLS = console.getPersonnage().getCaract(Caracteristique.VIE) < 15;
		
		/*
		 * Prise de décision
		 */
		
		// En cas d'absence de voisins
		if (voisins.isEmpty() || voisins.size() <= 0) {
			
			// Je me soigne jusqu'à être regénéré
			if (console.getPersonnage().getCaract(Caracteristique.VIE) < 100) {
				console.setPhrase("Je me soigne");
				arene.lanceAutoSoin(refRMI);
			
			// Puis je me déplace
			} else {
				console.setPhrase("J'erre...");
				arene.deplace(refRMI, 0);
			}
		
		// En cas de présence de voisins
		} else {
			// Réf et score du voisin le plus intéressant
			int refVois = 0;
			int scoreVois = -1000;
			
			int scoreCourant;
			
			// Calcul de la distance avec le voisin
			int distVois = Calculs.distanceChebyshev(position, arene.getPosition(refVois));
			
			// Analyse des voisins et recherche du voisin à cibler
			for (Map.Entry<Integer, Point> e : voisins.entrySet()) {
				
				// Si on est en PLS, on ne se déplacera pas plus loin que 5 cases pour boire une potion
				if (arene.estPotionFromRef(e.getKey()))
					scoreCourant = enPLS && distVois < 5 ? -1000 : calculScorePopo(e.getKey());
				
				// Si on est en PLS, on évite le combat avec les personnages...
				else if (arene.estPersonnageFromRef(e.getKey()))
					scoreCourant = enPLS ? -1000 : calculScorePerso(e.getKey());

				// ...et avec les minions.
				else if (arene.estMonstreFromRef(e.getKey()))
					scoreCourant = enPLS ? -1000 : calculScoreMinion(e.getKey());
				
				// On ignore les trucs inconnus
				else
					scoreCourant = -100;
				
				// Modificateur de distance
				scoreCourant -= distVois;
				
				// Si le voisin est une meilleure cible, on écrase l'ancien
				if (scoreCourant > scoreVois) {
					scoreVois = scoreCourant;
					refVois = e.getKey();
				}
			}
			
			
			// Pas de voisin intéressant...
			if (scoreVois <= 0) {
			
				// Liste des voisins à portée
				ArrayList<Integer> listeVoisinsProches = listerVoisinsAPortee(voisins);
				
				// Si on est dans le rayon d'action d'un seul personnage, on le tape.
				if (listeVoisinsProches.size() == 1) {
					arene.lanceAttaque(refRMI, Calculs.chercheElementProche(position, voisins));
				
				} else if (listeVoisinsProches.size() > 1) {
					// Si on est en présence de plusieurs adversaires, on cherche le point le plus safe pour fuir
					Point fuite = recherchePointSafe(listeVoisinsProches, position);
				
				} else {
					// Sinon, on erre
					console.setPhrase("Pas de voisin sympa (" + arene.nomFromRef(refVois) + "-" + refVois + " à " + scoreVois + ")");
					arene.deplace(refRMI, 0); 
				}
			
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
					// Sinon, on analyse la situation plus finement...
					console.setPhrase("Je vais vers mon voisin " + arene.nomFromRef(refVois) + "-" + refVois + " à " + scoreVois);
					arene.deplace(refRMI, refVois);
					
					// TODO : Déplacement vers perso
					/*
					 * Si target sur perso :
					 * 		Clervoyance si pas fait depuis 10 tours
					 * 		Dépacement vers lui sinon
					 * Fsi
					 * 
					 * Si perso à portée possible au prochain tour
					 * 		Si clervoyance pas faite depuis 5 tours
					 * 			Clervoyance
					 * 		Sinon si son initiative >= la notre OU sa force - notre défense > notre vie * 1.2
					 * 			Déplacment en bordure de sa zone d'action
					 * 		Sinon
					 * 			Déplacement au CàC
					 * Sinon
					 * 		Clervoyance
					 * Fsi
					 */
				}
			}
		}
	}
	

	/**
	 * Détermine un point safe où se déplacer.
	 * @param voisins La liste des voisins
	 * @return Les voisins qui peuvent nous attaquer.
	 */
	private Point recherchePointSafe(ArrayList<Integer> voisinsProches, Point positionActuelle) {
		// Génération de la liste des possibles
		ArrayList<Point> pointsProches = new ArrayList<Point>();
		int x;
		int y;
		
		// Parcours des cases possibles
		for (x=-1; x <= 1; x++) {
			for (y=-1; y <= 1; y++) {
				if (!(x == 0 && y == 0)) {
					
					// Vérification si la case est dans l'arène
					Point pointCourant = new Point((int)positionActuelle.getX() + x, (int)positionActuelle.getY() + y);
					
					if (Calculs.estDansArene(pointCourant)) {
						// Vérification si la case est dans l'aire d'action d'un voisin
						for (Integer i : voisinsProches) {
							
							try {
								if (Calculs.distanceChebyshev(pointCourant,console.getArene().getPosition(i)) > 3
										&& !pointsProches.contains(pointCourant))
									pointsProches.add(pointCourant);
							} catch (RemoteException e) {
								e.printStackTrace();
							}
						}
						
					}
				}
			}
		}
		
		if (pointsProches.equals(null));
		return null;
	}
	

	/**
	 * Détermine la liste des voisins en mesure de m'attaquer dans le tour.
	 * @param voisins La liste des voisins
	 * @return Les voisins qui peuvent nous attaquer.
	 */
	private ArrayList<Integer> listerVoisinsAPortee(HashMap<Integer, Point> voisins) {
		ArrayList<Integer> dangereux = new ArrayList<Integer>();
		
		for (Map.Entry<Integer, Point> e : voisins.entrySet()) {
			try {
				if (!console.getArene().estPotionFromRef(e.getKey())
						&& Calculs.distanceChebyshev(e.getValue(), console.getArene().getPosition(0)) <= 3) {
				
					dangereux.add(e.getKey());
				}
			} catch (RemoteException ex) {}
		}
		return dangereux;
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
			
			// Si le perso est vraiment mal, favorise encore les popo
			if (viePerso < 25)
				scorePopo *= 1.5;
			
			
		} catch (Exception e) {
			e.printStackTrace();
			scorePopo = -4000;
		}
		
		return scorePopo;
	}


	/**
	 * Commande le calcul du score d'un personnage.
	 * @param perso Le personnage dont on veut calculer le score
	 * @return Le score du personnage passée en paramètre
	 */
	private int calculScorePerso(int perso) {
		
		try {
			// Si on a déjà observé le personnage, on fait une analyse complexe
			if (MemoirePersonnage.dansMemoire(memoireClervoyance, perso)) {
				int depuisCelervoyance = console.getArene().getTour() - memoireClervoyance.get(perso).getTourClairvoyance();

				// On fait une moyenne pondérée avec une analyse complexe
				return calculScorePersoSimple(perso) * depuisCelervoyance/60 
						+ calculScorePersoComplexe(perso) * (60-depuisCelervoyance)/60;
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		}
			
		// Sinon, on fait une analyse simple
		return calculScorePersoSimple(perso);
	}
	

	/**
	 * Calcule le score d'un personnage en fonction de sa vie et de nos qualités.
	 * @param perso Le personnage dont on veut calculer le score
	 * @return Le score du personnage passée en paramètre, ou -5000 ou -4000 en cas de problème dans le calcul.
	 */
	private int calculScorePersoSimple(int perso) {
		
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
			
			// Récupération de la vie du voisin
			int vieVoisin = arene.caractFromRef(perso, Caracteristique.VIE);
			
			/*
			 * Calcul du score sans modificateurs
			 */
			
			// Nombre de coups pour tuer l'adversaire
			int meilleurNbCoups = 0;
			int pireNbCoups = 0;
			int vieVoisinTmp = vieVoisin;
			
			// Calcul du meilleur cas possible en combat (adversaire sans défenses et passif)
			do {
				vieVoisinTmp -= forceMonPerso;
				meilleurNbCoups ++;
			} while (vieVoisinTmp > 0);
			
			// Calcul du meilleur cas possible en combat (adversaire défendu qui se régen)
			vieVoisinTmp = vieVoisin;
			do {
				vieVoisinTmp -= forceMonPerso*0.5 - 2;
				pireNbCoups ++;
			} while (vieVoisinTmp > 0);
			
			// Score du personnage
			scorePerso = (-80*pireNbCoups+280)/2 + (-80*meilleurNbCoups+280)/2;
			
			/*
			 * Application des modificateurs
			 */
			
			// Bonus/Malus en fonction de la vie de l'adversaire
			if (vieVoisin > 75)
				scorePerso *= 0.7;
			else if (vieVoisin < 45)
				scorePerso *= 1.3;
			
			// Bonus/Malus en fonction de notre initiative
			if (initMonPerso > 165)
				scorePerso *= 1.1;
			else if (initMonPerso < 35)
				scorePerso *= 0.9;
			
			// Bonus/Malus en fonction de notre défense
			if (defenseMonPerso > 35)
				scorePerso *= 1.2;
			else if (initMonPerso < 15)
				scorePerso *= 0.8;
			
			// Si on est assez mal, on va éviter le combat
			if (vieMonPerso < 50)
				scorePerso -= (scorePerso / 3);
			
			// Si on perso est vraiment mal, on va éviter encore plus le combat
			if (vieMonPerso < 25)
				scorePerso -= (scorePerso / 2);
			
			
		} catch (Exception e) {
			e.printStackTrace();
			scorePerso = -4000;
		}
		
		return scorePerso;
	}
	
	
	/**
	 * Calcule le score d'un personnage en fonction de ses caractéristiques observées
	 * par clervoyance et de son score simple.
	 * @param perso Le personnage dont on veut calculer le score
	 * @return Le score du personnage passée en paramètre, ou -5000 ou -4000 en cas de problème dans le calcul.
	 */
	private int calculScorePersoComplexe(int perso) {

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
			
			// Récupération de la vie du voisin
			int vieVoisin = arene.caractFromRef(perso, Caracteristique.VIE);
			MemoirePersonnage souvenirClervoyance = MemoirePersonnage.getEntreeMemoire(memoireClervoyance, perso);
			
			/*
			 * Calcul du score sans modificateurs
			 */
			
			// Nombre de coups pour tuer l'adversaire
			int meilleurNbCoups = 0;
			int pireNbCoups = 0;
			int vieVoisinTmp = vieVoisin;
			int forceTmp = souvenirClervoyance.getForcePerso();
			
			// Calcul du meilleur cas possible en combat (adversaire sans défenses et passif)
			do {
				vieVoisinTmp -= forceMonPerso;
				meilleurNbCoups ++;
			} while (vieVoisinTmp > 0);
			
			// Calcul du meilleur cas possible en combat (adversaire défendu qui se régen)
			vieVoisinTmp = vieVoisin;
			do {
				vieVoisinTmp -= forceMonPerso*(1 - forceTmp/100) - 2;
				forceTmp = forceTmp < 10 ? 0 : forceTmp - 10;
				pireNbCoups ++;
			} while (vieVoisinTmp > 0);
			
			// Score du personnage
			scorePerso = (-80*pireNbCoups+280)/2 + (-80*meilleurNbCoups+280)/2;
			
			/*
			 * Application des modificateurs
			 */
			
			// Bonus/Malus en fonction de la vie de l'adversaire
			if (vieVoisin > 75)
				scorePerso *= 0.7;
			else if (vieVoisin < 45)
				scorePerso *= 1.3;
			
			// Bonus/Malus en fonction du ratio d'initiative
			scorePerso *= initMonPerso/souvenirClervoyance.getInitPerso() > 1 ? 1.1 : 0.9;

			// Bonus/Malus en fonction du ratio de force
			scorePerso *= forceMonPerso/souvenirClervoyance.getForcePerso() > 1 ? 1.15 : 0.95;
			
			// Bonus/Malus en fonction du ratio de défense
			scorePerso *= defenseMonPerso/souvenirClervoyance.getDefensePerso() > 1 ? 1.15 : 0.95;
			
			// Si on est assez mal, on va éviter le combat
			if (vieMonPerso < 50)
				scorePerso -= (scorePerso / 3);
			
			// Si on perso est vraiment mal, on va éviter encore plus le combat
			if (vieMonPerso < 25)
				scorePerso -= (scorePerso / 2);
			
			
		} catch (Exception e) {
			e.printStackTrace();
			scorePerso = -4000;
		}
		
		return scorePerso;
	}
	
	/**
	 * Calcule le score d'un monstre en fonction de ses caractéristiques et des notres.
	 * @param perso Le monstre dont on veut calculer le score
	 * @return Le score du monstre passée en paramètre, ou -5000 en cas de problème dans le calcul.
	 */
	private int calculScoreMinion(int minion) {

		// Score du minion
		int scoreMimi = -5000;
		
		try {
			// Récupérations des caractéristiques du personnage
			int vieMonPerso = console.getPersonnage().getCaract(Caracteristique.VIE);
			int forceMonPerso = console.getPersonnage().getCaract(Caracteristique.FORCE);
			
			// Score de base du minion
			scoreMimi = forceMonPerso - 10 > 0 ? 100: 10;
			
			/*
			 * Application des modificateurs
			 */
			
			// Bonus si on a besoin de force
			if (forceMonPerso < 60)
				scoreMimi *= 1.6;

			// Bonus/Malus en fonction de notre vie
			if (vieMonPerso > 75)
				scoreMimi *= 0.8;
			else if (vieMonPerso < 25)
				scoreMimi *= 0.2;
			else if (vieMonPerso > 40)
				scoreMimi *= 1.3;
			
			
		} catch (Exception e) {
			e.printStackTrace();
			scoreMimi = -4000;
		}
		
		return scoreMimi;
	}
}