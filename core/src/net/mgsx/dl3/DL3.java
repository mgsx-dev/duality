package net.mgsx.dl3;

import com.badlogic.gdx.Game;

import net.mgsx.dl3.screens.BattleScreen;

public class DL3 extends Game {
	
	
	@Override
	public void create () {
		setScreen(new BattleScreen());
	}
	
}
