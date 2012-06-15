/**
 * Class: Board
 * Author: Matthew Dailey
 * 
 * The Board represents an individual ants knowledge about the game and
 * is used to pass that information between ants.
 * 
 * The board is represented as a 2d array of bytes. Each square is marked
 * either as the hive, as a wall or as unknown. If it is known and not one
 * of those three, its value is the amount of food it has. The problem with
 * this representation is picking the correct square to put the hive from the 
 * beginning when ants do not know the whole map. To solve this I set the
 * hive at index [0][0] in the array and used modular arithmatic on the 
 * coordinates relative to the hive to find the correct index into the array.
 *
 * For example, the relative coordinate (1 west, 5 north) would be (-1,-5) = 
 * (19,19) mod 20 so would be at the index [19][19]. 
 * 
 * Note: x coordinate refer to east-west where east is positive and y 
 * coordinates refer to north-south where south is positive.
 * 
 * The map provides a number of useful methods to the ant:
 * 	- finding a nearby unknown location to scout (suggestScout)
 *  - finding a nearby food to gather (suggestFood)
 *  - find the shortest path between two points on the map, to the hive
 *  	or to the set target.
 */

import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.Stack;

import ants.*;

public class Board {
	final private int WIDTH = 20; //Size of the board.
	final private byte HIVE = -1; //Mark board index as hive
	final private byte WALL = -2; //Mark board index as wall
	final private byte UNKWOWN = -3; //Mark board index as unexplored
	
	private byte[][] board; //Map the game
	// set search order so all directional searches are conducted in order.
	public final Direction[] searchOrder = {Direction.NORTH, Direction.EAST, 
			Direction.SOUTH, Direction.WEST};
	private int currX; // The ant's current relative east-west position.
	private int currY; // The ant's current relative north-south position.
	private int targetX; // The ant's target east-west position.
	private int targetY; // The ant's target north-south position.
	
	/**
	 * Board
	 * 
	 * Instantiate a new board object with the ant at the origin and
	 * the entire board unknown.
	 */
	public Board(){
		// Instatiate the board array and set all values to unknown
		this.board = new byte[WIDTH][WIDTH];
		for( int y = 0 ; y < this.board.length; y++ ){
			for( int x = 0; x < this.board[0].length; x++ ){
				board[y][x] = this.UNKWOWN;
			}
		}
		// The board is always created on spawn so the ant starts at (0,0).
		this.currX = 0;
		this.currY = 0;
		this.board[0][0] = this.HIVE;
	}

	// Convert position relative to hive to index into the board array.
	private int convertToIndex( int rel ){
		return (this.WIDTH + rel)%this.WIDTH;
	}
	
	// View surrounding squares and update the board
	public void checkSurroundings(Surroundings surroundings) {
	    for ( Direction d : searchOrder ){
	    	// For each direction, get the tile and index, then update.
			Tile t = surroundings.getTile(d);
			HashMap<String,Integer> coords = convertDirection( this.currX, this.currY, d);
			updateIndex( coords.get("x"), coords.get("y"), t);
		}
	}
	
	/**
	 * Helper function to convert a coordinate and a direction to an new coordinate.
	 * 
	 * Returns a hash map from coordinate title as a string -> coordinate value.
	 */
	private HashMap<String, Integer> convertDirection( int x, int y, Direction d){
		HashMap<String, Integer> coords = new HashMap<String,Integer>();
		// Check the direction and execute the resulting case.
		switch( d ){
		case NORTH:
			y--;
			break;
		case EAST:
			x++;
			break;
		case SOUTH:
			y++;
			break;
		case WEST:
			x--;
			break;
		}
		// Output the new coordinates.
		coords.put("y", y);
		coords.put("x", x);
		return coords;
	}
	
	// Returns opposite of input direction.
	public Direction oppositeDirection(Direction d){
		switch( d ){
		case NORTH:
			return Direction.SOUTH;
		case EAST:
			return Direction.WEST;
		case SOUTH:
			return Direction.NORTH;
		case WEST:
			return Direction.EAST;
		}
		return null;
	}
	
	// Returns true if the ant can move in the input direction.  
	public boolean checkDirection(Direction d){
		HashMap<String,Integer> coords = convertDirection( this.currX, this.currY, d);
		//Check if the board value is a known or the hive, then can move there.
		if( board[convertToIndex(coords.get("y"))][convertToIndex(coords.get("x"))] > -2)
			return true;
		return false;
	}
	
	/**
	 * updateIndex
	 * @param x : x value of tile to update
	 * @param y : y value of tile to update
	 * @param t : content of the tile.
	 * 
	 * Updates the value in the board array based on the input tile.
	 */
	private void updateIndex(int x, int y,Tile t){
		 byte val = board[convertToIndex(y)][convertToIndex(x)];
		 if( val != this.HIVE){
			 board[convertToIndex(y)][convertToIndex(x)] = tileType(t);
		 }
	}
	
	/**
	 * tileType
	 * 
	 * @param tile : tile to examine
	 * @return the board value of that tile. Either amount of food,
	 * wall, unknown or hive.
	 */
	private byte tileType(Tile tile){
		if(!tile.isTravelable()){
			// If we can't travel there, it must be wall.
			return this.WALL;
		} else {
			// If we can, put the amount of food.
			return (byte)tile.getAmountOfFood();
		}
	}
	
	// Update an ant's position on the board base on an input movement direction.
	public void updatePosition(Direction d){
		HashMap<String,Integer> coords = convertDirection( this.currX, this.currY, d);
		this.currY = coords.get("y");
		this.currX = coords.get("x");
	}

	/**
	 * combineBoards
	 *
	 * @param new_board : input board whose information should be combined with
	 * the ant's knowledge.
	 * 
	 * Iterate over the board array. If a square in our board is unknown or has 
	 * more food, update our board. I choose to take the lower food value to prevent 
	 * wasted work because an extra trip to an empty food is generally more 
	 * damaging than going to a slightly further food which is guaranteed to have 
	 * food.
	 */
	public void combineBoards(Board new_board){
		this.targetX = new_board.targetX;
		this.targetY = new_board.targetY;
		
		for( int y = 0 ; y < this.board.length; y++ ){
			for( int x = 0; x < this.board[0].length; x++ ){
				// Iterate over the board.
				if( this.board[y][x] == this.UNKWOWN || 
						( this.board[y][x] > 0 && new_board.board[y][x] > -1 && 
						  this.board[y][x] > new_board.board[y][x] ) )
					// our board is unknown or our board has some food and the new board
					// is at least known and has fewer food.
					this.board[y][x] = new_board.board[y][x];
			}
		}
	}
	
	/**
	 * Route
	 * 
	 * Implementation of Dijkstra's algorithm on the game board.
	 * 
	 * @param x_init - starting east-west position
	 * @param y_init - starting north-south position
	 * @param x_final - ending east-west position
	 * @param y_final - ending north-south position
	 * Note: all are positions relative to the hive.
	 * 
	 * @return a stack of Directions such that if an ant pops a direction and moves
	 * that way every turn, it will go from its start to end position the fastest
	 * way known.
	 */
	private Stack<Direction> Route(int x_init, int y_init, int x_final, int y_final){
		// Convert from relative position to array indices
		x_init = convertToIndex(x_init);
		x_final = convertToIndex(x_final);
		y_init = convertToIndex(y_init);
		y_final = convertToIndex(y_final);
		
		// If start and are the same, return an empty stack.
		if(x_init == x_final && y_init == y_final)
			return new Stack<Direction>();
		
		// Build the vertex graph and assign distances
		Vertex[][] vertexMap = computeDistances(x_init,y_init);
		
		// Get the start and end vertices and make sure they are both known.
		Vertex start = vertexMap[y_init][x_init];
		Vertex end = vertexMap[y_final][x_final];
		if(start == null || end == null )
			return null;
		
		
		Stack<Direction> path = new Stack<Direction>();
		// We add directions to the stack from end to start, updating end.
		while(end != start){
			// Protect against an error in the distance computation.
			if( end == null )
				return null;
			// Add the direction to get to end.
			path.push(oppositeDirection(end.pred));
			// Compute the new end and update.
			HashMap<String,Integer> coord = convertDirection(end.x,end.y,end.pred);
			end = vertexMap[convertToIndex(coord.get("y"))][convertToIndex(coord.get("x"))];
		}
		return path;
	}
	
	// Helper debugging function to examine an ant's knowledge of the world.
	public void printBoard(){
		for( int y = 0 ; y < WIDTH; y++ ){
			for( int x = 0; x < WIDTH; x++ ){
				if(this.board[y][x] == this.UNKWOWN ){
					System.out.print("?");
				} else if(this.board[y][x] == this.WALL){
					System.out.print("X");
				} else if(x == 0 && y == 0){
					System.out.print("O");
				}else{
					System.out.print(" ");
				}
			}
			System.out.print("\n");
		}
	}
	
	// Return true if the ant is at the hive.
	public boolean atHive(){
		return (this.currX == 0 && this.currY == 0);
	}
	
	// Wrapper function for a route to hive.
	public Stack<Direction> RouteToHive(){
		return Route(this.currX, this.currY, 0, 0);
	}
	
	// Wrapper function for a route to the ant's target.
	public Stack<Direction> RouteToTarget(){
		return Route(this.currX, this.currY, this.targetX, this.targetY);
	}
	
	/**
	 * computeDistances
	 * 
	 * Method to compute the number of turns it will take to get to a
	 * square so ant's can find the closest unknown or food square. It
	 * includes only known, travellable squares as vertices.
	 * 
	 * @param x_init - starting east-west position.
	 * @param y_init - starting north-south position.
	 * Note: these are relative to the hive
	 * 
	 * @return a 2d array of Vertices each with it's distance from the 
	 * input position and it's predicessor in the path to it of that 
	 * length.
	 * 
	 */
	public Vertex[][] computeDistances(int x_init, int y_init){
		// Vertex priority queue is sorted by distance.
		PriorityQueue<Vertex> pq = new PriorityQueue<Vertex>();
		// Vertex table to return.
		Vertex[][] vertexMap = new Vertex[WIDTH][WIDTH];
		
		// Convert from relative coordinates to array indices.
		x_init = convertToIndex(x_init);
		y_init = convertToIndex(y_init);
		
		
		for( int y = 0 ; y < vertexMap.length; y++ ){
			for( int x = 0; x < vertexMap[0].length; x++ ){
				// Iterate over all vertices.
				if(this.board[y][x] == this.UNKWOWN || this.board[y][x] == this.WALL){
					// The terran in that square is unknown or untravellable
					// so we do not want a vertex representing it.
					vertexMap[y][x] = null;
				} else {
					// The vertex is travellable so add a vertex.
					vertexMap[y][x] = new Vertex(x,y);
					
					if(x == x_init && y == y_init)
						// This is the start so set the dist to 0.
						vertexMap[y][x].dist = 0;
					if(this.board[y][x]>0)
						// The vertex has food so update food.
						vertexMap[y][x].food = this.board[y][x];
					
					pq.add(vertexMap[y][x]);
				}
			}
		}
		
		// Now we will compute distances, this is normally part of the graph
		// initialization in Dijkstra's.
		while( !pq.isEmpty() ){
			// Get the closest vertex.
			Vertex v = pq.poll();
			
			for( Direction d : this.searchOrder){
				// Iterate over closest vertex's neighbors
				HashMap<String,Integer> coord = convertDirection(v.x,v.y,d);
				int x = convertToIndex(coord.get("x"));
				int y = convertToIndex(coord.get("y"));
				
				if(vertexMap[y][x] != null && v.dist+1 < vertexMap[y][x].dist){
					// If there is a vertex there and it is still far away
					// we remove from the priority queue and update its distance
					// then reinsert.
					pq.remove(vertexMap[y][x]);
					vertexMap[y][x].dist = v.dist+1;
					vertexMap[y][x].pred = oppositeDirection(d);
					pq.add(vertexMap[y][x]);
				}
			}
		}
		return vertexMap;
	}

	/**
	 * suggestFood
	 * 
	 * Sets an ant's map's target to be the nearest peice of food to that ant.
	 * Sets to the hive if there is no known food.
	 * 
	 * This is only called when an ant plans on going to food itself or is the
	 * waggler and is assigning a duty to a gatherer. In either case, there will
	 * be a food item gathered from that square and so we decrement the food to
	 * maintain an accurate record of the map and prevent wasted work by 
	 * gatherers.
	 */
	public void suggestFood(){
		// Compute distances of all vertices.
		Vertex[][] vertexMap = computeDistances(this.currX,this.currY);
		// Current known closest vertex with food.
		Vertex minVertex = null; 
		
		for(int y = 0; y < vertexMap.length; y++){
			for(int x = 0; x < vertexMap[0].length; x++){
				// Iterate over all vertices, updating the closest vertex as necessary.
				if( vertexMap[y][x] != null &&  vertexMap[y][x].food > 0 &&
					(minVertex == null || vertexMap[y][x].compareTo(minVertex) < 0)  )
					//update minVertex to the closest vertex with food. 
					minVertex = vertexMap[y][x];
			}
		}
		
		// We now know the min vertex.
		if( minVertex != null ){
			// If there is a known closest vertex with food, update target.
			this.targetX = minVertex.x;
			this.targetY = minVertex.y;
			// Update the amount of food on the target square since some ant
			// must go gather.
			this.board[this.targetY][this.targetX]--;
		}
	}
	
	/**
	 * suggestScout
	 * 
	 * Set the board's target to the nearest unknown square to the ant. This
	 * is used to optimize scouting of new territory.
	 * 
	 * If there is none, the target will be unchanged and remain the hive.
	 * 
	 */
	public void suggestScout(){
		// Compute the distances of vertices from the current location.
		Vertex[][] vertexMap = computeDistances(this.currX,this.currY);
		// Vertex representing the nearest known
		Vertex minVertex = null; 
		
		for(int y = 0; y < vertexMap.length; y++){
			for(int x = 0; x < vertexMap[0].length; x++){
				// Iterate through all vertices.
				if( vertexMap[y][x] != null &&  hasUnknownNeighbor(x,y) &&
					(minVertex == null || vertexMap[y][x].compareTo(minVertex) < 0)  )
					// update minVertex if there is a close vertex with an unknown neighbor.
					minVertex = vertexMap[y][x];
			}
		}
		
		if( minVertex != null ){
			// There exists a unknown vertex, go to it.
			this.targetX = minVertex.x;
			this.targetY = minVertex.y;
		} else {
			// There is none, go to the hive.
			this.cleanTarget();
		}
	}
	
	// Determine if a square has unknown neighbor.
	private boolean hasUnknownNeighbor(int x, int y){
		for( Direction d : this.searchOrder){
			// Iterate over neighboring directions, get coords and check the board.
			HashMap<String, Integer> coords = this.convertDirection(x, y, d);
			if(this.board[convertToIndex(coords.get("y"))][convertToIndex(coords.get("x"))] 
			                                               == this.UNKWOWN)
				return true;
		}
		return false;
	}
	
	// Sets the map target back to the hive.
	public void cleanTarget(){
		this.targetX = 0;
		this.targetY = 0;
	}
	
}
