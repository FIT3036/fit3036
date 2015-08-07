

import java.util.LinkedList;
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
	
	public class Cell {
	    private Set<Edge> edges;
	}
	
	private HashSet<Node> nodes;
	private HashSet<Edge> edges;
	private HashSet<Cell> cells;
	
	protected void addCell(Node... nodes) {
		
	}
	
	protected void addCell(double... nodePoints) {
		if (nodePoints.length % 2 != 0) {
			throw new IllegalArgumentException("addCell needs as list of coordinates x,y,x,y,etc. It was provided with a list of odd size,");
		}
		
		Node[] nodes = new Node[nodePoints.length/2];
		
		for (int i=0; i<nodePoints.length; i+=2) {
			double x = nodePoints[i];
			double y = nodePoints[i+1];
						
			for (Node testNode : this.nodes) {
				Node nodeToAdd;
				if (testNode.distanceSq(x,y)  < NODE_SNAP_DIST) {
					nodeToAdd = testNode;
				} else {
					nodeToAdd = new Node(x,y);
				}
				nodes[i/2] = nodeToAdd;
			}
			
		}
		
		Edge[] cellEdges = new Edge[nodes.length];
		
		for (int i = 0; i< nodes.length; i++) {
			Edge newEdge = new Edge(nodes[i], nodes[(i+1) % nodes.length]);
			cellEdges[i] = this.edges.getOrAdd(newEdge);
		}
		
		Cell newCell = new Cell();
	}
	
	public Navmesh() {
		this.nodes = new HashSet<Node>();
		this.edges = new HashSet<Edge>();
		this.cells = new HashSet<Cell>();
	}

	

}
