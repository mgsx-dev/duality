package net.mgsx.dl3.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

public class EnergyBar extends Table
{
	private Image img;

	public EnergyBar(Skin skin, Color color) {
		super(skin);
		
		setBackground(skin.newDrawable("white", Color.DARK_GRAY));
		
		pad(2);
		
		img = new Image(skin.newDrawable("white", color));
		add(img).expand().fill();
	}
	
	public void setValue(float value){
		img.setScaleX(MathUtils.clamp(value, 0, 1));
	}
	
}
