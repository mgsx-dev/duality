package net.mgsx.dl3.screens;

import com.badlogic.gdx.scenes.scene2d.actions.Actions;

public class Level2Screen extends BattleScreen
{
	public Level2Screen() {
		super("level2.g3dj");
		
		bossActor.addAction(Actions.repeat(-1, Actions.sequence(
				Actions.delay(2),
				Actions.repeat(3, Actions.sequence(
						emit("front.emit", "EyeDark", false),
						Actions.delay(1))),
				Actions.delay(2),
				Actions.repeat(3, Actions.sequence(
						emit("front.emit", "EyeLight", true),
						Actions.delay(1)))
			)));
//		
//		
//		EnemyPart emit1Part = new EnemyPart();
//		emit1Part.model = bossModel;
//		emit1Part.node = bossModel.getNode("Emit1", true);
//		emit1Part.material = bossModel.getMaterial("Active");
//		emit1Part.id = collisionSystem.attachEntity(bossModel, "Active");
//		emit1Part.energy = emit1Part.energyMax = 1; // energy in seconds of beam
//		
//		enemyParts.add(emit1Part);
	}
}
