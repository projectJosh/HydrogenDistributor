import java.util.ArrayList;

public class MofCrystalX {
	
	//Variables, of which most will be counters:
	public int numberOfHydrogen = 0;
	public int totalSites = 12;
	public int totalN = 4;
	public ArrayList<Integer> sites;
	public ArrayList<Integer> neighborhood;
	//First thing to create is the constructor:
	public MofCrystalX(){
		//We'll add in stuff as we realize we need them
		sites = new ArrayList<Integer>();
		neighborhood = new ArrayList<Integer>();
		for(int s = 0; s < totalSites; s++){
			sites.add(0);
			//neighborhood.add(0);
		}
		for(int r = 0; r < totalN+1; r++){ 
			neighborhood.add(0);	//
		}
	}
	
	/**
	 * this method adds a hydrogen to this crystal.
	 */
	public void incrementHydrogen(int x){
		numberOfHydrogen++;
		int oldV = sites.get(x%totalSites);
		sites.set(x%totalSites, 1+oldV);
	}
	
	/**
	 * This method returns the number of hydrogen sticking to this crystal.
	 * @return
	 */
	public int howMany(){
		return numberOfHydrogen;
	}
	
	//Next, we need to collect the mofdata and return it so that MHD can figure out the meta-data. For this mof, the data we need is this: how many hydrogen had 1,2,3,etc 
	//neighbors? We could create a method to count those neighbors, then return the results (the NValues) as an arrayList, and essentially add directly to the MHD's running total.
	/**
	 * This method counts how many neighbors the "source" hydrogen has, and then
	 * increments the index of 'neighborhood' of how many neighbors it has by 1.
	 * So, if this hydrogen has 2 neighbors, it will increment the index2 value of
	 * 'neighborhood' by 1. Needs to be changed for different MOFs.
	 * @param source
	 */
	public void findNeighbors(int source){	//TODO
		int neighbors = 0;
		for(int x = 0; x < sites.size(); x++){
			int other = sites.get(x);
			if(other > 0 && x!=source && Math.abs(x-source)<=2){
				neighbors++;
			}
			if(other > 0 && x!=source && Math.abs(x-source)>=10){
				neighbors++;
			}
		}
		int oldN = neighborhood.get(neighbors);
		neighborhood.set(neighbors, oldN+1);
	}
	
	/**
	 * Given a site number from targets, prior to mod12-ing, this method returns the number of neighbor sites which are already 
	 * filled. Needs to be changed for different MOFs.
	 * @param baseSite
	 * @return
	 */
	public int getChance(int baseSite){ //TODO
		int site = baseSite%totalSites;
		int filledN = 0;
		for(int x = 0; x < sites.size(); x++){
			int other = sites.get(x);
			if(other > 0 && x!= site && Math.abs(x-site)<=2){
				filledN++;
			}
			if(other > 0 && x!= site && Math.abs(x-site)>=10){
				filledN++;
			}
		}
		return filledN;
	}
	
	/**
	 * Run through all sites which have a hydrogen, and then run findNeighbors using that index as a parameter.
	 * @return
	 */
	public ArrayList<Integer> getNeighborhood(){
		for(int z = 0; z < sites.size(); z++){
			if(sites.get(z)>0){
				findNeighbors(z);
			}
		}
		return neighborhood;
	}
}
