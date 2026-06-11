package SheriffsssPackage;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

public class Main {
	
	public static void main(String[] args) {
		if (args != null && args.length > 0 && "--packaging-check".equals(args[0])) {
			return;
		}
		JFrame window = new JFrame();
		window.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		window.setResizable(false);
		window.setTitle("SHERIFFSSS");
	
		Game game = new Game();
		game.setWindow(window);
		window.setIconImage(game.getAssets().getImage("sprites/sheriffsss_icono.png"));
		Runtime.getRuntime().addShutdownHook(new Thread(game::shutdown, "SheriffsssGameShutdown"));
		window.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
				game.shutdown();
				window.dispose();
				System.exit(0);
			}
		});
		window.add(game);
		window.pack();
		
		window.setLocationRelativeTo(null);
		window.setVisible(true);
		
		game.startGame();

	}

}
