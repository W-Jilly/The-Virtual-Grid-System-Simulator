package gui;


import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Panel;
import java.awt.ScrollPane;
import java.util.ArrayList;

import javax.swing.JFrame;

import cluster.server.ClusterImplementation;


/**
 * 
 * The DebugPanel is a swing frame that displays information about the system. Use the
 * addStatusPanel function to add StatusPanel objects to it, and the debug panel will 
 * automatically refresh them at a set rate. 
 * 
 * @author Niels Brouwers, Boaz Pat-El
 *
 */
public class ClusterPanel extends JFrame implements Runnable {
	/**
	 * Generated serialverionUID
	 */
	private static final long serialVersionUID = 7764398835092386415L;
	
	// update at a rate of 10 frames/second 
	private static final long updateSleep = 100L;
	
	// list of status panels, used for automatic updating
	private ArrayList<StatusPanel> statusPanels;
	private Panel panelForScrollPane;
	private ScrollPane scrollPane;
	
	// update thread
	private Thread updateThread;
	private boolean running;
	
	/**
	 * Constructs a new DebugPanel object. 
	 * Adds a status panel that displays the cluster to the window.
	 * This is done so that the cluster panel will always be on top.
	 * @param cluster The cluster that is monitored by this Panel
	 */
	public ClusterPanel(ClusterImplementation cluster) {
		super("Status");
		this.setSize(340, 150);
		this.setResizable(false);
		this.setLayout(null);
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		statusPanels = new ArrayList<StatusPanel>();

		// Create the cluster status panel and add it to the frame
		ClusterStatusPanel clusterStatusPanel = new ClusterStatusPanel(cluster);
		statusPanels.add(clusterStatusPanel);
		this.add(clusterStatusPanel);
		// Place and resize the status panel
		clusterStatusPanel.setLocation(0, 0);
		clusterStatusPanel.setSize(clusterStatusPanel.getPreferredSize());
		
		// Now add the scrollpane to the frame that contains all clusterpanels
		scrollPane = new ScrollPane();
		scrollPane.setSize(getSize().width - 8, getSize().height - 32);
		this.add(scrollPane);
		// Place the scrollpane beneath the gridcluster status panel
		scrollPane.setLocation(0, clusterStatusPanel.getSize().height);
		
		// Now resize the frame so that the gridcluster status panel and
		// the scrollpane both can be displayed on the frame in full.
		setSize(getSize().width, getSize().height + clusterStatusPanel.getSize().height);
		setPreferredSize(getSize());
		
		FlowLayout layout = new FlowLayout();
		layout.setAlignment(FlowLayout.LEFT);
		
		panelForScrollPane = new Panel(layout);
		panelForScrollPane.setSize(0, 0);
		
		scrollPane.add(panelForScrollPane);
		scrollPane.setWheelScrollingEnabled(true);

		// start update thread
		running = true;
	}
	
	/**
	 * Adds a status panel to the window. Use this instead of the regular add method to make
	 * sure the panel is updated automatically. 
	 * @param statusPanel panel to add
	 */
	public void addStatusPanel(StatusPanel statusPanel)
	{
		//add(statusPanel);
		statusPanels.add(statusPanel);
		panelForScrollPane.add(statusPanel);
		
		// Automatically increase the size of the panelForScrollPane, 
		// otherwise we cannot scroll to see all the clusters
		Dimension nextDimension = panelForScrollPane.getPreferredSize();
		nextDimension.height += statusPanel.getPreferredSize().height + ClusterStatusPanel.padding + 1;
		panelForScrollPane.setPreferredSize(nextDimension);
	}
	
	/**
	 * Force an update of the window and all its panels.
	 */
	public void updatePanels() {
		update(this.getGraphics());
		
		for (StatusPanel panel : statusPanels)
			panel.update(panel.getGraphics());
	}
	
	public void start() {
		this.setVisible(true);
		updateThread = new Thread(this);
		updateThread.start();
	}

	/**
	 * Run function for the internal update thread. Do not call this externally.
	 */
	public void run() {
		while (running) {
			// update the debug window
			updatePanels();

			// sleep a while
			try {
				Thread.sleep(updateSleep);
			} catch (InterruptedException ex) {
				assert(false) : "Debug panel got interrupted";
			}
			
			// stop the update thread when the window is closed
			running = running & isVisible();
		}
		
	}
	
}
