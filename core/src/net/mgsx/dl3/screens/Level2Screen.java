package net.mgsx.dl3.screens;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;

import net.mgsx.dl3.model.EnemyPart;

public class Level2Screen extends BattleScreen
{
	private ModelInstance groundModel;
	private EnemyPart frontPart;
	private EnemyPart handLeftPart;
	private EnemyPart handRightPart;
	private EnemyPart legAPart;
	private EnemyPart legBPart;
	private EnemyPart legCPart;
	private EnemyPart backLeftPart;
	private EnemyPart backRightPart;
	private EnemyPart internalPart;

	public Level2Screen() {
		super("level2.g3dj");
		
		models.add(groundModel = new ModelInstance(levelModel, "Ground"));
		
		collisionSystem.attachEntity(groundModel);
		
		frontPart = createPart("front.emit", "Front", false);
		handLeftPart = createPart("hand.l", "HandLeft", true);
		handRightPart = createPart("hand.r", "HandRight", false);
		legAPart = createPart(null, "LegA", false);
		legBPart = createPart(null, "LegB", true);
		legCPart = createPart(null, "LegC", false);
		backLeftPart = createPart("back.emit.l", "BackLeft", false);
		backRightPart = createPart("back.emit.r", "BackRight", true);
		internalPart = createPart(null, "Internal", null);
		
		internalPart.material = bossModel.getMaterial("Boss");
		
		sequenceFront();
		
		sequenceBackLeft();
		sequenceBackRight();
		sequenceTentacleLeft();
		sequenceTentacleRight();
		
		
		
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
	
	private EnemyPart createPart(String boneID, String materialID, Boolean light){
		EnemyPart part = new EnemyPart();
		part.model = bossModel;
		part.node = boneID == null ? null : bossModel.getNode(boneID, true);
		part.material = bossModel.getMaterial(materialID);
		part.id = collisionSystem.attachEntity(bossModel, materialID);
		part.energy = part.energyMax = PART_ENERGY;
		part.light = light;
		enemyParts.add(part);
		return part;
	}
	
	private void sequenceFront()
	{
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
	}
	
	private void sequenceTentacleLeft()
	{
		bossActor.addAction(Actions.repeat(-1, Actions.sequence(
				Actions.delay(2),
				Actions.repeat(3, Actions.sequence(
						emit("hand.l", "EyeLight", true),
						Actions.delay(1)))
			)));
	}
	
	private void sequenceTentacleRight()
	{
		bossActor.addAction(Actions.repeat(-1, Actions.sequence(
				Actions.delay(2),
				Actions.repeat(3, Actions.sequence(
						emit("hand.r", "EyeDark", false),
						Actions.delay(1)))
			)));
	}
	
	private void sequenceBackLeft()
	{
		bossActor.addAction(Actions.repeat(-1, Actions.sequence(
				Actions.delay(2),
				Actions.repeat(3, Actions.sequence(
						emit("back.emit.l", "EyeDark", false),
						Actions.delay(1)))
			)));
	}
	
	private void sequenceBackRight()
	{
		bossActor.addAction(Actions.repeat(-1, Actions.sequence(
				Actions.delay(2),
				Actions.repeat(3, Actions.sequence(
						emit("back.emit.r", "EyeLight", true),
						Actions.delay(1)))
			)));
	}
}
