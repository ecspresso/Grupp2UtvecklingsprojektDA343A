package client;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

public abstract class DefaultPanel extends JPanel {
    public final Color BAD_COLOR = new Color(255,199,206);

    private final JMenuBar menuBar = new JMenuBar();
    private final JPanel mainPanel = new JPanel(new GridBagLayout());

    public DefaultPanel(boolean showMenuBar) {
        setLayout(new BorderLayout());

        if(showMenuBar) {
            JMenu menuFile = new JMenu("File");
            menuBar.add(menuFile);

            JMenuItem menuItemExit = new JMenuItem("Exit");
            menuFile.add(menuItemExit);

            menuItemExit.addActionListener(l -> closeApplication());

            add(menuBar, BorderLayout.NORTH);
        }

        add(mainPanel, BorderLayout.CENTER);
    }

    public abstract void closeApplication();

    void add(Component component, GridBagConstraints constraints) {
        mainPanel.add(component, constraints);
    }

    // abstract void showMessage(String message);

    void addJMenu(JMenu menu) {
        menuBar.add(menu);
    }

    void dispose() {

    }

    //    void addJMenuItem(JMenuItem menuItem, JMenu menu) {
}