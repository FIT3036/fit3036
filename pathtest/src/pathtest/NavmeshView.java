package pathtest;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.GeneralPath;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.Observable;

import javax.swing.JPanel;
import javax.swing.JFrame;

import pathtest.Navmesh.Cell;

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
			
			BasicStroke stroke = new BasicStroke(0.1f);
			g.setStroke(stroke);
			g.translate(offsetX, offsetY);
			g.scale(size/10, size/10);
			
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

	@Override
	public void update(Observable o, Object arg) {
		testI++;
		System.out.println("helloooo");
		this.surface.repaint();
	}
	
	
	
	
}
