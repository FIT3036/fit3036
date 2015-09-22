package pathtest;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.LinkedList;
import java.util.Map;
import java.util.Observable;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.JPanel;
import javax.swing.JFrame;

import pathtest.Navmesh.Cell;
import pathtest.Navmesh.Point;

public class NavmeshView extends JFrame implements java.util.Observer {
	
	private static final long serialVersionUID = 1L;

	class Surface extends JPanel {
		
		private Navmesh navmesh;
		
		private void draw(Graphics2D outerG) {
			
			int size;
			int offsetX;
			int offsetY;
			int width = this.getWidth();
			int height = this.getHeight();
			
			size = Math.min(width, height);
			
			offsetX = (width-size)/2;
			offsetY = (height-size)/2;
			
			Graphics2D g = (Graphics2D) outerG.create();
			Rectangle2D bb = navmesh.getBoundingBox();
			BasicStroke stroke = new BasicStroke(0.5f);
			g.setStroke(stroke);
			g.translate(offsetX, offsetY);
			g.scale(size/bb.getWidth(), -size/bb.getHeight());
			g.translate(-bb.getX(), -bb.getMinY());
			
			for (Cell cell: navmesh.getCells()) {
				double[] xCoords = cell.getXpoints();
				double[] yCoords = cell.getYpoints();
				
				Path2D cellPath = new GeneralPath();
				cellPath.moveTo(xCoords[0], yCoords[0]);
				for (int i = 0; i<xCoords.length; i++) {
					cellPath.lineTo(xCoords[i], yCoords[i]);
				}
				cellPath.closePath();
				
				g.draw(cellPath);
			}
			
			if (NavmeshView.this.exploredPathDebug != null) {
				NavmeshView.this.explorationLock.lock();
				try {
					for (Point[] exploration: NavmeshView.this.exploredPathDebug) {
						Line2D exLine = new Line2D.Double();
						exLine.setLine(exploration[0], exploration[1]);
						g.draw(exLine);
					}
				} finally {
					NavmeshView.this.explorationLock.unlock();
				}
			}
			
			g.setStroke(new BasicStroke(0.2f));
			
			if (NavmeshView.this.startCell != null) {
				Point2D startPoint = NavmeshView.this.startCell.centre;
				g.setPaint(Color.GREEN);
				g.draw(new Line2D.Double(startPoint, startPoint));
			}
			
			if (NavmeshView.this.endPoint != null) {
				Point2D endPoint = NavmeshView.this.endPoint;
				g.setPaint(Color.RED);
				g.draw(new Line2D.Double(endPoint, endPoint));
			}
			g.dispose();
		}
		
		@Override
		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			this.draw((Graphics2D) g);
		}
		
		public Surface() {
			this.navmesh = NavmeshView.this.navmesh;
		}
		
	}

	private Surface surface;
	private Navmesh navmesh;
	protected int testI;
	private LinkedList<Point[]> exploredPathDebug;
	private ReentrantLock explorationLock;
	private Cell startCell;
	private Point endPoint;
	
	private void initUI() {
		surface = new Surface();
		add(surface);
		this.setTitle("Graphics Test");
		this.setSize(500,500);
		this.setLocationRelativeTo(null);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}
	
	public NavmeshView(Navmesh model) {
		this.navmesh = model;
		this.navmesh.addObserver(this);
		testI = 0;
		initUI();
		this.setVisible(true);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void update(Observable o, Object arg) {
		Map<String, Object> argument = (Map<String, Object>) arg;
		if (arg != null) {
			if (argument.containsKey("startCell"))
				this.startCell = (Cell) argument.get("startCell");

			if (argument.containsKey("endPoint"))
				this.endPoint = (Point) argument.get("endPoint");
			
			if (argument.containsKey("exploredPath")) {
				this.exploredPathDebug = (LinkedList<Point[]>) argument.get("exploredPath");
				this.explorationLock = (ReentrantLock) argument.get("explorationLock");
			}
		}
		this.surface.repaint();
	}
	
	
	
	
}
