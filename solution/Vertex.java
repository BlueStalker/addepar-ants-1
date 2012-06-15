/**
 * Class: Vertex
 * Author: Matthew Dailey
 * 
 * This class is used to represent a graph of the game map. The graph is used
 * to compute distances on the map and run Dijkstra's shortest path algorithm.
 * 
 * It is comparable to allow sorting based on distance from the start node of 
 * Dijkstra's. Distance is set by the algorithm using the vertex and initialized
 * to infinity.
 */

import ants.*;

public class Vertex implements Comparable<Object>{
	Direction pred; // The predicessor in a bredth first search for Dijkstra's. 
	int dist; 		// The distance from a target square.
	int x;			// The x coordinate of the square it represents.
	int y;			// The y coordinate of the square it represents.
	int food;		// The amount of food on the square.
	
	public Vertex(int X, int Y){
		this.food = 0; // Assume no food.
		this.x = X; 
		this.y = Y;
		this.dist = Integer.MAX_VALUE / 2; // Set the distance to approx infty.
		this.pred = null; // No predicessor t
	}

	/**
	 * Vertices are compared by distance from the start node of the running
	 * algorithm and set during the run-time of the algorithm.
	 * 
	 * Returns positive if compared to a closer node, negative if compared
	 * to a further node and 0 if equa-distant.
	 */
	@Override
	public int compareTo(Object v) throws ClassCastException {
		if(!(v instanceof Vertex))
			throw new ClassCastException("Compare expects another vertex");

		return this.dist - ((Vertex)v).dist;
	}
	
}
