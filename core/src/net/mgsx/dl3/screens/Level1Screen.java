package net.mgsx.dl3.screens;

import com.badlogic.gdx.scenes.scene2d.actions.Actions;

import net.mgsx.dl3.model.EnemyPart;

public class Level1Screen extends BattleScreen
{
	public Level1Screen() {
		super("level1.g3dj");
		
		bossActor.addAction(Actions.repeat(-1, Actions.sequence(
				Actions.delay(2),
				animate(bossAnimator, "Boss", "Clap1Open", 1),
				Actions.repeat(3, Actions.sequence(
						emit("Emit1", "Eye", true),
						Actions.delay(1))),
				Actions.delay(2),
				animate(bossAnimator, "Boss", "Clap1Open", -1)
			)));
		
		
		EnemyPart emit1Part = new EnemyPart();
		emit1Part.model = bossModel;
		emit1Part.node = bossModel.getNode("Emit1", true);
		emit1Part.material = bossModel.getMaterial("Active");
		emit1Part.id = collisionSystem.attachEntity(bossModel, "Active");
		emit1Part.energy = emit1Part.energyMax = 1; // energy in seconds of beam
		
		enemyParts.add(emit1Part);
	}
}
