package net.mgsx.dl3.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

import net.mgsx.dl3.screens.BattleScreen;

public class GameUI extends Table
{

	private BattleScreen battle;
	private EnergyBar bossEnergy;
	private Label comboLabel;
	private EnergyBar playerEnergy;
	private EnergyBar playerCharge;

	public GameUI(BattleScreen battle, Skin skin) {
		super(skin);
		this.battle = battle;
		
		pad(4);
		
		Table topTable = new Table(skin);
		Table bottomTable = new Table(skin);
		Table footTable = new Table(skin);
		
		add(topTable).expandX().fill().row();
		add().expand().row();
		add(bottomTable).expandX().fill().row();
		add(footTable).expandX().fill().row();
		
		Label bossLabel;
		topTable.add(bossLabel = new Label("TENTACULA", skin)).padRight(32);
		bossLabel.setColor(Color.GRAY);
		topTable.add(bossEnergy = new EnergyBar(skin, Color.RED)).height(10).expandX().fill().row();
		
		bottomTable.add(label("Light Beam", Color.CYAN));
		bottomTable.add(comboLabel = new Label("", skin)).expand().center();
		bottomTable.add(label("Dark Beam", Color.RED));
		
		footTable.add("Life").padRight(32);
		footTable.add(playerEnergy = new EnergyBar(skin, Color.CYAN)).height(10).expandX().fill();
		footTable.add().width(50);
		footTable.add(playerCharge = new EnergyBar(skin, Color.WHITE)).height(10).expandX().fill();
		footTable.add("Recovery").padLeft(32);
		
	}

	private Actor label(String text, Color color) {
		Table table = new Table(getSkin());
		table.setBackground(getSkin().newDrawable("white", color));
		table.add(text).pad(4);
		return table;
	}
	
	@Override
	public void act(float delta) {
		playerCharge.setValue(battle.playerCharge);
		bossEnergy.setValue(battle.bossLife);
		playerEnergy.setValue(battle.playerLife);
		if(battle.combo > 0){
			comboLabel.setText("Combo " + battle.combo + "x");
		}else{
			comboLabel.setText("");
		}
		super.act(delta);
	}
	
}
