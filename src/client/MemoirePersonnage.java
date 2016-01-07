package client;

import java.util.ArrayList;
import java.util.HashMap;

import serveur.element.Caracteristique;

/**
 * Unité de mémoire d'un personnage.
 */
public class MemoirePersonnage {
	
	int refPerso;
	int viePerso;
	int forcePerso;
	int initPerso;
	int defensePerso;
	int tourClairvoyance;
	
	
	public static boolean dansMemoire(ArrayList<MemoirePersonnage> memoire, int refPerso) {
		for (MemoirePersonnage m : memoire) {
			if (m.getRefPerso() == refPerso)
				return true;
		}
		return false;
	}
	
	
	public static ArrayList<MemoirePersonnage> nettoyerMemoire(ArrayList<MemoirePersonnage> memoire, int tourCourant) {
		if (memoire.equals(null))
			return null;
		
		for (MemoirePersonnage m : memoire) {
			if (m.getTourClairvoyance() < tourCourant - 60)
				memoire.remove(m);
		}
		return memoire;
	}
	
	public MemoirePersonnage(int refPerso, HashMap<Caracteristique,Integer> vision, int tourClairvoyance) {
		this.refPerso = refPerso;
		this.tourClairvoyance = tourClairvoyance;
		
		for (Caracteristique e : vision.keySet()) {
			switch (e) {
			case VIE:
				this.viePerso = vision.get(e);
				break;
			case FORCE:
				this.forcePerso = vision.get(e);
				break;
			case INITIATIVE:
				this.initPerso = vision.get(e);
				break;
			case DEFENSE:
				this.defensePerso = vision.get(e);
			default:
				break;
			}
		}
	}

	public int getRefPerso() {
		return refPerso;
	}

	public int getViePerso() {
		return viePerso;
	}

	public int getForcePerso() {
		return forcePerso;
	}

	public int getInitPerso() {
		return initPerso;
	}

	public int getDefensePerso() {
		return defensePerso;
	}

	public int getTourClairvoyance() {
		return tourClairvoyance;
	}
	
}
