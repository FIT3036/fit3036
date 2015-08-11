package pathtest;

import java.util.Set;
import pathtest.util.HashSet;
import java.awt.geom.Point2D;
import java.lang.IllegalArgumentException;



public class Navmesh {

	public static final double NODE_SNAP_DIST = 1.0;
	
	
	public class Node extends Point2D.Double {
		private static final long serialVersionUID = 1L;
		
		public Node(double x, double y) {
			super(x,y);
		}
		
	}
	
	//possible gotcha: my edges are undirectional, i.e. p1 -> p2 == p2 -> 1.
	public class Edge {
	    Node node1;
	    Node node2;

	    private Set<Cell> cells;
	    
	    public Edge(Node node1, Node node2) {
	    	this.node1 = node1;
	    	this.node2 = node2;
	    }
	    
	    @Override
	    public boolean equals(Object o) {
	        if (o == null) return false;
	        if (!(o instanceof Edge)) return false;
	        Edge otherEdge = (Edge)o;
	        return (
	        	   (otherEdge.firstNode().equals(this.firstNode()))
	        	&& (otherEdge.secondNode().equals(this.secondNode()))
	        );
	    }
	    
	    @Override
	    public int hashCode() {
	    	return ((this.firstNode().hashCode() >> 16) << 16) | (this.secondNode().hashCode() >> 16);
	    }
	    
	    public Node firstNode() {
	    	if (node1.hashCode() <= node2.hashCode()) {
	    		return node1;
	    	} else {
	    		return node2;
	    	}
	    }
	    
	    public Node secondNode() {
	    	if (node1.hashCode() <= node2.hashCode()) {
	    		return node2;
	    	} else {
	    		return node1;
	    	}
	    }
	    
	    
	}
	
	// a cell. Must be a convex polygon.
	//TODO: actually enforce convexity
	//TODO: deleting cells will require some cleanup
	public class Cell {
	    private final Edge[] edges;
	    private final Node[] nodes;
	    private final double[][] edgeEquations;
	    private Cell(Edge[] edges, Node[] nodes) {
	    	
	    	this.edges = edges.clone();
	    	this.nodes = nodes.clone();
	    	for (Edge edge: edges) {
	    		// we want to be able to easily ask "what cells does this edge touch?"
	    		edge.cells.add(this);
	    	}

	    	this.edgeEquations = new double[this.nodes.length][3];
	    	for (int i = 0; i<this.nodes.length; i++) {
	    		Node p1 = this.nodes[i];
	    		Node p2 = this.nodes[(i+1) % this.nodes.length];
	    		
	    		// eqn for a line:
	    		// | x  y  1 |
	    		// | x1 y1 1 | = 0
	    		// | x2 y2 1 |
	    		//
	    		//   | y1 1 |     | x1 1 |     | x1 y1 |
	    		// x | y2 1 | - y | x2 1 | + 1 | x2 y2 | = 0
	    		//
	    		// so fitting the form ax+by+c = 0,
	    		//
	    		//      | y1 1 |       | x1 1 |      | x1 y1 |
	    		// a =  | y2 1 |, b = -| x2 1 |, c = | x2 y2 |
	    		
	    		double a = (p1.y)-(p2.y);
	    		double b = - ( (p1.x)-(p2.x) );
	    		double c = (p1.x*p2.y) - (p2.x*p1.y);
	    		
	    		this.edgeEquations[i][0] = a;
	    		this.edgeEquations[i][1] = b;
	    		this.edgeEquations[i][2] = c;
	    	}
	    }
	    
	    public boolean contains(Node testNode) {
	    	//go around all the nodes IN ORDER. We will build some algebraic level sets for the edges.
	    	//maintaining node order means that these expressions will all evaluate positive/negative
	    	//for points towards the centre/outside of the polygon in the same way.
	    	//We can then just evaluate our testNode in all of these expressions and check if the answers
	    	//are all of the same sign; if so, then our point is in the polygon.
	    	
	    	double testDirection = 0;
	    	for (int i = 0; i < this.nodes.length; i++) {
	    		//evaluate ax+by+c
	    		double testEvaluation =   edgeEquations[i][0]*testNode.x
	    				                + edgeEquations[i][1]*testNode.y
	    				                + edgeEquations[i][2];
	    		
	    		if (testEvaluation == 0) {
	    			// we are actually on an edge (technically the infinite extention of that edge),
	    			// if you ever played taps you would know that on the line counts as in?
	    			continue;
	    		} else if (testDirection == 0) {
	    			// must be the first time through
	    			testDirection = testEvaluation;
	    		} else if (testDirection * testEvaluation < 0) {
    				// if this direction and the first direction are of different sign
    				return false;
    			} else {
    				// no problems! check the next one.
    				continue;
    			}
	    		
	    	}
    		return true;
	    }
	}
	
	private HashSet<Node> nodes;
	private HashSet<Edge> edges;
	private HashSet<Cell> cells;
	
	protected void addCell(double... nodePoints) {
		if (nodePoints.length % 2 != 0) {
			throw new IllegalArgumentException("addCell needs as list of coordinates x,y,x,y,etc. It was provided with a list of odd size,");
		}
		
		Node[] cellNodes = new Node[nodePoints.length/2];
		
		for (int i=0; i<nodePoints.length; i+=2) {
			double x = nodePoints[i];
			double y = nodePoints[i+1];
						
			for (Node testNode : this.nodes) {
				Node nodeToAdd;
				if (testNode.distanceSq(x,y)  < NODE_SNAP_DIST) {
					nodeToAdd = testNode;
				} else {
					nodeToAdd = new Node(x,y);
					this.nodes.add(nodeToAdd);
				}
				cellNodes[i/2] = nodeToAdd;
			}
			
		}
		
		Edge[] cellEdges = new Edge[cellNodes.length];
		
		for (int i = 0; i< cellNodes.length; i++) {
			Edge newEdge = new Edge(cellNodes[i], cellNodes[(i+1) % cellNodes.length]);
			cellEdges[i] = this.edges.getOrAdd(newEdge);
		}
		
		Cell newCell = new Cell(cellEdges, cellNodes);
	}
	
	public Navmesh() {
		this.nodes = new HashSet<Node>();
		this.edges = new HashSet<Edge>();
		this.cells = new HashSet<Cell>();
	}

	

}
