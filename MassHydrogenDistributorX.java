import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

//Progress notes: AKA changelog
/*
 * Currently (061416 12:46pm), the number of available sites open on a mof crystal has no bearing on how likely it is for a hydrogen to stick to that mof. That seems wrong.
 * Do we assign hydrogen first to crystals, or straight to sites? Which is better? UPDATE(061416 1:07pm): redid it, so that each mof gets 12 keys in the hashmap, each representing
 * an open site.
 * Note: (061516 1:54pm), should probably change NValues to only have 5 indices, for clarity. Doing it later though, there may be a couple of different places that change will have to be reflected.
 * 		in the meantime, it doesn't actually affect the results (it miiiiight run faster after the change, maybe?), so can be dealt with at leisure.
 * (061616 1:50pm): About to try modifying sizes of NValues, totalN, and metadd to size 5.
 * 	line 77, changed 12 to 5.
 *  line 116, changed 12 to 5.
 *  lines 16-20 of crystal.java changed to separate the initialization of sites and neighborhood.
 * (061616 2:00pm): tested 0,1000,11000,12000 values, everything seems to work, no exceptions coming up! Now to try implementing neighbor impact, where the probability of a hydrogen to stick to
 *  site A is affected by how many neighboring sites have already been filled. This might be a goddamn mess.
 *   Let each crystal site count how many filled neighbors it has. Then, based on that number, when we try to place a hydrogen at that site, there is a chance that we will reroll, in which case
 *    (072616) Made this version, MHD-X, to be more friendly to doing different MOFs. Stephen said that for most MOFs, we'd probably just have to change site number and
 *    number of neighbor sites (aka keysPerCrystal and nSpots, respectively). Haven't tested yet, since i'm running MHD nonstop atm.
 *    MofCrystalX must also be changed, both 2 variables and, manually, a couple of the methods.
 * 
 * 
 */

public class MassHydrogenDistributorX {

	/*
	 * Goal:
	 * We want to create a statistical model of x hkust crystals adsorbing y hydrogen molecules, and calculating and keeping track of how many hydrogen molecules
	 *  have 1,2,3,4,...,12 neighbors. No site can contain 2 hydrogen molecules, and if 2 molecules are neighbors to each other and no others, then that counts as 2 increments 
	 *  of the "how many hydrogens have 1 neighbor?" value. Currently, each site has equal probability, though that will hopefully change later on as this program evolves.

	 * To simulate the mofmass, we will create a hashmap whose keys represent the mof crystals' open sites. Each key will map to a crystal 
	 * object. For the mof crystals, we're going to create a new object class, which will store many of the old global variables from HydrogenPlacer.java, and will
	 * contain some methods such as picking a site and counting neighbors. What we can do is this: Instantiate a bunch of crystals, then assign 12 keys per crystal. We create y
	 * random numbers, each of which simulate a hydrogen sticking to a metal site, and for each random number that matches a key, we increment that key's mof's hydrogen counter, 
	 * which is the number of hydrogen molecules sticking to sites on that crystal. Then, remove that key from the list of available sites, because that site has already been picked. 
	 * Thus, a crystal with 12 open sites should be more likely to be hit than a crystal with only 1 open site left. This setup will also segue into accounting for energy preferences
	 *  nicely, in that we would, presumably, change how many keys there are, and then remove all keys of given crystal when that crystal's hydrogen counter hits 12.
	 * Then, essentially scavenge HydrogenPlacer's code and run it for each crystal separately, and then collate the meta-data amongst all mof crystals.
	 */
	
	// Global variables:
	public static int numberOfCrystals = 1000; // Number of mof cells/units/molecules
	public static int hydrogenNumbers = 10000; // how many hydrogen we're sticking to the sample, aka the H2 per Cu
	public static int keysPerCrystal = 12;	   // how many metal sites each mof has to start. Shenanigans will ensue once we start playing with temperature variance though.
	public static int trials = 1000;		   // how many times we run the program, the results of which are then averaged. Higher this is, the more accurate and slow the program becomes.
	public static ArrayList<Integer> NValues;  // stores the neighbor counts of the crystal currently being examined.
	public static ArrayList<MofCrystal> sample;// stores all of the crystal objects in a structure which we can iterate through easily.
	public static ArrayList<Long> totalN;      // stores the cumulative neighbor counts at the meta-level, so that we can calculate our final answer for each trial.
	public static int nSpots = 4; 			   //This is how many neighbor sites which each site has. For HKUST-1 it's 4.
	public static double temperature = 15; //In kelvin
	public static double delE = 30; //its the delta Energy value
	//public static double boltz = 1.380649*Math.pow(10, -23);

	/**
	 * pull a random non-null key from the targets hashmap
	 * @param bestSeats
	 * @return
	 */
	public static Integer pullR(HashMap<Integer,MofCrystal> openSeats){
		Iterator<Integer> itr = openSeats.keySet().iterator();
		Random rng = new Random();
		int stop = rng.nextInt(openSeats.size());
		int counter = 0;
		int nextK = 0;
		
		nextK = itr.next(); //guarantee that we always have a non-null value.
		
		while(itr.hasNext() && counter < stop){
			nextK = itr.next();
			counter++;
		}
		return nextK;
	}
	
	/**
	 * This method initializes the mofcrystal objects, and then randomly places
	 * hydrogen into their sites.
	 */
	public static void attempt(){
		/*
		 * Here, we're going to initialize the mofcrystals, and then run the filling method. 
		 */
		NValues = new ArrayList<Integer>();
		sample = new ArrayList<MofCrystal>();
		for(int c = 0; c < nSpots+1; c++){ 
			NValues.add(0);
		}
		//First, we want to know how many crystals which we're simulating, and then we're going to need to know (later) energy preferences. Until then, all crystals are 
		// created equal.
		HashMap<Integer, MofCrystal> targets = new HashMap<Integer, MofCrystal>();
		for(int x = 0; x < keysPerCrystal*numberOfCrystals; x=x+keysPerCrystal){	// This for loop creates all of the mofCrystals and puts them into the hashmap
			MofCrystal newMof = new MofCrystal();
			sample.add(newMof);
			for(int a = x; a < x+keysPerCrystal; a++){
				targets.put(a, newMof);	
			}
		}
		// Now that we've set up the crystals, we need next to distribute y hydrogens amongst them, with no more than 12 hydrogen per crystal.
		// We're playing with a moving target, unfortunately. If key 22 is taken, and our RNG gets 22 a second time, that number is invalid. There are several
		//ways of dealing with this. I'm going to use an iterator, and iterate through the list of available keys/sites z times, where z is the number pulled from the RNG.
		Random rng = new Random();
		for(int y = 0; y < hydrogenNumbers; y++){	
			int target = pullR(targets);
			//this is where we'd step in if we decide to reroll.
			//So, within each if case, roll random number, see if it's less than a certain number. If the number is a hit, use:
			//targets.get(target).incrementHydrogen(target); targets.remove(target)
			//If it's a miss, decrement y, and don't do the above. Nothing happens on a miss except that y decreases by 1.
			int fN = targets.get(target).getChance(target); //fN is "filledNeighbors".
			if(fN == 0){
				double currentRoll = rng.nextDouble();
				if(currentRoll <= Math.exp(-4*delE/(temperature))){
				//if(currentRoll <= 0.2){
					targets.get(target).incrementHydrogen(target); //This line tells the crystal that it received a hydrogen molecule.
					targets.remove(target); //This line removes the key so that we can't choose the same site twice. Since the iterator is based on size and not a static #, should be fine.
				}
				else{
					y--;
				}
			}
			else if(fN == 1){
				double currentRoll = rng.nextDouble();
				if(currentRoll <= Math.exp(-3*delE/(temperature))){
				//if(currentRoll <= 0.4){
					targets.get(target).incrementHydrogen(target); //This line tells the crystal that it received a hydrogen molecule.
					targets.remove(target); //This line removes the key so that we can't choose the same site twice. Since the iterator is based on size and not a static #, should be fine.
				}
				else{
					y--;
				}
			}
			else if(fN == 2){
				double currentRoll = rng.nextDouble();
				if(currentRoll <= Math.exp(-2*delE/(temperature))){
				//if(currentRoll <= 0.6){
					targets.get(target).incrementHydrogen(target); //This line tells the crystal that it received a hydrogen molecule.
					targets.remove(target); //This line removes the key so that we can't choose the same site twice. Since the iterator is based on size and not a static #, should be fine.
				}
				else{
					y--;
				}
			}
			else if(fN == 3){
				double currentRoll = rng.nextDouble();
				if(currentRoll <= Math.exp(-delE/(temperature))){
				//if(currentRoll <= 0.8){
					targets.get(target).incrementHydrogen(target); //This line tells the crystal that it received a hydrogen molecule.
					targets.remove(target); //This line removes the key so that we can't choose the same site twice. Since the iterator is based on size and not a static #, should be fine.
				}
				else{
					y--;
				}
			}
			else if(fN == 4){ //If all 4 NN are filled, then this will hit 100%, no rerolling.
				targets.get(target).incrementHydrogen(target); //This line tells the crystal that it received a hydrogen molecule.
				targets.remove(target); //This line removes the key so that we can't choose the same site twice. Since the iterator is based on size and not a static #, should be fine.
			}
			//targets.get(target).incrementHydrogen(target); //This line tells the crystal that it received a hydrogen molecule.
			//targets.remove(target); //This line removes the key so that we can't choose the same site twice. Since the iterator is based on size and not a static #, should be fine.

		}
		//At this point, we have instantiated all of the mof crystals, and stuck all of the hydrogen to them and their sites, such that the sites were chosen randomly and that
		//no site has more than 1 hydrogen stuck to it. Now, we just told the crystals which of their sites were taken, and now we just need to calculate the meta-data.
		//To do so, we need to go through all crystals, and then ask them to run findNeighbors() on each hydrogen they have by running getNeighborhood().
		for(int index = 0; index < sample.size(); index++){ //For each crystal in the sample:
			ArrayList<Integer> metadd = sample.get(index).getNeighborhood(); // get the array representing that crystal's neighborCounts
			for(int n = 0; n < metadd.size(); n++){ //For each element in the array:
				int base = NValues.get(n);			//find the old value of the meta data's count for the number of hydrogens with that many neighbors,
				NValues.set(n, base+metadd.get(n)); //and then set it to the sum of the old value and the new data from the current crystal.
			}
		}
	}
	
	public static void main(String[] args){
		/*
		 * each time we run attempt(), we create new NValues, crystal objects, etc, and we want to add the new NValues to the cumulative total, which we will then average after this loop.
		 */
		totalN = new ArrayList<Long>();
		for(int i = 0; i < nSpots+1; i++){ 
			totalN.add((long) 0);
		}
		for(int trial = 0; trial < trials; trial++){
			attempt();
			for(int index = 0; index < totalN.size(); index++){
				Long old = totalN.get(index);
				totalN.set(index, old+NValues.get(index));
			}
		}
		for(int h = 0; h < totalN.size(); h++){
			long avg = totalN.get(h)/trials;
			totalN.set(h, avg);
		}
		/*
		 * At this point, i believe that we've finished calculating!
		 */

		System.out.println(NValues);
	}
}
