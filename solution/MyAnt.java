/**
 * Class: MyAnt
 * Author: Matthew Dailey
 * 
 * This is a solution to the Addepar ant challenge.
 * 
 * The solution is based off of the method a hive of bees uses to coordinate gathering 
 * food. In a bee hive there are 3 roles. A bee either gathers food, scouts for food or
 * does the "waggle dance" to direct the scouts and gatherers. In this solution, each ant 
 * can either be a waggler (there is only 1), a scout or a gatherer.
 * 
 * Every ant starts as a scout then returns to the hive to either become the waggler or a
 * gatherer. The ant will have a randomized starting direction to explore then, after a
 * set number of moves, it will go to the closest unknown square on the map. Once exploring
 * a certain number of unknown squares, the ant will go to the nearest food item then 
 * return to the hive with it. This search pattern allows efficient harvesting of food
 * before the map is fully explored and gradual/effective exploration to occur simultaniously.
 * 
 * The waggler is responsible for maintaining an up-to-date map of the world by combining
 * knowledge from returning scouts and gatherers. It is also responsible for assigning jobs
 * to gatherers efficiently so there are no wasted trips to depleted food sources. This is 
 * done using methods from the Board class.
 * 
 * The gatherer is responsible for harvesting food. It follows orders from the waggler but
 * has the ability to find a new food source or become a scout if there is ever a miss
 * communication.
 * 
 * Each ant maintains a copy of its information about the world as an instance of the Board
 * class. Ants gain knowledge about the board by passing their boards to each other when
 * they have an opportunity to send messages. The Gson library is used to serialize
 * the Board.
 *
 **/

import java.util.Random;
import java.util.Stack;

import ants.*;
import com.google.gson.*;


public class MyAnt implements Ant{
	
	//how many steps a scout should explore deterministicly
	private final int SCOUT_DETERM = 3;
	//how many steps a scout should explore total
	private final int SCOUT_TIME = 10; 
	
	private Board map;		// Map of the game board.
	private Role role;		// Type of action the ant will do.
	private Stack<Direction> plan;  // List of directions to follow.
	private boolean holdingFood;	// true if the ant has food.
	private int scoutCount;			// The number of turns the ant has been scouting.
	private int scoutStartIndex;	// Where in the search order the specific ant starts.
	private Direction scoutLastDir; // The last direction the ant has travelled.
	
	/**
	 * MyAnt
	 * 
	 * Instantiate a new ant which starts as a scout with no food, no plan
	 * and a blank map. 
	 */
	public MyAnt(){
		this.map = new Board(); 
		this.scoutCount = 0;
		this.plan = new Stack<Direction>();
		this.holdingFood = false;
		this.role = Role.SCOUTING;
		
		// Set the scoutStartIndex randomly so different ants search in different orders.
		Random rand = new Random();
		this.scoutStartIndex = rand.nextInt(map.searchOrder.length);
	}

	/**
	 * getAction
	 * 
	 * Called each turn by the game to decide what action an ant will take.
	 * The ant first adds its surroundings to the map then chooses an action 
	 * based on its current role.
	 */
	public Action getAction(Surroundings surroundings){
		// update the map
		map.checkSurroundings(surroundings);
		
		// choose a move base on the ants role
		switch(this.role){
		case WAGGLING:
			return doWaggle(surroundings);
		case SCOUTING:
			return doScout(surroundings);
		case GATHERING:
			return doGather(surroundings);
		}
			
		return Action.HALT;
	}
	
	/**
	 * followPlan
	 * 
	 * @return the next move in the ants current plan and updates the map according
	 *  to the plan. Returns null if there is no planned move.
	 */
	private Action followPlan(){
		if( this.plan !=  null && !this.plan.isEmpty()){
			Direction move = this.plan.pop();
			map.updatePosition(move);
			return Action.move(move);
		}
		return null;
	}
	
	/**
	 * checkShouldWaggle
	 * 
	 * Determine if an ant's role should be to Waggle based on the surroundings
	 * and returns the appropriate action if it should Waggle. Returns null if
	 * the ant should not waggle.
	 * 
	 * To become waggler, the ant must be at the hive, have scouted and be the 
	 * only ant at the hive.
	 */
	private Action checkShouldWaggle(Surroundings surroundings){
		if(map.atHive() && this.scoutCount > 0 && 
				surroundings.getCurrentTile().getNumAnts() == 1){
			// change the role as necessary
			this.role = Role.WAGGLING;
			
			// if the ant came back with food, make sure to drop it off.
			if(this.holdingFood){
				this.holdingFood = false;
				return Action.DROP_OFF;
			}else
				return Action.HALT;
		}
		return null;
	}

	// Return a random viable direction for the ant to move.
	private Direction getRandomMove(){
		Direction move = randomDirection();
		while( !map.checkDirection(move) )
			move = randomDirection();
		return move;
	}
	
	// Return a random direction to generate random moves.
	private Direction randomDirection(){
		Random rand = new Random();
		switch( rand.nextInt(4)){
		case 0:
			return Direction.NORTH;
		case 1:
			return Direction.EAST;
		case 2:
			return Direction.WEST;
		case 3:
			return Direction.SOUTH;
		}
		return null; 
	}

	/**
	 * moveBySearchOrder
	 * 
	 * @return a valid move based on the search order. Ignore moving directly back to
	 * the last place the ant was. If there is no such move, return a random move.
	 * 
	 * This provides 4 different basic search strategies so that not all ants follow the
	 * same heuristic.
	 */
	private Direction moveBySearchOrder() {
		// Iterate through the searchOrder based on the random scoutStart index.
		for( int i = scoutStartIndex; i < map.searchOrder.length + scoutStartIndex; i++ ){
			Direction d = map.searchOrder[i%map.searchOrder.length];
			
			// Get the opposite of the last direction moved so we don't make that move.
			Direction opposite = null;
			if( this.scoutLastDir != null )
				opposite = map.oppositeDirection(this.scoutLastDir);
			
			// Check if we can move the suggested direction.
			if( map.checkDirection(d) && d != opposite)
				return d;
		}
		// We found no move, get a random one.
		return getRandomMove();
	}
	
	/**
	 * doGather
	 * 
	 * Returns the move if the ant is a gatherer. The ant will go to its target which 
	 * will either be a close peice of food or the hive.
	 */
	private Action doGather( Surroundings surroundings){
		// Check if the ant should start waggling instead of gathering.
		Action shouldWaggle = checkShouldWaggle(surroundings);
		if(shouldWaggle != null)
			return shouldWaggle;
		
		if( surroundings.getCurrentTile().getAmountOfFood()>0 &&
				!map.atHive() && !this.holdingFood){
			// If there is food to gather and the ant isn't holding any, gather.
			this.holdingFood = true;
			this.plan = map.RouteToHive();
			return Action.GATHER;
		} else if ( this.holdingFood && map.atHive() ){
			// If the ant is at the hive and has food, drop off.
			this.holdingFood = false;
			return Action.DROP_OFF;
		} else {
			// Otherwise, follow the plan.
			Action planned = followPlan();
			if( planned == null ){
				// If there is no plan, find some nearby food and make a plan.
				map.suggestFood();
				this.plan = map.RouteToTarget();
				map.cleanTarget();
				planned = followPlan();
				if(planned != null){
					return planned;
				}	else {
					// If there was still no viable food plan, become a scout.
					this.role = Role.SCOUTING;
					return doScout(surroundings);
				}
			} else {
				return planned;
			}
		}
		
	}
	
	/**
	 * doScout
	 *
	 * Returns the move based on the ant being a scout. The ant follows its randomized 
	 * start point in the search order for several steps then searches for the closest 
	 * unknown square on the map. This means ants will start search off randomly in 
	 * several directions then start expanding the known map.
	 * 
	 * When the ant is done scouting it will change to a gatherer and find the closest 
	 * peice of food.
	 */
	private Action doScout( Surroundings surroundings){
		// Check if the ant should become the waggler.
		Action shouldWaggle = checkShouldWaggle(surroundings);
		if(shouldWaggle != null)
			return shouldWaggle;
		
		// Try to follow the plan.
		Action planned = followPlan();

		if(planned == null){
			// There is no plan.
			scoutCount++;

			if( scoutCount > this.SCOUT_DETERM ){
				// The ant has scouted for a while so find unknown places.
				map.suggestScout();
				this.plan = map.RouteToTarget();
				map.cleanTarget();
				// follow the new scout plan.
				planned = followPlan();
				if(planned == null)
					return Action.HALT;
				
				if( scoutCount > this.SCOUT_TIME){
					// The ant has scouted for long enough so find a close food
					scoutCount = 1;
					map.suggestFood();
					plan = map.RouteToTarget();
					map.cleanTarget();
					this.role = Role.GATHERING;
				}
				
				return planned;
			} else {
				// The scout has only had a few moves, follow the search order.
				this.scoutLastDir = moveBySearchOrder();
				map.updatePosition(scoutLastDir);
				return Action.move(this.scoutLastDir);
			}
		} else {
			//There is a plan, follow it.
			return planned;
		}
	}

	/**
	 * doWaggle
	 * 
	 * Method to define the action of a ant which is waggling. It will
	 * sit still at the hive and wait for other ants to arrive and assign target food.
	 */
	private Action doWaggle( Surroundings surroundings){
		return Action.HALT;
	}
	

	
	// Wrapper for serializing the board
	private byte[] writeBoard(){
		Gson gson = new Gson();
		return gson.toJson(this.map).getBytes();
	}
	
	// Wrapper for deserializing the board
	private Board readBoard(byte[] b){
		Gson gson = new Gson();
		String json = new String(b);
		return gson.fromJson(json, Board.class);
	}

	/**
	 * send
	 * 
	 * If the ant is waggling, have it choose a target food for the communicating ant.
	 * Otherwise just share share the info about the board.
	 */
	public byte[] send(){
		
		// If waggling, find the nearest food, otherwise hide target to not direct 
		// the other ant.
		if(this.role == Role.WAGGLING){
			map.suggestFood();
		} else {
			map.cleanTarget();
		}
		
		byte[] b = writeBoard();

		// clean the target so we don't accidentally send two ants to the same place.
		if(this.role == Role.WAGGLING)
			map.cleanTarget();
		
		return b;
	}
	
	/**
	 * receive
	 * 
	 * If the ant is at the hive and not the waggler, get a new target and become
	 * a gatherer. Otherwise, if the ant is waggling or scouting add the other ants
	 * map knowledge to its own map.
	 */
	public void receive(byte[] data){
		Board new_board = readBoard(data);
		
		if( new_board.atHive() && this.role != Role.WAGGLING){
			this.role = Role.GATHERING;		
			this.map.combineBoards(new_board);
			this.plan = map.RouteToTarget();
			map.cleanTarget();
		} else if ( this.role == Role.WAGGLING || this.role == Role.SCOUTING){
			this.map.combineBoards(new_board);
		}
	}
	
	/**
	 * Enum to represent the possible roles of ants.
	 */
	private enum Role {
		GATHERING, SCOUTING, WAGGLING
	}

}





