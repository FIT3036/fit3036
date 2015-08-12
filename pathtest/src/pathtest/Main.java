package pathtest;

import java.util.List;
import pathtest.Navmesh;
import pathtest.Navmesh.Cell;
import pathtest.Navmesh.Point;

public class Main {
	
	public final static int GRID_SIZE = 10;
	
    public static void main(String[] args) {
    	Navmesh n = new Navmesh();
    	
    	Cell[][] cells = new Cell[GRID_SIZE][GRID_SIZE];
    	
    	for (int i=0; i<GRID_SIZE; i++) {
    		for (int j=0; j<GRID_SIZE; j++) {
    			if (! ((i == 4) && (j > 4 || j < 6))) {
    				cells[i][j] = n.addCell(i, j, i, j+1, i+1, j+1, i+1, j);
    			}
    		}
    	}
    	
    	for (int i=0; i<GRID_SIZE; i++) {
    		for (int j=0; j<GRID_SIZE; j++) {
    			Cell cell = cells[i][j];
    			if (cell == null) {
    				continue;
    			}
    			int neighbours = cell.neighbours().size();
    			if (neighbours < 4) {
    				System.out.println(String.format("edge cell with %d neighbours at (%f, %f)", neighbours, cell.centre.x, cell.centre.y));
    			}
    		}
    	}
    	
    	
    	Point startP = n.new Point(1.5, 5.5);
    	Point endP = n.new Point(7.5, 5.5);
    	
    	if (cells[1][5].contains(startP)) {
    		System.out.println("yay");
    	} else {
    		System.out.println(":'(");
    	}
    	
    	if (n.getCellContaining(endP) == cells[7][5]) { 
    		System.out.println("yay2");
    	} else {
    		System.out.println("oh no");
    	}
    	
    	List<Cell> path = n.aStar(n.getCellContaining(startP), endP);
    	
    	for (Cell cell : path) {
    		System.out.println(String.format("Going through (%s, %s)", cell.centre.x, cell.centre.y));
    	}
    }
}
